package org.thoughtcrime.securesms.util

import android.content.Context
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.prefs
import java.io.IOException
import java.lang.RuntimeException

object VersionTracker {

    @JvmStatic
    fun getLastSeenVersion(context: Context): Int {
        var version = context.prefs.getLastVersionCode()
        // Zero means the app is freshly installed = user is actually on the current version.
        if (version == 0) {
            version = updateLastSeenVersion(context)
        }
        return version
    }

    @JvmStatic
    fun updateLastSeenVersion(context: Context): Int {
        return try {
            val currentVersionCode = Util.getCanonicalVersionCode()
            context.prefs.setLastVersionCode(currentVersionCode)
            currentVersionCode
        } catch (e: IOException) {
            throw RuntimeException("Failed to update the last seen app version.", e)
        }
    }
}