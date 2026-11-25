package com.v.island.bubble

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class NowMod { Torch, Recording }

@Stable
class ClockBubble {
    var isPressing by mutableStateOf(false)
        private set
    var drag by mutableStateOf(Offset.Zero)
        private set
    var timeWidth by mutableFloatStateOf(52f)
    var isRecordingOpen by mutableStateOf(false)
        private set

    private var holdJob: Job? = null
    private var homeJob: Job? = null
    private var downX = 0f
    private var downY = 0f

    val owner: NowMod?
        get() = when {
            (Island.torch?.step ?: 0) > 0 -> NowMod.Torch
            Island.recording != null -> NowMod.Recording
            else -> null
        }

    val isNowStanding: Boolean get() = owner != null

    var cornerWidth by mutableFloatStateOf(Dashboard.CORNER_WIDE_FLOOR)

    val isCornered: Boolean get() = Island.dashboard.isOpen

    val isOpen: Boolean get() = !isCornered && (owner == NowMod.Torch || (owner == NowMod.Recording && isRecordingOpen))

    fun target(): Frame {
        val appearance = Island.appearance
        if (Island.isPlayerOpen) return Frame(timeWidth, appearance.compactHeight, appearance.compactHeight / 2f, appearance.grab + EATEN_DROP, CLOCK_LEFT + EATEN_PULL)
        if (isCornered) {
            return Frame(cornerWidth, Dashboard.CORNER_TALL, Dashboard.CORNER_RADIUS, Dashboard.CORNER_INSET, Dashboard.CORNER_INSET)
        }
        if (isOpen) {
            val left = Island.screenWidth / 2f - NOW_PANEL_WIDTH / 2f
            return Frame(NOW_PANEL_WIDTH, NOW_PANEL_HEIGHT, 26f, appearance.grab, left)
        }
        val width = when (owner) {
            NowMod.Recording -> RECORDING_WIDTH
            NowMod.Torch -> TORCH_WIDTH
            null -> timeWidth
        }
        return Frame(width, appearance.compactHeight, appearance.compactHeight / 2f, appearance.grab, CLOCK_LEFT)
    }

    fun closePanel() {
        isRecordingOpen = false
    }

    fun onOutsideTap() {
        isRecordingOpen = false
    }

    fun onDown(x: Float, y: Float) {
        downX = x
        downY = y
        isPressing = true
        holdJob?.cancel()
        if (isOpen) return
        holdJob = Island.scope.launch {
            delay(Durations.HOLD.toLong())
            isPressing = false
            Haptics.play(Haptic.Expand)
            Island.actions.openClock()
        }
    }

    fun onMove(x: Float, y: Float) {
        if (isOpen) return
        val across = x - downX
        val down = y - downY
        val travel = hypot(across, down)
        val past = max(0f, travel - HOLD_BLOCK)
        val reach = DRAG_RADIUS * past / (past + DRAG_RADIUS)
        val scale = if (travel > 0f) reach / travel else 0f
        homeJob?.cancel()
        drag = Offset(across * scale, down * scale)
        if (travel > HOLD_BLOCK || down > CLOCK_PULL) {
            holdJob?.cancel()
            isPressing = false
        }
    }

    fun onUp(isTap: Boolean) {
        holdJob?.cancel()
        isPressing = false
        val from = drag
        if (from != Offset.Zero) {
            homeJob = Island.scope.launch {
                animate(0f, 1f, animationSpec = tween(Durations.HOME, easing = Curves.bubble)) { progress, _ -> drag = from * (1f - progress) }
            }
        }
        if (!isTap || abs(from.x) + abs(from.y) > 2f || isCornered) return
        if (owner == NowMod.Recording && !isRecordingOpen) {
            Haptics.play(Haptic.Expand)
            Island.bubble.toClosedIfExtended()
            isRecordingOpen = true
        }
    }

    fun setTorchStep(step: Int) {
        Haptics.play(Haptic.Tap)
        Island.actions.setTorch(step)
    }

    fun stopRecording() {
        val action = Island.recording?.actions?.lastOrNull() ?: return
        Haptics.play(Haptic.Tap)
        Island.actions.recordingAction(action.index)
        isRecordingOpen = false
    }

    companion object {
        const val CLOCK_LEFT = 14f
        const val EATEN_PULL = 46f
        const val EATEN_DROP = 24f
        const val CLOCK_PULL = 34f
        const val NOW_PANEL_WIDTH = 300f
        const val NOW_PANEL_HEIGHT = 190f
        const val RECORDING_WIDTH = 112f
        const val TORCH_WIDTH = 116f
        const val NOW_SLOT = 40f
        const val NOW_SLOT_GAP = 14f
        private const val HOLD_BLOCK = 18f
        private const val DRAG_RADIUS = 40f
    }
}
