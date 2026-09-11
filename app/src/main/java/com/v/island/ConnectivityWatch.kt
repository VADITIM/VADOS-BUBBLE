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











object ConnectivityWatch {

    
    private const val TETHER_STATE = "android.net.conn.TETHER_STATE_CHANGED"
    private const val TETHER_ACTIVE = "tetherArray"

    





    





    private const val ZEN_RULE =
        "dumpsys notification --noredact | grep -oE \"ZenRule\\[[^]]*\" | " +
            "grep enabled=TRUE | grep state=STATE_TRUE | grep -oE \"name=[^,]*\" | head -1"

    






    private const val WIFI_SSID = "cmd -w wifi status | grep -m1 'Wifi is connected to'"

    private const val BLUETOOTH_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val BLUETOOTH_BATTERY_EXTRA = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private var receiver: BroadcastReceiver? = null
    private var networks: ConnectivityManager.NetworkCallback? = null
    private var zenWatch: android.database.ContentObserver? = null
    private var report: ((JSONObject) -> Unit)? = null

    private var link = "none"
    private var level = -1
    private var generation = ""
    private var ssid = ""
    private var isUsbConnected = false
    private var isZen = false
    private var zenName = ""
    private var isHotspotOn = false
    private var pairedName: String? = null
    private val pairedNames = linkedSetOf<String>()
    private var pairedCharge = -1

    private var lastPublished = ""

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
                
                addAction("android.hardware.usb.action.USB_STATE")
                addAction(TETHER_STATE)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BLUETOOTH_BATTERY)
            }
        )

        
        
        
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
        readConnected(context)

        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, seen: NetworkCapabilities) {
                readLink(context, seen)
                publish()
            }

            
            
            override fun onLost(network: Network) {
                link = "none"
                level = -1
                ssid = ""
                publish()
            }
        }
        networks = callback
        
        
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
        ssid = ""
        isUsbConnected = false
        isHotspotOn = false
        pairedName = null
        pairedNames.clear()
        pairedCharge = -1
        lastPublished = ""
    }

    private fun hear(context: Context, intent: Intent) {
        when (intent.action) {
            "android.hardware.usb.action.USB_STATE" ->
                isUsbConnected = intent.getBooleanExtra("connected", false)

            TETHER_STATE ->
                isHotspotOn = !intent.getStringArrayListExtra(TETHER_ACTIVE).isNullOrEmpty()

            
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                nameOf(intent)?.takeIf { it.isNotEmpty() }?.let { pairedNames.add(it) }
                pairedName = pairedNames.firstOrNull()
                pairedCharge = -1
                readConnected(context)
            }

            // A headset holds several ACL links at once — A2DP, the handsfree profile, and an LE one — and Android reports each of them separately, so one transport dropping while the rest stay up arrived here as the device having gone. The name was cleared on that first disconnect and the ring emptied with the headphones still playing. A disconnect is a reason to ask what is still connected, never an answer on its own.
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                pairedCharge = -1
                readConnected(context)
            }

            
            
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                if (state != BluetoothAdapter.STATE_ON) {
                    pairedName = null
                    pairedNames.clear()
                    pairedCharge = -1
                } else {
                    
                    
                    readConnected(context)
                }
            }

            BLUETOOTH_BATTERY -> {
                val charge = intent.getIntExtra(BLUETOOTH_BATTERY_EXTRA, -1)
                if (charge in 0..100) {
                    nameOf(intent)?.takeIf { it.isNotEmpty() }?.let { pairedNames.add(it) }
                    if (pairedName == null) pairedName = pairedNames.firstOrNull()
                    pairedCharge = charge
                }
            }
        }
        publish()
    }

    





    












    // The first connected device of the first profile that answered used to be the whole answer, and it was latched — once a name was held, neither profile could correct it. A phone carries several devices at once, so what is published is the union of what both profiles report, rebuilt on every event rather than added to.
    private fun readConnected(context: Context) {
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return
        val found = linkedSetOf<String>()
        val listener = object : android.bluetooth.BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                val devices = runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
                runCatching { adapter.closeProfileProxy(profile, proxy) }
                devices.forEach { device ->
                    runCatching { device.name }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { found.add(it) }
                }
                pairedNames.clear()
                pairedNames.addAll(found)
                pairedName = pairedNames.firstOrNull()
                publish()
            }

            override fun onServiceDisconnected(profile: Int) {}
        }
        runCatching { adapter.getProfileProxy(context, listener, android.bluetooth.BluetoothProfile.A2DP) }
        runCatching { adapter.getProfileProxy(context, listener, android.bluetooth.BluetoothProfile.HEADSET) }
    }

    private fun nameOf(intent: Intent): String? {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        return runCatching { device?.name }.getOrNull()
    }

    





    
    private fun readZen(context: Context) {
        isZen = runCatching {
            android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
        }.getOrDefault(0) != 0
        zenName = if (isZen) readZenName() else ""
    }

    













    private fun readZenName(): String {
        
        
        
        
        
        
        val rule = ShizukuShell.run(ZEN_RULE)?.trim().orEmpty()
        return rule.removePrefix("name=").trim()
    }

    
    private fun readSsid(): String =
        ShizukuShell.run(WIFI_SSID)?.substringAfter('"', "")?.substringBefore('"').orEmpty()

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
        
        // The generation was read only while mobile was the network carrying the default route, so the moment wifi took over the mobile state had nothing to say and fell to its waiting line — it sat searching for as long as wifi was up. The radio reports its data type whether or not it is the route being used, so the read is unconditional and "" stays the honest answer when there is no service.
        generation = runCatching {
            nameOfNetwork(context.getSystemService(android.telephony.TelephonyManager::class.java).dataNetworkType)
        }.getOrDefault("")
        
        
        
        
        
        
        ssid = if (link == "wifi") ssid.ifEmpty { readSsid() } else ""
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
        
        
        
        
        
        
        
        if (link == "wifi" && ssid.isEmpty()) ssid = readSsid()
        if (isZen && zenName.isEmpty()) zenName = readZenName()
        val paired = pairedName?.let {
            JSONObject()
                .put("name", it)
                .put("names", org.json.JSONArray(pairedNames.toList()))
                .put("charge", pairedCharge)
        } ?: if (pairedCharge >= 0) JSONObject().put("charge", pairedCharge) else null
        /* `onCapabilitiesChanged` fires on every signal-strength wobble the default network reports — several times a minute at rest — and each one rebuilt the whole payload, pushed it into the page and repainted a bar that says exactly what it said before. The payload is its own dedupe key: nothing leaves here twice. */
        val payload = JSONObject()
                .put("link", link)
                .put("level", level)
                .put("generation", generation)
                .put("ssid", ssid)
                .put("usb", isUsbConnected)
                .put("hotspot", isHotspotOn)
                .put("zen", isZen)
                .put("zenName", zenName)
                .put("bluetooth", paired ?: JSONObject.NULL)
        val spelling = payload.toString()
        if (spelling == lastPublished) return
        lastPublished = spelling
        report?.invoke(payload)
    }
}
