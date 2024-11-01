package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import org.session.libsession.database.userAuth
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsession.snode.OwnedSwarmAuth
import org.session.libsession.snode.SwarmAuth
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject

private const val TAG = "PushRegistrationHandler"

/**
 * A class that listens to the config, user's preference, token changes and
 * register/unregister push notification accordingly.
 *
 * This class DOES NOT handle the legacy groups push notification.
 */
class PushRegistrationHandler
@Inject
constructor(
    private val pushRegistry: PushRegistryV2,
    private val configFactory: ConfigFactory,
    private val preferences: TextSecurePreferences,
    private val storage: Storage,
    private val tokenFetcher: TokenFetcher,
) {
    @OptIn(DelicateCoroutinesApi::class)
    private val scope: CoroutineScope = GlobalScope

    private var job: Job? = null

    @OptIn(FlowPreview::class)
    fun run() {
        require(job == null) { "Job is already running" }

        job = scope.launch(Dispatchers.Default) {
            combine(
                (configFactory.configUpdateNotifications as Flow<Any>)
                    .debounce(500L)
                    .onStart { emit(Unit) },
                IdentityKeyUtil.CHANGES.onStart { emit(Unit) },
                preferences.pushEnabled,
                tokenFetcher.token,
            ) { _, _, enabled, token ->
                if (!enabled || token.isNullOrEmpty()) {
                    return@combine emptyMap<SubscriptionKey, Subscription>()
                }

                val userAuth =
                    storage.userAuth ?: return@combine emptyMap<SubscriptionKey, Subscription>()
                getGroupSubscriptions(
                    token = token
                ) + mapOf(
                    SubscriptionKey(userAuth.accountId, token) to Subscription(userAuth, 0)
                )
            }
                .scan<Map<SubscriptionKey, Subscription>, Pair<Map<SubscriptionKey, Subscription>, Map<SubscriptionKey, Subscription>>?>(
                    null
                ) { acc, current ->
                    val prev = acc?.second.orEmpty()
                    prev to current
                }
                .filterNotNull()
                .collect { (prev, current) ->
                    val addedAccountIds = current.keys - prev.keys
                    val removedAccountIDs = prev.keys - current.keys
                    if (addedAccountIds.isNotEmpty()) {
                        Log.d(TAG, "Adding ${addedAccountIds.size} new subscriptions")
                    }

                    if (removedAccountIDs.isNotEmpty()) {
                        Log.d(TAG, "Removing ${removedAccountIDs.size} subscriptions")
                    }

                    val deferred = mutableListOf<Deferred<*>>()

                    addedAccountIds.mapTo(deferred) { key ->
                        val subscription = current.getValue(key)
                        async {
                            try {
                                pushRegistry.register(
                                    token = key.token,
                                    swarmAuth = subscription.auth,
                                    namespaces = listOf(subscription.namespace)
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to register for push notification", e)
                            }
                        }
                    }

                    removedAccountIDs.mapTo(deferred) { key ->
                        val subscription = prev.getValue(key)
                        async {
                            try {
                                pushRegistry.unregister(
                                    token = key.token,
                                    swarmAuth = subscription.auth,
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to unregister for push notification", e)
                            }
                        }
                    }

                    deferred.awaitAll()
                }
        }
    }

    private fun getGroupSubscriptions(
        token: String
    ): Map<SubscriptionKey, Subscription> {
        return buildMap {
            val groups = configFactory.withUserConfigs { it.userGroups.allClosedGroupInfo() }
            for (group in groups) {
                val adminKey = group.adminKey
                if (adminKey != null && adminKey.isNotEmpty()) {
                    put(
                        SubscriptionKey(group.groupAccountId, token),
                        Subscription(
                            auth = OwnedSwarmAuth.ofClosedGroup(group.groupAccountId, adminKey),
                            namespace = Namespace.GROUPS()
                        )
                    )
                    continue
                }

                val authData = group.authData
                if (authData != null && authData.isNotEmpty()) {
                    val subscription = configFactory.getGroupAuth(group.groupAccountId)
                        ?.let {
                            Subscription(
                                auth = it,
                                namespace = Namespace.GROUPS()
                            )
                        }

                    if (subscription != null) {
                        put(SubscriptionKey(group.groupAccountId, token), subscription)
                    }
                }
            }
        }
    }

    private data class SubscriptionKey(val accountId: AccountId, val token: String)
    private data class Subscription(val auth: SwarmAuth, val namespace: Int)
}