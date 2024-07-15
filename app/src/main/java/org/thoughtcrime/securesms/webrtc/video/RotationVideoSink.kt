package org.thoughtcrime.securesms.webrtc.video


import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicBoolean

class RotationVideoSink: CapturerObserver, VideoProcessor {

    var rotation: Int = 0

    private val capturing = AtomicBoolean(false)
    private var capturerObserver = SoftReference<CapturerObserver>(null)
    private var sink = SoftReference<VideoSink>(null)

    override fun onCapturerStarted(ignored: Boolean) {
        capturing.set(true)
    }

    override fun onCapturerStopped() {
        capturing.set(false)
    }

    override fun onFrameCaptured(videoFrame: VideoFrame?) {
        // rotate if need
        val observer = capturerObserver.get()
        if (videoFrame == null || observer == null || !capturing.get()) return

        // cater for frame rotation so that the video is always facing up as we rotate pas a certain point
        val newFrame = VideoFrame(videoFrame.buffer, videoFrame.rotation - rotation, videoFrame.timestampNs)

        // the frame we are sending to our contact needs to cater for rotation
        observer.onFrameCaptured(newFrame)

        // the frame we see on the user's phone doesn't require changes
        sink.get()?.onFrame(videoFrame)
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = SoftReference(sink)
    }

    fun setObserver(videoSink: CapturerObserver?) {
        capturerObserver = SoftReference(videoSink)
    }
}