package org.thoughtcrime.securesms.conversation.start.newmessage

internal interface Callbacks {
    fun onChange(value: String) {}
    fun onContinue() {}
    fun onScanQrCode(value: String) {}
}
