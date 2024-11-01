package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.Lazy
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
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.snode.SnodeClock
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.thoughtcrime.securesms.database.ConfigDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import javax.inject.Named
import javax.inject.Singleton

@Suppress("OPT_IN_USAGE")
@Module
@InstallIn(SingletonComponent::class)
object SessionUtilModule {

    private const val POLLER_SCOPE = "poller_coroutine_scope"

    @Provides
    @Named(POLLER_SCOPE)
    fun providePollerScope(): CoroutineScope = GlobalScope

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Named(POLLER_SCOPE)
    fun provideExecutor(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    @Provides
    @Singleton
    fun providePollerFactory(@Named(POLLER_SCOPE) coroutineScope: CoroutineScope,
                             @Named(POLLER_SCOPE) dispatcher: CoroutineDispatcher,
                             configFactory: ConfigFactory,
                             storage: Lazy<StorageProtocol>,
                             groupManagerV2: Lazy<GroupManagerV2>,
                             lokiApiDatabase: LokiAPIDatabaseProtocol,
                             clock: SnodeClock) = PollerFactory(
        scope = coroutineScope,
        executor = dispatcher,
        configFactory = configFactory,
        groupManagerV2 = groupManagerV2,
        storage = storage,
        lokiApiDatabase = lokiApiDatabase,
        clock = clock,
    )

    @Provides
    @Singleton
    fun provideSnodeClock() = SnodeClock()
}