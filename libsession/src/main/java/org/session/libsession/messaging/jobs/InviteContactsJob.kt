package org.session.libsession.messaging.jobs

import android.widget.Toast
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.session.libsession.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.GroupUpdated
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.messaging.utilities.MessageAuthentication.buildGroupInviteSignature
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.OTHER_NAME_KEY
import org.session.libsession.utilities.getClosedGroup
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateInviteMessage
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.GroupUpdateMessage
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.prettifiedDescription

class InviteContactsJob(val groupSessionId: String, val memberSessionIds: Array<String>) : Job {

    companion object {
        const val KEY = "InviteContactJob"
        private const val GROUP = "group"
        private const val MEMBER = "member"

    }

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override suspend fun execute(dispatcherName: String) {
        val configs = MessagingModuleConfiguration.shared.configFactory
        val adminKey = requireNotNull(configs.withUserConfigs { it.userGroups.getClosedGroup(groupSessionId) }?.adminKey) {
            "User must be admin of group to invite"
        }

        val sessionId = AccountId(groupSessionId)

        coroutineScope {
            val requests = memberSessionIds.map { memberSessionId ->
                async {
                    runCatching {
                        // Make the request for this member
                        val memberId = AccountId(memberSessionId)
                        val (groupName, subAccount) = configs.withMutableGroupConfigs(sessionId) { configs ->
                            configs.groupMembers.set(
                                configs.groupMembers.getOrConstruct(
                                    memberSessionId
                                ).setInvited()
                            )
                            configs.groupInfo.getName() to configs.groupKeys.makeSubAccount(memberId)
                        }

                        val timestamp = SnodeAPI.nowWithOffset
                        val signature = SodiumUtilities.sign(
                            buildGroupInviteSignature(memberId, timestamp),
                            adminKey
                        )

                        val groupInvite = GroupUpdateInviteMessage.newBuilder()
                            .setGroupSessionId(groupSessionId)
                            .setMemberAuthData(ByteString.copyFrom(subAccount))
                            .setAdminSignature(ByteString.copyFrom(signature))
                            .setName(groupName)
                        val message = GroupUpdateMessage.newBuilder()
                            .setInviteMessage(groupInvite)
                            .build()
                        val update = GroupUpdated(message).apply {
                            sentTimestamp = timestamp
                        }

                        MessageSender.send(update, Destination.Contact(memberSessionId), false)
                            .await()
                    }
                }
            }

            val results = memberSessionIds.zip(requests.awaitAll())

            configs.withMutableGroupConfigs(sessionId) { configs ->
                results.forEach { (memberSessionId, result) ->
                    if (result.isFailure) {
                        configs.groupMembers.get(memberSessionId)?.let { member ->
                            configs.groupMembers.set(member.setInviteFailed())
                        }
                    }
                }
            }

            val groupName = configs.withGroupConfigs(sessionId) { it.groupInfo.getName() }
                ?: configs.getClosedGroup(sessionId)?.name

            val failures = results.filter { it.second.isFailure }
            // if there are failed invites, display a message
            // assume job "success" even if we fail, the state of invites is tracked outside of this job
            if (failures.isNotEmpty()) {
                // show the failure toast
                val storage = MessagingModuleConfiguration.shared.storage
                val toaster = MessagingModuleConfiguration.shared.toaster
                when (failures.size) {
                    1 -> {
                        val (memberId, _) = failures.first()
                        val firstString = storage.getContactWithAccountID(memberId)?.name
                            ?: truncateIdForDisplay(memberId)
                        withContext(Dispatchers.Main) {
                            toaster.toast(R.string.groupInviteFailedUser, Toast.LENGTH_LONG,
                                mapOf(
                                    NAME_KEY to firstString,
                                    GROUP_NAME_KEY to groupName.orEmpty()
                                )
                            )
                        }
                    }
                    2 -> {
                        val (first, second) = failures
                        val firstString = first.first.let { storage.getContactWithAccountID(it) }?.name
                            ?: truncateIdForDisplay(first.first)
                        val secondString = second.first.let { storage.getContactWithAccountID(it) }?.name
                            ?: truncateIdForDisplay(second.first)

                        withContext(Dispatchers.Main) {
                            toaster.toast(R.string.groupInviteFailedTwo, Toast.LENGTH_LONG,
                                mapOf(
                                    NAME_KEY to firstString,
                                    OTHER_NAME_KEY to secondString,
                                    GROUP_NAME_KEY to groupName.orEmpty()
                                )
                            )
                        }
                    }
                    else -> {
                        val first = failures.first()
                        val firstString = first.first.let { storage.getContactWithAccountID(it) }?.name
                            ?: truncateIdForDisplay(first.first)
                        val remaining = failures.size - 1
                        withContext(Dispatchers.Main) {
                            toaster.toast(R.string.groupInviteFailedMultiple, Toast.LENGTH_LONG,
                                mapOf(
                                    NAME_KEY to firstString,
                                    OTHER_NAME_KEY to remaining.toString(),
                                    GROUP_NAME_KEY to groupName.orEmpty()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override fun serialize(): Data =
        Data.Builder()
            .putString(GROUP, groupSessionId)
            .putStringArray(MEMBER, memberSessionIds)
            .build()

    override fun getFactoryKey(): String = KEY

}