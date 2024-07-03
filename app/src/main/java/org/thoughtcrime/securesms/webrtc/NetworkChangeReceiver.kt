package org.thoughtcrime.securesms.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import org.session.libsignal.utilities.Log

class NetworkChangeReceiver(private val onNetworkChangedCallback: (Boolean)->Unit) {

    companion object {

        // Method to check if a valid Internet connection is available or not
        fun haveValidNetworkConnection(context: Context) : Boolean {
            val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.activeNetwork != null
        }
    }

    private val networkList: MutableSet<Network> = mutableSetOf()

    private val broadcastDelegate = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            receiveBroadcast(context, intent)
        }
    }

    val defaultObserver = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("Loki", "onAvailable: $network")
            networkList += network
            onNetworkChangedCallback(networkList.isNotEmpty())
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            Log.i("Loki", "onLosing: $network, maxMsToLive: $maxMsToLive")
        }

        override fun onLost(network: Network) {
            Log.i("Loki", "onLost: $network")
            networkList -= network
            onNetworkChangedCallback(networkList.isNotEmpty())
        }

        override fun onUnavailable() {
            Log.i("Loki", "onUnavailable")
        }
    }

    fun receiveBroadcast(context: Context, intent: Intent) {
        val connected = haveValidNetworkConnection(context)
        Log.i("Loki", "received broadcast, network connected: $connected")
        onNetworkChangedCallback(connected)
    }

    fun register(context: Context) {
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        context.registerReceiver(broadcastDelegate, intentFilter)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//            cm.registerDefaultNetworkCallback(defaultObserver)
//        } else {
//
//        }
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(broadcastDelegate)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//            cm.unregisterNetworkCallback(defaultObserver)
//        } else {
//
//        }
    }

}