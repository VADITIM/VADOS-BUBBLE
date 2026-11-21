package com.v.island.bubble

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

enum class Region { Main, Clock, Status, Clear }

interface HostActions {
    fun openNotification(key: String)
    fun openApp(packageName: String)
    fun openClock()
    fun openControlPanel()
    fun openPowerMenu()
    fun openQuickSettings()
    fun readNotifications(): List<Notice>
    fun dismissNotification(key: String)
    fun mediaControl(action: String)
    fun mediaSeek(milliseconds: Long)
    fun mediaSpeed(rate: Float)
    fun timerAction(index: Int)
    fun tripAction(index: Int)
    fun recordingAction(index: Int)
    fun setTorch(step: Int)
}

object Island {
    private val main = Handler(Looper.getMainLooper())

    var appearance by mutableStateOf(Appearance())
    var screenWidth by mutableStateOf(360f)
    var screenHeight by mutableStateOf(780f)
    var density = 3f
    var isLandscape by mutableStateOf(false)
    var isStageHidden by mutableStateOf(false)
    var isLocked by mutableStateOf(false)

    var unread by mutableStateOf(0)
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

    lateinit var actions: HostActions
    var scope: CoroutineScope = MainScope()
        private set

    val bubble = MainBubble()
    val clock = ClockBubble()
    val status = StatusBubble()

    fun start(actions: HostActions) {
        this.actions = actions
        scope.cancel()
        scope = MainScope()
    }

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
    }
}
