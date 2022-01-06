package org.thoughtcrime.securesms.dependencies

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.session.libsession.utilities.AppTextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindTextSecurePreferences(preferences: AppTextSecurePreferences): TextSecurePreferences

}