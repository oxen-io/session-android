package org.thoughtcrime.securesms.dependencies

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import network.loki.messenger.libsession_util.UserProfile
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.KeyPairUtilities

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class SessionUtilModule {

    private fun maybeUserEdSecretKey(context: ApplicationContext): ByteArray? {
        val edKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return null
        return edKey.secretKey.asBytes
    }

    @Provides
    @ActivityRetainedScoped
    fun provideUser(context: ApplicationContext): UserProfile {
        val key = maybeUserEdSecretKey(context)
        return UserProfile.newInstance(key ?: byteArrayOf())
    }



}