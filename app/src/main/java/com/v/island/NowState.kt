package com.v.island

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

/**
 * The torch, as a pill of its own at the far left of the status bar.
 *
 * Every other state the bubble carries is something an app said, and it belongs in
 * the bubble because that is where this phone's notifications now live. The torch is
 * not that: One UI 8.5 already draws a flashlight chip in its Now bar beside the
 * clock, and there is no setting anywhere that turns it off. Saying the same thing a
 * second time out at the cutout would be two indicators for one light, so this
 * stands exactly on top of Samsung's instead.
 *
 * That is why it is a second window rather than a mod. The bubble's window is
 * deliberately no wider than the bubble — every pixel it covers is a pixel the shade
 * swipe cannot start on — so it cannot be stretched across the whole status bar to
 * reach the clock. A window of its own is small, is placed where it has to be, and
 * cannot break anything the bubble does.
 */
class NowState(private val context: Context) {

    companion object {
        /**
         * Where the pill's left edge comes to rest, measured from the left edge of
         * the screen: past the clock, on the spot One UI gives its own flashlight
         * chip. The left edge, because that is the stop the pill flies into. It is
         * born at the punch hole — everything on this screen is — travels out here,
         * runs into this line, and opens out to the right off it. Nudge this if a
         * One UI update moves the Now bar.
         */
        private const val LEFT = 53

        /**
         * How wide the pill rests. Not the closed bubble's width, which is what it
         * was: this pill's whole job is to stand on top of One UI's own flashlight
         * chip, and that chip is wider than the bubble. Matched to the bubble
         * instead, Samsung's blue pill was left sticking out past its right edge
         * whenever the light was on — two flashlight indicators on one bar, which is
         * the exact thing this window exists to prevent. It is measured off One UI's
         * chip, so it moves when a One UI update moves that, not when the bubble is
         * resized. Flying, the pill is still a drop as wide as it is tall.
         */
        private const val RESTING_WIDTH = 82

        /** The same invisible margin the bubble's window carries. */
        private const val GRAB = 8

        /**
         * What the bubble at the cutout gives up while this pill is out — mirrors
         * CHIP_TAX in pill.html. The bubble is the one that yields the room, because
         * it is the one that can: this pill is standing on a fixed spot it does not
         * own and cannot move off without uncovering what is underneath it.
         */
        private const val CHIP_TAX = 24

        /**
         * And the same room for an overshoot to go past its mark into. The window is
         * the one clip the page cannot escape — no `overflow` rule reaches it — so the
         * bounce is given somewhere to happen and the pill simply does not rest there.
         * Kept in step with BLEED in BubbleService, and with --grab-x in now.html.
         */
        private const val BLEED = 14

        /**
         * The WebView is this big whatever the window is doing, and the window
         * clips it. Resizing the WebView itself reallocates its surface, which shows
         * up as the chip blinking out for a frame at the end of every animation.
         */
        private const val STAGE_WIDTH = 260
        private const val STAGE_HEIGHT = 160

        private const val FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // The panel closes on a tap anywhere else, and the window is only as
                // big as the chip, so that touch has to be heard from outside it.
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private lateinit var stage: FrameLayout
    private lateinit var webView: WebView
    private lateinit var params: WindowManager.LayoutParams

    private var ready = false
    private val pending = ArrayDeque<String>()

    /** Null while the light is out. The pill is still on screen for a while after. */
    private var torch: JSONObject? = null

    /**
     * Whether the pill has finished leaving. The light going out is not the pill
     * going away — it still has to fly home to the punch hole, and darkening the
     * window on the news would delete it mid-flight. Only the page knows when it has
     * actually arrived, so only the page says so.
     */
    private var gone = true

    private var isHidden = false

    private lateinit var blurCanvas: View
    private var blurAnimator: android.animation.ValueAnimator? = null
    private var blurCorner = 0f
    private var blurRadius = 0

    /** The bubble's own top margin, so the two pills stand on exactly one line. */
    private var grab = 0

    private var pillHeight = 34

    /** What the pill rests at: One UI's own chip, which this one covers. */
    private var pillWidth = RESTING_WIDTH

    fun attach(width: Int, height: Int, grabTop: Int, background: String) {
        pillHeight = height
        grab = grabTop
        webView = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("IslandBubble", "torch: ${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/now.html")
        }

        stage = object : FrameLayout(context) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    push("window.onOutsideTap()")
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            // Without this the window is pushed below the cutout and the chip can
            // never sit level with the status bar it is standing in.
            setOnApplyWindowInsetsListener { _, _ -> android.view.WindowInsets.CONSUMED }
            // The blur is a region of the window, not something the page can draw:
            // nothing a WebView renders reaches pixels it does not own. So an empty
            // view exactly the size of the pill carries it.
            blurCanvas = View(context)
            addView(
                blurCanvas,
                FrameLayout.LayoutParams(
                    dp(pillHeight), dp(pillHeight), Gravity.TOP or Gravity.START
                ).apply { topMargin = dp(grab); leftMargin = dp(GRAB + BLEED) }
            )
            addView(
                webView,
                FrameLayout.LayoutParams(
                    dp(STAGE_WIDTH), dp(STAGE_HEIGHT),
                    // Hung on the left, which is where the pill rests. The window only
                    // ever grows rightward — out towards the cutout, along the path
                    // the pill flew in on — so the page is never moved inside it.
                    Gravity.TOP or Gravity.START
                )
            )
        }

