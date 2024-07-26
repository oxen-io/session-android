package org.thoughtcrime.securesms.service

import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.PhoneStateListener.LISTEN_NONE
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.webrtc.HangUpRtcOnPstnCallAnsweredListener
import org.thoughtcrime.securesms.webrtc.HangUpRtcTelephonyCallback
import java.util.concurrent.ExecutorService

internal interface TelephonyHandler {
    fun register(telephonyManager: TelephonyManager)
    fun unregister(telephonyManager: TelephonyManager)
}

internal fun TelephonyHandler(serviceExecutor: ExecutorService, callback: () -> Unit) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    TelephonyHandlerV31(serviceExecutor, callback)
} else {
    TelephonyHandlerV23(callback)
}

@RequiresApi(Build.VERSION_CODES.S)
private class TelephonyHandlerV31(val serviceExecutor: ExecutorService, callback: () -> Unit): TelephonyHandler {
    private val callback = HangUpRtcTelephonyCallback(callback)

    override fun register(telephonyManager: TelephonyManager) {
        telephonyManager.registerTelephonyCallback(serviceExecutor, callback)
    }

    override fun unregister(telephonyManager: TelephonyManager) {
        telephonyManager.unregisterTelephonyCallback(callback)
    }
}

private class TelephonyHandlerV23(callback: () -> Unit): TelephonyHandler {
    val callback = HangUpRtcOnPstnCallAnsweredListener(callback)

    override fun register(telephonyManager: TelephonyManager) {
        telephonyManager.listen(callback, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun unregister(telephonyManager: TelephonyManager) {
        telephonyManager.listen(callback, LISTEN_NONE)
    }
}
