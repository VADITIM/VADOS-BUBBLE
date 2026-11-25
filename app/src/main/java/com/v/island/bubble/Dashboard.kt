package com.v.island.bubble

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

class DashboardControl(val bounds: () -> Rect, val onTap: () -> Unit)

@Stable
class Dashboard {
    var isOpen by mutableStateOf(false)
        private set
    var isLeaving by mutableStateOf(false)
        private set
    var generation by mutableIntStateOf(0)
        private set
    var areReadingsHeld by mutableStateOf(false)
        private set
    var toggles by mutableStateOf(Toggles.NONE)
        private set
    var vitals by mutableStateOf<Vitals?>(null)
        private set
    var weather by mutableStateOf<Weather?>(null)
        private set
    var dataToday by mutableStateOf("")
        private set
    var exactCharge by mutableDoubleStateOf(-1.0)
        private set
    var soloLevel by mutableStateOf<String?>(null)
        private set
    var isSoloOut by mutableStateOf(false)
        private set
    var soloGeneration by mutableIntStateOf(0)
        private set
    var nowBottom by mutableFloatStateOf(0f)
    var knobShift by mutableFloatStateOf(0f)
        private set
    var pairedTurn by mutableIntStateOf(0)

    val draggedLevels = mutableStateMapOf<String, Float>()
    val turns = mutableStateMapOf<String, Int>()
    val controls = HashMap<String, DashboardControl>()

    var isControlTouch = false
    var isLevelTouch = false
    var isKnobSwiped = false

    private var heldToggles: Toggles? = null
    private var enterJob: Job? = null
    private var leaveJob: Job? = null
    private var followJob: Job? = null
    private var glideJob: Job? = null
    private var soloJob: Job? = null

