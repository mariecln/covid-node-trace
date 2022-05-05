package com.covid.nodetrace.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi


object NetworkHelper {

    private var connectionState : ConnectionState = ConnectionState.DISCONNECTED

    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED
    }

    /**
     * Checks if the device is connected to the internet
     */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connectionManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectionManager.activeNetworkInfo ?: return false

        if (networkInfo.isConnected)
            connectionState = ConnectionState.CONNECTED
        else {
            connectionState = ConnectionState.DISCONNECTED
        }
        return networkInfo.isConnected
    }

    /**
     * Sets a listener that is updated when a network connected changes between 'Available' and 'Lost'
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun registerNetworkCallback(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val builder = NetworkRequest.Builder()
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)

            val networkRequest = builder.build()
            connectivityManager.registerNetworkCallback(networkRequest,
                object : ConnectivityManager.NetworkCallback () {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        connectionState = ConnectionState.CONNECTED
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        connectionState = ConnectionState.DISCONNECTED
                    }
                })

        } catch (e: Exception) {
            connectionState = ConnectionState.DISCONNECTED
        }
    }
}