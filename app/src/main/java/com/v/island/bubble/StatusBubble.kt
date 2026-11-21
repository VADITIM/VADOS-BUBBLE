package com.v.island.bubble

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private var holdJob: Job? = null
    private var announceJob: Job? = null

    fun closePanel() = Unit

    fun modus(): String? {
        val attached = Island.connectivity ?: return null
        return when {
            attached.isHotspot -> "hotspot"
            attached.paired != null -> "bluetooth"
            attached.isZen -> "zen"
            else -> null
        }
    }

    fun modusColour(): Color? = when (modus()) {
        "bluetooth" -> Color(0xFF6FB0FF)
        "hotspot" -> Color(0xFFFF9B3D)
        "zen" -> Color(0xFFA07DFF)
        else -> null
    }

    private fun wifiGlyph(attached: Connectivity): Glyph = when {
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
        Island.transfer?.let { shown += StatusSlot.Mark("transfer", if (it.isUpload) Glyphs.upload else Glyphs.download, if (it.isUpload) Color(0xFF3A8CFF) else Color(0xFFF09B3A)) }
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

    fun announce(event: BatteryEvent) {
        announceJob?.cancel()
        announcing = event
        Haptics.play(Haptic.Release)
        announceJob = Island.scope.launch {
            delay(CHARGE_DWELL)
            announcing = null
        }
    }

    fun onDown() {
        isPressing = true
        holdJob?.cancel()
        holdJob = Island.scope.launch {
            delay(Durations.HOLD.toLong())
            isPressing = false
            Haptics.play(Haptic.Expand)
            Island.actions.openControlPanel()
        }
    }

    fun onUp(isTap: Boolean) {
        val wasHeld = holdJob?.isCompleted == true
        holdJob?.cancel()
        isPressing = false
        if (!isTap || wasHeld) return
        Haptics.play(Haptic.Expand)
        Island.bubble.toClosedIfExtended()
        Island.actions.openQuickSettings()
    }

    companion object {
        const val STATUS_RIGHT = 14f
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

        fun chargeColour(charge: Charge): Color =
            if (charge.isPlugged) CHARGING_COLOUR
            else BATTERY_GRADIENT.firstOrNull { charge.percent >= it.first }?.second ?: BATTERY_GRADIENT.last().second
    }
}
