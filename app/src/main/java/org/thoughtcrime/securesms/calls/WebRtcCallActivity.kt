package org.thoughtcrime.securesms.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Outline
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityWebrtcBinding
import org.apache.commons.lang3.time.DurationFormatUtils
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.WebRtcCallService
import org.thoughtcrime.securesms.util.AvatarPlaceholderGenerator
import org.thoughtcrime.securesms.webrtc.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.CallViewModel
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_CONNECTED
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_INCOMING
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_OUTGOING
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_PRE_INIT
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_RECONNECTING
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_RINGING
import org.thoughtcrime.securesms.webrtc.Orientation
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.EARPIECE
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.SPEAKER_PHONE
import kotlin.math.asin

@AndroidEntryPoint
class WebRtcCallActivity : PassphraseRequiredActionBarActivity() {

    companion object {
        const val ACTION_PRE_OFFER = "pre-offer"
        const val ACTION_FULL_SCREEN_INTENT = "fullscreen-intent"
        const val ACTION_ANSWER = "answer"
        const val ACTION_END = "end-call"

        const val BUSY_SIGNAL_DELAY_FINISH = 5500L

        private const val CALL_DURATION_FORMAT = "HH:mm:ss"
    }

    private val viewModel by viewModels<CallViewModel>()
    private val glide by lazy { GlideApp.with(this) }
    private lateinit var binding: ActivityWebrtcBinding
    private var uiJob: Job? = null
    private var wantsToAnswer = false
        set(value) {
            field = value
            WebRtcCallService.broadcastWantsToAnswer(this, value)
        }
    private var hangupReceiver: BroadcastReceiver? = null

