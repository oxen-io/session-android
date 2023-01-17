package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideConfigFactory(@ApplicationContext context: Context, configDatabase: ConfigDatabase): ConfigFactory =
        ConfigFactory(configDatabase) {
            maybeUserEdSecretKey(context)
        }

}