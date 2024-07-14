package org.thoughtcrime.securesms.webrtc

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(private val callManager: CallManager): ViewModel() {

    enum class State {
        CALL_PENDING,

        CALL_PRE_INIT,
        CALL_INCOMING,
        CALL_OUTGOING,
        CALL_CONNECTED,
        CALL_RINGING,
        CALL_BUSY,
        CALL_DISCONNECTED,
        CALL_RECONNECTING,

        NETWORK_FAILURE,
        RECIPIENT_UNAVAILABLE,
        NO_SUCH_USER,
        UNTRUSTED_IDENTITY,
    }

    val floatingRenderer: SurfaceViewRenderer?
    get() = callManager.floatingRenderer

    val fullscreenRenderer: SurfaceViewRenderer?
    get() = callManager.fullscreenRenderer

    private var _microphoneEnabled: Boolean = true

    val microphoneEnabled: Boolean
        get() = _microphoneEnabled

    private var _isSpeaker: Boolean = false
    val isSpeaker: Boolean
        get() = _isSpeaker

    val audioDeviceState
        get() = callManager.audioDeviceEvents
                .onEach {
                    _isSpeaker = it.selectedDevice == SignalAudioManager.AudioDevice.SPEAKER_PHONE
                }

    val localAudioEnabledState
        get() = callManager.audioEvents.map { it.isEnabled }
            .onEach { _microphoneEnabled = it }

    val videoState: StateFlow<VideoState>
        get() = callManager.videoState

    var deviceOrientation: Orientation = Orientation.UNKNOWN
        set(value) {
            field = value
            callManager.setDeviceOrientation(value)
        }

    val currentCallState
        get() = callManager.currentCallState

    val callState
        get() = callManager.callStateEvents

    val recipient
        get() = callManager.recipientEvents

    val callStartTime: Long
        get() = callManager.callStartTime

    fun swapVideos() {
       callManager.swapVideos()
    }
}