package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.database.ConfigDatabase

@Module
@InstallIn(ActivityRetainedComponent::class)
object SessionUtilModule {

    private fun maybeUserEdSecretKey(context: Context): ByteArray? {
        val edKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return null
        return edKey.secretKey.asBytes
    }

    @Provides
    @ActivityRetainedScoped
    fun provideUser(@ApplicationContext context: Context, configDatabase: ConfigDatabase): Optional<UserProfile> =
        maybeUserEdSecretKey(context)?.let { key ->
            // also get the currently stored dump
            val instance = UserProfile.newInstance(key)
            Optional.of(instance)
        } ?: Optional.absent()

}