package org.thoughtcrime.securesms.webrtc

data class VideoState (
    val swapped: Boolean,
    val userVideoEnabled: Boolean,
    val remoteVideoEnabled: Boolean
){
    fun showFloatingVideo(): Boolean {
        return userVideoEnabled && !swapped ||
                remoteVideoEnabled && swapped
    }

    fun showFullscreenVideo(): Boolean {
        return userVideoEnabled && swapped ||
                remoteVideoEnabled && !swapped
    }
}