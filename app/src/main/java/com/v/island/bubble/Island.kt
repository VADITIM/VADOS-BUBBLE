package com.v.island.bubble

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

enum class Region { Shield, Dashboard, Now, Main, Torch, TorchOff, Clock, Status, Clear, System, Alert }

@androidx.compose.runtime.Immutable
class DarkAlert(val appName: String, val title: String, val text: String, val accent: androidx.compose.ui.graphics.Color, val generation: Int, val isAppending: Boolean, val revealKey: Int)

interface HostActions {
    fun openNotification(key: String)
    fun openApp(packageName: String)
    fun openClock()
    fun openControlPanel()
    fun openPowerMenu()
    fun openConnectionSettings(kind: String)
    fun requestToggles()
    fun setToggle(name: String, isOn: Boolean)
    fun setLevel(name: String, percent: Int, isFinal: Boolean)
    fun requestVitals()
    fun requestWeather()
    fun readBatteryExact(): Double
    fun readDataToday(): String
    fun transferAction(index: Int)
    fun readNotifications(): List<Notice>
    fun dismissNotification(key: String)
    fun noticeAction(key: String, index: Int)
    fun mediaControl(action: String)
    fun mediaSeek(milliseconds: Long)
    fun mediaSpeed(rate: Float)
    fun timerAction(index: Int)
    fun tripAction(index: Int)
    fun recordingAction(index: Int)
    fun setTorch(step: Int)
    fun passTap(x: Float, y: Float)
    fun sleepScreen()
}

object Island {
    private val main = Handler(Looper.getMainLooper())

    var appearance by mutableStateOf(Appearance())

    val isPlayerOpen: Boolean get() = bubble.size == Size.Player
    // Only the player sent the Clock and Status away, so an Alert or the menu grew over them and their glyphs stood above its glass; any opened Main clears them.
    val isMainOpen: Boolean get() = !bubble.size.isClosed && !dashboard.isOpen
    var screenWidth by mutableStateOf(360f)
    var screenHeight by mutableStateOf(780f)
    var density = 3f
    var isLandscape by mutableStateOf(false)
    var isStageHidden by mutableStateOf(false)
    var isLocked by mutableStateOf(false)

    var unread by mutableStateOf(0)
    var isBarLight by mutableStateOf(false)
    var media by mutableStateOf<Media?>(null)
    var timer by mutableStateOf<Timer?>(null)
    var call by mutableStateOf<Call?>(null)
    var trip by mutableStateOf<Trip?>(null)
    var torch by mutableStateOf<Torch?>(null)
    var recording by mutableStateOf<Recording?>(null)
    var transfer by mutableStateOf<Transfer?>(null)
    var connectivity by mutableStateOf<Connectivity?>(null)
    var charge by mutableStateOf<Charge?>(null)
    var conversations by mutableStateOf<List<Conversation>>(emptyList())

    var isTouchDown = false
    var wasCancelled = false

    val regions = mutableStateMapOf<Region, Rect>()
    val liquid = Liquid()
    var onBlobsDrawn: () -> Unit = {}
    var onDashboardLeft: () -> Unit = {}

    lateinit var actions: HostActions
    var scope: CoroutineScope = uiScope()
        private set

    val bubble = MainBubble()
    val clock = ClockBubble()
    val status = StatusBubble()
    val dashboard = Dashboard()
    val now = NowBubble()
    var frost = 0f

    /* Mirrors setScreenDark and paintDarkAlert in js/alert.js: an arrival on a dark screen is read as always-on text on black at a low brightness, not as the Alert the lit screen gets. */
    var isScreenDark by mutableStateOf(false)
        private set
    var isDarkSimulated = false
        private set
    var darkAlert by mutableStateOf<DarkAlert?>(null)
        private set
    var darkAlertBox: Rect? = null
    private var darkGeneration = 0
    private var sleepJob: kotlinx.coroutines.Job? = null

    fun setScreenDark(isOn: Boolean, isSimulated: Boolean = false) {
        isScreenDark = isOn
        isDarkSimulated = isOn && isSimulated
        sleepJob?.cancel()
        if (!isOn) darkAlert = null
    }

    fun showDarkAlert(appName: String, title: String, text: String, accent: androidx.compose.ui.graphics.Color, isAppending: Boolean) {
        darkGeneration += 1
        val previous = darkAlert
        val isAdded = isAppending && previous != null
        darkAlert = DarkAlert(appName, title, text, accent, darkGeneration, isAdded, if (isAdded) previous!!.revealKey else darkGeneration)
    }

    fun liftScreenDark() {
        if (!isScreenDark) return
        setScreenDark(false)
    }

    fun sleepDark() {
        if (!isScreenDark) return
        darkAlert = null
        sleepJob?.cancel()
        sleepJob = scope.launch {
            kotlinx.coroutines.delay(DARK_SLEEP)
            if (isDarkSimulated) liftScreenDark() else actions.sleepScreen()
        }
    }

    /* Mirrors HOME_TRAVEL in js/alert.js: the text fades before the screen is put back to sleep. */
    private const val DARK_SLEEP = 460L

    fun start(actions: HostActions) {
        this.actions = actions
        scope.cancel()
        scope = uiScope()
    }

    // A bare MainScope has no MonotonicFrameClock, so any animate() launched from it outside a composition threw on the first frame and took the service down mid-transition — StatusBubble.goHome on a dashboard pull was the one that surfaced.
    private fun uiScope() = CoroutineScope(kotlinx.coroutines.SupervisorJob() + androidx.compose.ui.platform.AndroidUiDispatcher.Main)

    fun stop() {
        scope.cancel()
        regions.clear()
    }

    fun post(work: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) work() else main.post(work)
    }

    fun onTouch(isDown: Boolean, isCancel: Boolean) {
        if (isDown) {
            isTouchDown = true
            wasCancelled = false
            bubble.onPressStarted()
        } else {
            isTouchDown = false
            wasCancelled = isCancel
        }
    }

    fun onOutsideTap(x: Float, y: Float) {
        val isOurs = regions.values.any { it.contains(androidx.compose.ui.geometry.Offset(x, y)) }
        if (isOurs) return
        bubble.onOutsideTap()
        clock.onOutsideTap()
        dashboard.close()
    }
}
