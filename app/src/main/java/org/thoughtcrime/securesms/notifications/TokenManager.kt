package org.thoughtcrime.securesms.notifications

import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

private const val INTERVAL: Int = 12 * 60 * 60 * 1000

@Singleton
class TokenManager @Inject constructor(
    private val prefs: TextSecurePreferences
) {
    val hasValidRegistration get() = isRegistered && !isExpired
    val isRegistered get() = time > 0
    private val isExpired get() = currentTime() > time + INTERVAL

    fun register() {
        time = currentTime()
    }

    fun unregister() {
        time = 0
    }

    private var time
        get() = prefs.getPushRegisterTime()
        set(value) = prefs.setPushRegisterTime(value)

    private fun currentTime() = System.currentTimeMillis()
}
