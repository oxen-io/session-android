package org.thoughtcrime.securesms.util

import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    val currentTimeMillis: Long
}

@Singleton
class AndroidClock @Inject constructor(): Clock {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
}
