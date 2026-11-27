package com.v.island

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject







class MainActivity : Activity() {

    private val preferences by lazy { Preferences.of(this) }
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#181818"))
            settings.javaScriptEnabled = true
            overScrollMode = WebView.OVER_SCROLL_NEVER
            addJavascriptInterface(Panel(), "Panel")
            
            
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("IslandPanel", "${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
            loadUrl("file:///android_asset/panel.html")
        }
        setContentView(webView)

    }

    override fun onResume() {
        super.onResume()
        
        ShizukuShell.bind(this)
        
        
        if (ShizukuShell.isReady && HeadsUp.read() != HeadsUp.SUPPRESSED) HeadsUp.set(true)
        
        
        repaint()
    }

    private fun repaint() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.onStateChanged(${state()})", null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("test", false)) pushToIsland(testPayload())
    }

    private fun state(): JSONObject = JSONObject()
        .put("accessibility", hasAccessibilityAccess())
        .put(
            "postNotifications",
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
        )
        .put("notificationAccess", hasNotificationAccess())
        .put("serviceRunning", BubbleService.isRunning)
        .put("shizuku", ShizukuShell.isRunning() && ShizukuShell.hasPermission())
        .put("wirelessAdb", WirelessAdb.isPaired(this))
        .put("statusBar", StatusBarPolicy.read())
        .put("preferences", Preferences.asJson(preferences))
        .put("defaults", JSONObject(Preferences.defaults as Map<*, *>))

    

    private fun hasAccessibilityAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_accessibility_services")
        return enabled?.split(':')?.any { it.startsWith("$packageName/") } == true
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.split(':')?.any { it.startsWith("$packageName/") } == true
    }

    





    private fun openSettings(action: String, withPackageUri: Boolean) {
        val scoped = Intent(action).apply {
            if (withPackageUri) data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(scoped)
        } catch (notFound: ActivityNotFoundException) {
            try {
                startActivity(Intent(action))
            } catch (stillNotFound: ActivityNotFoundException) {
                openApplicationDetails()
            }
        }
    }

    private fun openApplicationDetails() = startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
    )

    
    private fun testPayload() = JSONObject()
        .put("key", "test")
        .put("app", "generic")
        .put("appName", "Dynamic Bubble")
        .put("accent", AppStyles.of(packageName).accent)
        .put("title", "Test notification")
        .put(
            "text",
            "Pushed at ${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())}"
        )
        .put("iconBase64", JSONObject.NULL)
        .put("mediaState", JSONObject.NULL)

    inner class Panel {

        @JavascriptInterface
        fun readState(): String = state().toString()

        





        @JavascriptInterface
        fun requestAccessibility() = runOnUiThread {
            if (ShizukuShell.isReady) {
                val component = "$packageName/$packageName.BubbleService"
                val enabled = ShizukuShell.run("settings get secure enabled_accessibility_services")
                    ?.trim()
                    ?.takeUnless { it == "null" || it.isEmpty() }
                val list = if (enabled == null) component else "$enabled:$component"
                ShizukuShell.run("settings put secure enabled_accessibility_services $list")
                ShizukuShell.run("settings put secure accessibility_enabled 1")
                repaint()
                return@runOnUiThread
            }
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackageUri = false)
        }

        @JavascriptInterface
        fun requestNotificationAccess() = runOnUiThread {
            if (ShizukuShell.isReady) {
                ShizukuShell.run(
                    "cmd notification allow_listener " +
                        "$packageName/$packageName.IslandNotificationListener"
                )
                repaint()
                return@runOnUiThread
            }
            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, withPackageUri = false)
        }

        @JavascriptInterface
        fun requestPostNotifications() = runOnUiThread {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) return@runOnUiThread
            if (ShizukuShell.isReady) {
                ShizukuShell.run("pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
                repaint()
                return@runOnUiThread
            }
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        
        @JavascriptInterface
        fun requestShizuku() = runOnUiThread {
            if (!ShizukuShell.isRunning()) {
                openApplicationDetails()
                return@runOnUiThread
            }
            ShizukuShell.requestPermission()
        }

        




        @JavascriptInterface
        fun pairWirelessAdb() = runOnUiThread { WirelessAdb.startPairing(this@MainActivity) }

        @JavascriptInterface
        fun toggleStatusBar() = runOnUiThread {
            StatusBarPolicy.set(StatusBarPolicy.read() != StatusBarPolicy.HIDDEN)
            repaint()
        }

        





        @JavascriptInterface
        fun toggleBubbles() = runOnUiThread {
            if (!BubbleService.isRunning) {
                requestAccessibility()
                webView.postDelayed({ repaint() }, 400)
                return@runOnUiThread
            }
            BubbleService.stopBubbles()
            
            webView.postDelayed({ repaint() }, 400)
        }

        
        @JavascriptInterface
        fun openApplicationInfo() = runOnUiThread { openApplicationDetails() }

        @JavascriptInterface
        fun testNotification() = runOnUiThread { pushToIsland(testPayload()) }

        @JavascriptInterface
        fun simulateDarkAlert() = runOnUiThread {
            BubbleService.deliverDark(
                testPayload()
                    .put("key", "test-dark")
                    .put("title", "Always-on alert")
                    .put("text", "This is how a message reads when it wakes a dark screen: the app name sweeps in, and the rest types out.")
            )
        }

        @JavascriptInterface
        fun sendDebugNotifications() = runOnUiThread { DebugNotifications.send(this@MainActivity, 5) }

        
        @JavascriptInterface
        fun readAppStyles(): String = JSONArray().apply {
            AppStyles.all().forEach {
                put(
                    JSONObject()
                        .put("key", it.key)
                        .put("label", it.label)
                        .put("accent", it.accent)
                )
            }
        }.toString()

        




        
        @JavascriptInterface
        fun stage(name: String) = runOnUiThread { DebugStage.run(name) }

        

        

        @JavascriptInterface
        fun setPreference(key: String, value: Int) = Preferences.set(preferences, key, value)

        
        @JavascriptInterface
        fun triggerHaptic() = getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
