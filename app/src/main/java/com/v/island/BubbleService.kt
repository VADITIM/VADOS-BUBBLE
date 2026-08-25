package com.v.island

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

/**
 * Hosts the bubble window and owns the WebView that renders it.
 * Everything the UI needs arrives as a JSON payload pushed into JS; everything
 * the UI wants to do comes back through [Bridge].
 *
 * It is an accessibility service for one reason: window layering. SystemUI's
 * status bar ranks above every `TYPE_APPLICATION_OVERLAY`, so a bubble drawn at
 * the cutout could be seen but not touched. `TYPE_ACCESSIBILITY_OVERLAY` is the
 * only window type above the status bar that an unprivileged app can obtain, and
 * it is granted by the accessibility toggle rather than by a signature permission.
 */
class BubbleService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        /** Long enough to watch the bubble arrive and read it, short enough not to drain. */
        private const val WAKE_MILLIS = 6_000L

        /**
         * An invisible margin around the bubble that still takes touches, so a tap does
         * not have to be precise. It costs exactly this much of the shade swipe at the
         * very top of the screen, which is why it is small and not generous.
         */
        private const val GRAB = 8

        /**
         * Room around the bubble that exists only so an overshoot has somewhere to go.
         * Every curve here settles by going past its mark and coming back, and the
         * window is the one clip nothing in the page can escape — `overflow: visible`
         * cannot help, because what does the cutting is not a box in the document. So
         * the window is given a margin the size of the largest overshoot and the page
         * simply does not use it.
         *
         * It is not free: this is that much more status bar the shade swipe cannot
         * start on, which is why it is the size of the bounce and not a round number.
         */
        private const val BLEED = 14

        /** Kept in step with the grown states' border-radius in pill.html. */
        private const val GROWN_CORNER = 26

        /**
         * The WebView is this big whatever the window is doing, and the window simply
         * clips it. Resizing the WebView itself reallocates its surface, which showed
         * up as the bubble blinking out for a frame every time an animation ended.
         */
        private const val STAGE_WIDTH = 340

        /**
         * Tall enough for the tallest thing the page can ask for, which is the
         * notification list at three quarters of the screen — see historyWindow() in
         * pill.html. The stage is the ceiling on everything: the window can be told
         * any height, but nothing is drawn past the edge of the surface inside it.
         */
        private const val STAGE_HEIGHT = 620

        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // An open bubble closes on a tap anywhere else, and the window is only
                // as big as the bubble, so the touch has to be heard from outside it.
                // ACTION_OUTSIDE reports it without consuming it: whatever was tapped
                // still gets the tap.
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        /** The system owns this service's lifecycle, so the running instance is the flag. */
        private var instance: BubbleService? = null

        val isRunning: Boolean get() = instance != null

        /** Dropped when the bubble is not on screen; there is nothing to show it on. */
        fun deliver(payload: JSONObject) {
            val service = instance ?: return
            service.wakeScreen()
            service.push("window.onNotificationUpdate($payload)")
            service.push("window.setUnreadCount(${IslandNotificationListener.count()})")
        }

        /** How many notifications the shade is holding, as the badge sees it. */
        fun deliverCount(count: Int) {
            instance?.push("window.setUnreadCount($count)")
        }

        /** The running timer, or null once the clock app stops counting. */
        fun deliverTimer(timer: JSONObject?) {
            instance?.push("window.onTimerUpdate(${timer ?: "null"})")
        }

        /** A notification the shade no longer holds — killed, dismissed or withdrawn. */
        fun deliverGone(key: String) {
            instance?.push("window.onNotificationGone(${JSONObject.quote(key)})")
        }

        /** The connected call, or null once it is hung up. */
        fun deliverCall(call: JSONObject?) {
            instance?.push("window.onCallUpdate(${call ?: "null"})")
        }

        /**
         * The light. It does not go to the bubble at all: it has a pill of its own at
         * the far left of the status bar, standing on One UI's own flashlight chip.
         * The bubble is only told that the chip is out, because it pulls its width in
         * a little while something else is on the same bar.
         */
        fun deliverTorch(torch: JSONObject?) {
            instance?.nowState?.deliver(torch)
            // The Haptic panel carries a switch for it, and that switch is painted from
            // the camera service rather than from its own tap: lit from Samsung's tile
            // or from the panel it stands in, it has to read the same either way.
            instance?.push("window.onTorchLit(${torch != null})")
        }

        /**
         * The bubble pulling its width in for the chip, or giving it back. Said by the
         * torch's own page rather than here: the width goes when the flying pill has
         * physically left the bubble, and only that page knows where it is.
         */
        fun torchChip(out: Boolean) {
            instance?.push("window.onTorchChip($out)")
        }

        /** The bubble taking the top back when the torch panel closes: same-type overlays stack in add order, so being on top means being added last. */
        fun raiseBubble() {
            instance?.let { host ->
                runCatching { host.windowManager.removeViewImmediate(host.stage) }
                runCatching { host.windowManager.addView(host.stage, host.params) }
            }
        }

        /** The chip's own window has no vibrator of its own to reach for. */
        fun vibrateFor(type: String) {
            instance?.vibrate(
                when (type) {
                    "tap" -> VibrationEffect.EFFECT_CLICK
                    "expand" -> VibrationEffect.EFFECT_HEAVY_CLICK
                    "dismiss" -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    else -> VibrationEffect.EFFECT_TICK
                }
            )
        }
    }

    /** The torch's own pill, out on the left where One UI draws its own. */
    private var nowState: NowState? = null

    private lateinit var windowManager: WindowManager

    /** The window's root; the WebView inside it keeps a constant size. */
    private lateinit var stage: FrameLayout
    private lateinit var webView: WebView

    /**
     * An empty view that exists only to carry the blur. The blurred region is a
     * view's bounds, so blurring the window root blurred the invisible grab margin
     * with it; this one is exactly the bubble, and it follows the bubble's own
     * animation rather than jumping to the finished size.
     */
    private lateinit var blurCanvas: View
    private var blurAnimator: android.animation.ValueAnimator? = null

    /** The satellites' glass runs on the split's own clock, which is not the bubble's. */
    private var satelliteAnimator: android.animation.ValueAnimator? = null

    /** The radius each blurred region is rounded by right now, in pixels. */
    private var blurCorner = 0f
    private val satelliteCorners = floatArrayOf(0f, 0f)

    /**
     * A satellite's own glass. Two of them, because three mods flank the bubble —
     * one circle a side — and each is its own pane: a single region spanning the row
     * would blur the gaps between them as well.
     */
    private lateinit var blurSatellites: List<View>
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var preferences: SharedPreferences
    private var pageReady = false
    private val pending = ArrayDeque<String>()

    /** The bubble has nothing to say over a fullscreen app or a landscape screen. */
    private var isFullScreen = false
    private var isLandscape = false
    private var isGrown = false

    /**
     * A dark screen is not an empty one. Left plugged in overnight the phone shows the
     * always-on display, and anything still drawn is drawn in the same pixels for
     * hours — which is how an OLED gets burnt. Nothing is worth showing to a screen
     * nobody is looking at, so the bubble goes away with the screen and comes back
     * with it.
     */
    private var isScreenOff = false
    private var screenWatch: BroadcastReceiver? = null

    /**
     * Nothing is read from the events themselves; they are only the cue to look at
     * the status bar again. Our own window's insets cannot answer that question —
     * it lays out beyond every limit — so the display's metrics are asked instead,
     * and a window change is the only moment the answer can have changed.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (instance !== this) return
        val isBarHidden = !windowManager.currentWindowMetrics.windowInsets
            .isVisible(android.view.WindowInsets.Type.statusBars())
        if (isBarHidden == isFullScreen) return
        isFullScreen = isBarHidden
        applyVisibility()
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (instance != null) return

        preferences = Preferences.of(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        windowManager = getSystemService(WindowManager::class.java)

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // A silent JS error in a WebView-driven interface looks exactly like a
            // dead bubble, so the console goes to logcat.
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("IslandBubble", "${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/pill.html")
        }

        stage = object : FrameLayout(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    push("window.onOutsideTap()")
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            // Without this the window gets pushed below the cutout and the bubble
            // can never sit level with it.
            setOnApplyWindowInsetsListener { _, _ -> android.view.WindowInsets.CONSUMED }
            blurCanvas = View(this@BubbleService)
            addView(
                blurCanvas,
                FrameLayout.LayoutParams(
                    dp(compactWidth()),
                    dp(compactHeight()),
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply { topMargin = dp(topGrab()) }
            )
            blurSatellites = List(2) { View(this@BubbleService).apply { visibility = View.GONE } }
            blurSatellites.forEach { satellite ->
                addView(
                    satellite,
                    FrameLayout.LayoutParams(
                        dp(compactHeight()),
                        dp(compactHeight()),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    ).apply { topMargin = dp(topGrab()) }
                )
            }
            addView(
                webView,
                FrameLayout.LayoutParams(
                    dp(STAGE_WIDTH),
                    dp(STAGE_HEIGHT),
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
        }

        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        params = WindowManager.LayoutParams(
            dp(compactWidth() + (GRAB + BLEED) * 2),
            dp(compactHeight() + topGrab() + GRAB + BLEED),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET))
            // The top of the screen, and it stays there: the margin above the bubble is
            // the whole offset, so anything that wants to be drawn higher than the
            // resting bubble has room without the window moving for it.
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            // A centre-gravity window changes its x whenever its width changes, and
            // the window manager animates that move: the bubble jumped to the new
            // left edge and slid back to the middle after every collapse. The CSS
            // owns this motion; the window must not add its own.
            // One UI drops an overlay to 60Hz unless it says otherwise, and the growth
            // curves read as stepped at that rate. Ask for the fastest mode the panel
            // has; the window is a few hundred pixels, so it costs nothing to run it
            // at 120.
            preferredRefreshRate = fastestRefreshRate()
            windowAnimations = 0
            setCanPlayMoveAnimation(false)
        }
        applyBlur()
        // The refresh rate is only half of it: the platform also throttles a view that
        // does not say it wants frames, and a WebView animating CSS looks idle to it.
        stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        // The torch's window goes up first, and that is the whole of how it ends up
        // behind the bubble: two windows of one type from one app are stacked in the
        // order they were added, and there is no z to set on an accessibility overlay.
        // It matters because the pill is born inside the bubble at the punch hole — it
        // has to come out from under it, not slide across its face.
        nowState = NowState(this).also {
            it.attach(compactWidth(), compactHeight(), topGrab(), Preferences.backgroundCss(preferences))
        }
        windowManager.addView(stage, params)
        applyVisibility()
        ShizukuShell.bind(this)
        instance = this

        MediaControl.start(this) { media ->
            push("window.onMediaUpdate(${media ?: "null"})")
        }

        // The battery never wakes the screen. Plugging in at night is exactly the case
        // this must not light up for, and the platform posts its own low-battery
        // warning for the one that matters with the phone in a pocket.
        BatteryWatch.start(this) { battery -> push("window.onBattery($battery)") }

        // The light is state the same way a song is, and it is read from the camera
        // service rather than from this app, so it is right whoever lit it.
        TorchWatch.start(this) { torch -> deliverTorch(torch) }

        isScreenOff = !getSystemService(PowerManager::class.java).isInteractive
        screenWatch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isScreenOff = intent.action == Intent.ACTION_SCREEN_OFF
                applyVisibility()
            }
        }.also {
            registerReceiver(
                it,
                IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
            )
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyVisibility()
    }

    /**
     * Hidden means transparent *and* untouchable: the window stays attached so it
     * keeps receiving the insets that say the fullscreen app has gone away.
     */
    private fun applyVisibility() {
        val isHidden = isLandscape || isFullScreen || isScreenOff
        // The chip is on the same glass and goes away for the same reasons.
        nowState?.setHidden(isHidden)
        stage.alpha = if (isHidden) 0f else 1f
        // The blur is the compositor's, not the view's, so a transparent view keeps
        // blurring: it has to be taken off by hand or it hangs over a landscape
        // screen with nothing drawn on it.
        if (isHidden) {
            SamsungBlur.clear(blurCanvas)
            blurSatellites.forEach { SamsungBlur.clear(it) }
        } else {
            applyBlur()
            blurSatellites.forEachIndexed { index, satellite ->
                if (satellite.visibility == View.VISIBLE) {
                    applyBlur(satellite, satelliteCorners[index])
                }
            }
        }
        params.flags =
            if (isHidden) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else BASE_FLAGS
        runCatching { windowManager.updateViewLayout(stage, params) }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            MediaControl.stop()
            BatteryWatch.stop(this)
            TorchWatch.stop()
            nowState?.detach()
            nowState = null
            screenWatch?.let { runCatching { unregisterReceiver(it) } }
            screenWatch = null
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            runCatching { windowManager.removeView(stage) }
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(changed: SharedPreferences, key: String?) {
        // Geometry lives on the window, colour lives in CSS. Both apply live.
        params.width = dp(compactWidth() + (GRAB + BLEED) * 2)
        params.height = dp(compactHeight() + topGrab() + GRAB + BLEED)
        params.x = dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET))
        params.y = 0
        applyBlur()
        runCatching { windowManager.updateViewLayout(stage, params) }
        pushAppearance()
    }

    /**
     * The same blur One UI puts behind its own pop-ups: the window asks the compositor
     * to blur whatever is behind it, which is the only way to get it — nothing a
     * WebView draws can reach pixels it does not own, so `backdrop-filter` inside the
     * page blurs nothing. A radius of 0 takes the flag off again and leaves the plain
     * translucent background alone.
     *
     * The compositor refuses the effect outright while the phone is in battery saver
     * or has blurs switched off in developer options, and says so rather than the
     * setting looking broken.
     */
    private fun applyBlur(view: View, corner: Float) {
        // Nothing is drawn in landscape or under a fullscreen app, so nothing may be
        // blurred either — the page keeps pushing layouts it cannot see.
        if (isLandscape || isFullScreen || isScreenOff) {
            SamsungBlur.clear(view)
            return
        }
        val radius = Preferences.get(preferences, Preferences.BLUR)
        if (radius == 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.blurBehindRadius = 0
            SamsungBlur.clear(view)
            return
        }
        if (view === blurCanvas) blurCorner = corner
        if (SamsungBlur.apply(view, dp(radius), corner)) return

        // AOSP's path, for a build where the vendor one is gone. It is a no-op while
        // the compositor has blurs switched off, which is the case on this phone.
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = dp(radius)
        if (!windowManager.isCrossWindowBlurEnabled) {
            android.util.Log.i("IslandBubble", "blur asked for but disabled system-wide")
        }
    }

    private fun applyBlur() = applyBlur(blurCanvas, blurCorner)

    /** One blurred region: a size, a rounding and where it sits across the window. */
    private class BlurBox(
        val width: Int,
        val height: Int,
        val corner: Float,
        val offset: Float
    )

    private fun boundsOf(view: View) = view.layoutParams as FrameLayout.LayoutParams

    private fun place(view: View, from: BlurBox, to: BlurBox, travelled: Float) {
        val bounds = boundsOf(view)
        bounds.width = from.width + ((to.width - from.width) * travelled).toInt()
        bounds.height = from.height + ((to.height - from.height) * travelled).toInt()
        view.layoutParams = bounds
        view.translationX = from.offset + (to.offset - from.offset) * travelled
        // The corner is re-applied every frame, because the bubble's own radius is
        // transitioning too: a blur that jumped straight to the target radius
        // spilled past the bubble's still-rounder corners.
        applyBlur(view, from.corner + (to.corner - from.corner) * travelled)
    }

    /**
     * The bubble's own growth, replayed for the blur. The blur cannot be animated by
     * the CSS that owns the bubble — it is a window effect, not a pixel the page can
     * draw — so the page hands over the shapes it is heading for and how long it will
     * take, and the same curve is run here. Without this the blur snapped to the
     * finished size and the animation played inside a shape that was already there.
     *
     * Two regions, not one: a satellite is its own circle with its own glass, and a
     * single region spanning both blurred the gap between them as well.
     */
    private fun animateBlur(
        main: BlurBox,
        satellites: List<BlurBox?>,
        duration: Long,
        satelliteDuration: Long,
        satelliteDelay: Long
    ) {
        blurAnimator?.cancel()
        satelliteAnimator?.cancel()
        val mainFrom = BlurBox(
            boundsOf(blurCanvas).width,
            boundsOf(blurCanvas).height,
            blurCorner,
            blurCanvas.translationX
        )
        val satellitesFrom = blurSatellites.mapIndexed { index, view ->
            BlurBox(
                boundsOf(view).width,
                boundsOf(view).height,
                satelliteCorners[index],
                view.translationX
            )
        }

        blurSatellites.forEachIndexed { index, view ->
            val box = satellites.getOrNull(index)
            view.visibility = if (box == null) View.GONE else View.VISIBLE
            if (box == null) SamsungBlur.clear(view)
        }

        if (duration <= 0L) {
            place(blurCanvas, main, main, 1f)
        } else {
            blurAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                // The same overshoot the bubble's --ease-grow has, so the two edges stay
                // together instead of one arriving late.
                interpolator = android.view.animation.PathInterpolator(0.22f, 1.12f, 0.36f, 1f)
                addUpdateListener {
                    place(blurCanvas, mainFrom, main, it.animatedValue as Float)
                }
                start()
            }
        }

        if (satellites.all { it == null }) return

        // A satellite's glass is not on the bubble's clock and never was: the page
        // moves a circle on --ease-split, which overshoots by most of its own travel,
        // holds it back by --split-delay so the bubble can narrow first, and only then
        // lets it go. Run on the bubble's shorter, gentler curve with no delay at all,
        // the frosted circle set off early, took a straighter line and arrived first —
        // which is the glass and the circle visibly coming apart mid-split. Its own
        // duration, its own delay, its own curve, or it cannot be in step.
        if (satelliteDuration <= 0L) {
            blurSatellites.forEachIndexed { index, view ->
                satellites.getOrNull(index)?.let {
                    satelliteCorners[index] = it.corner
                    place(view, it, it, 1f)
                }
            }
            return
        }
        satelliteAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = satelliteDuration
            startDelay = satelliteDelay
            interpolator = android.view.animation.PathInterpolator(0.2f, 1.7f, 0.35f, 1f)
            addUpdateListener {
                val travelled = it.animatedValue as Float
                blurSatellites.forEachIndexed { index, view ->
                    val box = satellites.getOrNull(index) ?: return@forEachIndexed
                    val from = satellitesFrom[index]
                    place(view, from, box, travelled)
                    satelliteCorners[index] = from.corner + (box.corner - from.corner) * travelled
                }
            }
            start()
        }
    }

    private fun pushAppearance() {
        // The chip is the same glass at the same height, so it takes the same two.
        nowState?.setMetrics(compactWidth(), compactHeight(), topGrab())
        nowState?.setBackground(Preferences.backgroundCss(preferences))
        nowState?.setBlur(Preferences.get(preferences, Preferences.BLUR))
        push("window.setPillBackground('${Preferences.backgroundCss(preferences)}')")
        push("window.setCompactSize(${Preferences.get(preferences, Preferences.WIDTH)},${Preferences.get(preferences, Preferences.HEIGHT)})")
        push("window.setGrab(${topGrab()})")
        push("window.setNotificationIdentity(${Preferences.get(preferences, Preferences.NOTIFICATION_IDENTITY)})")
        push("window.setUnreadCount(${IslandNotificationListener.count()})")
        // The closed mods are state, not events: whatever was already true before this
        // page existed has to be asked for, because nothing will announce it again.
        MediaControl.refresh()
        IslandNotificationListener.publishTimer()
        // Including a call that was already connected: the listener publishes it when
        // it binds, and on an install that happens before this service exists — the
        // push was dropped on the floor and nothing was ever going to say it again.
        IslandNotificationListener.publishCall()
    }

    private fun push(js: String) {
        webView.post {
            if (pageReady) webView.evaluateJavascript(js, null) else pending.addLast(js)
        }
    }

    /**
     * Exactly the bubble, with no padding to reach past the status bar: an
     * accessibility overlay is above it, so every pixel of the bubble is tappable.
     */
    private fun compactHeight(): Int = Preferences.get(preferences, Preferences.HEIGHT)

    private fun compactWidth(): Int = Preferences.get(preferences, Preferences.WIDTH)

    /** 0 means "no preference", which is the honest answer if the display is gone. */
    private fun fastestRefreshRate(): Float =
        getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            ?.supportedModes
            ?.maxOfOrNull { it.refreshRate }
            ?: 0f

    /**
     * The same margin above the bubble, except that it runs all the way to the top of
     * the screen. Those pixels are already unreachable — the bubble sits below the
     * status bar and a swipe that starts above it never reaches the shade from there
     * anyway — so giving them to the bubble costs nothing and makes the top edge of
     * the gesture area the top edge of the phone.
     */
    private fun topGrab(): Int = Preferences.get(preferences, Preferences.VERTICAL_OFFSET)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    /**
     * Nothing an unprivileged app draws reaches the always-on display — that surface
     * belongs to SystemUI — so the only way the arrival animation can be seen on a
     * dark screen is to turn the screen on for it.
     */
    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        @Suppress("DEPRECATION")
        power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "island:notification"
        ).acquire(WAKE_MILLIS)
        // The broadcast that says the screen is on arrives after the animation has
        // begun, and the bubble would play its arrival hidden. This is a wake we asked
        // for, so it counts from here.
        isScreenOff = false
        applyVisibility()
    }

    private fun vibrate(effect: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createPredefined(effect))
    }

    inner class Bridge {
        @JavascriptInterface
        fun ready() {
            webView.post {
                pageReady = true
                pushAppearance()
                // The appearance goes through post() and so lands on the *next* loop,
                // while a drain here would run immediately — and a song that arrived
                // before the page was ready would then size the window against the
                // default bubble instead of the configured one. Same queue, same order.
                webView.post {
                    while (pending.isNotEmpty()) webView.evaluateJavascript(pending.removeFirst(), null)
                }
            }
        }

        /**
         * Keeps the window exactly as big as the bubble currently is. It sits above
         * the status bar, so every pixel it covers is a pixel the shade swipe cannot
         * start on — at rest that has to be the bubble and nothing more.
         */
        @JavascriptInterface
        fun setWindowSize(widthDp: Int, heightDp: Int) =
            setWindowBounds(widthDp, heightDp, 0, 0)

        /**
         * `riseDp` is how much higher than the resting bubble a state draws — the
         * alert grows over the cutout instead of stopping under it. It is room rather
         * than a move: the window already begins at the top of the screen, and one that
         * slid up while the page animated the same distance inside it fought itself,
         * which is why the alert drifted out of line and snapped back on the way home.
         */
        @JavascriptInterface
        fun setWindowBounds(widthDp: Int, heightDp: Int, riseDp: Int, shiftDp: Int) {
            webView.post {
                // Negative means "back to the user's compact size".
                isGrown = heightDp >= 0
                params.width = dp((if (widthDp < 0) compactWidth() else widthDp) + (GRAB + BLEED) * 2)
                params.height = dp((if (isGrown) heightDp else compactHeight()) + topGrab() + GRAB + BLEED)
                // riseDp is what the page wants to draw above the resting bubble. It is
                // room, not a move: the window already starts at the top of the screen,
                // so the height covers it and the y never changes.
                params.y = 0
                // A satellite hangs off one side, so the window is wider on that side
                // only. Without this the centred window would slide the bubble off the
                // cutout to make the room; with it, the extra width goes where the
                // satellite is and the bubble stays welded to the camera.
                params.x = dp(
                    Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET) + shiftDp
                )
                runCatching { windowManager.updateViewLayout(stage, params) }
            }
        }

        /**
         * The bubble's size as the CSS is about to draw it, which is not the window's:
         * the window is the bubble plus the grab margin, and during a collapse it is
         * still the old, larger one. The blur follows this, not the window.
         */
        @JavascriptInterface
        fun setBlurBounds(
            widthDp: Int,
            heightDp: Int,
            cornerDp: Int,
            offsetDp: Int,
            satelliteWidthDp: Int,
            satelliteOffsetDp: Int,
            farSatelliteWidthDp: Int,
            farSatelliteOffsetDp: Int,
            durationMs: Int,
            satelliteMs: Int,
            satelliteDelayMs: Int
        ) {
            webView.post {
                val main = BlurBox(
                    dp(if (widthDp < 0) compactWidth() else widthDp),
                    dp(if (heightDp < 0) compactHeight() else heightDp),
                    dp(cornerDp).toFloat(),
                    dp(offsetDp).toFloat()
                )
                // A satellite is a pill of the closed height, however wide it is
                // mid-drag, so it is rounded by half that height and never by more.
                fun satellite(widthDp: Int, offsetDp: Int) = if (widthDp <= 0) null else BlurBox(
                    dp(widthDp),
                    dp(compactHeight()),
                    dp(compactHeight()) / 2f,
                    dp(offsetDp).toFloat()
                )
                animateBlur(
                    main,
                    listOf(
                        satellite(satelliteWidthDp, satelliteOffsetDp),
                        satellite(farSatelliteWidthDp, farSatelliteOffsetDp)
                    ),
                    durationMs.toLong(),
                    satelliteMs.toLong(),
                    satelliteDelayMs.toLong()
                )
            }
        }

        @JavascriptInterface
        fun triggerHaptic(type: String) = vibrate(
            when (type) {
                "tap" -> VibrationEffect.EFFECT_CLICK
                "expand" -> VibrationEffect.EFFECT_HEAVY_CLICK
                "dismiss" -> VibrationEffect.EFFECT_DOUBLE_CLICK
                else -> VibrationEffect.EFFECT_TICK
            }
        )

        /**
         * Swiping an alert away only sends it back to closed. The badge counts what
         * the shade is holding, and dismissing the bubble does not empty the shade.
         */
        @JavascriptInterface
        fun onSwipeDismiss() = Unit

        /** Everything the notification centre is holding right now. */
        @JavascriptInterface
        fun readHistory(): String = IslandNotificationListener.shade().toString()

        @JavascriptInterface
        fun dismissNotification(key: String) = IslandNotificationListener.dismiss(key)

        @JavascriptInterface
        fun readUnreadCount(): Int = IslandNotificationListener.count()

        /**
         * Opens the app that posted it. The badge is not touched: reading a
         * notification here does not take it out of the shade, and a count that
         * disagreed with the shade would be worse than no count at all.
         */
        @JavascriptInterface
        fun openNotification(key: String) {
            IslandNotificationListener.open(key)
        }

        /** "allowed" | "blocked" | "unavailable" — unavailable means no shell UID. */
        @JavascriptInterface
        fun readMicrophoneAccess(): String = MicrophoneAccess.read()

        /** Read from the camera service, so the switch is right whoever lit the torch. */
        @JavascriptInterface
        fun readTorchLit(): Boolean = TorchWatch.isLit

        /**
         * Switched rather than dialled, so it goes back to the step it was last left
         * at — a light turned on from here at 1/5 when it was last used at 5/5 would
         * read as the switch having dimmed it.
         */
        @JavascriptInterface
        fun setTorchLit(isLit: Boolean) {
            TorchWatch.set(if (isLit) TorchWatch.lastStep else 0)
        }

        @JavascriptInterface
        fun setMicrophoneAccess(isAllowed: Boolean) {
            MicrophoneAccess.set(isAllowed)
            // Painted optimistically in JS, corrected here from what the system
            // actually did — a refused command must not leave a lying switch.
            push("window.onMicrophoneAccessChanged('${MicrophoneAccess.read()}')")
        }

        /**
         * The app behind an open mod, opened by holding it. A mod has no notification
         * of its own to tap through — the song is a media session and the timer's
         * notification is the clock's, so the launcher intent is the honest way in.
         */
        @JavascriptInterface
        fun openApp(packageName: String) {
            // The media session knows its own way in and, more importantly, is allowed
            // to take it; a service starting the activity itself is a background
            // activity launch and Android drops it silently.
            // Only for the app that owns the session, though: the shortcut used to be
            // taken for every package, so asking for anything else opened the player.
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

        /** Step 0 puts the light out, 1 to 5 light it at that share of full. */
        @JavascriptInterface
        fun setTorch(step: Int) = TorchWatch.set(step)

        @JavascriptInterface
        fun mediaControl(action: String) = MediaControl.command(action)

        /** Where the song is right now, for the moment the player is opened. */
        @JavascriptInterface
        fun readMediaPosition(): String = MediaControl.position().toString()

        /** One of the clock app's own notification buttons, by position. */
        @JavascriptInterface
        fun timerAction(index: Int) = IslandNotificationListener.timerAction(index)

        @JavascriptInterface
        fun mediaSeek(milliseconds: String) {
            MediaControl.seek(milliseconds.toLongOrNull() ?: return)
        }
    }
}

/**
 * Fire a payload at the bubble. The accessibility service is bound by the system
 * and cannot be started on demand, so a payload arriving while it is off is dropped.
 */
fun Context.pushToIsland(payload: JSONObject) = BubbleService.deliver(payload)
