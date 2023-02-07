package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.database.ConfigDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionUtilModule {

    private fun maybeUserEdSecretKey(context: Context): ByteArray? {
        val edKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return null
        return edKey.secretKey.asBytes
    }

    @Provides
    @Singleton
    fun provideConfigFactory(@ApplicationContext context: Context, configDatabase: ConfigDatabase, storage: StorageProtocol): ConfigFactory =
        ConfigFactory(context, configDatabase, storage) {
            val localUserPublicKey = TextSecurePreferences.getLocalNumber(context)
            val secretKey = maybeUserEdSecretKey(context)
            if (localUserPublicKey == null || secretKey == null) null
            else secretKey to localUserPublicKey
        }

}