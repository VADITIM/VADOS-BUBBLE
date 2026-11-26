package com.v.island.bubble

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

sealed interface StatusSlot {
    val name: String

    data class Mark(override val name: String, val glyph: Glyph, val tint: Color = Color.White) : StatusSlot
    data class Reading(override val name: String, val text: String) : StatusSlot
    data class Paired(override val name: String, val charge: Int) : StatusSlot
    data class Battery(override val name: String, val charge: Charge) : StatusSlot
    data class Announce(override val name: String, val event: BatteryEvent) : StatusSlot
}

@Stable
class StatusBubble {
    var isPressing by mutableStateOf(false)
        private set
    var contentWidth by mutableFloatStateOf(40f)
    var announcing by mutableStateOf<BatteryEvent?>(null)
        private set

    var drag by mutableStateOf(Offset.Zero)
        private set

    private var holdJob: Job? = null
    private var homeJob: Job? = null
    private var announceJob: Job? = null
    private var downX = 0f
    private var downY = 0f
    private var deepest = 0f
    private var isPulled = false

    fun closePanel() = Island.dashboard.close()

    fun modus(): String? {
        val attached = Island.connectivity ?: return null
        return when {
            attached.isHotspot -> "hotspot"
            attached.paired != null -> "bluetooth"
            attached.isZen -> "zen"
            else -> null
        }
    }

    fun wifiGlyph(attached: Connectivity?): Glyph = when {
        attached == null || attached.link != "wifi" -> Glyphs.wifi
        !attached.isOnline -> Glyphs.wifiAlert
        attached.level < 0 || attached.level >= 3 -> Glyphs.wifi
        attached.level == 2 -> Glyphs.wifiGood
        attached.level == 1 -> Glyphs.wifiFair
        else -> Glyphs.wifiWeak
    }

    private fun zenGlyph(attached: Connectivity): Glyph {
        val name = attached.zenName.lowercase()
        return ZEN_MODES.firstOrNull { (_, words) -> words.any { name.contains(it) } }?.first ?: Glyphs.zen
    }

    fun slots(): List<StatusSlot> {
        val event = announcing
        if (event != null) return listOf(StatusSlot.Announce("announce", event))
        val attached = Island.connectivity
        val shown = mutableListOf<StatusSlot>()
        if (attached?.isUsb == true) shown += StatusSlot.Mark("usb", Glyphs.usb)
        when (modus()) {
            "hotspot" -> shown += StatusSlot.Mark("modus", Glyphs.hotspot)
            "bluetooth" -> {
                val charge = attached?.paired?.charge ?: -1
                shown += if (charge >= 0) StatusSlot.Paired("modus", charge) else StatusSlot.Mark("modus", Glyphs.bluetooth)
            }
            "zen" -> shown += StatusSlot.Mark("modus", zenGlyph(attached!!))
        }
        when (attached?.link) {
            "wifi" -> shown += StatusSlot.Mark("link", wifiGlyph(attached))
            "ethernet" -> shown += StatusSlot.Mark("link", Glyphs.ethernet)
            "mobile" -> shown += if (attached.generation.isNotEmpty()) StatusSlot.Reading("link", attached.generation) else StatusSlot.Mark("link", Glyphs.mobile)
        }
        Island.charge?.let { shown += StatusSlot.Battery("battery", it) }
        return shown
    }

    fun retireAnnouncement() {
        announceJob?.cancel()
        announcing = null
    }

    fun announce(event: BatteryEvent) {
        if (Island.dashboard.isOpen) return
        announceJob?.cancel()
        announcing = event
        Haptics.play(Haptic.Release)
        announceJob = Island.scope.launch {
            delay(CHARGE_DWELL)
            announcing = null
        }
    }

    fun subject(): String = modus() ?: Island.connectivity?.link ?: "none"

    fun target(): Frame {
        val appearance = Island.appearance
        if (Island.dashboard.isOpen) {
            return Frame(CORNER_WIDE, Dashboard.CORNER_TALL, Dashboard.CORNER_RADIUS, Dashboard.CORNER_INSET, Island.screenWidth - Dashboard.CORNER_INSET - CORNER_WIDE)
        }
        val width = contentWidth + 9.6f
        return Frame(width, appearance.compactHeight, appearance.compactHeight / 2f, appearance.grab, Island.screenWidth - STATUS_RIGHT - width)
    }

