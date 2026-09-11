package com.v.island

import android.app.KeyguardManager
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject












class BubbleService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        
        private const val WAKE_MILLIS = 6_000L

        




        private const val GRAB = 8

        










        private const val BLEED = 14

        
        private const val SHADE_STRIP = 48

        
        private const val GROWN_CORNER = 26

        


















        private const val BLUR_PANES = 14

        






        private const val SCRIM_PANE = 0

        
        private const val LOCK_AWAKE = 15_000L

        
        private const val WAKE_FRAMES_EVERY = 150L

        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                
                
                
                
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        







        private const val CANVAS_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        
        private var instance: BubbleService? = null

        val isRunning: Boolean get() = instance != null

        
        fun stopBubbles() {
            instance?.disableSelf()
        }

        
        fun toggleStatusPanel() {
            instance?.push("window.onToggleStatusPanel()")
        }


        fun toggleNotifications() {
            instance?.push("window.onToggleNotifications()")
        }

        




        fun trace(): String {
            val service = instance ?: return "the bubble is not running"
            return service.lastTouch + "\n" + service.lastNote
        }

        
        fun deliver(payload: JSONObject) {
            val service = instance ?: return
            service.wakeScreen()
            service.push("window.onNotificationUpdate($payload)")
            service.push("window.setUnreadCount(${IslandNotificationListener.count()})")
        }

        
        fun deliverCount(count: Int) {
            instance?.push("window.setUnreadCount($count)")
            
            
            instance?.push("window.onNotesChanged()")
        }

        
        fun deliverTimer(timer: JSONObject?) {
            instance?.push("window.onTimerUpdate(${timer ?: "null"})")
        }

        
        fun deliverGone(key: String) {
            instance?.push("window.onNotificationGone(${JSONObject.quote(key)})")
        }

        
        fun deliverCall(call: JSONObject?) {
            instance?.push("window.onCallUpdate(${call ?: "null"})")
        }

        
        fun deliverMedia(media: JSONObject?) {
            instance?.push("window.onMediaUpdate(${media ?: "null"})")
        }

        
        fun deliverBattery(battery: JSONObject) {
            instance?.push("window.onBattery($battery)")
        }

        








        fun stageLock(locked: Boolean) {
            instance?.push("window.onLock($locked)")
        }

        
        fun deliverNowMods(mods: JSONObject) {
            instance?.push("window.onNowMods($mods)")
        }

        
        fun deliverConnectivity(state: JSONObject) {
            instance?.push("window.onConnectivity($state)")
        }

        





        fun deliverTorch(torch: JSONObject?) {
            instance?.push("window.onTorchUpdate(${torch ?: "null"})")
            
            
            
            
        }


    }

    private lateinit var windowManager: WindowManager

    
    private lateinit var stage: FrameLayout
    private lateinit var webView: WebView

    
    private lateinit var touchProxy: View
    private lateinit var proxyParams: WindowManager.LayoutParams

    
    private lateinit var lockProxy: View
    private lateinit var lockProxyParams: WindowManager.LayoutParams

    
    private lateinit var statusProxy: View
    private lateinit var statusProxyParams: WindowManager.LayoutParams

    




    private lateinit var clockProxy: View
    private lateinit var clockProxyParams: WindowManager.LayoutParams

    




    private var shadeGuard: View? = null


    private var shadeGuardParams: WindowManager.LayoutParams? = null














    private lateinit var blurPanes: List<View>

    
    private val paneCorners = FloatArray(BLUR_PANES)

    






    
    private val paneSpec = arrayOfNulls<String>(BLUR_PANES)

    private val paneBlurRadius = IntArray(BLUR_PANES) { -1 }
    private val paneBlurCorner = FloatArray(BLUR_PANES) { -1f }

    
    private val paneShares = FloatArray(BLUR_PANES) { 1f }

    
    private var blurRadius = 0

    
    private var scrimBlurRadius = 0
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var preferences: SharedPreferences
    private var pageReady = false
    private val pending = ArrayDeque<String>()

    
    private var isFullScreen = false
    private var isLandscape = false
    private var isGrown = false

    






    private var isScreenOff = false
    private var screenWatch: BroadcastReceiver? = null

    







    private var isLocked = false

    





    














    private var isBarOurs = false

    private fun readBarPolicy() {
        val value = runCatching {
            android.provider.Settings.Global.getString(contentResolver, "policy_control")
        }.getOrNull().orEmpty()
        isBarOurs = value.contains("immersive.status") || value.contains("immersive.full")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (instance !== this) return
        val isBarHidden = !windowManager.currentWindowMetrics.windowInsets
            .isVisible(android.view.WindowInsets.Type.statusBars()) && !isBarOurs
        
        
        
        android.util.Log.i(
            "IslandBubble",
            "statusBar event=${event?.eventType} isBarHidden=$isBarHidden wasFullScreen=$isFullScreen"
        )
        if (isBarHidden == isFullScreen) return
        isFullScreen = isBarHidden
        applyVisibility()
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (instance != null) return

        
        AppStyles.learnFrom(this)
        preferences = Preferences.of(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        blurRadius = Preferences.get(preferences, Preferences.BLUR)
        scrimBlurRadius = Preferences.get(preferences, Preferences.SCRIM_BLUR)
        windowManager = getSystemService(WindowManager::class.java)

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            
            
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("IslandBubble", "${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = AssetOrigin.client(this@BubbleService)
            loadUrl(AssetOrigin.ROOT + "pill.html")
        }

        stage = FrameLayout(this).apply {
            
            
            setOnApplyWindowInsetsListener { _, _ -> android.view.WindowInsets.CONSUMED }
            
            
            
            
            blurPanes = List(BLUR_PANES) { View(this@BubbleService).apply { visibility = View.GONE } }
            blurPanes.forEach { pane ->
                addView(
                    pane,
                    FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START)
                )
            }
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    stageHeight(),
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
        }

        
        
        
        touchProxy = object : View(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    reportOutside(event)
                    return false
                }
                forwardTouch(event, proxyParams, "main")
                return true
            }
        }

        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        params = WindowManager.LayoutParams(
            screenWidth(),
            stageHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            CANVAS_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET))
            
            
            
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            
            
            
            
            
            
            
            
            preferredRefreshRate = fastestRefreshRate()
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }

        
        
        proxyParams = WindowManager.LayoutParams(
            dp(compactWidth() + (GRAB + BLEED) * 2),
            dp(compactHeight() + topGrab() + GRAB),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET))
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        statusProxy = object : View(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    reportOutside(event)
                    return false
                }
                forwardTouch(event, statusProxyParams, "status")
                return true
            }
        }
        
        
        
        statusProxyParams = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        clockProxy = object : View(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    reportOutside(event)
                    return false
                }
                forwardTouch(event, clockProxyParams, "clock")
                return true
            }
        }
        clockProxyParams = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        lockProxy = object : View(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    reportOutside(event)
                    return false
                }
                forwardTouch(event, lockProxyParams, "lock")
                return true
            }
        }
        
        
        
        
        lockProxyParams = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        
        
        
        
        stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        windowManager.addView(stage, params)
        
        
        windowManager.addView(touchProxy, proxyParams)
        windowManager.addView(lockProxy, lockProxyParams)
        windowManager.addView(statusProxy, statusProxyParams)
        windowManager.addView(clockProxy, clockProxyParams)
        applyBarLock()
        readBarPolicy()
        
        
        contentResolver.registerContentObserver(
            android.provider.Settings.Global.getUriFor("policy_control"),
            false,
            object : android.database.ContentObserver(android.os.Handler(mainLooper)) {
                override fun onChange(selfChange: Boolean) {
                    readBarPolicy()
                    
                    
                    
                    if (isBarOurs && isFullScreen) {
                        isFullScreen = false
                        applyVisibility()
                    }
                }
            }
        )
        applyVisibility()
        ShizukuShell.bind(this)
        SystemToggles.attach(this)
        instance = this

        MediaControl.start(this) { media -> deliverMedia(media) }

        
        
        
        BatteryWatch.start(
            this,
            { battery -> deliverBattery(battery) },
            { percent, plugged, remainingMinutes -> instance?.push("window.onCharge($percent, $plugged, $remainingMinutes)") }
        )

        
        
        TorchWatch.start(this) { torch -> deliverTorch(torch) }

        
        
        ConnectivityWatch.start(this) { state -> deliverConnectivity(state) }

        isScreenOff = !getSystemService(PowerManager::class.java).isInteractive
        screenWatch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) {
                    isScreenOff = intent.action == Intent.ACTION_SCREEN_OFF
                    applyVisibility()
                }
                reportLock()
            }
        }.also {
            registerReceiver(
                it,
                IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    
                    
                    
                    
                    addAction(Intent.ACTION_USER_PRESENT)
                }
            )
        }
        reportLock()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyVisibility()
    }

    
    private fun isHidden() = isLandscape || isFullScreen || isScreenOff

    









    


    private fun applyBarLock() {
        val wanted = Preferences.get(preferences, Preferences.BAR_LOCKED) == 1
        if (wanted == (shadeGuard != null)) return
        if (!wanted) {
            runCatching { windowManager.removeView(shadeGuard) }
            shadeGuard = null
            shadeGuardParams = null
            return
        }
        val guard = object : View(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean = true
        }
        val guardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(SHADE_STRIP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        runCatching { windowManager.addView(guard, guardParams) }
        shadeGuard = guard
        shadeGuardParams = guardParams
        
        applyVisibility()
        
        listOf(
            touchProxy to proxyParams,
            lockProxy to lockProxyParams,
            statusProxy to statusProxyParams,
            clockProxy to clockProxyParams,
        ).forEach { (view, viewParams) ->
            runCatching {
                windowManager.removeView(view)
                windowManager.addView(view, viewParams)
            }
        }
    }

    private fun proxyFlags(isLive: Boolean) =
        if (isLive && !isHidden()) BASE_FLAGS
        else BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    



    private fun applyVisibility() {
        val isHidden = isHidden()
        
        /* `alpha = 0f` left the WebView visible to Chromium, so every animation and every rAF in the page went on running through a screen-off night at the high frame rate the stage had asked for and never given back. INVISIBLE stops the page drawing while leaving its timers and the bridge alone, and the frame rate drops back until something wakes it. */
        stage.visibility = if (isHidden) View.INVISIBLE else View.VISIBLE
        push("window.setStageHidden($isHidden)")
        if (isHidden) {
            stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_LOW
            webView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_LOW
            framesWokeAt = 0L
        }
        
        
        
        if (isHidden) blurPanes.forEachIndexed { index, pane -> clearBlur(index, pane) } else repaintBlur()
        
        
        proxyParams.flags =
            if (isHidden) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else BASE_FLAGS
        
        
        
        
        
        
        
        
        if (isHidden) {
            lockProxyParams.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(lockProxy, lockProxyParams) }
            statusProxyParams.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(statusProxy, statusProxyParams) }
            clockProxyParams.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(clockProxy, clockProxyParams) }
        } else {
            push("window.refitProxies()")
        }
        
        shadeGuardParams?.let { guardParams ->
            guardParams.flags =
                if (isHidden) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                else BASE_FLAGS
            runCatching { windowManager.updateViewLayout(shadeGuard, guardParams) }
        }
        runCatching { windowManager.updateViewLayout(stage, params) }
        runCatching { windowManager.updateViewLayout(touchProxy, proxyParams) }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            MediaControl.stop()
            BatteryWatch.stop(this)
            ConnectivityWatch.stop(this)
            TorchWatch.stop()
            screenWatch?.let { runCatching { unregisterReceiver(it) } }
            screenWatch = null
            awake.removeCallbacks(sleepAgain)
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            runCatching { windowManager.removeView(clockProxy) }
            runCatching { windowManager.removeView(statusProxy) }
            runCatching { windowManager.removeView(lockProxy) }
            runCatching { windowManager.removeView(touchProxy) }
            runCatching { windowManager.removeView(stage) }
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(changed: SharedPreferences, key: String?) {
        
        
        params.x = dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET))
        proxyParams.width = dp(compactWidth() + (GRAB + BLEED) * 2)
        proxyParams.height = dp(compactHeight() + topGrab() + GRAB)
        proxyParams.x = params.x
        proxyParams.y = 0
        blurRadius = Preferences.get(preferences, Preferences.BLUR)
        scrimBlurRadius = Preferences.get(preferences, Preferences.SCRIM_BLUR)
        repaintBlur()
        runCatching { windowManager.updateViewLayout(stage, params) }
        runCatching { windowManager.updateViewLayout(touchProxy, proxyParams) }
        applyBarLock()
        pushAppearance()
    }

    










    private fun applyBlur(index: Int, view: View, corner: Float, resized: Boolean = false) {
        
        
        if (isLandscape || isFullScreen || isScreenOff) {
            clearBlur(index, view)
            return
        }
        
        
        
        
        
        
        val radius = Math.round((if (index == SCRIM_PANE) scrimBlurRadius else blurRadius) * paneShares[index])
        if (radius == 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.blurBehindRadius = 0
            clearBlur(index, view)
            return
        }
        
        
        
        
        if (!resized && paneBlurRadius[index] == radius && paneBlurCorner[index] == corner) return
        if (SamsungBlur.apply(view, dp(radius), corner)) {
            paneBlurRadius[index] = radius
            paneBlurCorner[index] = corner
            return
        }

        
        
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = dp(radius)
        if (!windowManager.isCrossWindowBlurEnabled) {
            android.util.Log.i("IslandBubble", "blur asked for but disabled system-wide")
        }
    }

    
    private fun clearBlur(index: Int, view: View) {
        
        
        paneSpec[index] = null
        if (paneBlurRadius[index] == -1) return
        SamsungBlur.clear(view)
        paneBlurRadius[index] = -1
        paneSpec[index] = null
    }

    
    private fun repaintBlur() = blurPanes.forEachIndexed { index, pane ->
        if (pane.visibility == View.VISIBLE) applyBlur(index, pane, paneCorners[index])
    }

    











    private val awake = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepAgain = Runnable {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        runCatching { windowManager.updateViewLayout(stage, params) }
    }

    private fun keepAwake() {
        
        
        
        
        
        
        if (!isLocked) return
        awake.removeCallbacks(sleepAgain)
        if (params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            runCatching { windowManager.updateViewLayout(stage, params) }
        }
        awake.postDelayed(sleepAgain, LOCK_AWAKE)
    }

    
    private fun screenWidth(): Int = windowManager.currentWindowMetrics.bounds.width()

    










    private fun stageHeight(): Int = windowManager.currentWindowMetrics.bounds.height()

    




    private fun reportLock() {
        isLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        
        
        if (!isLocked) { awake.removeCallbacks(sleepAgain); sleepAgain.run() }
        push("window.onLock($isLocked)")
    }

    @Volatile
    private var lastTouch = "nothing has been touched yet"

    @Volatile
    private var lastNote = ""


    









    





    private fun reportOutside(event: android.view.MotionEvent) {
        val density = resources.displayMetrics.density
        
        
        val x = (event.rawX - params.x) / density
        val y = event.rawY / density
        push("window.onOutsideTap($x, $y)")
    }

    private fun forwardTouch(
        event: android.view.MotionEvent,
        proxy: WindowManager.LayoutParams,
        source: String
    ) {
        val density = resources.displayMetrics.density
        val centred = proxy.gravity and Gravity.CENTER_HORIZONTAL == Gravity.CENTER_HORIZONTAL
        val proxyLeft =
            if (centred) (screenWidth() - proxy.width) / 2f + proxy.x else proxy.x.toFloat()
        val x = (proxyLeft - params.x + event.x) / density
        
        
        
        val y = (proxy.y + event.y) / density
        
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            wakeFrames()
            keepAwake()
        }
        val action = when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> "down"
            android.view.MotionEvent.ACTION_MOVE -> "move"
            android.view.MotionEvent.ACTION_UP -> "up"
            else -> "cancel"
        }
        if (action != "move") {
            lastTouch = "$source proxy heard $action at ${x.toInt()}, ${y.toInt()}" +
                " — window at ${(proxy.x / density).toInt()}, ${(proxy.width / density).toInt()} wide"
        }
        push("window.onProxyTouch('$action', $x, $y, '$source')")
    }

    















    private fun placeBlurFrame(spec: String) {
        val density = resources.displayMetrics.density
        val regions = spec.split(';')
        blurPanes.forEachIndexed { index, pane ->
            val region = regions.getOrNull(index).orEmpty()
            
            
            
            
            
            
            if (paneSpec[index] == region) return@forEachIndexed
            paneSpec[index] = region
            val numbers = region
                .split(',')
                .mapNotNull { it.toFloatOrNull() }
            if (numbers.size < 5 || numbers[2] < 1f || numbers[3] < 1f) {
                if (pane.visibility != View.GONE) {
                    pane.visibility = View.GONE
                    clearBlur(index, pane)
                }
                return@forEachIndexed
            }
            val bounds = pane.layoutParams as FrameLayout.LayoutParams
            val width = (numbers[2] * density).toInt()
            val height = (numbers[3] * density).toInt()
            
            
            val resized = bounds.width != width || bounds.height != height
            if (resized) {
                bounds.width = width
                bounds.height = height
                pane.layoutParams = bounds
            }
            pane.translationX = numbers[0] * density
            pane.translationY = numbers[1] * density
            pane.visibility = View.VISIBLE
            
            
            paneCorners[index] = numbers[4] * density
            
            
            
            
            paneShares[index] = numbers.getOrNull(5) ?: 1f
            applyBlur(index, pane, paneCorners[index], resized)
        }
    }

    private fun pushAppearance() {
        push("window.setPillBackground('${Preferences.backgroundCss(preferences)}')")
        push("window.setAccent('${Preferences.accentCss(preferences)}')")
        push("window.setCompactSize(${Preferences.get(preferences, Preferences.WIDTH)},${Preferences.get(preferences, Preferences.HEIGHT)})")
        push("window.setGrab(${topGrab()})")
        push("window.setNotificationIdentity(${Preferences.get(preferences, Preferences.NOTIFICATION_IDENTITY)})")
        push("window.setGoo(${Preferences.get(preferences, Preferences.GOO)})")
        push("window.setModWidth(${Preferences.get(preferences, Preferences.MOD_WIDTH)})")
        push("window.setNowPushes(${Preferences.get(preferences, Preferences.NOW_PUSHES)})")
        push("window.setAlertDwell(${Preferences.get(preferences, Preferences.ALERT_DWELL)})")
        push("window.setQuickDividers(${Preferences.get(preferences, Preferences.QUICK_DIVIDERS)})")
        push("window.setEdgeMerge(${Preferences.get(preferences, Preferences.EDGE_MERGE)})")
        push("window.setFonts(${Preferences.get(preferences, Preferences.FONT_CLOCK)},${Preferences.get(preferences, Preferences.FONT_MAIN)},${Preferences.get(preferences, Preferences.FONT_SATELLITE)},${Preferences.get(preferences, Preferences.FONT_STATUS)},${Preferences.get(preferences, Preferences.FONT_OVERLAY)},${Preferences.get(preferences, Preferences.FONT_BATTERY)},${Preferences.get(preferences, Preferences.FONT_STATS)},${Preferences.get(preferences, Preferences.FONT_CONNECTORS)},${Preferences.get(preferences, Preferences.FONT_NOTIFICATION_HEADING)},${Preferences.get(preferences, Preferences.FONT_NOTIFICATION_CONTENT)})")
        push("window.setFontSizes(${Preferences.get(preferences, Preferences.FONT_SIZE_CLOCK)},${Preferences.get(preferences, Preferences.FONT_SIZE_MAIN)},${Preferences.get(preferences, Preferences.FONT_SIZE_SATELLITE)},${Preferences.get(preferences, Preferences.FONT_SIZE_STATUS)},${Preferences.get(preferences, Preferences.FONT_SIZE_OVERLAY)},${Preferences.get(preferences, Preferences.FONT_SIZE_BATTERY)},${Preferences.get(preferences, Preferences.FONT_SIZE_STATS)},${Preferences.get(preferences, Preferences.FONT_SIZE_CONNECTORS)},${Preferences.get(preferences, Preferences.FONT_SIZE_NOTIFICATION_HEADING)},${Preferences.get(preferences, Preferences.FONT_SIZE_NOTIFICATION_CONTENT)})")
        push("window.setLockShift(${Preferences.get(preferences, Preferences.LOCK_X)})")
        push("window.setUnreadCount(${IslandNotificationListener.count()})")
        
        
        MediaControl.refresh()
        IslandNotificationListener.publishTimer()
        
        
        
        IslandNotificationListener.publishCall()
    }

    private fun push(js: String) {
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        webView.post {
            wakeFrames()
            if (pageReady) webView.evaluateJavascript(js, null) else pending.addLast(js)
        }
    }

    
    private var framesWokeAt = 0L

    





    private fun wakeFrames() {
        if (isHidden()) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - framesWokeAt < WAKE_FRAMES_EVERY) return
        framesWokeAt = now
        stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        webView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        stage.invalidate()
        webView.invalidate()
    }

    



    private fun compactHeight(): Int = Preferences.get(preferences, Preferences.HEIGHT)

    private fun compactWidth(): Int = Preferences.get(preferences, Preferences.WIDTH)

    
    private fun fastestRefreshRate(): Float =
        getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            ?.supportedModes
            ?.maxOfOrNull { it.refreshRate }
            ?: 0f

    






    private fun topGrab(): Int = Preferences.get(preferences, Preferences.VERTICAL_OFFSET)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    




    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        @Suppress("DEPRECATION")
        power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "island:notification"
        ).acquire(WAKE_MILLIS)
        
        
        
        isScreenOff = false
        applyVisibility()
    }

    private fun vibrate(effect: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createPredefined(effect))
    }

    /* TrafficStats counts from boot, not from midnight, so the day's figure is the total minus a stamp taken at the first read of each day; a total below the stamp is the counters having restarted with the phone and re-stamps rather than reporting a negative day. */
    private fun dataUsedToday(): String {
        val total = android.net.TrafficStats.getTotalRxBytes() + android.net.TrafficStats.getTotalTxBytes()
        val store = Preferences.of(this)
        val day = java.time.LocalDate.now().toEpochDay()
        var base = store.getLong(Preferences.DATA_BASE, total)
        if (day != store.getLong(Preferences.DATA_DAY, -1L) || total < base) {
            base = total
            store.edit().putLong(Preferences.DATA_DAY, day).putLong(Preferences.DATA_BASE, base).apply()
        }
        val used = total - base
        val megabytes = used / 1024.0 / 1024.0
        return if (megabytes >= 1024) String.format("%.1f GB", megabytes / 1024)
        else String.format("%.0f MB", megabytes)
    }

    private fun vibrate(effect: VibrationEffect) {
        getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(effect)
    }

    private val togglesWorker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private val togglesQueued = java.util.concurrent.atomic.AtomicInteger(0)
    private val levelTargets = java.util.concurrent.ConcurrentHashMap<String, Int>()

    // `svc wifi enable` and its siblings hand the ask to a system service and return before it has landed, so the read taken straight afterwards still says what the switch was a moment ago — which is the switch turning on, falling back off under the read, and coming on again at the next one. What was asked for is held over the read until the phone agrees with it.
    private val toggleIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()

    // How long a switch may stand for what was asked before the phone's own answer wins instead. Past it, a command the shell refused is a switch that goes back rather than one that lies.
    private val TOGGLE_SETTLE_MILLIS = 2_500L

    // How soon after a switch the phone is asked again, for as long as one is still standing for an ask nothing has confirmed.
    private val TOGGLE_RECHECK_MILLIS = 400L

    inner class Bridge {
        @JavascriptInterface
        fun ready() {
            webView.post {
                pageReady = true
                pushAppearance()
                
                
                
                
                webView.post {
                    while (pending.isNotEmpty()) webView.evaluateJavascript(pending.removeFirst(), null)
                }
            }
        }

        





        @JavascriptInterface
        fun setWindowSize(widthDp: Int, heightDp: Int) =
            setWindowBounds(widthDp, heightDp, 0, 0)

        






        @JavascriptInterface
        fun setWindowBounds(widthDp: Int, heightDp: Int, riseDp: Int, shiftDp: Int) {
            webView.post {
                
                isGrown = heightDp >= 0
                proxyParams.width =
                    dp((if (widthDp < 0) compactWidth() else widthDp) + (GRAB + BLEED) * 2)
                // The proxy ended a grace margin below the bubble's own bottom edge, and every point in that margin routes to the bubble, so a tap aimed at the app underneath opened the bubble and a tap under a closing one reopened it. The margin is sideways and upward now: a grown state ends at its own bottom edge, the compact bubble GRAB past its.
                proxyParams.height =
                    dp(
                        if (isGrown) heightDp + topGrab()
                        else compactHeight() + topGrab() + GRAB
                    )
                
                
                
                proxyParams.y = 0
                
                
                
                proxyParams.x =
                    dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET) + shiftDp)
                runCatching { windowManager.updateViewLayout(touchProxy, proxyParams) }
            }
        }

        










        @JavascriptInterface
        fun setBlurFrame(spec: String) {
            webView.post { placeBlurFrame(spec) }
        }

        




        @JavascriptInterface
        fun wakeFrames() {
            webView.post { this@BubbleService.wakeFrames() }
        }

        



        
        @JavascriptInterface
        fun note(text: String) {
            lastNote = text
        }

        





        @JavascriptInterface
        fun setLockProxy(widthDp: Int, heightDp: Int, leftDp: Int, topDp: Int) {
            webView.post {
                val isLive = widthDp > 0
                lockProxyParams.width = if (isLive) dp(widthDp) else 0
                lockProxyParams.height = if (isLive) dp(heightDp) else 0
                
                
                
                lockProxyParams.x = params.x + dp(leftDp)
                lockProxyParams.y = dp(topDp)
                lockProxyParams.flags = proxyFlags(isLive)
                runCatching { windowManager.updateViewLayout(lockProxy, lockProxyParams) }
            }
        }

        




        
        @JavascriptInterface
        fun mediaSpeed(rate: String) {
            MediaControl.setSpeed(rate.toFloatOrNull() ?: 1f)
        }


        
        @JavascriptInterface
        fun recordingAction(index: Int) {
            IslandNotificationListener.recordingAction(index)
        }

        
        @JavascriptInterface
        fun setClockProxy(widthDp: Int, heightDp: Int, leftDp: Int) {
            webView.post {
                val isLive = widthDp > 0
                clockProxyParams.width = if (isLive) dp(widthDp) else 0
                clockProxyParams.height = if (isLive) dp(heightDp + topGrab()) else 0
                clockProxyParams.x = params.x + dp(leftDp)
                clockProxyParams.flags = proxyFlags(isLive)
                runCatching { windowManager.updateViewLayout(clockProxy, clockProxyParams) }
            }
        }

        @JavascriptInterface
        fun setStatusProxy(widthDp: Int, heightDp: Int, leftDp: Int) {
            webView.post {
                val isLive = widthDp > 0
                statusProxyParams.width = if (isLive) dp(widthDp) else 0
                statusProxyParams.height = if (isLive) dp(heightDp + topGrab()) else 0
                statusProxyParams.x = params.x + dp(leftDp)
                statusProxyParams.flags = proxyFlags(isLive)
                runCatching { windowManager.updateViewLayout(statusProxy, statusProxyParams) }
            }
        }

        




        @JavascriptInterface
        fun openConnectionSettings(kind: String) {
            val action = when (kind) {
                "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
                "mobile" -> android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS
                "plane" -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
                "hotspot" -> "com.android.settings.WIFI_TETHER_SETTINGS"
                "usb" -> "android.settings.USB_SETTINGS"

                "battery" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
                "vitals" -> android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                else -> android.provider.Settings.ACTION_SETTINGS
            }
            if (kind == "battery" && runCatching {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .setClassName(
                                "com.samsung.android.lool",
                                "com.samsung.android.sm.battery.ui.BatteryActivity"
                            )
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                }.getOrDefault(false)
            ) return
            // Device Care's own dashboard is storage, RAM and battery in one screen with Optimise now on it — the closest thing this phone has to "the vitals card's own settings". Asked for by action rather than by class: SmartManagerDashBoardActivity is an older One UI's name for it, resolves to nothing here, and the silent fallback landed on Manage storage instead.
            if (kind == "vitals" && runCatching {
                    startActivity(
                        Intent("com.samsung.android.sm.ACTION_DASHBOARD")
                            .setPackage("com.samsung.android.lool")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                }.getOrDefault(false)
            ) return
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            
            if (!runCatching { startActivity(intent); true }.getOrDefault(false)) {
                runCatching {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        


        @JavascriptInterface
        fun openClock() {
            val alarms = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            val clockPackage = packageManager.resolveActivity(alarms, 0)?.activityInfo?.packageName
            val launch = clockPackage?.let { packageManager.getLaunchIntentForPackage(it) }
            val intent = (launch ?: alarms)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
                .onFailure { android.util.Log.w("IslandBubble", "no clock app", it) }
        }

        @JavascriptInterface
        fun openControlPanel() {
            val intent = Intent(this@BubbleService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
                .onFailure { android.util.Log.w("IslandBubble", "no control panel", it) }
        }

        @JavascriptInterface
        fun openPowerMenu() {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }

        









        @JavascriptInterface
        fun triggerHaptic(type: String) {
            if (type == "toggle") {
                vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.45f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 40)
                        .compose()
                )
                return
            }
            vibrate(
                when (type) {
                    "tap" -> VibrationEffect.EFFECT_CLICK
                    "expand" -> VibrationEffect.EFFECT_HEAVY_CLICK
                    "dismiss" -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    else -> VibrationEffect.EFFECT_TICK
                }
            )
        }

        



        @JavascriptInterface
        fun onSwipeDismiss() = Unit

        
        @JavascriptInterface
        fun readHistory(): String = IslandNotificationListener.shade().toString()

        @JavascriptInterface
        fun dismissNotification(key: String) = IslandNotificationListener.dismiss(key)

        @JavascriptInterface
        fun readUnreadCount(): Int = IslandNotificationListener.count()

        




        @JavascriptInterface
        fun openNotification(key: String) {
            IslandNotificationListener.open(key)
        }

        
        @JavascriptInterface
        fun readMicrophoneAccess(): String = MicrophoneAccess.read()

        
        @JavascriptInterface
        fun readTorchLit(): Boolean = TorchWatch.isLit

        @JavascriptInterface
        fun readDataToday(): String = dataUsedToday()

        




        @JavascriptInterface
        fun setTorchLit(isLit: Boolean) {
            TorchWatch.set(if (isLit) TorchWatch.lastStep else 0)
        }

        @JavascriptInterface
        fun setMicrophoneAccess(isAllowed: Boolean) {
            MicrophoneAccess.set(isAllowed)
            
            
            push("window.onMicrophoneAccessChanged('${MicrophoneAccess.read()}')")
        }

        






        @JavascriptInterface
        fun requestToggles() {
            queueToggleWork { }
        }

        @JavascriptInterface
        fun requestVitals() {
            val context = this@BubbleService
            Thread { push("window.onVitals(${VitalsWatch.read(context)})") }.start()
        }

        @JavascriptInterface
        fun requestWeather() {
            WeatherWatch.request(this@BubbleService) { weather ->
                push("window.onWeather(${weather ?: "null"})")
            }
        }

        // Every setToggle and setLevel used to start its own thread, so a fast run of taps or a single drag put a dozen concurrent binder calls into the Shizuku shell and whichever landed last, rather than whichever was asked last, decided the state; one worker now runs them in the order they were asked.
        private fun queueToggleWork(work: () -> Unit) {
            togglesQueued.incrementAndGet()
            togglesWorker.execute {
                work()
                // Reading the whole of system settings costs a dozen shell commands and would answer with a half-applied state anyway, so only the last piece of queued work reports back.
                if (togglesQueued.decrementAndGet() == 0) reportToggles()
            }
        }

        private fun reportToggles() {
            val state = SystemToggles.read()
            var isWaiting = false
            val now = android.os.SystemClock.uptimeMillis()
            for ((name, intent) in toggleIntents) {
                val (wanted, deadline) = intent
                // A switch the read cannot see at all — the hotspot and the recorder are asked for by tapping a system tile — has nothing that could ever confirm it, and a name written in here would make the page believe the panel reports it.
                if (!state.has(name) || state.optBoolean(name) == wanted || now > deadline) {
                    toggleIntents.remove(name)
                    continue
                }
                state.put(name, wanted)
                isWaiting = true
            }
            push("window.onTogglesChanged($state)")
            // Nothing else asks again, so a switch still standing for an unconfirmed ask has to bring the next read with it — otherwise it holds that ask until someone reopens the panel.
            if (isWaiting) {
                togglesWorker.schedule(
                    { reportToggles() }, TOGGLE_RECHECK_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS
                )
            }
        }

        




        @JavascriptInterface
        fun setToggle(name: String, isOn: Boolean) {
            toggleIntents[name] = isOn to
                (android.os.SystemClock.uptimeMillis() + TOGGLE_SETTLE_MILLIS)
            queueToggleWork { SystemToggles.set(name, isOn) }
        }

        // A drag asks far faster than a shell command can answer, so each frame only leaves its value behind: the first queued run takes the newest one and the runs behind it find nothing left and cost nothing.
        @JavascriptInterface
        fun setLevel(name: String, percent: Int) {
            levelTargets[name] = percent
            togglesWorker.execute {
                val target = levelTargets.remove(name) ?: return@execute
                SystemToggles.setLevel(name, target)
            }
        }

        




        @JavascriptInterface
        fun openApp(packageName: String) {
            
            
            
            
            
            if (packageName == MediaControl.packageName &&
                MediaControl.open(this@BubbleService)
            ) return
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                android.util.Log.w("IslandBubble", "no launcher intent for $packageName")
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
                .onFailure { android.util.Log.w("IslandBubble", "cannot open $packageName", it) }
        }

        
        @JavascriptInterface
        fun setTorch(step: Int) = TorchWatch.set(step)

        @JavascriptInterface
        fun mediaControl(action: String) = MediaControl.command(action)

        
        @JavascriptInterface
        fun timerAction(index: Int) = IslandNotificationListener.timerAction(index)

        @JavascriptInterface
        fun mediaSeek(milliseconds: String) {
            MediaControl.seek(milliseconds.toLongOrNull() ?: return)
        }
    }
}





fun Context.pushToIsland(payload: JSONObject) = BubbleService.deliver(payload)
