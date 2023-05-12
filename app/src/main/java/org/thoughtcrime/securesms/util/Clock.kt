package org.thoughtcrime.securesms.util

interface Clock {
    val currentTimeMillis: Long
}

object AndroidClock: Clock {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
}
