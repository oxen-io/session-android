package org.thoughtcrime.securesms.dependencies

import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPoller
import org.session.libsession.snode.SnodeClock
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.AccountId
import java.util.concurrent.ConcurrentHashMap

class PollerFactory(
    private val scope: CoroutineScope,
    private val executor: CoroutineDispatcher,
    private val configFactory: ConfigFactory,
    private val groupManagerV2: Lazy<GroupManagerV2>,
    private val storage: Lazy<StorageProtocol>,
    private val lokiApiDatabase: LokiAPIDatabaseProtocol,
    private val clock: SnodeClock,
    ) {

    private val pollers = ConcurrentHashMap<AccountId, ClosedGroupPoller>()

    fun pollerFor(sessionId: AccountId): ClosedGroupPoller? {
        // Check if the group is currently in our config and approved, don't start if it isn't
        val invited = configFactory.withUserConfigs {
            it.userGroups.getClosedGroup(sessionId.hexString)?.invited
        }

        if (invited != false) return null

        return pollers.getOrPut(sessionId) {
            ClosedGroupPoller(
                scope = scope,
                executor = executor,
                closedGroupSessionId = sessionId,
                configFactoryProtocol = configFactory,
                groupManagerV2 = groupManagerV2.get(),
                storage = storage.get(),
                lokiApiDatabase = lokiApiDatabase,
                clock = clock,
            )
        }
    }

    fun startAll() {
        configFactory
            .withUserConfigs { it.userGroups.allClosedGroupInfo() }
            .filterNot(GroupInfo.ClosedGroupInfo::invited)
            .forEach { pollerFor(it.groupAccountId)?.start() }
    }

    fun stopAll() {
        pollers.forEach { (_, poller) ->
            poller.stop()
        }
    }

    fun updatePollers() {
        val currentGroups = configFactory
            .withUserConfigs { it.userGroups.allClosedGroupInfo() }.filterNot(GroupInfo.ClosedGroupInfo::invited)
        val toRemove = pollers.filter { (id, _) -> id !in currentGroups.map { it.groupAccountId } }
        toRemove.forEach { (id, _) ->
            pollers.remove(id)?.stop()
        }
        startAll()
    }

}