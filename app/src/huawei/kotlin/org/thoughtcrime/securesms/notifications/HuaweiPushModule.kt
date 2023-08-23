package org.thoughtcrime.securesms.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HuaweiBindingModule {
    @Binds
    abstract fun bindTokenFetcher(tokenFetcher: HuaweiTokenFetcher): TokenFetcher
}
