package com.v.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.Executors

object WirelessAdb {

    private const val TAG = "IslandBubble"
    private const val CHANNEL = "wireless-adb"
    private const val PAIRING_NOTIFICATION = 7301
    private const val CODE_KEY = "code"
    private const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
    private const val SHIZUKU_START = "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh"
    private const val CONNECT_TIMEOUT_MILLIS = 8000L
    private const val REVIVE_ATTEMPTS = 8
    private const val REVIVE_PAUSE_MILLIS = 3000L
    private const val SHIZUKU_WAIT_MILLIS = 8000L

    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var isReviving = false

    @Volatile private var pairingPort = -1

    private var pairingDiscovery: NsdManager.DiscoveryListener? = null

    private lateinit var appContext: Context

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private fun keyFile() = File(appContext.filesDir, "adb.key")
    private fun certificateFile() = File(appContext.filesDir, "adb.crt")
    private fun pairedMark() = File(appContext.filesDir, "adb.paired")

    fun isPaired(context: Context): Boolean {
        attach(context)
        return pairedMark().exists()
    }

    private class Manager(private val privateKey: PrivateKey, private val certificate: Certificate) : AbsAdbConnectionManager() {
        init {
            setApi(Build.VERSION.SDK_INT)
        }

        override fun getPrivateKey() = privateKey
        override fun getCertificate() = certificate
        override fun getDeviceName() = "VADOS BUBBLE"
    }

    private fun manager(): Manager {
        if (!keyFile().exists() || !certificateFile().exists()) {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair = generator.generateKeyPair()
            val name = X500Name("CN=VADOS BUBBLE")
            val now = Date()
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now.time), now, Date(now.time + 20L * 365 * 86_400_000), name, pair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
            val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            keyFile().writeBytes(pair.private.encoded)
            certificateFile().writeBytes(certificate.encoded)
        }
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile().readBytes()))
        val certificate = certificateFile().inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        return Manager(privateKey, certificate)
    }

    fun startPairing(context: Context) {
        attach(context)
        watchPairingPort()
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Wireless debugging", NotificationManager.IMPORTANCE_HIGH))
        postPairing("Wireless debugging → Pair device with pairing code, then type the code here.")
        val developer = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(developer) }
    }

    private fun postPairing(text: String, isAskingForCode: Boolean = true) {
        val builder = Notification.Builder(appContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pair VADOS BUBBLE")
            .setContentText(text)
            .setOnlyAlertOnce(true)
        if (isAskingForCode) {
            val reply = PendingIntent.getBroadcast(
                appContext, 0,
                Intent(appContext, PairingReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val input = RemoteInput.Builder(CODE_KEY).setLabel("Pairing code").build()
            builder.addAction(Notification.Action.Builder(null, "Enter code", reply).addRemoteInput(input).build())
        }
        appContext.getSystemService(NotificationManager::class.java).notify(PAIRING_NOTIFICATION, builder.build())
    }

    private fun watchPairingPort() {
        val nsd = appContext.getSystemService(NsdManager::class.java)
        pairingDiscovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        pairingPort = resolved.port
                    }

                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) = Unit
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        pairingDiscovery = listener
        nsd.discoverServices(PAIRING_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun stopWatchingPairingPort() {
        val listener = pairingDiscovery ?: return
        pairingDiscovery = null
        runCatching { appContext.getSystemService(NsdManager::class.java).stopServiceDiscovery(listener) }
    }

    private fun pair(code: String) = worker.execute {
        val port = pairingPort
        if (port <= 0) {
            postPairing("The pairing dialog was not found — keep it open and enter the code again.")
            return@execute
        }
        val isPaired = runCatching { manager().use { it.pair("127.0.0.1", port, code.trim()) } }
            .onFailure { Log.w(TAG, "wireless adb pairing failed", it) }
            .getOrDefault(false)
        if (!isPaired) {
            postPairing("Pairing failed — check the code and enter it again.")
            return@execute
        }
        pairedMark().createNewFile()
        stopWatchingPairingPort()
        run("pm grant ${appContext.packageName} android.permission.WRITE_SECURE_SETTINGS")
        postPairing("Paired. Shizuku will be restarted whenever it stops.", isAskingForCode = false)
    }

    class PairingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            attach(context)
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CODE_KEY)?.toString() ?: return
            postPairing("Pairing…", isAskingForCode = false)
            pair(code)
        }
    }

    fun run(command: String): String? {
        if (!::appContext.isInitialized || !pairedMark().exists()) return null
        return runCatching {
            manager().use { adb ->
                if (!adb.connectTls(appContext, CONNECT_TIMEOUT_MILLIS)) return null
                adb.openStream("shell:$command").use { stream ->
                    stream.openInputStream().bufferedReader().readText()
                }
            }
        }.onFailure { Log.w(TAG, "wireless adb `$command` failed", it) }.getOrNull()
    }

    private fun ensureWirelessDebugging(isForced: Boolean) {
        val resolver = appContext.contentResolver
        runCatching {
            if (isForced) {
                Settings.Global.putInt(resolver, "adb_wifi_enabled", 0)
                Thread.sleep(500)
            }
            if (Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) != 1) {
                Settings.Global.putInt(resolver, "adb_wifi_enabled", 1)
                Thread.sleep(1500)
            }
        }.onFailure { Log.w(TAG, "wireless debugging could not be switched on", it) }
    }

    fun revive() {
        if (!::appContext.isInitialized || !pairedMark().exists() || isReviving) return
        isReviving = true
        worker.execute {
            try {
                for (attempt in 0 until REVIVE_ATTEMPTS) {
                    Thread.sleep(REVIVE_PAUSE_MILLIS)
                    if (ShizukuShell.isRunning()) return@execute
                    ensureWirelessDebugging(isForced = attempt >= 2 && attempt % 2 == 0)
                    if (run(SHIZUKU_START) == null) continue
                    val deadline = System.currentTimeMillis() + SHIZUKU_WAIT_MILLIS
                    while (System.currentTimeMillis() < deadline) {
                        if (ShizukuShell.isRunning()) {
                            Log.i(TAG, "shizuku revived over wireless adb")
                            return@execute
                        }
                        Thread.sleep(250)
                    }
                }
                Log.w(TAG, "shizuku could not be revived")
            } finally {
                isReviving = false
            }
        }
    }
}
