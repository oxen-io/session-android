package org.session.libsession.messaging.groups

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import network.loki.messenger.libsession_util.ReadableGroupKeysConfig
import network.loki.messenger.libsession_util.util.GroupMember
import network.loki.messenger.libsession_util.util.Sodium
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.GroupUpdated
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.OwnedSwarmAuth
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.waitUntilGroupConfigsPushed
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateMessage
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import javax.inject.Inject

private const val TAG = "RemoveGroupMemberHandler"

private const val MIN_PROCESS_INTERVAL_MILLS = 1_000L

/**
 * This handler is responsible for processing pending group member removals.
 *
 * It automatically does so by listening to the config updates changes and checking for any pending removals.
 */
class RemoveGroupMemberHandler @Inject constructor(
    private val configFactory: ConfigFactoryProtocol,
    private val textSecurePreferences: TextSecurePreferences,
    private val groupManager: GroupManagerV2,
) {
    private var job: Job? = null

    fun start() {
        require(job == null) { "Already started" }

        job = GlobalScope.launch {
            while (true) {
                // Make sure we have a local number before we start processing
                textSecurePreferences.watchLocalNumber().first { it != null }

                val processStartedAt = SystemClock.uptimeMillis()

                try {
                    processPendingMemberRemoval()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing pending member removal", e)
                }

                configFactory.configUpdateNotifications.firstOrNull()

                // Make sure we don't process too often. As some of the config changes don't apply
                // to us, but we have no way to tell if it does or not. The safest way is to process
                // everytime any config changes, with a minimum interval.
                val delayMills =
                    MIN_PROCESS_INTERVAL_MILLS - (SystemClock.uptimeMillis() - processStartedAt)

                if (delayMills > 0) {
                    delay(delayMills)
                }
            }
        }
    }

    private suspend fun processPendingMemberRemoval() {
        configFactory.withUserConfigs { it.userGroups.allClosedGroupInfo() }
            .asSequence()
            .filter { it.hasAdminKey() }
            .forEach { group ->
                processPendingRemovalsForGroup(group.groupAccountId, group.adminKey!!)
            }
    }

    private suspend fun processPendingRemovalsForGroup(
        groupAccountId: AccountId,
        adminKey: ByteArray
    ) {
        val groupAuth = OwnedSwarmAuth.ofClosedGroup(groupAccountId, adminKey)

        val (pendingRemovals, batchCalls) = configFactory.withGroupConfigs(groupAccountId) { configs ->
            val pendingRemovals = configs.groupMembers.all().filter { it.removed }
            if (pendingRemovals.isEmpty()) {
                // Skip if there are no pending removals
                return@withGroupConfigs pendingRemovals to emptyList()
            }

            Log.d(TAG, "Processing ${pendingRemovals.size} pending removals for group")

            // Perform a sequential call to group snode to:
            // 1. Revoke the member's sub key (by adding the key to a "revoked list" under the hood)
            // 2. Send a message to a special namespace on the group to inform the removed members they have been removed
            // 3. Conditionally, send a `GroupUpdateDeleteMemberContent` to the group so the message deletion
            //    can be performed by everyone in the group.
            val calls = ArrayList<SnodeAPI.SnodeBatchRequestInfo>(3)

            // Call No 1. Revoke sub-key. This call is crucial and must not fail for the rest of the operation to be successful.
            calls += checkNotNull(
                SnodeAPI.buildAuthenticatedRevokeSubKeyBatchRequest(
                    groupAdminAuth = groupAuth,
                    subAccountTokens = pendingRemovals.map {
                        configs.groupKeys.getSubAccountToken(AccountId(it.sessionId))
                    }
                )
            ) { "Fail to create a revoke request" }

            // Call No 2. Send a "kicked" message to the revoked namespace
            calls += SnodeAPI.buildAuthenticatedStoreBatchInfo(
                namespace = Namespace.REVOKED_GROUP_MESSAGES(),
                message = buildGroupKickMessage(groupAccountId.hexString, pendingRemovals, configs.groupKeys, adminKey),
                auth = groupAuth,
            )

            // Call No 3. Conditionally send the `GroupUpdateDeleteMemberContent`
            if (pendingRemovals.any { it.shouldRemoveMessages }) {
                calls += SnodeAPI.buildAuthenticatedStoreBatchInfo(
                    namespace = Namespace.CLOSED_GROUP_MESSAGES(),
                    message = buildDeleteGroupMemberContentMessage(
                        groupAccountId = groupAccountId.hexString,
                        memberSessionIDs = pendingRemovals
                            .asSequence()
                            .filter { it.shouldRemoveMessages }
                            .map { it.sessionId }
                    ),
                    auth = groupAuth,
                )
            }

            pendingRemovals to (calls as List<SnodeAPI.SnodeBatchRequestInfo>)
        }

        if (pendingRemovals.isEmpty() || batchCalls.isEmpty()) {
            return
        }

        val node = SnodeAPI.getSingleTargetSnode(groupAccountId.hexString).await()
        val response = SnodeAPI.getBatchResponse(node, groupAccountId.hexString, batchCalls, sequence = true)

        val firstError = response.results.firstOrNull { !it.isSuccessful }
        check(firstError == null) {
            "Error processing pending removals for group: code = ${firstError?.code}, body = ${firstError?.body}"
        }

        Log.d(TAG, "Essential steps for group removal are done")

        // The essential part of the operation has been successful once we get to this point,
        // now we can go ahead and update the configs
        configFactory.withMutableGroupConfigs(groupAccountId) { configs ->
            pendingRemovals.forEach(configs.groupMembers::erase)
            configs.rekey()
        }

        configFactory.waitUntilGroupConfigsPushed(groupAccountId)

        Log.d(TAG, "Group configs updated")

        // Try to delete members' message. It's ok to fail as they will be re-tried in different
        // cases (a.k.a the GroupUpdateDeleteMemberContent message handling) and could be by different admins.
        val deletingMessagesForMembers = pendingRemovals.filter { it.shouldRemoveMessages }
        if (deletingMessagesForMembers.isNotEmpty()) {
            try {
                groupManager.removeMemberMessages(
                    groupAccountId,
                    deletingMessagesForMembers.map { AccountId(it.sessionId) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting messages for removed members", e)
            }
        }
    }

    private fun buildDeleteGroupMemberContentMessage(
        groupAccountId: String,
        memberSessionIDs: Sequence<String>
    ): SnodeMessage {
        return MessageSender.buildWrappedMessageToSnode(
            destination = Destination.ClosedGroup(groupAccountId),
            message = GroupUpdated(
                GroupUpdateMessage.newBuilder()
                    .setDeleteMemberContent(
                        SignalServiceProtos.DataMessage.GroupUpdateDeleteMemberContentMessage
                            .newBuilder()
                            .apply {
                                for (id in memberSessionIDs) {
                                    addMemberSessionIds(id)
                                }
                            }
                    )
                    .build()
            ),
            isSyncMessage = false
        )
    }

    private fun buildGroupKickMessage(
        groupAccountId: String,
        pendingRemovals: List<GroupMember>,
        keys: ReadableGroupKeysConfig,
        adminKey: ByteArray
    ) = SnodeMessage(
        recipient = groupAccountId,
        data = Base64.encodeBytes(
            Sodium.encryptForMultipleSimple(
                messages = Array(pendingRemovals.size) {
                    "${pendingRemovals[it].sessionId}${keys.currentGeneration()}".encodeToByteArray()
                },
                recipients = Array(pendingRemovals.size) {
                    AccountId(pendingRemovals[it].sessionId).pubKeyBytes
                },
                ed25519SecretKey = adminKey,
                domain = Sodium.KICKED_DOMAIN
            )
        ),
        ttl = SnodeMessage.DEFAULT_TTL,
        timestamp = SnodeAPI.nowWithOffset
    )
}