        params = WindowManager.LayoutParams(
            dp(height + (GRAB + BLEED) * 2), dp(height + grabTop + GRAB + BLEED),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            // The window never moves — only its right side goes in and out — but the
            // window manager would animate even that, on top of the motion the CSS
            // already owns. One motion, and it is the page's.
            setCanPlayMoveAnimation(false)
            x = dp(LEFT - GRAB - BLEED)
        }

        runCatching { windowManager.addView(stage, params) }
        pending += "window.setFlight(${flightDistance()})"
        pending += "window.setGrab($grabTop)"
        pending += "window.setPillSize($pillWidth,$height)"
        pending += "window.setPillBackground('$background')"
        apply()
    }

    fun detach() {
        runCatching { windowManager.removeView(stage) }
        runCatching { webView.destroy() }
    }

    /** The light, or null once it is out. Leaving is a flight, and it takes time. */
    fun deliver(state: JSONObject?) {
        torch = state
        if (state != null) gone = false
        push("window.onTorchUpdate(${state ?: "null"})")
        apply()
    }

    fun setMetrics(width: Int, height: Int, grabTop: Int) {
        pillHeight = height
        grab = grabTop
        push("window.setGrab($grabTop)")
        push("window.setPillSize($pillWidth,$height)")
        // The resting width moved, so the middle of the pill did, and the flight is
        // measured to that middle.
        push("window.setFlight(${flightDistance()})")
    }

    /**
     * Two windows of one type from one app stack in the order they were added and
     * there is no z to set on an accessibility overlay, so the only way up is to be
     * added again. The pill lives under the bubble — it is born inside it — but its
     * open panel is wider than the bar it stands on and would be drawn through the
     * bubble's face; the bubble takes the top back the same way when the panel shuts.
     */
    fun raise() {
        runCatching { windowManager.removeViewImmediate(stage) }
        runCatching { windowManager.addView(stage, params) }
    }

    fun setBackground(rgba: String) = push("window.setPillBackground('$rgba')")

    /** Hidden with the bubble: landscape, a fullscreen app, a dark screen. */
    fun setHidden(hidden: Boolean) {
        isHidden = hidden
        apply()
    }

    /**
     * A window with nothing in it must not eat touches. The status bar underneath is
     * exactly where the shade swipe starts, and an invisible chip swallowing it
     * would be the worst kind of bug to find.
     */
    private fun apply() {
        val dark = isHidden || gone
        // The blur is the compositor's, not the view's, so a transparent view goes
        // on blurring: it has to be taken off by hand or it hangs over the status
        // bar with nothing drawn on it.
        applyBlur()
        stage.alpha = if (dark) 0f else 1f
        params.flags =
            if (dark) FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else FLAGS
        runCatching { windowManager.updateViewLayout(stage, params) }
    }

    /**
     * How far the pill has to travel: from the true middle of the screen, which is
     * the middle of the punch hole, out to where it rests. The pill is born at the
     * hole like everything else on this bar and flies here, so this is the one
     * number the page cannot work out for itself.
     */
    private fun flightDistance(): Int {
        val density = context.resources.displayMetrics.density
        val screenCentre = context.resources.displayMetrics.widthPixels / 2f
        return ((screenCentre - dp(LEFT + pillWidth / 2)) / density).toInt()
    }

    /**
     * The blur cannot be animated by CSS — it is the window's, not the page's — so
     * the pill's own growth is replayed onto it. The same curve is close enough at
     * this size; what matters is that it is not a jump.
     */
    private fun animateBlur(
        toWidth: Int, toHeight: Int, corner: Float, offset: Int, milliseconds: Long
    ) {
        blurAnimator?.cancel()
        val bounds = blurCanvas.layoutParams as FrameLayout.LayoutParams
        val fromWidth = bounds.width
        val fromHeight = bounds.height
        val fromCorner = blurCorner
        val fromOffset = bounds.leftMargin - dp(GRAB + BLEED)
        if (milliseconds <= 0L) {
            placeBlur(dp(toWidth), dp(toHeight), corner, dp(offset))
            return
        }
        blurAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = milliseconds
            interpolator = android.view.animation.PathInterpolator(0.22f, 1.12f, 0.36f, 1f)
            addUpdateListener { step ->
                val travelled = step.animatedValue as Float
                placeBlur(
                    fromWidth + ((dp(toWidth) - fromWidth) * travelled).toInt(),
                    fromHeight + ((dp(toHeight) - fromHeight) * travelled).toInt(),
                    fromCorner + (corner - fromCorner) * travelled,
                    fromOffset + ((dp(offset) - fromOffset) * travelled).toInt()
                )
            }
            start()
        }
    }

    private fun placeBlur(width: Int, height: Int, corner: Float, offset: Int) {
        val bounds = blurCanvas.layoutParams as FrameLayout.LayoutParams
        bounds.width = width
        bounds.height = height
        bounds.topMargin = dp(grab)
        bounds.leftMargin = dp(GRAB + BLEED) + offset
        blurCanvas.layoutParams = bounds
        blurCorner = corner
        applyBlur()
    }

    private fun applyBlur() {
        if (isHidden || gone || blurRadius == 0) {
            SamsungBlur.clear(blurCanvas)
            return
        }
        SamsungBlur.apply(blurCanvas, dp(blurRadius), blurCorner)
    }

    fun setBlur(radius: Int) {
        blurRadius = radius
        applyBlur()
    }

    private fun push(script: String) {
        if (!ready) {
            pending += script
            return
        }
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    inner class Bridge {
        @JavascriptInterface
        fun ready() {
            webView.post {
                ready = true
                while (pending.isNotEmpty()) {
                    webView.evaluateJavascript(pending.removeFirst(), null)
                }
            }
        }

        /**
         * The page asks for the pill; the margins are the window's business. The pad
         * is the corridor the pill flies down, held open only while it is flying —
         * the window covers status bar the whole way, and every pixel it covers is a
         * pixel the shade swipe cannot start on, so it is given back on arrival.
         */
        @JavascriptInterface
        fun setWindowSize(widthDp: Int, heightDp: Int, padRightDp: Int) {
            webView.post {
                params.width = dp(widthDp + (GRAB + BLEED) * 2 + padRightDp)
                params.height = dp(heightDp + grab + GRAB + BLEED)
                runCatching { windowManager.updateViewLayout(stage, params) }
            }
        }

        /** Where the pill is going and how long it is taking, for the glass. */
        @JavascriptInterface
        fun setBlurBounds(
            widthDp: Int, heightDp: Int, cornerDp: Int, offsetDp: Int, milliseconds: Int
        ) {
            webView.post {
                animateBlur(
                    widthDp, heightDp, dp(cornerDp).toFloat(), offsetDp, milliseconds.toLong()
                )
            }
        }

        /**
         * The moment the flying pill clears the bubble at the cutout. The bubble pulls
         * its width in then — not when the light came on — so the two read as one body
         * of water: this drop leaves, that one closes behind it.
         */
        @JavascriptInterface
        fun setBubbleTax(out: Boolean) = BubbleService.torchChip(out)

        /** The open panel is wider than the bar it stands on, so it goes over the bubble rather than through it. */
        @JavascriptInterface
        fun setRaised(raised: Boolean) {
            webView.post { if (raised) raise() else BubbleService.raiseBubble() }
        }

        /** The pill has flown home and is off the screen: the window may go dark. */
        @JavascriptInterface
        fun setGone() {
            webView.post {
                gone = true
                apply()
            }
        }

        /** Step 0 puts the light out, 1 to 5 light it at that share of full. */
        @JavascriptInterface
        fun setTorch(step: Int) = TorchWatch.set(step)

        @JavascriptInterface
        fun triggerHaptic(type: String) = BubbleService.vibrateFor(type)
    }
}
