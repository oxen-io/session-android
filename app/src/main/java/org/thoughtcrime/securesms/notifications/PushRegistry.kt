package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.goterl.lazysodium.utils.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.combine.and
import org.session.libsession.messaging.sending_receiving.notifications.PushRegistryV1
import org.session.libsession.utilities.Device
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.SessionId
import org.session.libsignal.utilities.emptyPromise
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val TAG = PushRegistry::class.java.name

@Singleton
class PushRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: Device,
    private val tokenManager: TokenManager,
    private val pushRegistryV2: PushRegistryV2,
    private val prefs: TextSecurePreferences,
    private val tokenFetcher: TokenFetcher,
    private val configFactory: ConfigFactory
) {

    private var pushRegistrationJob: Job? = null
    private val pushGroupJobs = ConcurrentHashMap<String, Job>()

    fun refresh(force: Boolean): Job {
        Log.d(TAG, "refresh() called with: force = $force")

        pushRegistrationJob?.apply {
            if (force) cancel() else if (isActive) return MainScope().launch {}
        }

        return MainScope().launch(Dispatchers.IO) {
            try {
                register(tokenFetcher.fetch()).get()
            } catch (e: Exception) {
                Log.e(TAG, "register failed", e)
            }
        }.also { pushRegistrationJob = it }
    }

    fun register(token: String?): Promise<*, Exception> {
        Log.d(TAG, "register() called")

        if (token?.isNotEmpty() != true) return emptyPromise()

        prefs.setPushToken(token)

        val userPublicKey = prefs.getLocalNumber() ?: return emptyPromise()
        val userEdKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return emptyPromise()

        return when {
            prefs.isPushEnabled() -> register(token, userPublicKey, userEdKey)
            tokenManager.isRegistered -> unregister(token, userPublicKey, userEdKey)
            else -> emptyPromise()
        }
    }

    fun registerForGroup(groupSessionId: SessionId) {
        val groupHexString = groupSessionId.hexString()
        val authData = configFactory.userGroups?.getClosedGroup(groupHexString)?.authData ?: return
        val keysConfig = configFactory.getGroupKeysConfig(groupSessionId) ?: return

        MainScope().launch(Dispatchers.IO) {
            try {
                val token = tokenFetcher.fetch() ?: return@launch

                pushRegistryV2.registerGroup(
                    device,
                    token,
                    groupHexString,
                    authData,
                    keysConfig
                ).get()
            } catch (e: Exception) {
                Log.e(TAG, "register group failed", e)
            }
        }.also { newJob ->
            // replace old one by returned current value in concurrent hashmap
            pushGroupJobs.replace(groupHexString, newJob)?.cancel()
        }
    }

    fun unregisterForGroup(groupSessionId: SessionId) {
        val groupHexString = groupSessionId.hexString()
        val authData = configFactory.userGroups?.getClosedGroup(groupHexString)?.authData ?: return
        val keysConfig = configFactory.getGroupKeysConfig(groupSessionId) ?: return

        MainScope().launch(Dispatchers.IO) {
            try {
                val token = tokenFetcher.fetch() ?: return@launch

                pushRegistryV2.unregisterGroup(
                    device,
                    token,
                    groupHexString,
                    authData,
                    keysConfig
                )
            } catch (e: Exception) {
                Log.e(TAG, "unregister group failed", e)
            }
        }.also { newJob ->
            // replace old one by returned current value in concurrent hashmap
            pushGroupJobs.replace(groupHexString, newJob)?.cancel()
        }
    }

    /**
     * Register for push notifications.
     */
    private fun register(
        token: String,
        publicKey: String,
        userEd25519Key: KeyPair,
        namespaces: List<Int> = listOf(Namespace.DEFAULT())
    ): Promise<*, Exception> {
        Log.d(TAG, "register() called")

        val v1 = PushRegistryV1.register(
            device = device,
            token = token,
            publicKey = publicKey
        ) fail {
            Log.e(TAG, "register v1 failed", it)
        }

        val v2 = pushRegistryV2.register(
            device, token, publicKey, userEd25519Key, namespaces
        ) fail {
            Log.e(TAG, "register v2 failed", it)
        }

        return v1 and v2 success {
            Log.d(TAG, "register v1 & v2 success")
            tokenManager.register()
        }
    }

    private fun unregister(
        token: String,
        userPublicKey: String,
        userEdKey: KeyPair
    ): Promise<*, Exception> = PushRegistryV1.unregister() and pushRegistryV2.unregister(
        device, token, userPublicKey, userEdKey
    ) fail {
        Log.e(TAG, "unregisterBoth failed", it)
    } success {
        tokenManager.unregister()
    }
}
