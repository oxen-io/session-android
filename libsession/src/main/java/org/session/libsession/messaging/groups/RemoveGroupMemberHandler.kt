package org.session.libsession.messaging.groups

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.GroupMember
import network.loki.messenger.libsession_util.util.Sodium
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.GroupUpdated
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateMessage
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace

private const val TAG = "RemoveGroupMemberHandler"

private const val MIN_PROCESS_INTERVAL_MILLS = 1_000L

class RemoveGroupMemberHandler(
    private val configFactory: ConfigFactoryProtocol,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    init {
        scope.launch {
            while (true) {
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
        val userGroups = checkNotNull(configFactory.userGroups) {
            "User groups config is null"
        }

        for (group in userGroups.allClosedGroupInfo()) {
            // Only proceed if we are the admin of the group
            val adminKey = group.adminKey ?: continue

            configFactory.withGroupConfigsOrNull(group.groupAccountId) withConfig@ { info, members, keys ->
                val pendingRemovals = members.all().filter { it.removed }
                if (pendingRemovals.isEmpty()) {
                    // Skip if there are no pending removals
                    return@withConfig
                }

                Log.d(TAG, "Processing ${pendingRemovals.size} pending removals for group ${group.name}")

                // Perform a sequential calls to snode to:
                // 1. Revoke the member's sub key (by adding the key to a "revoked list")
                // 2. Send a message to a special namespace to inform the removed members they have been removed
                // 3. Conditionally, remove the message from the group's message store, if that option is selected by the deleting admin
                val seqCalls = ArrayList<SnodeAPI.SnodeBatchRequestInfo>(3)

                // Call No 1. Revoke sub-key
                val revokeMessage = SnodeAPI.buildAuthenticatedRevokeSubKeyBatchRequest(
                    publicKeyDestination = group.groupAccountId.hexString,
                    signingKey = adminKey,
                    subAccounts = Array(pendingRemovals.size) {
                        keys.getSubAccountToken(AccountId(pendingRemovals[it].sessionId))
                    }
                )

                if (revokeMessage != null) {
                    seqCalls += revokeMessage
                }

                // Call No 2. Send a message to the removed members
                seqCalls += SnodeAPI.buildAuthenticatedStoreBatchInfo(
                    namespace = Namespace.REVOKED_GROUP_MESSAGES(),
                    message = buildGroupKickMessage(group, pendingRemovals, keys, adminKey),
                    signingKey = adminKey,
                )

                // Call No 3. Conditionally remove the message from the group's message store
                if (pendingRemovals.any { it.shouldRemoveMessages }) {
                    seqCalls += SnodeAPI.buildAuthenticatedStoreBatchInfo(
                        namespace = Namespace.CLOSED_GROUP_MESSAGES(),
                        message = buildDeleteGroupMemberContentMessage(
                            groupId = group.groupAccountId.hexString,
                            memberSessionIDs = pendingRemovals
                                .asSequence()
                                .filter { it.shouldRemoveMessages }
                                .map { it.sessionId }
                        ),
                        signingKey = adminKey,
                    )
                }

                // Make the call:
                SnodeAPI.getSingleTargetSnode(group.groupAccountId.hexString)
            }
        }
    }

    private fun buildDeleteGroupMemberContentMessage(
        groupId: String,
        memberSessionIDs: Sequence<String>
    ): SnodeMessage {
        return MessageSender.buildWrappedMessageToSnode(
            destination = Destination.ClosedGroup(groupId),
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
        group: GroupInfo.ClosedGroupInfo,
        pendingRemovals: List<GroupMember>,
        keys: GroupKeysConfig,
        adminKey: ByteArray
    ) = SnodeMessage(
        recipient = group.groupAccountId.hexString,
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
        ttl = SnodeMessage.CONFIG_TTL,
        timestamp = SnodeAPI.nowWithOffset
    )
}

private inline fun <T: Any> ConfigFactoryProtocol.withGroupConfigsOrNull(
    groupId: AccountId,
    cb: (GroupInfoConfig, GroupMembersConfig, GroupKeysConfig) -> T
): T? {
    getGroupInfoConfig(groupId)?.use { groupInfo ->
        getGroupMemberConfig(groupId)?.use { groupMembers ->
            getGroupKeysConfig(groupId, groupInfo, groupMembers)?.use { groupKeys ->
                return cb(groupInfo, groupMembers, groupKeys)
            }
        }
    }

    return null
}