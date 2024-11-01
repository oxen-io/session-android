package org.thoughtcrime.securesms.dependencies

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.service.ExpiringMessageManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {

    @Binds
    abstract fun bindStorageProtocol(storage: Storage): StorageProtocol

    @Binds
    abstract fun bindLokiAPIDatabaseProtocol(lokiAPIDatabase: LokiAPIDatabase): LokiAPIDatabaseProtocol

    @Binds
    abstract fun bindMessageExpirationManagerProtocol(manager: ExpiringMessageManager): SSKEnvironment.MessageExpirationManagerProtocol

}