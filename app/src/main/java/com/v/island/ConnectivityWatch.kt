package com.v.island

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.json.JSONObject

/**
 * What the phone is *connected* to, which is the Status bubble's whole subject and the line
 * between it and the Now bubble: Now carries what is happening, Status carries what is
 * attached. One payload rather than one per source, because the bubble draws them together —
 * a link, whatever is riding on top of it, and the charge of whatever is paired.
 *
 * Everything here is a broadcast or a callback and nothing is polled. A status bar that woke
 * up twice a second to ask whether anything had changed would be the one thing on this phone
 * running all day for nothing.
 */
object ConnectivityWatch {

    /** The tethering broadcast, which is public knowledge but has no constant on the SDK. */
    private const val TETHER_STATE = "android.net.conn.TETHER_STATE_CHANGED"
    private const val TETHER_ACTIVE = "tetherArray"

    /**
     * A paired device's charge, which the platform has carried since Oreo and has never made
     * public: the action and its extra are what SystemUI's own battery meter reads, and asking
     * `BluetoothDevice` for it is a hidden method that is blocked. A broadcast has no such
     * problem, and unheard it simply means the level stays unknown rather than wrong.
     */
    private const val BLUETOOTH_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val BLUETOOTH_BATTERY_EXTRA = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private var receiver: BroadcastReceiver? = null
    private var networks: ConnectivityManager.NetworkCallback? = null
    private var report: ((JSONObject) -> Unit)? = null

    private var link = "none"
    private var level = -1
    private var isUsbConnected = false
    private var isHotspotOn = false
    private var pairedName: String? = null
    private var pairedCharge = -1

    fun start(context: Context, onChange: (JSONObject) -> Unit) {
        if (receiver != null) return
        report = onChange

        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = hear(context, intent)
        }
        receiver = listener
        context.registerReceiver(
            listener,
            IntentFilter().apply {
                // Sticky, so registering is also the first read: the USB state arrives immediately.
                addAction("android.hardware.usb.action.USB_STATE")
                addAction(TETHER_STATE)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BLUETOOTH_BATTERY)
            }
        )

        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, seen: NetworkCapabilities) {
                readLink(context, seen)
                publish()
            }

            // The default network going away is a real answer and the only thing that says
            // there is no link at all — capabilities never arrive to say it.
            override fun onLost(network: Network) {
                link = "none"
                level = -1
                publish()
            }
        }
        networks = callback
        // The *default* network rather than every network: what the phone is actually using is
        // one answer, and a request for all of them reports a wifi that is up but unrouted.
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        networks?.let { callback ->
            runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        }
        networks = null
        report = null
        link = "none"
        level = -1
        isUsbConnected = false
        isHotspotOn = false
        pairedName = null
        pairedCharge = -1
    }

    private fun hear(context: Context, intent: Intent) {
        when (intent.action) {
            "android.hardware.usb.action.USB_STATE" ->
                isUsbConnected = intent.getBooleanExtra("connected", false)

            TETHER_STATE ->
                isHotspotOn = !intent.getStringArrayListExtra(TETHER_ACTIVE).isNullOrEmpty()

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                pairedName = nameOf(intent)
                pairedCharge = -1
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                pairedName = null
                pairedCharge = -1
            }

            // The adapter itself going off takes whatever was on it with it; a device does not
            // always get to say goodbye when the radio is what was switched off.
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                if (state != BluetoothAdapter.STATE_ON) {
                    pairedName = null
                    pairedCharge = -1
                }
            }

            BLUETOOTH_BATTERY -> {
                val charge = intent.getIntExtra(BLUETOOTH_BATTERY_EXTRA, -1)
                if (charge in 0..100) {
                    if (pairedName == null) pairedName = nameOf(intent)
                    pairedCharge = charge
                }
            }
        }
        publish()
    }

    /**
     * A device's name needs BLUETOOTH_CONNECT from Android 12 on, and this app is sideloaded —
     * it may or may not have been granted. Unnamed is a device that is still connected, so the
     * bubble says so with its icon and leaves the name out, rather than the pairing vanishing
     * because a permission is missing.
     */
    private fun nameOf(intent: Intent): String? {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        return runCatching { device?.name }.getOrNull()
    }

    private fun readLink(context: Context, seen: NetworkCapabilities) {
        link = when {
            seen.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            seen.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            seen.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "none"
        }
        // Wifi is the one whose strength is readable without a dangerous permission: the
        // capabilities carry the RSSI and WifiManager knows what the bars mean. A cellular
        // level needs READ_PHONE_STATE, which is not worth a runtime prompt for a glyph, so
        // mobile draws its own icon at full strength and says which link it is instead.
        level = if (link == "wifi") {
            val rssi = seen.signalStrength
            val wifi = context.getSystemService(WifiManager::class.java)
            if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) -1
            else wifi.calculateSignalLevel(rssi).coerceIn(0, wifi.maxSignalLevel)
        } else {
            -1
        }
    }

    private fun publish() {
        val paired = pairedName?.let {
            JSONObject().put("name", it).put("charge", pairedCharge)
        } ?: if (pairedCharge >= 0) JSONObject().put("charge", pairedCharge) else null
        report?.invoke(
            JSONObject()
                .put("link", link)
                .put("level", level)
                .put("usb", isUsbConnected)
                .put("hotspot", isHotspotOn)
                .put("bluetooth", paired ?: JSONObject.NULL)
        )
    }
}
