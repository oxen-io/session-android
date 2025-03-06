package org.thoughtcrime.securesms.util

import android.app.Application
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a flow that emits `true` when the device has internet connectivity and `false` otherwise.
 */
@Singleton
class InternetConnectivity @Inject constructor(application: Application) {
    val networkAvailable = callbackFlow {
        val connectivityManager = application.getSystemService(ConnectivityManager::class.java)

        val callback = object : NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.v("InternetConnectivity", "Network capabilities changed: hasInternet? $hasInternet")
                trySend(hasInternet)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.v("InternetConnectivity", "Network become lost")
                trySend(false)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.stateIn(
        scope = GlobalScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = haveValidNetworkConnection(application)
    )


    companion object {
        // Method to determine if we have a valid Internet connection or not
        private fun haveValidNetworkConnection(context: Context): Boolean {
            val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            // Early exit if we have no active network..
            val activeNetwork = cm.activeNetwork ?: return false

            // ..otherwise determine what capabilities are available to the active network.
            val networkCapabilities = cm.getNetworkCapabilities(activeNetwork)
            val internetConnectionValid = networkCapabilities != null &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            return internetConnectionValid
        }
    }

}
