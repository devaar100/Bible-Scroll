package aarnav100.developer.biblescroll.core.content.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Whether a validated internet connection exists right now, and whether it's unmetered (e.g. Wi-Fi). */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>

    /** Online on an unmetered network: safe for prefetching. */
    val isUnmetered: Flow<Boolean>
}

class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = capabilityFlow { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }

    override val isUnmetered: Flow<Boolean> = capabilityFlow { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }

    private fun capabilityFlow(test: (NetworkCapabilities) -> Boolean): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(false)
            awaitClose()
            return@callbackFlow
        }
        fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && test(it)
        } ?: false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(current()) }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
