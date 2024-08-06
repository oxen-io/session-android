package org.thoughtcrime.securesms.onboarding.manager

import android.app.Application
import org.session.libsession.snode.SnodeModule
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.VersionDataFetcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateAccountManager @Inject constructor(
    private val application: Application,
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory,
    private val versionDataFetcher: VersionDataFetcher
) {
    private val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage

    fun createAccount(displayName: String) {
        prefs.setProfileName(displayName)

        // This is here to resolve a case where the app restarts before a user completes onboarding
        // which can result in an invalid database state
        database.clearAllLastMessageHashes()
        database.clearReceivedMessageHashValues()

        val keyPairGenerationResult = KeyPairUtilities.generate()
        val seed = keyPairGenerationResult.seed
        val ed25519KeyPair = keyPairGenerationResult.ed25519KeyPair
        val x25519KeyPair = keyPairGenerationResult.x25519KeyPair

        KeyPairUtilities.store(application, seed, ed25519KeyPair, x25519KeyPair)
        val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
        val registrationID = KeyHelper.generateRegistrationId(false)
        prefs.setLocalRegistrationId(registrationID)
        prefs.setLocalNumber(userHexEncodedPublicKey)
        prefs.setRestorationTime(0)

        configFactory.keyPairChanged()
        configFactory.user?.setName(displayName)
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(application)

        versionDataFetcher.startTimedVersionCheck()
    }
}