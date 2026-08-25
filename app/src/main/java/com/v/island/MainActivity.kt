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

/**
 * The control panel. It is a WebView for the same reason the pill is one: the
 * house style is a CSS design, and reimplementing hairline panels, micro-labels
 * and the positional stagger in Android views would be a second implementation
 * of an identity that already exists.
 */
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
            // A silent JS error in a WebView-driven interface looks exactly like a
            // dead button, so the console goes to logcat.
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
        // Shizuku can have been started or granted while we were away.
        ShizukuShell.bind(this)
        // Every grant happens in another app, so the panel is repainted on the way back.
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
        .put("headsUp", HeadsUp.read())
        .put("preferences", Preferences.asJson(preferences))
        .put("defaults", JSONObject(Preferences.defaults as Map<*, *>))

    /** The accessibility toggle is what puts the bubble above the status bar. */
    private fun hasAccessibilityAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_accessibility_services")
        return enabled?.split(':')?.any { it.startsWith("$packageName/") } == true
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.split(':')?.any { it.startsWith("$packageName/") } == true
    }

    /**
     * Every settings deep link here is tried twice: One UI does not always accept
     * the package-scoped form of these intents, and an unhandled intent from a
     * grant button is exactly the failure that looks like "the app cannot be
     * given permission at all".
     */
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

    /** Used only until that app has actually sent something the log can replay. */
    private fun standInFor(style: AppStyles.Style) = JSONObject()
        .put("key", "stand-in:${style.key}")
        .put("app", style.key)
        .put("appName", style.label)
        .put("accent", style.accent)
        .put("package", style.packageName)
        .put("title", "Testeingang")
        .put("text", "Noch keine echte Benachrichtigung von ${style.label}")
        .put("iconBase64", JSONObject.NULL)
        .put("imageBase64", JSONObject.NULL)
        .put("mediaState", JSONObject.NULL)

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
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackageUri = false)
        }

        @JavascriptInterface
        fun requestNotificationAccess() = runOnUiThread {
            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, withPackageUri = false)
        }

        @JavascriptInterface
        fun requestPostNotifications() = runOnUiThread {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) return@runOnUiThread
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        /** Shizuku is the only route to a shell UID, which the microphone toggle needs. */
        @JavascriptInterface
        fun requestShizuku() = runOnUiThread {
            if (!ShizukuShell.isRunning()) {
                openApplicationDetails()
                return@runOnUiThread
            }
            ShizukuShell.requestPermission()
        }

        /** Hands SystemUI's pop-up off to the bubble, or gives it back. */
        @JavascriptInterface
        fun toggleHeadsUp() = runOnUiThread {
            HeadsUp.set(HeadsUp.read() != HeadsUp.SUPPRESSED)
            webView.evaluateJavascript("window.onStateChanged(${state()})", null)
        }

        /** The one route to the "Allow restricted settings" menu a sideloaded app needs. */
        @JavascriptInterface
        fun openApplicationInfo() = runOnUiThread { openApplicationDetails() }

        @JavascriptInterface
        fun testNotification() = runOnUiThread { pushToIsland(testPayload()) }

        /** The styled apps, in the order the debug screen should list them. */
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

        /**
         * Replays that app's last real notification — image, sender and all — because
         * a made-up one never has the shape the real layout has to survive.
         */
        @JavascriptInterface
        fun replay(styleKey: String) = runOnUiThread {
            val style = AppStyles.byKey(styleKey) ?: return@runOnUiThread
            pushToIsland(NotificationLog.lastOf(styleKey) ?: standInFor(style))
        }

        @JavascriptInterface
        fun setPreference(key: String, value: Int) = Preferences.set(preferences, key, value)

        /** Motion that ends on a physical device should be felt ending. */
        @JavascriptInterface
        fun triggerHaptic() = getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
