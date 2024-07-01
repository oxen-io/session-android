package org.thoughtcrime.securesms.onboarding

data class LinkDeviceState(
    val recoveryPhrase: String = "",
    val error: String? = null
)
