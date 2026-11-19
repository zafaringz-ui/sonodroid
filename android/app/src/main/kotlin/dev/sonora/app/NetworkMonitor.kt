package dev.sonora.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.sonora.core.model.NetworkKind

/**
 * Feeds `state/network.rs` (plan §3): the core needs to know whether a lossless
 * stream is acceptable right now, and playback must pause when the network drops.
 */
class NetworkMonitor(
    context: Context,
    private val onChanged: (NetworkKind) -> Unit,
) {
    private val connectivity =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = report()

        override fun onLost(network: Network) = report()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = report()

        private fun report() = onChanged(current())
    }

    fun start() {
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
        onChanged(current())
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    private fun current(): NetworkKind {
        val manager = connectivity ?: return NetworkKind.OFFLINE
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return NetworkKind.OFFLINE
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                NetworkKind.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && hasInternet ->
                NetworkKind.WIFI
            hasInternet -> NetworkKind.WIFI
            else -> NetworkKind.OFFLINE
        }
    }
}
