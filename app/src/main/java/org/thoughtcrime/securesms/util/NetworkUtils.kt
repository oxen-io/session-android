package org.thoughtcrime.securesms.util

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkUtils {

    companion object {

        // Method to determine if we have a valid Internet connection or not
        fun haveValidNetworkConnection(context: Context) : Boolean {
            val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            // Early exit if we have no active network..
            if (cm.activeNetwork == null) return false

            // ..otherwise determine what capabilities are available to the active network.
            val networkCapabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            val internetConnectionValid = cm.activeNetwork != null    &&
                    networkCapabilities != null &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            return internetConnectionValid
        }
    }
}