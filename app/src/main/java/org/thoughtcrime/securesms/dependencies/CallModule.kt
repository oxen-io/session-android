package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.session.libsession.database.StorageProtocol
import org.thoughtcrime.securesms.webrtc.CallManager
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CallModule {

    @Provides
    @Singleton
    fun provideAudioManagerCompat(@ApplicationContext context: Context) = AudioManagerCompat.create(context)

    @Provides
    @Singleton
    fun provideCallManager(@ApplicationContext context: Context, audioManagerCompat: AudioManagerCompat, storage: StorageProtocol) =
            CallManager(context, audioManagerCompat, storage)

}