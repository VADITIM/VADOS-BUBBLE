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
         * Where the Now bubble's left edge comes to rest, measured from the left edge of
         * the screen. It stands at the very start of the bar, over One UI's clock as well
         * as over the Now bar beside it: the light is what that end of the bar says while
         * it is on, and half-covering the row it replaces is worse than covering none of
         * it. Mirrors the --now-left property in pill.html.
         *
         * The WebView fills the canvas and never changes size — resizing it reallocates
         * its surface, which showed up as the bubble blinking out for a frame at the end
         * of every animation — so this is a position in the page, not a window.
         */
        private const val NOW_LEFT = 16


        /**
         * One pane of glass per bubble that can stand anywhere on this screen: the main
         * bubble, a satellite each side, the Now bubble, and the lock screen's own. Mirrored in pill.html's BLUR_PANES,
         * which sends exactly this many regions in exactly this order — there is no
         * build step joining the two, so a bubble added on one side and not the other
         * is a bubble that draws without glass or a pane blurring nothing.
         */
        private const val BLUR_PANES = 5

        /** How long a touch on a bubble holds the screen on for. */
        private const val LOCK_AWAKE = 15_000L

        /** One wake covers a gesture; asking on every touch event is asking for nothing. */
        private const val WAKE_FRAMES_EVERY = 150L

        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // An open bubble closes on a tap anywhere else, and the proxy is only
                // as big as the bubble, so the touch has to be heard from outside it.
                // ACTION_OUTSIDE reports it without consuming it: whatever was tapped
                // still gets the tap.
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        /**
         * The canvas draws and never takes a touch. It spans the whole status bar so
         * that every bubble is on one surface and can therefore merge with the others,
         * and a touchable window that wide would make the top of the screen dead: every
         * pixel a touchable window covers is a pixel the shade swipe cannot start on.
         * Untouchable, the swipe passes straight through it and only the small proxy
         * over the bubble is in the way.
         */
        private const val CANVAS_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        /** The system owns this service's lifecycle, so the running instance is the flag. */
        private var instance: BubbleService? = null

        val isRunning: Boolean get() = instance != null

        /**
         * The last touch forwarded to the page, and what the page did with it. Read by
         * the control panel's Debug screen: this phone has no adb on it, so a question
         * about where a finger actually landed has nowhere else to be answered.
         */
        fun trace(): String {
            val service = instance ?: return "the bubble is not running"
            return service.lastTouch + "\n" + service.lastNote
        }

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

        /** The song playing, or null once it stops. */
        fun deliverMedia(media: JSONObject?) {
            instance?.push("window.onMediaUpdate(${media ?: "null"})")
        }

        /** A charge level worth saying something about. */
        fun deliverBattery(battery: JSONObject) {
            instance?.push("window.onBattery($battery)")
        }

        /**
         * The light. It is not drawn in the bubble: it has a bubble of its own out at
         * the clock, standing on One UI's own flashlight chip — but that bubble is in
         * the same page, on the same surface, which is the only way it can merge with
         * the row when it flies past.
         */
        fun deliverTorch(torch: JSONObject?) {
            instance?.push("window.onTorchUpdate(${torch ?: "null"})")
            // The Haptic panel carries a switch for it, and that switch is painted from
            // the camera service rather than from its own tap: lit from Samsung's tile
            // or from the panel it stands in, it has to read the same either way.
            instance?.push("window.onTorchLit(${torch != null})")
        }


    }

    private lateinit var windowManager: WindowManager

    /** The canvas window's root. Everything drawn lives in here, at a constant size. */
    private lateinit var stage: FrameLayout
    private lateinit var webView: WebView

    /** The window that hears the finger, kept exactly as big as what is interactive. */
    private lateinit var touchProxy: View
    private lateinit var proxyParams: WindowManager.LayoutParams

    /**
     * The same thing again, over the Now bubble out at the clock. Two proxies rather
     * than one wide one: the gap between them is most of the status bar, and it has to
     * stay somewhere the shade swipe can start.
     */
    private lateinit var nowProxy: View
    private lateinit var nowProxyParams: WindowManager.LayoutParams

    /** The same again for the lock screen bubble, down at the bottom of the canvas. */
    private lateinit var lockProxy: View
    private lateinit var lockProxyParams: WindowManager.LayoutParams

    /**
     * One empty view per bubble, carrying nothing but that bubble's glass: main, the
     * two satellites, the Now pill. The blurred region is a view's bounds, so blurring
     * the window root blurred the invisible grab margin with it, and one region
     * spanning the row would blur the gaps between the shapes as well.
     *
     * They are placed, never animated. The page mirrors every shape's real
     * `getBoundingClientRect()` frame by frame and sends the rectangles as they are
     * being drawn, so the glass has no curve, no duration and no geometry of its own to
     * keep in step with the CSS — it is wherever its bubble is this frame. The pane
     * that replayed the bubble's curve on the host's own clock is what used to drift
     * out from under a shape that was scaled, dragged or handed over mid-flight.
     */
    private lateinit var blurPanes: List<View>

    /** The radius each pane is rounded by right now, in pixels. */
    private val paneCorners = FloatArray(BLUR_PANES)

    /**
     * What was last actually handed to the compositor for each pane, so a frame that changed nothing about a pane costs
     * nothing: the blur is re-applied per pane per frame and each application is four reflective invocations plus a
     * `SemBlurInfo` allocation, which at 120Hz over five panes was ~2,400 reflective calls a second for an effect that
     * only changes when a bubble's width or rounding does. `-1` means cleared, which is a different answer from a
     * radius of zero and has to survive a re-apply.
     */
    /** The region string each pane was last placed from, so an unchanged pane is not re-parsed or re-laid-out. */
    private val paneSpec = arrayOfNulls<String>(BLUR_PANES)

    private val paneBlurRadius = IntArray(BLUR_PANES) { -1 }
    private val paneBlurCorner = FloatArray(BLUR_PANES) { -1f }

    /** Mirrors `Preferences.BLUR`, re-read only when it changes — it was a `SharedPreferences` read per pane per frame. */
    private var blurRadius = 0
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
     * Whether the keyguard is up. The lock screen is not a mode this app enters — it is
     * a place the phone is, and the page draws a different set of bubbles while it is
     * there. Read from `KeyguardManager` rather than tracked through the broadcasts,
     * because the broadcasts are only ever the cue to look: SCREEN_ON fires before the
     * keyguard has decided, and a phone woken by a notification is locked while a phone
     * woken to an already-dismissed keyguard is not.
     */
    private var isLocked = false

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
        // Temporary: logs every read so a swipe-to-reveal or back-gesture peek in a
        // fullscreen game can be compared against what the bubbles actually did — see
        // C15 in PLAN.md, the reveal was reported to hide the bubbles instead of showing them.
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

        preferences = Preferences.of(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        blurRadius = Preferences.get(preferences, Preferences.BLUR)
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
            webViewClient = AssetOrigin.client(this@BubbleService)
            loadUrl(AssetOrigin.ROOT + "pill.html")
        }

        stage = FrameLayout(this).apply {
            // Without this the window gets pushed below the cutout and the bubble
            // can never sit level with it.
            setOnApplyWindowInsetsListener { _, _ -> android.view.WindowInsets.CONSUMED }
            // Hung off the left edge and moved by translation alone: the page measures
            // every rectangle from the left edge of the canvas, which is this window,
            // so a pane placed at that number is over its bubble without a second
            // coordinate system to convert between.
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

        // The one window that hears a finger. It carries nothing and decides nothing:
        // it forwards where it was touched and the page works out what was touched,
        // because the page is the only side that knows what it is currently drawing.
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

        nowProxy = object : View(this) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    reportOutside(event)
                    return false
                }
                forwardTouch(event, nowProxyParams, "now")
                return true
            }
        }

        // The proxy is the old bubble window, emptied out: the same size, the same
        // place, the same grab margin — it simply has no picture in it any more.
        proxyParams = WindowManager.LayoutParams(
            dp(compactWidth() + (GRAB + BLEED) * 2),
            dp(compactHeight() + topGrab() + GRAB + BLEED),
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
        // Placed from the page like the Now bubble.s, and given no size until the lock
        // screen actually has a bubble to be over. Unlike the two at the top of the
        // screen this one costs no shade swipe at all -- nothing starts a gesture down there
        // except the navigation bar, which is below it.
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
        // Hung on the left, where the Now bubble stands, rather than centred like the
        // bubble's own. It is given no size until the light is on.
        nowProxyParams = WindowManager.LayoutParams(
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
        // No pane is painted here: every one of them is hidden until the page sends the
        // first frame of rectangles, which is the only side that knows what it is drawing.
        // The refresh rate is only half of it: the platform also throttles a view that
        // does not say it wants frames, and a WebView animating CSS looks idle to it.
        stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        windowManager.addView(stage, params)
        // After the canvas, so a finger reaches the proxy rather than being caught by
        // a window that is only there to draw.
        windowManager.addView(touchProxy, proxyParams)
        windowManager.addView(nowProxy, nowProxyParams)
        windowManager.addView(lockProxy, lockProxyParams)
        applyVisibility()
        ShizukuShell.bind(this)
        instance = this

        MediaControl.start(this) { media -> deliverMedia(media) }

        // The battery never wakes the screen. Plugging in at night is exactly the case
        // this must not light up for, and the platform posts its own low-battery
        // warning for the one that matters with the phone in a pocket.
        BatteryWatch.start(this) { battery -> deliverBattery(battery) }

        // The light is state the same way a song is, and it is read from the camera
        // service rather than from this app, so it is right whoever lit it.
        TorchWatch.start(this) { torch -> deliverTorch(torch) }

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
                    // The one event that says the keyguard is *gone*. Unlocking raises no
                    // screen event at all — the screen was already on — so without this
                    // the page would keep the lock screen's bubbles up over the home
                    // screen until something else happened to make it look again.
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

    /**
     * Hidden means transparent *and* untouchable: the window stays attached so it
     * keeps receiving the insets that say the fullscreen app has gone away.
     */
    private fun applyVisibility() {
        val isHidden = isLandscape || isFullScreen || isScreenOff
        // One canvas, so the Now bubble goes with it: it is drawn in the same page.
        stage.alpha = if (isHidden) 0f else 1f
        // The blur is the compositor's, not the view's, so a transparent view keeps
        // blurring: it has to be taken off by hand or it hangs over a landscape
        // screen with nothing drawn on it.
        if (isHidden) blurPanes.forEachIndexed { index, pane -> clearBlur(index, pane) } else repaintBlur()
        // The canvas is never touchable; the proxy is what stops hearing fingers when
        // there is nothing on screen to touch.
        proxyParams.flags =
            if (isHidden) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else BASE_FLAGS
        // The other two proxies stand over bubbles that are not always there, so hiding
        // takes the flag away and *showing has to ask for it back*. It used to be left
        // to whatever the page happened to repaint next, and that is a proxy that never
        // becomes touchable again until something unrelated moves: waking to a lock
        // screen sets the keyguard flag before the screen event lands, so the page sees
        // no change, sends nothing, and the bubble at the bottom of the screen is drawn
        // perfectly and cannot be touched. The page is the only side that knows what is
        // interactive, so it is asked rather than guessed at.
        if (isHidden) {
            nowProxyParams.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(nowProxy, nowProxyParams) }
            lockProxyParams.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(lockProxy, lockProxyParams) }
        } else {
            push("window.refitProxies()")
        }
        runCatching { windowManager.updateViewLayout(stage, params) }
        runCatching { windowManager.updateViewLayout(touchProxy, proxyParams) }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            MediaControl.stop()
            BatteryWatch.stop(this)
            TorchWatch.stop()
            screenWatch?.let { runCatching { unregisterReceiver(it) } }
            screenWatch = null
            awake.removeCallbacks(sleepAgain)
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            runCatching { windowManager.removeView(lockProxy) }
            runCatching { windowManager.removeView(nowProxy) }
            runCatching { windowManager.removeView(touchProxy) }
            runCatching { windowManager.removeView(stage) }
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(changed: SharedPreferences, key: String?) {
        // Geometry lives on the windows, colour lives in CSS. Both apply live. The
        // canvas only ever moves for the horizontal offset: its size is the screen's.
        params.x = dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET))
        proxyParams.width = dp(compactWidth() + (GRAB + BLEED) * 2)
        proxyParams.height = dp(compactHeight() + topGrab() + GRAB + BLEED)
        proxyParams.x = params.x
        proxyParams.y = 0
        blurRadius = Preferences.get(preferences, Preferences.BLUR)
        repaintBlur()
        runCatching { windowManager.updateViewLayout(stage, params) }
        runCatching { windowManager.updateViewLayout(touchProxy, proxyParams) }
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
    private fun applyBlur(index: Int, view: View, corner: Float, resized: Boolean = false) {
        // Nothing is drawn in landscape or under a fullscreen app, so nothing may be
        // blurred either — the page keeps pushing layouts it cannot see.
        if (isLandscape || isFullScreen || isScreenOff) {
            clearBlur(index, view)
            return
        }
        val radius = blurRadius
        if (radius == 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.blurBehindRadius = 0
            clearBlur(index, view)
            return
        }
        // A resize counts as a change even though radius and corner are identical: the blurred region is the view's
        // size at the moment it was asked for, and a pill's corner is half its height — which does not move at all
        // while the bubble is growing or closing. Keyed on radius and corner alone the whole animation was one apply
        // on the first frame and skips after it, so the glass stayed the width the bubble started at.
        if (!resized && paneBlurRadius[index] == radius && paneBlurCorner[index] == corner) return
        // Temporary: every apply on the main pane, to see what the last frames of a close actually do — the bubble
        // was reported losing its glass for a frame exactly as it lands. Pull this back out once diagnosed.
        if (index == 0) {
            android.util.Log.i(
                "IslandBubble",
                "blur apply pane0 radius=$radius corner=$corner resized=$resized size=${view.width}x${view.height}"
            )
        }
        if (SamsungBlur.apply(view, dp(radius), corner)) {
            paneBlurRadius[index] = radius
            paneBlurCorner[index] = corner
            return
        }

        // AOSP's path, for a build where the vendor one is gone. It is a no-op while
        // the compositor has blurs switched off, which is the case on this phone.
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = dp(radius)
        if (!windowManager.isCrossWindowBlurEnabled) {
            android.util.Log.i("IslandBubble", "blur asked for but disabled system-wide")
        }
    }

    /** Takes the blur off a pane and forgets what it was wearing, so the next apply is never skipped as a repeat. */
    private fun clearBlur(index: Int, view: View) {
        // Forgotten even when there was no blur to take off: the pane's placement is what the next frame is diffed
        // against, and a cleared pane has to be placed again whatever it was or was not wearing.
        paneSpec[index] = null
        if (paneBlurRadius[index] == -1) return
        // Temporary, with the apply log above: a clear landing mid-close is the shape of the reported flash.
        if (index == 0) android.util.Log.i("IslandBubble", "blur clear pane0")
        SamsungBlur.clear(view)
        paneBlurRadius[index] = -1
        paneSpec[index] = null
    }

    /** Every pane that is showing, blurred again at the rounding it already has. */
    private fun repaintBlur() = blurPanes.forEachIndexed { index, pane ->
        if (pane.visibility == View.VISIBLE) applyBlur(index, pane, paneCorners[index])
    }

    /**
     * A touch on a bubble counts as using the phone, and the keyguard has no idea it
     * happened: these windows are not the lock screen's, so working the media controls
     * down there was watched by nobody and the screen went off in the middle of it.
     *
     * PowerManager.userActivity, which is what actually resets the timer, is a signature
     * permission and out of reach. FLAG_KEEP_SCREEN_ON is not — while it is set the
     * timeout cannot run out at all — so it is set on the touch and taken off again a
     * while later, which is the same thing from the outside: every touch pushes the
     * screen-off out by LOCK_AWAKE from that moment. It has to come off again, or the
     * one flag left standing is a phone that never sleeps.
     */
    private val awake = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepAgain = Runnable {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        runCatching { windowManager.updateViewLayout(stage, params) }
    }

    private fun keepAwake() {
        // Only while the keyguard is up, which is the only place the problem was: these
        // windows are not the lock screen's, so a touch on them is watched by nobody.
        // Held on an unlocked phone it does the opposite of what it looks like — the flag
        // suspends the timeout rather than restarting it, so the moment it comes off again
        // the display is already past due and the screen goes off on the spot, which reads
        // as the phone locking itself at random a few seconds after being unlocked.
        if (!isLocked) return
        awake.removeCallbacks(sleepAgain)
        if (params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            runCatching { windowManager.updateViewLayout(stage, params) }
        }
        awake.postDelayed(sleepAgain, LOCK_AWAKE)
    }

    /** The display's own width in pixels, which is exactly how wide the canvas is. */
    private fun screenWidth(): Int = windowManager.currentWindowMetrics.bounds.width()

    /**
     * The canvas is the whole screen, and it has to be: the lock screen carries a bubble
     * of its own down at the bottom, and a bubble is only liquid with what shares its
     * surface. It was 620dp — enough for the notification list at three quarters of the
     * screen and no more — and everything below that was simply not drawable. Untouchable
     * and transparent where nothing is drawn, so the extra room costs nothing but the
     * surface itself; what it buys is that every bubble on this phone is still in one page
     * and one goo layer, top of the screen and bottom alike. The stage is the ceiling on
     * everything: the window can be told any height, but nothing is drawn past the edge of
     * the surface inside it.
     */
    private fun stageHeight(): Int = windowManager.currentWindowMetrics.bounds.height()

    /**
     * Tells the page where the phone is. Sent on every cue rather than only on changes,
     * because the page queues anything that arrives before it is ready and the state has
     * to survive that; the page itself ignores a value it already has.
     */
    private fun reportLock() {
        isLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        // Unlocked, the hold is over: the phone is being used and its own timeout is the
        // right one again.
        if (!isLocked) { awake.removeCallbacks(sleepAgain); sleepAgain.run() }
        push("window.onLock($isLocked)")
    }

    @Volatile
    private var lastTouch = "nothing has been touched yet"

    @Volatile
    private var lastNote = ""


    /**
     * A touch on a proxy, told to the page in the page's own coordinates.
     *
     * The canvas is centred and as wide as the screen, so its left edge is exactly the
     * horizontal offset; a proxy's left edge is its own. The difference between the two
     * is the whole of the translation, and everything else — what was touched, what the
     * gesture means, whether it was a tap or a drag — is the page's to work out. Which
     * proxy heard it goes with it: a grown panel is drawn over the whole bar and would
     * otherwise take every touch aimed at the bubble standing underneath it.
     */
    /**
     * A touch that landed outside this proxy, told to the page in the page's own
     * coordinates. It is not necessarily outside the *interface*: there is more than
     * one proxy, so a tap on an open panel is an outside touch to every proxy but the
     * one under it. Only the page knows which, so it is given the point and decides.
     */
    private fun reportOutside(event: android.view.MotionEvent) {
        val density = resources.displayMetrics.density
        // The canvas is as wide as the screen and centred, so its left edge is exactly
        // the horizontal offset.
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
        // Plus the window's own top, because a proxy is not always at the top of the
        // screen any more: the lock screen's bubble stands at the bottom of the canvas,
        // and a touch reported at its own window's y would land in the status bar.
        val y = (proxy.y + event.y) / density
        // A finger landing is the other thing that always ends in motion.
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

    /**
     * One frame of glass: every pane put exactly where its bubble is being drawn right
     * now, in the page's own coordinates.
     *
     * There is no animation here on purpose. The host used to be handed the shape each
     * bubble was heading for and how long it would take, and it replayed that curve on
     * its own clock — which meant every curve in the CSS had a twin in Kotlin, and any
     * motion the page had not thought to describe (a hold's scale, a drag, a swap that
     * renames two boxes without moving them) left the frosted rectangle standing at a
     * size and a place its bubble was not. The page mirrors real rectangles every frame
     * for the liquid skin already, and the same measurement drives the glass: it cannot
     * disagree with a shape it is read off.
     *
     * A rectangle of no width is a bubble that is not there, and its pane is hidden and
     * cleared by hand — a transparent view goes on blurring.
     */
    private fun placeBlurFrame(spec: String) {
        val density = resources.displayMetrics.density
        val regions = spec.split(';')
        blurPanes.forEachIndexed { index, pane ->
            val region = regions.getOrNull(index).orEmpty()
            // A pane whose region reads exactly as it did last frame is already standing where this frame would put
            // it, so the parse, the layout and the blur are all skipped: the page sends every bubble every frame and
            // most of them are not the one that is moving. The page's own dedupe cannot do this — it compares the
            // whole spec, so one bubble moving re-sends all five. `clearBlur` forgets the region it belongs to, which
            // is what keeps this honest at screen-off: the host clears the panes by hand there, and a pane that had
            // been left believing it was still placed would never be given its blur back on wake.
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
            // Assigned only on a real change: writing layoutParams back is a layout pass, and it was being paid per
            // pane per frame for a row that is standing still most of the time.
            val resized = bounds.width != width || bounds.height != height
            if (resized) {
                bounds.width = width
                bounds.height = height
                pane.layoutParams = bounds
            }
            pane.translationX = numbers[0] * density
            pane.translationY = numbers[1] * density
            pane.visibility = View.VISIBLE
            // The bubble's own radius moves with its width, so the glass is re-rounded whenever it changes: glass that
            // kept a rounding of its own spilled past the corners.
            paneCorners[index] = numbers[4] * density
            applyBlur(index, pane, paneCorners[index], resized)
        }
    }

    private fun pushAppearance() {
        push("window.setNowLeft($NOW_LEFT)")
        push("window.setPillBackground('${Preferences.backgroundCss(preferences)}')")
        push("window.setCompactSize(${Preferences.get(preferences, Preferences.WIDTH)},${Preferences.get(preferences, Preferences.HEIGHT)})")
        push("window.setGrab(${topGrab()})")
        push("window.setNotificationIdentity(${Preferences.get(preferences, Preferences.NOTIFICATION_IDENTITY)})")
        push("window.setGoo(${Preferences.get(preferences, Preferences.GOO)})")
        push("window.setModWidth(${Preferences.get(preferences, Preferences.MOD_WIDTH)})")
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
        // Every one of these is about to cause motion, and the panel this is drawn on
        // is variable-rate: a status bar nobody has touched in a minute is being
        // refreshed a handful of times a second, and the first frames of an animation
        // started into that idle state arrive late and unevenly — which is the stutter
        // on an animation that has not played for a while, and the reason the second
        // one looks fine. So the frames are asked for before the thing that needs them,
        // exactly as window room is.
        wakeFrames()
        webView.post {
            if (pageReady) webView.evaluateJavascript(js, null) else pending.addLast(js)
        }
    }

    /** When the panel was last asked to come up to speed. */
    private var framesWokeAt = 0L

    /**
     * Ask for a high refresh rate and force a draw, which is what actually pulls the
     * display out of its idle rate — a category set once at startup is not enough on a
     * view that then draws nothing for a minute. Throttled, because a drag calls it on
     * every event and one wake covers the whole gesture.
     */
    private fun wakeFrames() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - framesWokeAt < WAKE_FRAMES_EVERY) return
        framesWokeAt = now
        stage.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        webView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        stage.invalidate()
        webView.invalidate()
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
         * Keeps the touch proxy exactly as big as what is interactive right now. It
         * sits above the status bar, so every pixel it covers is a pixel the shade
         * swipe cannot start on — at rest that has to be the bubble and nothing more.
         * The canvas behind it never changes size and is never in the way.
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
                proxyParams.width =
                    dp((if (widthDp < 0) compactWidth() else widthDp) + (GRAB + BLEED) * 2)
                proxyParams.height =
                    dp((if (isGrown) heightDp else compactHeight()) + topGrab() + GRAB + BLEED)
                // riseDp is what the page wants to draw above the resting bubble. It is
                // room, not a move: the proxy already starts at the top of the screen,
                // so the height covers it and the y never changes.
                proxyParams.y = 0
                // A satellite hangs off one side, so the proxy is wider on that side
                // only. Without this the centred proxy would sit off the bubble to make
                // the room, and every touch would land shifted by half the difference.
                proxyParams.x =
                    dp(Preferences.get(preferences, Preferences.HORIZONTAL_OFFSET) + shiftDp)
                runCatching { windowManager.updateViewLayout(touchProxy, proxyParams) }
            }
        }

        /**
         * Where every bubble is being drawn this frame — one region per bubble, in the
         * page's own coordinates, as `left,top,width,height,corner` separated by
         * semicolons and in the order the panes stand in: main, left satellite, right
         * satellite, Now. An empty region is a bubble that is not on screen.
         *
         * This arrives on the page's animation frame while it has something moving, and
         * stops when it does. It is not a request for a journey: the page is not saying
         * where a shape is going, it is saying where the shape *is*, so there is nothing
         * for the host to interpolate and nothing of the CSS's timing to duplicate here.
         */
        @JavascriptInterface
        fun setBlurFrame(spec: String) {
            webView.post { placeBlurFrame(spec) }
        }

        /**
         * The page is about to start moving something the host had no hand in — a
         * timer running out, a transition it decided on its own. Everything the host
         * pushes wakes the panel already; this is the same wake for the other half.
         */
        @JavascriptInterface
        fun wakeFrames() {
            webView.post { this@BubbleService.wakeFrames() }
        }

        /**
         * Where the Now bubble can be touched. Nothing else along that stretch of bar
         * may be: it is all shade swipe.
         */
        /** What the page decided about a touch, so the two sides can be compared. */
        @JavascriptInterface
        fun note(text: String) {
            lastNote = text
        }

        /**
         * Where the lock screen bubble is being drawn, in the page's own coordinates and
         * measured from the top of the canvas — which is the top of the screen, so the
         * page sends a top even though the CSS hangs the bubble off the bottom. Mirrors
         * LOCK_INSET / LOCK_BOTTOM / LOCK_HEIGHT in pill.html.
         */
        @JavascriptInterface
        fun setLockProxy(widthDp: Int, heightDp: Int, leftDp: Int, topDp: Int) {
            webView.post {
                val isLive = widthDp > 0
                lockProxyParams.width = if (isLive) dp(widthDp) else 0
                lockProxyParams.height = if (isLive) dp(heightDp) else 0
                // Plus the canvas's own x, for the same reason the Now proxy takes it:
                // the page measures from the left edge of the canvas and the canvas is
                // moved by the user's horizontal offset.
                lockProxyParams.x = params.x + dp(leftDp)
                lockProxyParams.y = dp(topDp)
                lockProxyParams.flags =
                    if (isLive) BASE_FLAGS
                    else BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { windowManager.updateViewLayout(lockProxy, lockProxyParams) }
            }
        }

        @JavascriptInterface
        fun setNowProxy(widthDp: Int, heightDp: Int, leftDp: Int) {
            webView.post {
                val isLive = widthDp > 0
                // Exactly the bubble, with none of the bubble's grab margin: that margin
                // exists for a drag and a hold that overshoots, and this bubble has
                // neither — it is tapped and held in place. It cost more than it was
                // worth, too. The light now reaches most of the way to the punch hole,
                // so a margin either side of it lay over the bubble standing there, and
                // two windows claiming the same pixels means the one that hears a finger
                // is whichever won the race.
                nowProxyParams.width = if (isLive) dp(widthDp) else 0
                nowProxyParams.height = if (isLive) dp(heightDp + topGrab()) else 0
                // Plus the canvas's own x: the page measures this from the left edge of
                // the canvas, and the canvas is moved by the horizontal offset. Without
                // it the window stands beside the bubble it is meant to be over by
                // exactly that offset, and every touch it hears lands somewhere else.
                nowProxyParams.x = params.x + dp(leftDp)
                nowProxyParams.flags =
                    if (isLive) BASE_FLAGS
                    else BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { windowManager.updateViewLayout(nowProxy, nowProxyParams) }
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
