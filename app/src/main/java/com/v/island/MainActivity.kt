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
        // Samsung's own banner is not a setting anyone should have to find twice: the bubble is
        // what replaces it, so the moment a shell UID exists it is taken away and never asked about.
        if (ShizukuShell.isReady && HeadsUp.read() != HeadsUp.SUPPRESSED) HeadsUp.set(true)
        // Every grant that could not be done from here happens in another app, so the panel is
        // repainted on the way back.
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
        .put("statusBar", StatusBarPolicy.read())
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
        .put("text", "No real notification from ${style.label} yet")
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

        /**
         * With a shell UID the whole grant is one write and nobody has to leave the panel; the
         * settings deep link is only the fallback for the one run before Shizuku exists. The
         * existing list is read and appended to rather than written over, because that key holds
         * every accessibility service on the phone and stamping on it turns the others off.
         */
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

        /** Shizuku is the only route to a shell UID, which the microphone toggle needs. */
        @JavascriptInterface
        fun requestShizuku() = runOnUiThread {
            if (!ShizukuShell.isRunning()) {
                openApplicationDetails()
                return@runOnUiThread
            }
            ShizukuShell.requestPermission()
        }

        /**
         * Hides the real status bar, or gives it back. One button for both, because it is one
         * state — and it reads the setting back rather than assuming the write took, since
         * whether One UI honours the flag at all can only be found out on the phone.
         */
        @JavascriptInterface
        fun toggleStatusBar() = runOnUiThread {
            StatusBarPolicy.set(StatusBarPolicy.read() != StatusBarPolicy.HIDDEN)
            repaint()
        }

        /**
         * Stop the bubbles, not the app. Testing can leave the overlay in a state no repaint gets
         * out of, and the only fix was revoking accessibility in the system settings and granting
         * it again — this is that, from here, in one press. The service is what draws every window,
         * so disabling it is the whole stop; the Accessibility button above brings it back.
         */
        @JavascriptInterface
        fun stopBubbles() = runOnUiThread {
            BubbleService.stopBubbles()
            // The service dies asynchronously, so a repaint on this frame still reads it as running.
            webView.postDelayed({ repaint() }, 400)
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

        /** One state, set up and shown. See DebugStage for what each name stands for. */
        @JavascriptInterface
        fun stage(name: String) = runOnUiThread { DebugStage.run(name) }

        /** Every state in a fixed order, slowly enough to be recorded and compared. */
        @JavascriptInterface
        fun stageSequence() = runOnUiThread { DebugStage.sequence() }

        /** Where the last finger landed and what the bubble made of it. */
        @JavascriptInterface
        fun readTrace(): String = BubbleService.trace()

        @JavascriptInterface
        fun setPreference(key: String, value: Int) = Preferences.set(preferences, key, value)

        /** Motion that ends on a physical device should be felt ending. */
        @JavascriptInterface
        fun triggerHaptic() = getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
