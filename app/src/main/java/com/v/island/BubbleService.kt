package com.v.island

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.PowerManager
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.v.island.bubble.Appearance
import com.v.island.bubble.BlobShape
import com.v.island.bubble.HostActions
import com.v.island.bubble.Island
import com.v.island.bubble.IslandStage
import com.v.island.bubble.Notice
import com.v.island.bubble.Payloads
import com.v.island.bubble.Region
import com.v.island.vas.Haptics
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class BubbleService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener, HostActions {

    companion object {
        private const val WAKE_MILLIS = 6_000L
        private const val SYSTEM_WAKE_MILLIS = 1_500L
        private const val DARK_BRIGHTNESS = 0.07f
        private const val TAP_MILLIS = 40L
        private const val ROTATION_SETTLE = 600L
        private val LANDSCAPE_REGIONS = setOf(Region.Dashboard, Region.Clock, Region.Status)
        private const val TOGGLE_SETTLE_MILLIS = 2_500L
        private const val TOGGLE_RECHECK_MILLIS = 400L
        private const val LEVEL_SETTLE_SLACK = 3
        /* Mirrors the liquid layer's MAXIMUM_BLOBS: one blur pane per blob it can draw. */
        private const val BLUR_PANES = 16
        const val PANEL_BROADCAST = "com.v.island.TOGGLE_STATUS_PANEL"
        const val NOTIFICATIONS_BROADCAST = "com.v.island.TOGGLE_NOTIFICATIONS"

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

        fun toggleStatusPanel() = Island.post {
            val service = instance ?: return@post
            if (service.isLandscape && !Island.dashboard.isOpen) service.showLandscapeStage()
            Island.dashboard.toggle()
        }

        fun toggleNotifications() = Island.post {
            if (instance == null) return@post
            if (Island.bubble.size == com.v.island.bubble.Size.Notifications) Island.bubble.toClosed()
            else Island.bubble.openNotifications()
        }

        fun isDarkArrival(): Boolean = instance?.isDarkArrival() == true

        fun deliver(payload: JSONObject, arrivedDark: Boolean = false) {
            val notice = Payloads.notice(payload)
            Island.post {
                val service = instance ?: return@post
                val isDark = arrivedDark || service.isDarkArrival()
                if (isDark) {
                    service.wakeScreen()
                    Island.setScreenDark(true)
                }
                Island.unread = IslandNotificationListener.count()
                Island.bubble.onNotice(notice, Island.isScreenDark)
            }
        }

        fun deliverDark(payload: JSONObject) {
            val notice = Payloads.notice(payload)
            Island.post {
                if (instance == null) return@post
                Island.setScreenDark(true, isSimulated = true)
                Island.bubble.onNotice(notice, isDark = true)
            }
        }

        fun deliverCount(count: Int) = Island.post { Island.unread = count }

        fun deliverTimer(timer: JSONObject?) {
            val parsed = Payloads.timer(timer)
            Island.post {
                Island.timer = parsed
                Island.bubble.onModsChanged()
            }
        }

        fun deliverTrip(trip: JSONObject?) {
            val parsed = Payloads.trip(trip)
            Island.post {
                Island.trip = parsed
                Island.bubble.onModsChanged()
            }
        }

        fun deliverGone(key: String) = Island.post { Island.bubble.onNoticeGone(key) }

        fun deliverCall(call: JSONObject?) {
            val parsed = Payloads.call(call)
            Island.post {
                Island.call = parsed
                Island.bubble.onModsChanged()
            }
        }

        fun deliverCallVolume(isLive: Boolean) = Unit

        fun deliverMedia(media: JSONObject?) {
            val parsed = Payloads.media(media)
            Island.post {
                val previous = Island.media
                Island.media = if (parsed != null && parsed.art == null && media?.has("artBase64") != true && previous != null) parsed.copy(art = previous.art) else parsed
                Island.bubble.onModsChanged()
            }
        }

        fun deliverBattery(battery: JSONObject) {
            val event = Payloads.batteryEvent(battery) ?: return
            Island.post { Island.status.announce(event) }
        }

        fun clearStage() = Island.post {
            if (instance == null) return@post
            Island.trip = null
            Island.torch = null
            Island.unread = 0
            Island.isLocked = false
            Island.status.retireAnnouncement()
            Island.liftScreenDark()
            Island.bubble.onModsChanged()
            Island.bubble.toClosed()
        }

        fun republishReal() {
            val service = instance ?: return
            MediaControl.republish()
            IslandNotificationListener.publishTimer()
            IslandNotificationListener.publishTrip()
            IslandNotificationListener.publishCall()
            IslandNotificationListener.publishNowMods()
            deliverTorch(TorchWatch.state())
            Island.post {
                Island.unread = IslandNotificationListener.count()
                Island.isLocked = service.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            }
        }

        fun deliverNowMods(mods: JSONObject) {
            val recording = Payloads.recording(mods.optJSONObject("recording"))
            val transfer = Payloads.transfer(mods.optJSONObject("transfer"))
            Island.post {
                Island.recording = recording
                Island.transfer = transfer
                Island.bubble.onModsChanged()
            }
        }

        fun deliverConnectivity(state: JSONObject) {
            val parsed = Payloads.connectivity(state)
            Island.post { Island.connectivity = parsed }
        }

        fun deliverTorch(torch: JSONObject?) {
            val parsed = Payloads.torch(torch)
            Island.post {
                Island.torch = parsed
                Island.bubble.onModsChanged()
            }
        }
    }

    private inner class Proxy(val region: Region) {
        val params = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        var pending: Rect? = null
        var placed: Rect? = null
        val view = object : View(this@BubbleService) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    reportOutside(event)
                    return false
                }
                forwardTouch(event, region)
                return true
            }
        }

        fun place(rect: Rect?) {
            val isShrinking = rect == null || placed?.let { old -> !(rect.left <= old.left && rect.top <= old.top && rect.right >= old.right && rect.bottom >= old.bottom) } == true
            if (isShrinking && Island.isTouchDown) {
                pending = rect ?: Rect.Zero
                return
            }
            pending = null
            placed = rect
            val density = Island.density
            if (rect == null || rect.width <= 0f) {
                params.width = 0
                params.height = 0
                params.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.x = (rect.left * density).roundToInt() + canvasParams.x
                params.y = (rect.top * density).roundToInt()
                params.width = (rect.width * density).roundToInt()
                params.height = (rect.height * density).roundToInt()
                params.flags = proxyFlags(region)
            }
            relayout(view, params)
        }

        fun settle() {
            val waiting = pending ?: return
            place(waiting.takeIf { it.width > 0f })
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: SharedPreferences
    private lateinit var stage: FrameLayout
    private lateinit var composeView: ComposeView
    private lateinit var canvasParams: WindowManager.LayoutParams
    private lateinit var proxies: Map<Region, Proxy>
    private lateinit var blurPanes: List<View>
    private lateinit var scrimPane: View
    private var scrimRadius = -1
    private val owner = OverlayOwner()
    private val paneRadius = IntArray(BLUR_PANES) { -1 }
    private val paneCorner = FloatArray(BLUR_PANES) { -1f }
    private var isFullScreen = false
    private var isLandscape = false
    private var isLandscapeStage = false
    private var isScreenOff = false
    private var isBarOurs = false
    private var screenWatch: BroadcastReceiver? = null
    private var panelWatch: BroadcastReceiver? = null
    private var outsideReportedAt = -1L
    private var isUserPresent = false
    private var screenOnAt = 0L
    private val appliedLayouts = HashMap<View, WindowManager.LayoutParams>()
    private var isPaneSyncQueued = false
    private val togglesWorker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    /* A level shares nothing with the toggles' worker: a drag begun as the Dashboard opens would otherwise wait behind the panel's whole read-back. */
    private val levelsWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val togglesQueued = java.util.concurrent.atomic.AtomicInteger(0)
    private val levelTargets = java.util.concurrent.ConcurrentHashMap<String, Int>()
    /* A switch or level just asked for reads back at what it was a moment ago, so what was asked is held over the read until the phone agrees or the settle runs out. */
    private val toggleIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val levelIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()

    private val paneSync = Choreographer.FrameCallback {
        isPaneSyncQueued = false
        syncPanes()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (instance !== this) return
        readFullScreen()
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (instance != null) return
        AppStyles.learnFrom(this)
        Haptics.attach(this)
        preferences = Preferences.of(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        windowManager = getSystemService(WindowManager::class.java)
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        Island.start(this)
        readScreen()
        Island.appearance = Appearance.read(preferences)
        Island.onBlobsDrawn = ::requestPaneSync
        Island.onDashboardLeft = ::hideLandscapeStage

        owner.start()
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { IslandStage() }
        }
        stage = FrameLayout(this).apply {
            setOnApplyWindowInsetsListener { _, _ -> android.view.WindowInsets.CONSUMED }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            scrimPane = View(this@BubbleService).apply { visibility = View.GONE }
            addView(scrimPane, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            blurPanes = List(BLUR_PANES) { View(this@BubbleService).apply { visibility = View.GONE } }
            blurPanes.forEach { addView(it, FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START)) }
            addView(composeView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        canvasParams = WindowManager.LayoutParams(
            screenWidth(), screenHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            CANVAS_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(horizontalOffset())
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            preferredRefreshRate = fastestRefreshRate()
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        windowManager.addView(stage, canvasParams)
        proxies = listOf(Region.Shield, Region.Dashboard, Region.Now, Region.Clear, Region.System, Region.Torch, Region.TorchOff, Region.Main, Region.Menu, Region.Status, Region.Clock, Region.Alert).associateWith { Proxy(it) }
        proxies.values.forEach { windowManager.addView(it.view, it.params) }
        instance = this

        Island.scope.launch {
            snapshotFlow { Island.regions.toMap() }.collect { regions ->
                proxies.forEach { (region, proxy) -> proxy.place(regions[region]) }
            }
        }
        Island.scope.launch {
            snapshotFlow { Island.isScreenDark }.collect { isDark ->
                setStageBrightness(if (isDark) DARK_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }

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
            },
        )
        applyVisibility()
        ShizukuShell.bind(this)
        ShizukuShell.watch(this)
        SystemToggles.attach(this)
        getSystemService(android.media.AudioManager::class.java)?.addOnModeChangedListener(mainExecutor) { requestToggles() }
        MediaControl.start(this) { media -> deliverMedia(media) }
        BatteryWatch.start(
            this,
            { battery -> deliverBattery(battery) },
            { percent, exact, plugged, remainingMinutes ->
                Island.post { Island.charge = com.v.island.bubble.Charge(percent, exact, plugged, remainingMinutes) }
            },
        )
        TorchWatch.start(this) { torch -> deliverTorch(torch) }
        ConnectivityWatch.start(this) { state -> deliverConnectivity(state) }
        isScreenOff = !getSystemService(PowerManager::class.java).isInteractive
        screenWatch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_SCREEN_ON) Island.setScreenDark(false)
                if (intent.action == Intent.ACTION_SCREEN_ON) screenOnAt = android.os.SystemClock.elapsedRealtime()
                if (intent.action == Intent.ACTION_USER_PRESENT) isUserPresent = true
                if (intent.action == Intent.ACTION_SCREEN_OFF) isUserPresent = false
                if (intent.action != Intent.ACTION_USER_PRESENT) {
                    isScreenOff = intent.action == Intent.ACTION_SCREEN_OFF
                    applyVisibility()
                }
                reportLock()
            }
        }.also {
            registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            })
        }
        panelWatch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == PANEL_BROADCAST) toggleStatusPanel()
                if (intent.action == NOTIFICATIONS_BROADCAST) toggleNotifications()
            }
        }.also {
            registerReceiver(it, IntentFilter(PANEL_BROADCAST).apply { addAction(NOTIFICATIONS_BROADCAST) }, Context.RECEIVER_EXPORTED)
        }
        reportLock()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        // A configuration change delivered before onServiceConnected read the window manager that is only set there and crashed the restart.
        if (!::windowManager.isInitialized) return
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        isLandscapeStage = false
        readScreen()
        canvasParams.width = screenWidth()
        canvasParams.height = screenHeight()
        canvasParams.x = dp(horizontalOffset())
        relayout(stage, canvasParams)
        applyVisibility()
        stage.postDelayed({ readFullScreen() }, ROTATION_SETTLE)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            MediaControl.stop()
            BatteryWatch.stop(this)
            ConnectivityWatch.stop(this)
            TorchWatch.stop()
            screenWatch?.let { runCatching { unregisterReceiver(it) } }
            panelWatch?.let { runCatching { unregisterReceiver(it) } }
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            Choreographer.getInstance().removeFrameCallback(paneSync)
            togglesWorker.shutdownNow()
            levelsWorker.shutdownNow()
            Island.onBlobsDrawn = {}
            Island.onDashboardLeft = {}
            Island.stop()
            proxies.values.forEach { runCatching { windowManager.removeView(it.view) } }
            runCatching { windowManager.removeView(stage) }
            owner.stop()
        }
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(changed: SharedPreferences, key: String?) {
        Island.appearance = Appearance.read(preferences)
        Island.bubble.onAppearanceChanged()
        canvasParams.x = dp(horizontalOffset())
        relayout(stage, canvasParams)
        proxies.values.forEach { it.place(it.placed) }
        paneRadius.fill(-1)
        if (scrimRadius != -1) scrimRadius = 0
        requestPaneSync()
    }

    private fun readScreen() {
        val bounds = windowManager.currentWindowMetrics.bounds
        Island.density = resources.displayMetrics.density
        Island.screenWidth = bounds.width() / Island.density
        Island.screenHeight = bounds.height() / Island.density
        Island.isLandscape = isLandscape
    }

    private fun readBarPolicy() {
        val value = runCatching { android.provider.Settings.Global.getString(contentResolver, "policy_control") }.getOrNull().orEmpty()
        isBarOurs = value.contains("immersive.status") || value.contains("immersive.full")
    }

    private fun readFullScreen() {
        val isBarHidden = !windowManager.currentWindowMetrics.windowInsets
            .isVisible(android.view.WindowInsets.Type.statusBars()) && !isBarOurs
        if (isBarHidden == isFullScreen) return
        isFullScreen = isBarHidden
        applyVisibility()
    }

    /* Landscape hid the stage outright, so the Dashboard launcher opened a panel nobody could see; the stage is shown in landscape for as long as the Dashboard is open or leaving, and nothing but the Dashboard is drawn on it. */
    private fun isHidden() = isScreenOff || (isLandscape && !isLandscapeStage) || (!isLandscape && isFullScreen)

    private fun showLandscapeStage() {
        if (isLandscapeStage || isScreenOff) return
        isLandscapeStage = true
        applyVisibility()
    }

    private fun hideLandscapeStage() {
        if (!isLandscapeStage || Island.dashboard.isOpen) return
        isLandscapeStage = false
        applyVisibility()
    }

    private fun proxyFlags(region: Region) =
        if (isHidden() || (isLandscape && region !in LANDSCAPE_REGIONS)) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS

    private fun applyVisibility() {
        val isHidden = isHidden()
        stage.visibility = if (isHidden) View.INVISIBLE else View.VISIBLE
        stage.requestedFrameRate = if (isHidden) View.REQUESTED_FRAME_RATE_CATEGORY_LOW else View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        Island.isStageHidden = isHidden
        if (isHidden) {
            blurPanes.forEachIndexed { index, pane -> clearPane(index, pane) }
            clearScrim()
            Island.dashboard.close()
            Island.bubble.toClosedIfExtended()
        } else requestPaneSync()
        proxies.values.forEach { proxy ->
            proxy.placed?.let { proxy.params.flags = proxyFlags(proxy.region) }
            relayout(proxy.view, proxy.params)
        }
    }

    private fun relayout(view: View?, layout: WindowManager.LayoutParams) {
        if (view == null) return
        val applied = appliedLayouts.getOrPut(view) { WindowManager.LayoutParams() }
        if (applied.copyFrom(layout) == 0) return
        runCatching { windowManager.updateViewLayout(view, layout) }.onFailure { appliedLayouts.remove(view) }
    }

    private fun reportOutside(event: MotionEvent) {
        if (event.eventTime == outsideReportedAt) return
        outsideReportedAt = event.eventTime
        if (isHidden()) return
        val density = Island.density
        val x = (event.rawX - canvasParams.x) / density
        val y = event.rawY / density
        Island.post { Island.onOutsideTap(x, y) }
    }

    private fun forwardTouch(event: MotionEvent, region: Region) {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) Island.onTouch(isDown = true, isCancel = false)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            Island.onTouch(isDown = false, isCancel = action == MotionEvent.ACTION_CANCEL)
        }
        if (action != MotionEvent.ACTION_MOVE) {
            android.util.Log.d("IslandBubble", "probe $region proxy heard ${MotionEvent.actionToString(action)} at ${(event.rawX / Island.density).toInt()}, ${(event.rawY / Island.density).toInt()}")
        }
        val forwarded = MotionEvent.obtain(event)
        forwarded.setLocation(event.rawX - canvasParams.x, event.rawY - canvasParams.y)
        if (action == MotionEvent.ACTION_CANCEL) forwarded.action = MotionEvent.ACTION_UP
        composeView.dispatchTouchEvent(forwarded)
        forwarded.recycle()
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            proxies.values.forEach { it.settle() }
        }
    }

    private fun requestPaneSync() {
        if (isPaneSyncQueued || !::stage.isInitialized) return
        isPaneSyncQueued = true
        Choreographer.getInstance().postFrameCallback(paneSync)
    }

    private fun syncPanes() {
        if (isHidden()) return
        syncScrim()
        val shapes: List<BlobShape> = Island.liquid.shapes() + Island.glass.values.mapNotNull { it() }.filter { it.opacity > 0.01f && it.rect.width > 0.5f && it.rect.height > 0.5f }
        val radius = Island.appearance.blurRadius
        blurPanes.forEachIndexed { index, pane ->
            val shape = shapes.getOrNull(index)
            if (shape == null || radius == 0) {
                if (pane.visibility != View.GONE) pane.visibility = View.GONE
                clearPane(index, pane)
                return@forEachIndexed
            }
            val left = shape.rect.left.roundToInt()
            val top = shape.rect.top.roundToInt()
            val right = shape.rect.right.roundToInt()
            val bottom = shape.rect.bottom.roundToInt()
            val isResized = pane.left != left || pane.top != top || pane.right != right || pane.bottom != bottom
            if (pane.visibility != View.VISIBLE) pane.visibility = View.VISIBLE
            (pane.layoutParams as FrameLayout.LayoutParams).apply {
                width = right - left
                height = bottom - top
                leftMargin = left
                topMargin = top
            }
            pane.left = left
            pane.top = top
            pane.right = right
            pane.bottom = bottom
            val corner = shape.radius.roundToInt().toFloat()
            val wanted = (radius * shape.opacity).roundToInt()
            if (!isResized && paneRadius[index] == wanted && paneCorner[index] == corner) return@forEachIndexed
            if (SamsungBlur.apply(pane, dp(wanted), corner)) {
                paneRadius[index] = wanted
                paneCorner[index] = corner
            }
        }
    }

    private fun syncScrim() {
        val wanted = (Island.appearance.scrimBlurRadius * Island.frost).roundToInt()
        if (wanted <= 0) {
            clearScrim()
            return
        }
        if (scrimPane.visibility != View.VISIBLE) scrimPane.visibility = View.VISIBLE
        if (wanted == scrimRadius) return
        if (SamsungBlur.apply(scrimPane, dp(wanted), 0f)) scrimRadius = wanted
    }

    private fun clearScrim() {
        if (scrimRadius == -1) return
        SamsungBlur.clear(scrimPane)
        scrimPane.visibility = View.GONE
        scrimRadius = -1
    }

    private fun clearPane(index: Int, pane: View) {
        if (paneRadius[index] == -1) return
        SamsungBlur.clear(pane)
        paneRadius[index] = -1
    }

    private fun reportLock() {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        Island.isLocked = keyguard?.isKeyguardLocked == true
    }

    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        @Suppress("DEPRECATION")
        power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "island:arrival",
        ).acquire(WAKE_MILLIS)
        isScreenOff = false
        applyVisibility()
    }

    /* Mirrors isDarkArrival in the retired BubbleService: Samsung lights the screen for a lock-screen notification before it is delivered, so a screen that came on a moment ago over a locked keyguard with nobody unlocking it still counts as dark. */
    private fun isDarkArrival(): Boolean {
        if (!getSystemService(PowerManager::class.java).isInteractive) return true
        return !isUserPresent &&
            android.os.SystemClock.elapsedRealtime() - screenOnAt < SYSTEM_WAKE_MILLIS &&
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
    }

    private fun setStageBrightness(brightness: Float) {
        if (canvasParams.screenBrightness == brightness) return
        canvasParams.screenBrightness = brightness
        relayout(stage, canvasParams)
    }

    private fun screenWidth(): Int = windowManager.currentWindowMetrics.bounds.width()

    private fun screenHeight(): Int = windowManager.currentWindowMetrics.bounds.height()

    private fun horizontalOffset() = if (isLandscape) 0 else Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun fastestRefreshRate(): Float =
        windowManager.defaultDisplay.supportedModes.maxOfOrNull { it.refreshRate } ?: 60f

    override fun openNotification(key: String) {
        if (!IslandNotificationListener.open(key)) android.util.Log.w("IslandBubble", "nothing opened for $key")
    }

    override fun openApp(packageName: String) {
        if (packageName == MediaControl.packageName && MediaControl.open(this)) return
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure { android.util.Log.w("IslandBubble", "cannot open $packageName", it) }
    }

    override fun openClock() {
        val alarms = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        val clockPackage = packageManager.resolveActivity(alarms, 0)?.activityInfo?.packageName
        val launch = clockPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        runCatching { startActivity((launch ?: alarms).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { android.util.Log.w("IslandBubble", "no clock app", it) }
    }

    override fun openControlPanel() {
        runCatching { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun openPowerMenu() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    override fun openConnectionSettings(kind: String) {
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
        if (kind == "battery" && startOrFalse(Intent(Intent.ACTION_MAIN).setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))) return
        if (kind == "vitals" && startOrFalse(Intent("com.samsung.android.sm.ACTION_DASHBOARD").setPackage("com.samsung.android.lool"))) return
        if (!startOrFalse(Intent(action))) startOrFalse(Intent(android.provider.Settings.ACTION_SETTINGS))
    }

    private fun startOrFalse(intent: Intent): Boolean =
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)

    override fun requestToggles() = queueToggleWork {}

    override fun setToggle(name: String, isOn: Boolean) {
        toggleIntents[name] = isOn to (android.os.SystemClock.uptimeMillis() + TOGGLE_SETTLE_MILLIS)
        if (SystemToggles.setDirect(name, isOn)) queueToggleWork {}
        else queueToggleWork { SystemToggles.set(name, isOn) }
    }

    override fun setLevel(name: String, percent: Int, isFinal: Boolean) {
        levelIntents[name] = percent to (android.os.SystemClock.uptimeMillis() + TOGGLE_SETTLE_MILLIS)
        if (isFinal) {
            levelTargets.remove(name)
            levelsWorker.execute { SystemToggles.setLevel(name, percent, true) }
            return
        }
        levelTargets[name] = percent
        levelsWorker.execute {
            val target = levelTargets.remove(name) ?: return@execute
            SystemToggles.setLevel(name, target, false)
        }
    }

    private fun queueToggleWork(work: () -> Unit) {
        togglesQueued.incrementAndGet()
        togglesWorker.execute {
            work()
            if (togglesQueued.decrementAndGet() == 0) reportToggles()
        }
    }

    private fun reportToggles() {
        val state = SystemToggles.read()
        var isWaiting = false
        val now = android.os.SystemClock.uptimeMillis()
        for ((name, intent) in toggleIntents) {
            val (wanted, deadline) = intent
            if (!state.has(name) || state.optBoolean(name) == wanted || now > deadline) {
                toggleIntents.remove(name)
                continue
            }
            state.put(name, wanted)
            isWaiting = true
        }
        for ((name, intent) in levelIntents) {
            val (wanted, deadline) = intent
            val answer = if (state.has(name)) state.optInt(name) else wanted
            if (kotlin.math.abs(answer - wanted) <= LEVEL_SETTLE_SLACK || now > deadline) {
                levelIntents.remove(name)
                continue
            }
            state.put(name, wanted)
            isWaiting = true
        }
        val toggles = Payloads.toggles(state)
        Island.post { Island.dashboard.onToggles(toggles) }
        if (isWaiting) togglesWorker.schedule({ reportToggles() }, TOGGLE_RECHECK_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    override fun requestVitals() {
        val context = this
        togglesWorker.execute {
            val vitals = Payloads.vitals(VitalsWatch.read(context))
            Island.post { Island.dashboard.onVitals(vitals) }
        }
    }

    override fun requestWeather() {
        WeatherWatch.request(this) { weather ->
            val parsed = Payloads.weather(weather)
            Island.post { Island.dashboard.onWeather(parsed) }
        }
    }

    override fun readBatteryExact(): Double = BatteryWatch.readExact(this)

    override fun readDataToday(): String {
        val total = android.net.TrafficStats.getTotalRxBytes() + android.net.TrafficStats.getTotalTxBytes()
        val day = java.time.LocalDate.now().toEpochDay()
        var base = preferences.getLong(Preferences.DATA_BASE, total)
        if (day != preferences.getLong(Preferences.DATA_DAY, -1L) || total < base) {
            base = total
            preferences.edit().putLong(Preferences.DATA_DAY, day).putLong(Preferences.DATA_BASE, base).apply()
        }
        val megabytes = (total - base) / 1024.0 / 1024.0
        return if (megabytes >= 1024) String.format(java.util.Locale.ROOT, "%.1f GB", megabytes / 1024)
        else String.format(java.util.Locale.ROOT, "%.0f MB", megabytes)
    }

    override fun transferAction(index: Int) = IslandNotificationListener.transferAction(index)

    override fun readNotifications(): List<Notice> = Payloads.notices(IslandNotificationListener.shade())

    override fun dismissNotification(key: String) = IslandNotificationListener.dismiss(key)

    override fun mediaControl(action: String) = MediaControl.command(action)

    override fun mediaSeek(milliseconds: Long) = MediaControl.seek(milliseconds)

    override fun mediaSpeed(rate: Float) = MediaControl.setSpeed(rate)

    override fun timerAction(index: Int) = IslandNotificationListener.timerAction(index)
    override fun noticeAction(key: String, index: Int) = IslandNotificationListener.noticeAction(key, index)

    override fun tripAction(index: Int) = IslandNotificationListener.tripAction(index)

    override fun recordingAction(index: Int) = IslandNotificationListener.recordingAction(index)

    override fun setTorch(step: Int) = TorchWatch.set(step)

    /* Mirrors replayAlertTap in the retired BubbleService: a tap the Alert's band heard that was not for the bubble is handed on to the app underneath, with the band untouchable for as long as the replay takes. */
    override fun passTap(x: Float, y: Float) {
        val band = proxies[Region.Alert] ?: return
        if (band.placed == null) return
        band.params.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        relayout(band.view, band.params)
        val restore = {
            if (band.placed != null) {
                band.params.flags = proxyFlags(Region.Alert)
                relayout(band.view, band.params)
            }
        }
        val path = android.graphics.Path().apply { moveTo(x * Island.density + canvasParams.x, y * Island.density) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, TAP_MILLIS)
        val isSent = runCatching {
            dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(),
                object : GestureResultCallback() {
                    override fun onCompleted(description: android.accessibilityservice.GestureDescription?) = restore()
                    override fun onCancelled(description: android.accessibilityservice.GestureDescription?) = restore()
                },
                null,
            )
        }.getOrDefault(false)
        if (!isSent) restore()
    }

    override fun sleepScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }
}

fun Context.pushToIsland(payload: JSONObject, arrivedDark: Boolean = false) = BubbleService.deliver(payload, arrivedDark)