    /**
     * We need to track the device's orientation so we can calculate whether or not to rotate the video streams
     * This works a lot better than using `OrientationEventListener > onOrientationChanged'
     * which gives us a rotation angle that doesn't take into account pitch vs roll, so tipping the device from front to back would
     * trigger the video rotation logic, while we really only want it when the device is in portrait or landscape.
     */
    private var orientationManager = OrientationManager(this)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_ANSWER) {
            val answerIntent = WebRtcCallService.acceptCallIntent(this)
            answerIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            ContextCompat.startForegroundService(this, answerIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        binding = ActivityWebrtcBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        if (intent.action == ACTION_ANSWER) {
            answerCall()
        }
        if (intent.action == ACTION_PRE_OFFER) {
            wantsToAnswer = true
            answerCall() // this will do nothing, except update notification state
        }
        if (intent.action == ACTION_FULL_SCREEN_INTENT) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }

        binding.floatingRendererContainer.setOnClickListener {
            viewModel.swapVideos()
        }

        binding.microphoneButton.setOnClickListener {
            val audioEnabledIntent =
                WebRtcCallService.microphoneIntent(this, !viewModel.microphoneEnabled)
            startService(audioEnabledIntent)
        }

        binding.speakerPhoneButton.setOnClickListener {
            val command =
                AudioManagerCommand.SetUserDevice(if (viewModel.isSpeaker) EARPIECE else SPEAKER_PHONE)
            WebRtcCallService.sendAudioManagerCommand(this, command)
        }

        binding.acceptCallButton.setOnClickListener {
            if (viewModel.currentCallState == CALL_PRE_INIT) {
                wantsToAnswer = true
                updateControls()
            }
            answerCall()
        }

        binding.declineCallButton.setOnClickListener {
            val declineIntent = WebRtcCallService.denyCallIntent(this)
            startService(declineIntent)
        }

        hangupReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                finish()
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(hangupReceiver!!, IntentFilter(ACTION_END))

        binding.enableCameraButton.setOnClickListener {
            Permissions.with(this)
                .request(Manifest.permission.CAMERA)
                .onAllGranted {
                    val intent = WebRtcCallService.cameraEnabled(this, !viewModel.videoState.value.userVideoEnabled)
                    startService(intent)
                }
                .execute()
        }

        binding.switchCameraButton.setOnClickListener {
            startService(WebRtcCallService.flipCamera(this))
        }

        binding.endCallButton.setOnClickListener {
            startService(WebRtcCallService.hangupIntent(this))
        }
        binding.backArrow.setOnClickListener {
            onBackPressed()
        }

        lifecycleScope.launch {
            orientationManager.orientation.collect { orientation ->
                viewModel.deviceOrientation = orientation
                updateControlsRotation()
            }
        }

        clipFloatingInsets()
    }

    /**
     * Makes sure the floating video inset has clipped rounded corners, included with the video stream itself
     */
    private fun clipFloatingInsets() {
        // clip the video inset with rounded corners
        val videoInsetProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // all corners
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    resources.getDimensionPixelSize(R.dimen.video_inset_radius).toFloat()
                )
            }
        }

        binding.floatingRendererContainer.outlineProvider = videoInsetProvider
        binding.floatingRendererContainer.clipToOutline = true
    }

    override fun onResume() {
        super.onResume()
        orientationManager.startOrientationListener()

    }

    override fun onPause() {
        super.onPause()
        orientationManager.stopOrientationListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        hangupReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        }

        orientationManager.destroy()
    }

    private fun answerCall() {
        val answerIntent = WebRtcCallService.acceptCallIntent(this)
        ContextCompat.startForegroundService(this, answerIntent)
    }

    private fun updateControlsRotation() {
        with (binding) {
            val rotation = when(viewModel.deviceOrientation){
                Orientation.LANDSCAPE -> -90f
                Orientation.REVERSED_LANDSCAPE -> 90f
                else -> 0f
            }

            remoteRecipient.animate().cancel()
            remoteRecipient.animate().rotation(rotation).start()

            speakerPhoneButton.animate().cancel()
            speakerPhoneButton.animate().rotation(rotation).start()

            microphoneButton.animate().cancel()
            microphoneButton.animate().rotation(rotation).start()

            enableCameraButton.animate().cancel()
            enableCameraButton.animate().rotation(rotation).start()

            switchCameraButton.animate().cancel()
            switchCameraButton.animate().rotation(rotation).start()

            endCallButton.animate().cancel()
            endCallButton.animate().rotation(rotation).start()
        }
    }

    private fun updateControls(state: CallViewModel.State? = null) {
        with(binding) {
            if (state == null) {
                if (wantsToAnswer) {
                    controlGroup.isVisible = true
                    remoteLoadingView.isVisible = true
                    incomingControlGroup.isVisible = false
                }
            } else {
                controlGroup.isVisible = state in listOf(
                    CALL_CONNECTED,
                    CALL_OUTGOING,
                    CALL_INCOMING
                ) || (state == CALL_PRE_INIT && wantsToAnswer)
                remoteLoadingView.isVisible =
                    state !in listOf(CALL_CONNECTED, CALL_RINGING, CALL_PRE_INIT) || wantsToAnswer
                incomingControlGroup.isVisible =
                    state in listOf(CALL_RINGING, CALL_PRE_INIT) && !wantsToAnswer
                reconnectingText.isVisible = state == CALL_RECONNECTING
                endCallButton.isVisible = endCallButton.isVisible || state == CALL_RECONNECTING
            }
        }
    }

    override fun onStart() {
        super.onStart()

        uiJob = lifecycleScope.launch {

            launch {
                viewModel.audioDeviceState.collect { state ->
                    val speakerEnabled = state.selectedDevice == SPEAKER_PHONE
                    // change drawable background to enabled or not
                    binding.speakerPhoneButton.isSelected = speakerEnabled
                }
            }

            launch {
                viewModel.callState.collect { state ->
                    Log.d("Loki", "Consuming view model state $state")
                    when (state) {
                        CALL_RINGING -> if (wantsToAnswer) {
                            answerCall()
                            wantsToAnswer = false
                        }
                        CALL_CONNECTED -> wantsToAnswer = false
                        else -> {}
                    }
                    updateControls(state)
                }
            }

            launch {
                viewModel.recipient.collect { latestRecipient ->
                    if (latestRecipient.recipient != null) {
                        val publicKey = latestRecipient.recipient.address.serialize()
                        val displayName = getUserDisplayName(publicKey)
                        supportActionBar?.title = displayName
                        val signalProfilePicture = latestRecipient.recipient.contactPhoto
                        val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject
                        val sizeInPX =
                            resources.getDimensionPixelSize(R.dimen.extra_large_profile_picture_size)
                        binding.remoteRecipientName.text = displayName
                        if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                            glide.clear(binding.remoteRecipient)
                            glide.load(signalProfilePicture)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .circleCrop()
                                .error(
                                    AvatarPlaceholderGenerator.generate(
                                        this@WebRtcCallActivity,
                                        sizeInPX,
                                        publicKey,
                                        displayName
                                    )
                                )
                                .into(binding.remoteRecipient)
                        } else {
                            glide.clear(binding.remoteRecipient)
                            glide.load(
                                AvatarPlaceholderGenerator.generate(
                                    this@WebRtcCallActivity,
                                    sizeInPX,
                                    publicKey,
                                    displayName
                                )
                            )
                                .diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop()
                                .into(binding.remoteRecipient)
                        }
                    } else {
                        glide.clear(binding.remoteRecipient)
                    }
                }
            }

            launch {
                while (isActive) {
                    val startTime = viewModel.callStartTime
                    if (startTime == -1L) {
                        binding.callTime.isVisible = false
                    } else {
                        binding.callTime.isVisible = true
                        binding.callTime.text = DurationFormatUtils.formatDuration(
                            System.currentTimeMillis() - startTime,
                            CALL_DURATION_FORMAT
                        )
                    }

                    delay(1_000)
                }
            }

            launch {
                viewModel.localAudioEnabledState.collect { isEnabled ->
                    // change drawable background to enabled or not
                    binding.microphoneButton.isSelected = !isEnabled
                }
            }

            // handle video state
            launch {
                viewModel.videoState.collect { state ->
                    binding.floatingRenderer.removeAllViews()
                    binding.fullscreenRenderer.removeAllViews()

                    // the floating video inset (empty or not) should be shown
                    // the moment we have either of the video streams
                    val showFloatingContainer = state.userVideoEnabled || state.remoteVideoEnabled
                    binding.floatingRendererContainer.isVisible = showFloatingContainer
                    binding.swapViewIcon.isVisible = showFloatingContainer

                    // handle fullscreen video window
                    if(state.showFullscreenVideo()){
                        viewModel.fullscreenRenderer?.let { surfaceView ->
                            binding.fullscreenRenderer.addView(surfaceView)
                            binding.fullscreenRenderer.isVisible = true
                            binding.remoteRecipient.isVisible = false
                        }
                    } else {
                        binding.fullscreenRenderer.isVisible = false
                        binding.remoteRecipient.isVisible = true
                    }

                    // handle floating video window
                    if(state.showFloatingVideo()){
                        viewModel.floatingRenderer?.let { surfaceView ->
                            binding.floatingRenderer.addView(surfaceView)
                            binding.floatingRenderer.isVisible = true
                            binding.swapViewIcon.bringToFront()
                        }
                    } else {
                        binding.floatingRenderer.isVisible = false
                    }

                    // handle buttons
                    binding.enableCameraButton.isSelected = state.userVideoEnabled
                }
            }
        }
    }

    private fun getUserDisplayName(publicKey: String): String {
        val contact =
            DatabaseComponent.get(this).sessionContactDatabase().getContactWithSessionID(publicKey)
        return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
    }

    override fun onStop() {
        super.onStop()
        uiJob?.cancel()
        binding.fullscreenRenderer.removeAllViews()
        binding.floatingRenderer.removeAllViews()
    }
}