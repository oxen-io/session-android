package org.thoughtcrime.securesms.notifications

interface PushManager {
    fun refresh(force: Boolean)
}