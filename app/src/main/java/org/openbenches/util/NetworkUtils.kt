package org.openbenches.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun isNetworkAvailable(context: Context): Boolean = hasValidatedInternet(context)

/** True when the default network has a validated internet route. */
fun hasValidatedInternet(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

fun isAirplaneModeOn(context: Context): Boolean =
    Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.AIRPLANE_MODE_ON,
        0,
    ) != 0

/** True when the device cannot reach the internet (airplane mode, no validated network, etc.). */
fun isDeviceOffline(context: Context): Boolean =
    isAirplaneModeOn(context) || !hasValidatedInternet(context)

/** Updates when airplane mode or connectivity changes. */
@Composable
fun rememberDeviceOffline(): Boolean {
    val context = LocalContext.current
    var offline by remember { mutableStateOf(isDeviceOffline(context)) }
    DisposableEffect(context) {
        fun refresh() {
            offline = isDeviceOffline(context)
        }
        refresh()

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = refresh()
        }
        if (connectivityManager != null) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } catch (_: Exception) {
            }
        }

        val airplaneReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = refresh()
        }
        ContextCompat.registerReceiver(
            context,
            airplaneReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val activity = context as? ComponentActivity
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        activity?.lifecycle?.addObserver(lifecycleObserver)

        onDispose {
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            if (connectivityManager != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                } catch (_: Exception) {
                }
            }
            try {
                context.unregisterReceiver(airplaneReceiver)
            } catch (_: Exception) {
            }
        }
    }
    return offline
}

/** @see rememberDeviceOffline */
@Composable
fun rememberNetworkAvailable(): Boolean = !rememberDeviceOffline()
