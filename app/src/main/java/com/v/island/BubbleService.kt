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
        private const val SHADE_STRIP = 48
        private const val ROTATION_SETTLE = 600L
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

        fun toggleStatusPanel() = Island.post { instance?.openQuickSettings() }

        fun toggleMessaging() = Unit

        fun toggleNotifications() = Island.post {
            if (instance == null) return@post
            if (Island.bubble.size == com.v.island.bubble.Size.Notifications) Island.bubble.toClosed()
            else Island.bubble.openNotifications()
        }

        fun trace(): String {
            val service = instance ?: return "the bubble is not running"
            val hotspot = SystemToggles.hotspotRefusal.takeIf { it.isNotEmpty() }?.let { "\nhotspot refused:\n$it" }.orEmpty()
            return service.lastTouch + hotspot
        }

        fun isDarkArrival(): Boolean = instance?.let { !it.getSystemService(PowerManager::class.java).isInteractive } == true

        fun deliver(payload: JSONObject, arrivedDark: Boolean = false) {
            val notice = Payloads.notice(payload)
            Island.post {
                val service = instance ?: return@post
                if (arrivedDark || isDarkArrival()) service.wakeScreen()
                Island.unread = IslandNotificationListener.count()
                Island.bubble.onNotice(notice)
            }
        }

        fun deliverDark(payload: JSONObject) = deliver(payload)

        fun deliverCount(count: Int) = Island.post { Island.unread = count }

        fun deliverMessaging(conversations: JSONArray) {
            val parsed = Payloads.conversations(conversations)
            Island.post { Island.conversations = parsed }
        }

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

        fun stageLock(locked: Boolean) = Island.post { Island.isLocked = locked }

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
                params.flags = proxyFlags()
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
    private val owner = OverlayOwner()
    private val paneRadius = IntArray(BLUR_PANES) { -1 }
    private val paneCorner = FloatArray(BLUR_PANES) { -1f }
    private var shadeGuard: View? = null
    private var shadeGuardParams: WindowManager.LayoutParams? = null
    private var isFullScreen = false
    private var isLandscape = false
    private var isScreenOff = false
    private var isBarOurs = false
    private var screenWatch: BroadcastReceiver? = null
    private var panelWatch: BroadcastReceiver? = null
    private var outsideReportedAt = -1L
    private var lastTouch = ""
    private val appliedLayouts = HashMap<View, WindowManager.LayoutParams>()
    private var isPaneSyncQueued = false

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
        proxies = listOf(Region.Clear, Region.Main, Region.Status, Region.Clock).associateWith { Proxy(it) }
        proxies.values.forEach { windowManager.addView(it.view, it.params) }
        applyBarLock()
        instance = this

        Island.scope.launch {
            snapshotFlow { Island.regions.toMap() }.collect { regions ->
                proxies.forEach { (region, proxy) -> proxy.place(regions[region]) }
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
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
            Island.onBlobsDrawn = {}
            Island.stop()
            proxies.values.forEach { runCatching { windowManager.removeView(it.view) } }
            shadeGuard?.let { runCatching { windowManager.removeView(it) } }
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
        requestPaneSync()
        applyBarLock()
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

    private fun isHidden() = isScreenOff || isLandscape || isFullScreen

    private fun proxyFlags() =
        if (isHidden()) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS

    private fun applyVisibility() {
        val isHidden = isHidden()
        stage.visibility = if (isHidden) View.INVISIBLE else View.VISIBLE
        stage.requestedFrameRate = if (isHidden) View.REQUESTED_FRAME_RATE_CATEGORY_LOW else View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        Island.isStageHidden = isHidden
        if (isHidden) {
            blurPanes.forEachIndexed { index, pane -> clearPane(index, pane) }
            Island.bubble.toClosedIfExtended()
        } else requestPaneSync()
        proxies.values.forEach { proxy ->
            proxy.placed?.let { proxy.params.flags = proxyFlags() }
            relayout(proxy.view, proxy.params)
        }
        shadeGuardParams?.let { guardParams ->
            guardParams.flags = if (isHidden) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS
            relayout(shadeGuard, guardParams)
        }
    }

    private fun applyBarLock() {
        val isWanted = Preferences.get(preferences, Preferences.BAR_LOCKED) == 1
        if (isWanted == (shadeGuard != null)) return
        if (!isWanted) {
            runCatching { windowManager.removeView(shadeGuard) }
            shadeGuard?.let { appliedLayouts.remove(it) }
            shadeGuard = null
            shadeGuardParams = null
            return
        }
        val guard = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean = true
        }
        val guardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(SHADE_STRIP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, BASE_FLAGS, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        runCatching { windowManager.addView(guard, guardParams) }
        shadeGuard = guard
        shadeGuardParams = guardParams
        proxies.values.forEach { proxy ->
            runCatching {
                windowManager.removeView(proxy.view)
                windowManager.addView(proxy.view, proxy.params)
            }
            appliedLayouts.remove(proxy.view)
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
            lastTouch = "$region proxy heard ${MotionEvent.actionToString(action)} at ${(event.rawX / Island.density).toInt()}, ${(event.rawY / Island.density).toInt()}"
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
        val shapes: List<BlobShape> = Island.liquid.shapes()
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

    override fun openQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    override fun readNotifications(): List<Notice> = Payloads.notices(IslandNotificationListener.shade())

    override fun dismissNotification(key: String) = IslandNotificationListener.dismiss(key)

    override fun mediaControl(action: String) = MediaControl.command(action)

    override fun mediaSeek(milliseconds: Long) = MediaControl.seek(milliseconds)

    override fun mediaSpeed(rate: Float) = MediaControl.setSpeed(rate)

    override fun timerAction(index: Int) = IslandNotificationListener.timerAction(index)

    override fun tripAction(index: Int) = IslandNotificationListener.tripAction(index)

    override fun recordingAction(index: Int) = IslandNotificationListener.recordingAction(index)

    override fun setTorch(step: Int) = TorchWatch.set(step)
}

fun Context.pushToIsland(payload: JSONObject, arrivedDark: Boolean = false) = BubbleService.deliver(payload, arrivedDark)
