package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.session.libsession.utilities.ConfigFactoryUpdateListener
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.database.ConfigDatabase
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionUtilModule {

    const val POLLER_SCOPE = "poller_coroutine_scope"

    private fun maybeUserEdSecretKey(context: Context): ByteArray? {
        val edKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return null
        return edKey.secretKey.asBytes
    }

    @Provides
    @Singleton
    fun provideConfigFactory(@ApplicationContext context: Context, configDatabase: ConfigDatabase): ConfigFactory =
        ConfigFactory(context, configDatabase) {
            val localUserPublicKey = TextSecurePreferences.getLocalNumber(context)
            val secretKey = maybeUserEdSecretKey(context)
            if (localUserPublicKey == null || secretKey == null) null
            else secretKey to localUserPublicKey
        }.apply {
            registerListener(context as ConfigFactoryUpdateListener)
        }

    @Provides
    @Named(POLLER_SCOPE)
    fun providePollerScope(@ApplicationContext applicationContext: Context): CoroutineScope = GlobalScope

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Named(POLLER_SCOPE)
    fun provideExecutor(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    @Provides
    @Singleton
    fun providePollerFactory(@Named(POLLER_SCOPE) coroutineScope: CoroutineScope,
                             @Named(POLLER_SCOPE) dispatcher: CoroutineDispatcher,
                             configFactory: ConfigFactory) = PollerFactory(coroutineScope, dispatcher, configFactory)

}