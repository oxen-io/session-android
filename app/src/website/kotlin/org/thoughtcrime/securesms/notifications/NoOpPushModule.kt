package org.thoughtcrime.securesms.notifications

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NoOpPushModule {
    @Binds
    abstract fun bindTokenFetcher(tokenFetcher: NoOpTokenFetcher): TokenFetcher
}