    fun onDown(x: Float, y: Float) {
        downX = x
        downY = y
        deepest = 0f
        isPulled = false
        isPressing = !Island.dashboard.isOpen
        homeJob?.cancel()
        holdJob?.cancel()
        if (Island.dashboard.isOpen) return
        holdJob = Island.scope.launch {
            delay(Durations.HOLD.toLong())
            isPressing = false
            Haptics.play(Haptic.Expand)
            Island.actions.openConnectionSettings(subject())
        }
    }

    fun onMove(x: Float, y: Float) {
        if (isPulled || Island.dashboard.isOpen) return
        val across = x - downX
        val down = y - downY
        deepest = max(deepest, down)
        val travel = hypot(across, down)
        val past = max(0f, travel - HOLD_BLOCK)
        val reach = DRAG_RADIUS * past / (past + DRAG_RADIUS)
        val scale = if (travel > 0f) reach / travel else 0f
        drag = Offset(across * scale, down * scale)
        if (travel > HOLD_BLOCK) {
            holdJob?.cancel()
            isPressing = false
        }
        if (down > STATUS_PULL && down > abs(across)) {
            isPulled = true
            holdJob?.cancel()
            isPressing = false
            goHome()
            Haptics.play(Haptic.Expand)
            Island.dashboard.open()
        }
    }

    fun onUp(isTap: Boolean) {
        val wasHeld = holdJob?.isCompleted == true
        holdJob?.cancel()
        isPressing = false
        goHome()
        if (Island.wasCancelled) {
            if (!wasHeld && !isPulled && !Island.dashboard.isOpen && deepest > STATUS_PULL_STOLEN) {
                Haptics.play(Haptic.Expand)
                Island.dashboard.open()
            }
            return
        }
        if (!isTap || wasHeld || isPulled) return
        Haptics.play(Haptic.Tap)
        Island.dashboard.toggle()
    }

    private fun goHome() {
        val from = drag
        if (from == Offset.Zero) return
        homeJob = Island.scope.launch {
            animate(0f, 1f, animationSpec = tween(Durations.HOME, easing = Curves.bubble)) { progress, _ -> drag = from * (1f - progress) }
        }
    }

    companion object {
        const val STATUS_RIGHT = 14f
        /* Mirrors STATUS_CORNER_WIDE in the retired js/status.js: the actions face has no width of its own to be measured. */
        const val CORNER_WIDE = 82f
        const val STATUS_PULL = 34f
        const val STATUS_PULL_STOLEN = 12f
        private const val HOLD_BLOCK = 18f
        private const val DRAG_RADIUS = 40f
        const val CHARGE_DWELL = 2600L
        private val ZEN_MODES = listOf(
            Glyphs.zenSleep to listOf("schlaf", "nacht", "sleep", "night", "bett", "bed"),
            Glyphs.zenHeart to listOf("<3", "❤", "♥", "herz", "liebe", "love", "heart"),
            Glyphs.zenWork to listOf("arbeit", "work", "büro", "buro", "office", "fokus", "focus", "lern", "study"),
            Glyphs.zenDrive to listOf("fahr", "auto", "driv", "car", "pendel", "commut"),
            Glyphs.zenExercise to listOf("sport", "training", "workout", "fitness", "lauf", "run", "gym"),
            Glyphs.zenGame to listOf("spiel", "gam", "zock"),
        )
        private val BATTERY_GRADIENT = listOf(
            90 to Color(0xFF00E676), 80 to Color(0xFF10E000), 70 to Color(0xFF76FF03), 60 to Color(0xFFB2FF59),
            50 to Color(0xFFCCFF00), 40 to Color(0xFFFFEE00), 30 to Color(0xFFFFC400), 20 to Color(0xFFFF9100),
            10 to Color(0xFFFF3D00), 0 to Color(0xFFFF1744),
        )
        val CHARGING_COLOUR = Color(0xFF3BFA1E)
        val ANNOUNCE_COLOURS = mapOf(
            BatteryEvent.Plugged to Color(0xFF5BFD5B),
            BatteryEvent.Low to Color(0xFFFFB300),
            BatteryEvent.Critical to Color(0xFFFF3B30),
        )

        fun chargeColour(charge: Charge): Color = if (charge.isPlugged) CHARGING_COLOUR else levelColour(charge.percent)

        fun levelColour(percent: Int): Color = BATTERY_GRADIENT.firstOrNull { percent >= it.first }?.second ?: BATTERY_GRADIENT.last().second
    }
}
