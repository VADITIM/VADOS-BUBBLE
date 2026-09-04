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
    /**
     * The name of whichever mode is currently keeping the phone quiet. One UI's Modes are one
     * automatic zen rule each, and the running one is the rule that is both enabled and true —
     * a rule can be true on its schedule while switched off, which is why both are matched.
     * The grep runs on the phone so one line comes back rather than the whole dump.
     */
    private const val ZEN_RULE =
        "dumpsys notification --noredact | grep -oE \"ZenRule\\[[^]]*\" | " +
            "grep enabled=TRUE | grep state=STATE_TRUE | grep -oE \"name=[^,]*\" | head -1"

    private const val BLUETOOTH_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val BLUETOOTH_BATTERY_EXTRA = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private var receiver: BroadcastReceiver? = null
    private var networks: ConnectivityManager.NetworkCallback? = null
    private var zenWatch: android.database.ContentObserver? = null
    private var report: ((JSONObject) -> Unit)? = null

    private var link = "none"
    private var level = -1
    private var generation = ""
    private var isUsbConnected = false
    private var isZen = false
    private var zenName = ""
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

        // Do not disturb, which is the Modus the Status bubble wears when nothing is riding on
        // the link. It is a global setting rather than a broadcast — the one the platform
        // exposes is protected — and reading it costs no permission at all.
        val zen = object : android.database.ContentObserver(android.os.Handler(context.mainLooper)) {
            override fun onChange(selfChange: Boolean) {
                readZen(context)
                publish()
            }
        }
        zenWatch = zen
        runCatching {
            context.contentResolver.registerContentObserver(
                android.provider.Settings.Global.getUriFor("zen_mode"), false, zen
            )
        }
        readZen(context)

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
        zenWatch?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        zenWatch = null
        report = null
        isZen = false
        zenName = ""
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

    /**
     * The short reading the status bar uses, not the platform's constant name. Everything older
     * than EDGE is one letter because that is what it has always been called up there, and
     * anything unrecognised is left blank rather than guessed at — a wrong generation is worse
     * than none, because it is read as fact.
     */
    /** 0 is off; 1, 2 and 3 are the three kinds of quiet, and the bubble draws them the same. */
    private fun readZen(context: Context) {
        isZen = runCatching {
            android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
        }.getOrDefault(0) != 0
        zenName = if (isZen) readZenName() else ""
    }

    /**
     * Which mode is keeping the phone quiet, by name.
     *
     * "Bitte nicht stören" on this phone is not one state: One UI's Modes are automatic zen
     * rules, one per mode, and which of them is running is what the person set — sleep, driving,
     * a mode for one person. The bubble wears an icon for the mode rather than the one crossed
     * circle for all of them, and the *name* is the only handle the platform gives us for that:
     * Samsung's own icon is not on the rule (`iconResName` is null on every Lifestyle mode this
     * phone has), so there is nothing to load and draw, and the page matches the name against a
     * small glyph set instead. A mode nobody recognises still gets the plain do-not-disturb ring.
     *
     * The manual toggle from the quick panel is not a rule at all and has no name, which is
     * exactly right: it is the plain one.
     */
    private fun readZenName(): String {
        // Over the shell rather than through NotificationManager: `getAutomaticZenRules` needs
        // notification *policy* access, which is a separate grant this phone would not hand over
        // — `cmd notification allow_dnd` runs without complaint and grants nothing — and the same
        // rules are in a dumpsys the shell we already have can read. The grep runs on the phone,
        // so what crosses back is one line however long the dump is, and it only runs at all on
        // the frames zen actually turns on.
        val rule = ShizukuShell.run(ZEN_RULE)?.trim().orEmpty()
        return rule.removePrefix("name=").trim()
    }

    private fun nameOfNetwork(type: Int): String = when (type) {
        android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
        android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_EDGE -> "E"
        android.telephony.TelephonyManager.NETWORK_TYPE_GPRS -> "G"
        else -> ""
    }

    private fun readLink(context: Context, seen: NetworkCapabilities) {
        link = when {
            seen.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            seen.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            seen.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "none"
        }
        // What generation the mobile link is, which is the reading the status bar has always
        // carried and the one the bubble replaces. It needs READ_PHONE_STATE — granted over adb
        // by grant.ps1, the same way everything else here is, because this phone is sideloaded
        // and One UI refuses the toggle in Settings anyway. Ungranted it throws, and an unknown
        // generation is a link that is up with nothing to say about itself.
        generation = if (link == "mobile") {
            val telephony = context.getSystemService(android.telephony.TelephonyManager::class.java)
            runCatching { nameOfNetwork(telephony.dataNetworkType) }.getOrDefault("")
        } else {
            ""
        }
        // Wifi is the one whose strength is readable without a dangerous permission: the
        // capabilities carry the RSSI and WifiManager knows what the bars mean. A mobile link
        // says which generation it is instead of drawing bars, which is what the icon it stands
        // on did — the number of bars up there was never the cellular one anyway.
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
        // The mode's name comes from the shell, and the shell is not always there to be asked:
        // Shizuku binds a moment after the service starts, so the read at boot — which is the one
        // that matters, because the mode was already running before the bubble existed — lands
        // before there is anything to run it. Asked again while it is still missing, it costs one
        // command until it is answered and nothing at all afterwards.
        if (isZen && zenName.isEmpty()) zenName = readZenName()
        val paired = pairedName?.let {
            JSONObject().put("name", it).put("charge", pairedCharge)
        } ?: if (pairedCharge >= 0) JSONObject().put("charge", pairedCharge) else null
        report?.invoke(
            JSONObject()
                .put("link", link)
                .put("level", level)
                .put("generation", generation)
                .put("usb", isUsbConnected)
                .put("hotspot", isHotspotOn)
                .put("zen", isZen)
                .put("zenName", zenName)
                .put("bluetooth", paired ?: JSONObject.NULL)
        )
    }
}
