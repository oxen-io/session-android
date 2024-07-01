package org.thoughtcrime.securesms.onboarding.loading

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.session.libsession.snode.SnodeModule
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadingManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val configFactory: ConfigFactory,
    private val prefs: TextSecurePreferences
) {
    private val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage

    private var restoreJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    fun load(seed: ByteArray) {
        // only have one sync job running at a time (prevent QR from trying to spawn a new job)
        if (restoreJob?.isActive == true) return

        restoreJob = scope.launch {
            // This is here to resolve a case where the app restarts before a user completes onboarding
            // which can result in an invalid database state
            database.clearAllLastMessageHashes()
            database.clearReceivedMessageHashValues()

            // RestoreActivity handles seed this way
            val keyPairGenerationResult = KeyPairUtilities.generate(seed)
            val x25519KeyPair = keyPairGenerationResult.x25519KeyPair
            KeyPairUtilities.store(context, seed, keyPairGenerationResult.ed25519KeyPair, x25519KeyPair)
            configFactory.keyPairChanged()
            val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
            val registrationID = org.session.libsignal.utilities.KeyHelper.generateRegistrationId(false)
            prefs.apply {
                setLocalRegistrationId(registrationID)
                setLocalNumber(userHexEncodedPublicKey)
                setRestorationTime(System.currentTimeMillis())
                setHasViewedSeed(true)
            }

            ApplicationContext.getInstance(context).apply { startPollingIfNeeded() }
        }
    }
}