    private val isMoving: Boolean get() = enterJob?.isActive == true || isLeaving

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        if (isOpen || Island.isStageHidden) return
        enterJob?.cancel()
        leaveJob?.cancel()
        isLeaving = false
        Island.status.retireAnnouncement()
        Island.bubble.toClosedIfExtended()
        Island.clock.closePanel()
        isOpen = true
        Island.bubble.onModsChanged()
        Island.now.enterPanel()
        generation += 1
        areReadingsHeld = true
        Island.actions.requestVitals()
        Island.actions.requestWeather()
        Island.actions.requestToggles()
        followJob?.cancel()
        followJob = Island.scope.launch {
            while (isActive) {
                val exact = Island.actions.readBatteryExact()
                if (exact >= 0) exactCharge = exact
                delay(BATTERY_FOLLOW)
            }
        }
        enterJob = Island.scope.launch {
            delay(ENTER_SPAN.toLong())
            dataToday = Island.actions.readDataToday() + " today"
            areReadingsHeld = false
            releaseToggles()
        }
    }

    fun close(thrown: androidx.compose.ui.geometry.Offset? = null) {
        if (!isOpen) return
        endSolo()
        isOpen = false
        Island.bubble.onModsChanged()
        Island.now.leavePanel(thrown)
        isLeaving = true
        generation += 1
        enterJob?.cancel()
        followJob?.cancel()
        glideJob?.cancel()
        areReadingsHeld = false
        draggedLevels.clear()
        leaveJob = Island.scope.launch {
            delay(maxOf(Durations.GROW, DROPPED).toLong() + 40)
            isLeaving = false
            releaseToggles()
            Island.onDashboardLeft()
        }
    }

    fun onToggles(state: Toggles) {
        if (isMoving) {
            heldToggles = state
            return
        }
        heldToggles = null
        toggles = state
    }

    private fun releaseToggles() {
        val state = heldToggles ?: return
        if (isMoving) return
        heldToggles = null
        toggles = state
    }

    fun onVitals(reading: Vitals) {
        vitals = reading
    }

    fun onWeather(reading: Weather?) {
        weather = reading
    }

    fun isOn(name: String): Boolean = when (name) {
        "hotspot" -> Island.connectivity?.isHotspot == true
        "recording" -> Island.recording != null
        else -> toggles.switches[name] == true
    }

    fun isAvailable(name: String): Boolean = name in ALWAYS_AVAILABLE || toggles.has(name)

    fun press(name: String) {
        if (!isAvailable(name)) return
        val next = !isOn(name)
        toggles = toggles.copy(switches = toggles.switches + (name to next))
        turns[name] = (turns[name] ?: 0) + 1
        Haptics.play(Haptic.Toggle)
        Island.actions.setToggle(name, next)
        if (name == "recording") close()
    }

    fun holdMode(name: String) {
        Haptics.play(Haptic.Expand)
        val app = HOLD_OPENS[name]
        if (app != null) Island.actions.openApp(app) else Island.actions.openConnectionSettings(name)
        close()
    }

    fun openApp(packageName: String) {
        Haptics.play(Haptic.Tap)
        Island.actions.openApp(packageName)
        close()
    }

    fun openSettings(kind: String) {
        Haptics.play(Haptic.Expand)
        Island.actions.openConnectionSettings(kind)
        close()
    }

    fun levelShare(name: String): Float {
        draggedLevels[name]?.let { return it }
        val value = toggles.levels[name] ?: return 0f
        return positionOf(name, value.toFloat())
    }

    fun dragLevel(name: String, position: Float) {
        draggedLevels[name] = position.coerceIn(0f, 100f)
    }

    fun settleLevel(name: String) {
        val position = draggedLevels[name] ?: return
        val value = valueAt(name, position).roundToInt()
        toggles = toggles.copy(levels = toggles.levels + (name to value))
        draggedLevels.remove(name)
        Island.actions.setLevel(name, value, true)
    }

    fun enterSolo(name: String) {
        if (soloLevel != null) return
        soloJob?.cancel()
        soloLevel = name
        soloGeneration += 1
        if (name == "brightness") isSoloOut = true
        Haptics.play(Haptic.Expand)
    }

    fun leaveSolo() {
        if (soloLevel == null) return
        soloLevel = null
        isSoloOut = false
    }

    private fun endSolo() {
        soloJob?.cancel()
        soloLevel = null
        isSoloOut = false
    }

    fun shiftKnobs(value: Float) {
        var shift = value % KNOB_CYCLE
        if (shift > 0f) shift -= KNOB_CYCLE
        knobShift = shift
    }

    fun stopKnobGlide() {
        glideJob?.cancel()
    }

    fun glideKnobs(speed: Float) {
        var velocity = speed.coerceIn(-KNOB_FLING_CAP, KNOB_FLING_CAP)
        if (abs(velocity) < KNOB_STILL) return
        glideJob?.cancel()
        glideJob = Island.scope.launch(AndroidUiDispatcher.Main) {
            var last = withFrameMillis { it }
            while (isActive && abs(velocity) >= KNOB_STILL) {
                val now = withFrameMillis { it }
                val step = minOf(32L, now - last).toFloat()
                last = now
                shiftKnobs(knobShift + velocity * step)
                velocity *= KNOB_FRICTION.pow(step / 16f)
            }
        }
    }

    fun tapGround(at: Offset) {
        var nearest: DashboardControl? = null
        var closest = QUICK_GRACE
        controls.values.forEach { control ->
            val box = control.bounds()
            if (box.width <= 0f) return@forEach
            val across = maxOf(box.left - at.x, 0f, at.x - box.right)
            val down = maxOf(box.top - at.y, 0f, at.y - box.bottom)
            val gap = hypot(across, down)
            if (gap >= closest) return@forEach
            closest = gap
            nearest = control
        }
        val control = nearest
        if (control != null) control.onTap() else close()
    }

    fun toggleMods() {
        Haptics.play(Haptic.Tap)
        val bubble = Island.bubble
        if (bubble.clearedMods.isNotEmpty()) bubble.restoreMods() else bubble.clearMods()
    }

    companion object {
        /* Mirrors --corner-inset, --corner-tall and --corner-radius in the retired pill.css: the grown Clock and Status bubbles stand in the screen's two top corners while the Dashboard is open. */
        const val CORNER_INSET = 10f
        const val CORNER_TALL = 40f
        const val CORNER_RADIUS = 18f
        const val CORNER_WIDE_FLOOR = StatusBubble.CORNER_WIDE
        const val QUICK_CLEARANCE = 22f
        const val MARGIN = 10f
        const val GAP = 10f
        const val RANK_STEPS = 6
        const val POP = 360
        const val STAGGER = 42
        const val PULL = 240f
        const val PULL_FLOOR = 56f
        const val JITTER = 50
        const val DROP_STAGGER = 20
        const val FAST_SPREAD = 300
        const val ENTER_SPAN = RANK_STEPS * STAGGER + JITTER + POP
        const val DROPPED = RANK_STEPS * DROP_STAGGER + 180
        const val COLLAPSE = 95
        const val SOLO_HOLD = 420L
        const val SOLO_TRAVEL = 420
        const val SOLO_FROST = 260
        const val SOLO_RANKS = 5
        const val KNOB_TURN = 460
        const val KNOB_SIZE = 56f
        const val KNOB_ROW_GAP = 12f
        const val KNOB_COPIES = 3
        val KNOBS = listOf("augenkomfort", "modus", "dim", "recording", "rotate", "mic", "saver", "comdirect")
        val MODES = listOf("hotspot", "plane", "mobile", "gps", "wifi", "bluetooth")
        val KNOB_CYCLE = KNOBS.size * (KNOB_SIZE + KNOB_ROW_GAP)
        const val KNOB_DRAG_SLOP = 8f
        const val TAP_SLOP = 12f
        const val KNOB_FLING_CAP = 4f
        const val KNOB_FRICTION = 0.94f
        const val KNOB_STILL = 0.02f
        const val QUICK_GRACE = 12f
        const val PUSH_DISMISS = 24f
        const val PUSH_STRAY = 40f
        const val PAIRED_SWAP = 5000L
        const val BATTERY_FOLLOW = 1000L
        const val COMDIRECT = "de.comdirect.app"
        private val ALWAYS_AVAILABLE = setOf("usb", "hotspot", "recording", "comdirect")
        private val HOLD_OPENS = mapOf("gps" to "com.life360.android.safetymapd")
        /* Half smoothstep and half straight: the ends of a bar are where a person fine-tunes, so they get more travel per percent than the middle. */
        private const val LEVEL_EASE = 0.6f

        /* The volume bar shared the eased ends, so quiet listening sat in the bar's bottom fifth and a step was a few pixels; its knee stands above 60% of the bar, everything under it fine-tuning the quiet range and the rest of the room only for loud. */
        private const val VOLUME_KNEE_POSITION = 62f
        private const val VOLUME_KNEE_VALUE = 30f

        fun valueAt(name: String, position: Float): Float {
            if (name == "volume") {
                return if (position <= VOLUME_KNEE_POSITION) position / VOLUME_KNEE_POSITION * VOLUME_KNEE_VALUE
                else VOLUME_KNEE_VALUE + (position - VOLUME_KNEE_POSITION) / (100f - VOLUME_KNEE_POSITION) * (100f - VOLUME_KNEE_VALUE)
            }
            val unit = position / 100f
            val smooth = unit * unit * (3f - 2f * unit)
            return 100f * (unit + LEVEL_EASE * (smooth - unit))
        }

        fun positionOf(name: String, value: Float): Float {
            if (value <= 0f) return 0f
            if (value >= 100f) return 100f
            var low = 0f
            var high = 100f
            repeat(20) {
                val middle = (low + high) / 2f
                if (valueAt(name, middle) < value) low = middle else high = middle
            }
            return (low + high) / 2f
        }
    }
}
