package com.v.island.bubble

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Faces
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import com.v.island.vas.LocalAccent
import com.v.island.vas.Palette
import com.v.island.vas.SquircleShape
import com.v.island.vas.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private class PanelGeometry(screenWidth: Float, screenHeight: Float, nowTall: Float) {
    val left = Dashboard.MARGIN
    val top = Dashboard.CORNER_INSET + Dashboard.CORNER_TALL + Dashboard.QUICK_CLEARANCE
    val width = screenWidth - Dashboard.MARGIN * 2f
    val height = screenHeight - top - Dashboard.MARGIN
    val statsTall = height * STATS_SHARE
    val band = height * TOGGLE_SHARE
    val lift = max(0f, (band - Dashboard.KNOB_SIZE) / 2f)
    val firstDivider = statsTall + Dashboard.GAP + DIVIDER_SHIFT
    val systemTop = firstDivider + 1f + DIVIDER_ROOM + Dashboard.GAP
    val knobTop = height - lift - Dashboard.KNOB_SIZE
    /* Mirrors html.lock-live in pill.css: with the Now bubble standing over the toggles the lower divider sits a gap above it, so the room over the bubble and under it are one measurement. */
    val secondDivider = if (nowTall > 0f) min(height - band - Dashboard.GAP - DIVIDER_SHIFT, knobTop - NowBubble.PANEL_GAP * 2f - nowTall) - 1f
    else height - band - Dashboard.GAP - DIVIDER_SHIFT - 1f
    val systemBottom = secondDivider - DIVIDER_ROOM - Dashboard.GAP
    val systemTall = systemBottom - systemTop
    val gridTop = systemTop + (systemTall - GRID_TALL) / 2f
    val gridRow = (GRID_TALL - Dashboard.GAP * 2f) / 3f
    val vitalsCentre = STATS_DROP + WEATHER_TALL + Dashboard.GAP + VITALS_TALL / 2f
    val transferCentre = STATS_DROP + WEATHER_TALL + Dashboard.GAP * 2f + VITALS_TALL + TRANSFER_TALL / 2f
    val levelsCentre = systemTop + systemTall / 2f

    fun modeCentre(row: Int) = gridTop + row * (gridRow + Dashboard.GAP) + gridRow / 2f
}

private enum class PopKind { Plain, Turning, Knob, Level }

private class PopRoll {
    val strength = 0.6f + Random.nextFloat() * 0.4f
    val throwEase: Easing = CubicBezierEasing(0.3f, 1.1f + Random.nextFloat() * 0.35f, 0.6f, 1f)
    val throwMilliseconds = (Dashboard.POP * (0.8f + Random.nextFloat() * 0.2f)).roundToInt()
    val jitter = Random.nextInt(Dashboard.JITTER + 1)
    val fastAt = Random.nextInt(Dashboard.FAST_SPREAD + 1)
}

@Composable
private fun Modifier.pop(dashboard: Dashboard, centre: Float, panelTall: Float, kind: PopKind): Modifier {
    val generation = dashboard.generation
    val reach = (panelTall / 2f).coerceAtLeast(1f)
    val fromCentre = ((centre - panelTall / 2f) / reach).coerceIn(-1f, 1f)
    val rank = ((1f - abs(fromCentre)) * Dashboard.RANK_STEPS).roundToInt()
    val side = if (fromCentre < 0f) -1f else 1f
    val roll = remember(generation) { PopRoll() }
    val pull = (fromCentre * Dashboard.PULL + side * Dashboard.PULL_FLOOR) * if (kind == PopKind.Knob) roll.strength else 1f
    val progress = remember { Animatable(0f) }
    var isLeaving by remember { mutableStateOf(false) }
    val isFast = Island.appearance.isFastDashboard
    LaunchedEffect(generation) {
        if (dashboard.isOpen) {
            isLeaving = false
            progress.snapTo(0f)
            if (isFast) {
                delay(roll.fastAt.toLong())
                progress.animateTo(1f, tween(FAST_DROP, easing = LinearOutSlowInEasing))
                return@LaunchedEffect
            }
            val jitter = if (kind == PopKind.Turning) roll.jitter else 0
            delay((rank * Dashboard.STAGGER + jitter).toLong())
            progress.animateTo(1f, if (kind == PopKind.Knob) tween(roll.throwMilliseconds, easing = roll.throwEase) else tween(Dashboard.POP, easing = Curves.sling))
        } else {
            isLeaving = true
            delay(((Dashboard.RANK_STEPS - rank) * Dashboard.DROP_STAGGER).toLong())
            progress.animateTo(0f, tween(Dashboard.COLLAPSE, easing = Curves.leave))
        }
    }
    val solo = remember { Animatable(1f) }
    val soloRank = remember(dashboard.soloGeneration) { Random.nextInt(Dashboard.SOLO_RANKS + 1) }
    LaunchedEffect(dashboard.isSoloOut) {
        if (kind == PopKind.Level) return@LaunchedEffect
        if (dashboard.isSoloOut) {
            delay(soloRank * SOLO_SHRINK_STEP)
            solo.animateTo(0f, tween(SOLO_SHRINK, easing = Curves.leave))
        } else if (solo.value < 1f) {
            delay(soloRank * SOLO_SWELL_STEP)
            solo.animateTo(1f, tween(SOLO_SWELL, easing = Curves.sling))
        }
    }
    val turnsIn = kind == PopKind.Turning || kind == PopKind.Knob
    return graphicsLayer {
        val value = progress.value
        val soloValue = solo.value
        if (isFast && !isLeaving) {
            translationY = -FAST_LIFT * (1f - value) * density
            alpha = if (value > 0f) soloValue else 0f
            scaleX = soloValue
            scaleY = soloValue
            return@graphicsLayer
        }
        val rest = 1f - value
        val scale = value * soloValue
        scaleX = scale
        scaleY = scale
        if (isLeaving) {
            alpha = value.coerceIn(0f, 1f)
            rotationZ = if (turnsIn) rest * 180f else 0f
            translationY = rest * pull * 0.6f * density
        } else {
            alpha = soloValue
            rotationZ = if (turnsIn) rest * -360f else 0f
            translationY = rest * (if (kind == PopKind.Level) -pull else pull) * density
        }
    }
}

@Composable
private fun Modifier.control(
    dashboard: Dashboard,
    id: String,
    pressedScale: Float,
    isEnabled: Boolean = true,
    onHold: (() -> Unit)? = null,
    onTap: () -> Unit,
): Modifier {
    var isPressing by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (isPressing) pressedScale else 1f, tween(200, easing = Curves.split), label = "press")
    val tap = rememberUpdatedState(onTap)
    val hold = rememberUpdatedState(onHold)
    val enabled = rememberUpdatedState(isEnabled)
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(id) {
        dashboard.controls[id] = DashboardControl(
            bounds = {
                val coordinates = placed[0]
                if (coordinates == null || !coordinates.isAttached) Rect.Zero
                else coordinates.boundsInRoot().let { Rect(it.left / Island.density, it.top / Island.density, it.right / Island.density, it.bottom / Island.density) }
            },
            onTap = { if (enabled.value) tap.value() },
        )
        onDispose { dashboard.controls.remove(id) }
    }
    return this
        .onGloballyPositioned { placed[0] = it }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                dashboard.isControlTouch = true
                isPressing = true
                var isHeld = false
                val holdJob = hold.value?.let { action ->
                    Island.scope.launch {
                        delay(Durations.HOLD.toLong())
                        isHeld = true
                        isPressing = false
                        action()
                    }
                }
                var travel = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    travel = max(travel, (change.position - down.position).getDistance() / density)
                    if (travel > Dashboard.KNOB_DRAG_SLOP) {
                        holdJob?.cancel()
                        isPressing = false
                    }
                    if (!change.pressed) {
                        holdJob?.cancel()
                        if (!isHeld && travel < Dashboard.TAP_SLOP && !Island.wasCancelled && dashboard.isOpen && enabled.value) tap.value()
                        break
                    }
                }
                holdJob?.cancel()
                isPressing = false
            }
        }
}

private suspend fun PointerInputScope.ground(dashboard: Dashboard) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val isFromControl = dashboard.isControlTouch
    val isFromLevel = dashboard.isLevelTouch
    var isPushed = false
    var travel = 0f
    val trail = ArrayList<Triple<Float, Float, Long>>()
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        val across = (change.position.x - down.position.x) / density
        val drop = (change.position.y - down.position.y) / density
        trail.add(Triple(change.position.x / density, change.position.y / density, change.uptimeMillis))
        travel = max(travel, hypot(across, drop))
        if (!change.pressed) {
            if (!isFromControl && !isPushed && travel < Dashboard.TAP_SLOP && !Island.wasCancelled && dashboard.isOpen) {
                dashboard.tapGround(Offset(change.position.x / density, change.position.y / density))
            }
            break
        }
        if (!isPushed && !isFromLevel && drop < -Dashboard.PUSH_DISMISS && abs(across) < Dashboard.PUSH_STRAY) {
            isPushed = true
            dashboard.close(Sling.throwOf(trail))
        }
    }
    dashboard.isControlTouch = false
    dashboard.isLevelTouch = false
}

/* Mirrors FOCUS_RAMP in js/liquid.js. */
private const val FOCUS_RAMP = 240

@Composable
fun DashboardScrim(dashboard: Dashboard) {
    val isShown = dashboard.isOpen && !dashboard.isSoloOut
    val opacity = animateFloatAsState(
        if (isShown) 1f else 0f,
        when {
            isShown -> tween(15)
            dashboard.isSoloOut -> tween(Dashboard.SOLO_FROST, easing = Curves.leave)
            else -> tween(84)
        },
        label = "scrim",
    )
    val frost = animateFloatAsState(
        if (isShown) 1f else 0f,
        tween(if (isShown) 62 else if (dashboard.isSoloOut) Dashboard.SOLO_FROST else 270, easing = Curves.sweep),
        label = "frost",
    )
    /* Mirrors focusFrost in js/liquid.js: a focused Alert and the open Notifications menu frost the screen through the Dashboard's own pane. */
    val bubble = Island.bubble
    val isFocused = bubble.size == Size.AlertCentre || bubble.size == Size.Notifications
    val focus = animateFloatAsState(if (isFocused) 1f else 0f, tween(FOCUS_RAMP, easing = FastOutSlowInEasing), label = "focus")
    Canvas(Modifier.fillMaxSize()) {
        val stepped = (maxOf(frost.value, focus.value) * FROST_STEPS).roundToInt() / FROST_STEPS.toFloat()
        if (stepped != Island.frost) {
            Island.frost = stepped
            Island.onBlobsDrawn()
        }
        if (opacity.value > 0f) drawRect(SCRIM.copy(alpha = SCRIM.alpha * opacity.value))
    }
}

@Composable
fun DashboardPanel(dashboard: Dashboard) {
    val isOpen = dashboard.isOpen
    val screenWidth = Island.screenWidth
    val screenHeight = Island.screenHeight
    SideEffect {
        if (isOpen) Island.regions[Region.Dashboard] = Rect(0f, 0f, screenWidth, screenHeight)
        else Island.regions.remove(Region.Dashboard)
    }
    val now = Island.now
    val nowTall = animateFloatAsState(if (now.isInPanel && Island.media != null) now.restHeight else 0f, tween(NowBubble.GROW, easing = Curves.grow), label = "nowTall")
    val geometry = PanelGeometry(screenWidth, screenHeight, nowTall.value)
    SideEffect { dashboard.nowBottom = geometry.top + geometry.knobTop - NowBubble.PANEL_GAP }
    if (!isOpen && !dashboard.isLeaving) return
    val appearance = Island.appearance
    val paired = Island.connectivity?.paired
    val pairedCount = paired?.names?.size ?: 0
    LaunchedEffect(pairedCount, isOpen) {
        if (pairedCount < 2 || !isOpen) {
            dashboard.pairedTurn = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(Dashboard.PAIRED_SWAP)
            dashboard.pairedTurn += 1
        }
    }
    Box(Modifier.fillMaxSize().then(if (isOpen) Modifier.pointerInput(dashboard) { ground(dashboard) } else Modifier)) {
        Box(
            Modifier
                .offset { IntOffset((geometry.left * Island.density).roundToInt(), (geometry.top * Island.density).roundToInt()) }
                .size(geometry.width.dp, geometry.height.dp),
        ) {
            StatsSection(dashboard, geometry, appearance)
            Divider(dashboard, geometry.firstDivider, geometry.width, appearance)
            SystemSection(dashboard, geometry, appearance)
            Divider(dashboard, geometry.secondDivider, geometry.width, appearance)
            KnobRow(dashboard, geometry, appearance)
            ModsToggle(dashboard, geometry)
            Mark(dashboard, geometry)
        }
    }
}

@Composable
private fun appearing(dashboard: Dashboard, delayMilliseconds: Int, isSoloSensitive: Boolean = true): Float {
    val isShown = dashboard.isOpen && !(isSoloSensitive && dashboard.isSoloOut)
    return animateFloatAsState(
        if (isShown) 1f else 0f,
        when {
            isShown -> tween(1, delayMilliseconds)
            dashboard.isSoloOut -> tween(110, easing = Curves.leave)
            else -> tween(200)
        },
        label = "appear",
    ).value
}

@Composable
private fun Divider(dashboard: Dashboard, top: Float, panelWide: Float, appearance: Appearance) {
    if (!appearance.hasDividers) return
    val shown = appearing(dashboard, 180)
    val wide = panelWide * DIVIDER_SHARE
    Box(
        Modifier
            .offset { IntOffset((((panelWide - wide) / 2f) * Island.density).roundToInt(), (top * Island.density).roundToInt()) }
            .size(wide.dp, 1.dp)
            .graphicsLayer { alpha = shown * 0.5f }
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Palette.textMuted, Color.Transparent)), RoundedCornerShape(1.dp)),
    )
}

@Composable
private fun Mark(dashboard: Dashboard, geometry: PanelGeometry) {
    val shown = appearing(dashboard, 220, isSoloSensitive = false)
    Box(Modifier.size(geometry.width.dp, geometry.height.dp).padding(bottom = 4.dp).graphicsLayer { alpha = shown }, contentAlignment = Alignment.BottomCenter) {
        BasicText("V/AS", style = type(0.55f, Color.White.copy(alpha = 0.07f), Faces.mono, spacing = 0.11f))
    }
}

@Composable
private fun ModsToggle(dashboard: Dashboard, geometry: PanelGeometry) {
    val bubble = Island.bubble
    val isCleared = bubble.clearedMods.isNotEmpty()
    if (!isCleared && !bubble.hasPresentMods) return
    val shown = appearing(dashboard, 160, isSoloSensitive = false)
    Box(
        Modifier
            .offset { IntOffset(((geometry.width - MODS_SIZE) * Island.density).roundToInt(), (MODS_TOP * Island.density).roundToInt()) }
            .size(MODS_SIZE.dp)
            .graphicsLayer { alpha = shown * 0.32f }
            .control(dashboard, "mods", 0.9f) { dashboard.toggleMods() }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Palette.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(if (isCleared) Glyphs.modsRestore else Glyphs.modsClear, Palette.textBright, Modifier.size((MODS_SIZE * 0.6f).dp))
    }
}

@Composable
private fun QuickBubble(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = SquircleShape(24.dp), content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(shape)
            .background(Island.appearance.pillFill)
            .border(1.dp, Palette.border, shape),
        content = content,
    )
}

@Composable
private fun StatsSection(dashboard: Dashboard, geometry: PanelGeometry, appearance: Appearance) {
    Column(
        Modifier
            .size(geometry.width.dp, geometry.statsTall.dp)
            .padding(top = STATS_DROP.dp, start = STATS_INSET.dp, end = STATS_INSET.dp),
        verticalArrangement = Arrangement.spacedBy(Dashboard.GAP.dp),
    ) {
        WeatherLine(dashboard, appearance)
        Row(Modifier.fillMaxWidth().height(VITALS_TALL.dp), horizontalArrangement = Arrangement.spacedBy(Dashboard.GAP.dp)) {
            VitalsBubble(dashboard, geometry, appearance, Modifier.weight(1f).fillMaxHeight())
            BatteryBubble(dashboard, geometry, appearance, Modifier.width(SIDE_WIDE.dp).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(TRANSFER_TALL.dp), horizontalArrangement = Arrangement.spacedBy(Dashboard.GAP.dp)) {
            TransferStrip(dashboard, appearance, Modifier.weight(1f).fillMaxHeight())
            if (Island.connectivity?.isUsb == true) UsbBubble(dashboard, geometry, appearance, Modifier.width(SIDE_WIDE.dp).fillMaxHeight())
        }
    }
}

/* The glyph beside a held reading stood there alone before the sweep showed the reading it belongs to; the two keep their room unseen and are swept in as one. */
@Composable
private fun HeldReveal(dashboard: Dashboard, text: String, style: TextStyle, bar: Color, isGlyphFirst: Boolean, glyph: @Composable () -> Unit) {
    val line = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.2.dp)) {
            if (isGlyphFirst) glyph()
            BasicText(text, style = style, maxLines = 1, softWrap = false)
            if (!isGlyphFirst) glyph()
        }
    }
    if (dashboard.areReadingsHeld) Box(Modifier.graphicsLayer { alpha = 0f }) { line() }
    else Reveal(text, bar) { line() }
}

@Composable
private fun WeatherLine(dashboard: Dashboard, appearance: Appearance) {
    val shown = appearing(dashboard, 160)
    val weather = dashboard.weather
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().height(WEATHER_TALL.dp).graphicsLayer { alpha = shown },
        horizontalArrangement = Arrangement.spacedBy(7.2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = type(0.78f, Color.White, Faces.clashMedium, appearance.statsScale, spacing = 0.0156f, lineHeight = 1.2f)
        HeldReveal(dashboard, if (weather != null) "${weather.celsius.roundToInt()}°C" else "--°C", style, accent, isGlyphFirst = false) {
            GlyphIcon(
                when (weather?.sky) {
                    "clear" -> Glyphs.weatherClear
                    "rain" -> Glyphs.weatherRain
                    else -> Glyphs.weatherCloud
                },
                Color.White,
                Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun VitalsBubble(dashboard: Dashboard, geometry: PanelGeometry, appearance: Appearance, modifier: Modifier) {
    val accent = LocalAccent.current
    val vitals = dashboard.vitals
    QuickBubble(
        modifier
            .pop(dashboard, geometry.vitalsCentre, geometry.height, PopKind.Plain)
            .control(dashboard, "vitals", 0.97f, onHold = { dashboard.openSettings("vitals") }) { dashboard.openSettings("vitals") },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 17.6.dp, vertical = 9.6.dp),
            verticalArrangement = Arrangement.spacedBy(6.4.dp, Alignment.CenterVertically),
        ) {
            VitalRow(dashboard, 0, Glyphs.vitalStorage, accent, vitals?.storage, appearance)
            VitalRow(dashboard, 1, Glyphs.vitalRam, RAM_COLOUR, vitals?.ram, appearance)
        }
    }
}

@Composable
private fun VitalRow(dashboard: Dashboard, index: Int, glyph: Glyph, colour: Color, reading: Vital?, appearance: Appearance) {
    val isShown = dashboard.isOpen
    val start = VITAL_DELAY + index * VITAL_STAGGER
    val rise = animateFloatAsState(if (isShown) 1f else 0f, if (isShown) tween(Durations.POP_IN, start, Curves.grow) else tween(120), label = "rise")
    val share = animateFloatAsState(if (isShown) (reading?.percent ?: 0) / 100f else 0f, tween(VITAL_GAUGE, if (isShown) start + VITAL_GAUGE_LAG else 0, Curves.split), label = "gauge")
    /* The storage and RAM rows stood there from the first frame while everything around them arrived; each rises from below in a fade and its line fills from empty. */
    Row(
        Modifier.graphicsLayer {
            alpha = rise.value.coerceIn(0f, 1f)
            translationY = (1f - rise.value) * VITAL_DROP * density
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(glyph, colour, Modifier.size(15.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.5.dp)) {
            BasicText(
                reading?.let { String.format(java.util.Locale.ROOT, "%.1f / %.0f GB", it.usedGigabytes, it.totalGigabytes) } ?: "—",
                style = type(0.64f, Palette.textBright, Faces.mono, appearance.statsScale, lineHeight = 1f),
                maxLines = 1,
                softWrap = false,
            )
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val corner = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                drawRoundRect(colour.copy(alpha = 0.22f), cornerRadius = corner)
                drawRoundRect(colour, size = size.copy(width = size.width * share.value.coerceIn(0f, 1f)), cornerRadius = corner)
            }
        }
    }
}

@Composable
private fun BatteryBubble(dashboard: Dashboard, geometry: PanelGeometry, appearance: Appearance, modifier: Modifier) {
    val charge = Island.charge
    val percent = charge?.percent ?: -1
    val isPlugged = charge?.isPlugged == true
    val colour = animateColorAsState(if (charge == null) Palette.textFaint else StatusBubble.chargeColour(charge), tween(500), label = "charge")
    val outline = animateColorAsState(if (isPlugged) colour.value else Color.Transparent, tween(500), label = "outline")
    val isSaving = dashboard.toggles.switches["saver"] == true
    val isLow = !isPlugged && percent in 0..BATTERY_CRITICAL
    val edge = animateColorAsState(
        when {
            isLow -> BATTERY_LOW_EDGE
            isSaving -> Palette.toggles.getValue("saver")
            else -> colour.value
        },
        tween(500),
        label = "edge",
    )
    val share = (percent.coerceAtLeast(0)) / 100f
    Row(
        modifier
            .pop(dashboard, geometry.vitalsCentre, geometry.height, PopKind.Plain)
            .control(dashboard, "battery", 0.97f, onHold = { dashboard.openSettings("battery") }) { dashboard.openSettings("battery") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cellShape = SquircleShape(22.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cellShape)
                .background(BATTERY_WELL)
                .border(2.dp, outline.value, cellShape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 6.dp.toPx()
                val wide = max(0f, (size.width - 4.dp.toPx()) * share - 8.dp.toPx())
                if (wide > 0f) {
                    val outline = FILL_SHAPE.createOutline(androidx.compose.ui.geometry.Size(wide, size.height - inset * 2f), layoutDirection, this)
                    translate(inset, inset) { drawOutline(outline, colour.value) }
                }
            }
            if (isPlugged && dashboard.isOpen) ChargeBubbles(share)
            BatteryInk(dashboard, appearance, share, Color.White, isOverFill = false)
            BatteryInk(dashboard, appearance, share, Color.Black, isOverFill = true)
        }
        Box(
            Modifier
                .padding(start = 5.dp)
                .width(20.dp)
                .fillMaxHeight(0.58f)
                .clip(RoundedCornerShape(8.dp))
                .background(BATTERY_WELL)
                .border(2.dp, outline.value, RoundedCornerShape(8.dp))
                .padding(3.dp)
                .background(edge.value, RoundedCornerShape(4.dp)),
        )
    }
}

/* Mirrors #battery-fill in pill.css: the fill is a squircle like its cell, and a circular 17dp corner read rounder than the WebView's. */
private val FILL_SHAPE = SquircleShape(17.dp)

@Composable
private fun BoxScope.ChargeBubbles(share: Float) {
    val transition = rememberInfiniteTransition(label = "bubbles")
    val time = transition.animateFloat(0f, CHARGE_LOOP, infiniteRepeatable(tween(CHARGE_LOOP.toInt(), easing = LinearEasing), RepeatMode.Restart), label = "time")
    Canvas(Modifier.matchParentSize()) {
        val inset = 6.dp.toPx()
        val wide = max(0f, (size.width - 4.dp.toPx()) * share - 8.dp.toPx())
        if (wide <= 0f) return@Canvas
        val tall = size.height - inset * 2f
        clipRect(inset, inset, inset + wide, inset + tall) {
            CHARGE_BUBBLES.forEach { bubble ->
                val at = ((time.value / 1000f - bubble.delay) % bubble.seconds + bubble.seconds) % bubble.seconds / bubble.seconds
                val rise = CHARGE_RISE.transform(at)
                val opacity = when {
                    at < 0.15f -> at / 0.15f
                    at > 0.8f -> (1f - at) / 0.2f
                    else -> 1f
                }
                val wobblePhase = ((time.value / 1000f - bubble.delay) % (bubble.seconds / 3f)) / (bubble.seconds / 3f)
                val wobble = if (((time.value / 1000f - bubble.delay) / (bubble.seconds / 3f)).toInt() % 2 == 0) wobblePhase else 1f - wobblePhase
                val radius = bubble.size.dp.toPx() / 2f * (0.6f + 0.4f * wobble)
                val x = inset - bubble.size.dp.toPx() + (wide + bubble.size.dp.toPx() * 2f) * rise
                val y = inset + tall * bubble.top + (-2f + 4f * wobble) * density
                drawCircle(Color.White.copy(alpha = 0.55f * opacity), radius, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.35f * opacity), radius, Offset(x, y), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun BoxScope.BatteryInk(dashboard: Dashboard, appearance: Appearance, share: Float, colour: Color, isOverFill: Boolean) {
    val charge = Island.charge
    val exact = dashboard.exactCharge.takeIf { it >= 0 } ?: charge?.exact ?: -1.0
    Box(
        Modifier.matchParentSize().drawWithContent {
            val edge = max(0f, 2.dp.toPx() + (size.width - 4.dp.toPx()) * share - 4.dp.toPx())
            if (isOverFill) clipRect(right = edge) { this@drawWithContent.drawContent() }
            else clipRect(left = edge) { this@drawWithContent.drawContent() }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (charge == null || charge.percent < 0) {
            BasicText("--", style = type(1.75f, colour, appearance.batteryFont, appearance.batteryScale, weight = FontWeight.SemiBold, lineHeight = 1f))
        } else {
            val reading = String.format(java.util.Locale.ROOT, "%.2f", exact)
            val whole = reading.substringBefore('.')
            val hundredths = reading.substringAfter('.')
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(whole, style = type(1.75f, colour, appearance.batteryFont, appearance.batteryScale, weight = FontWeight.SemiBold, lineHeight = 1f))
                Column(Modifier.padding(start = 1.7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val side = type(0.875f, colour, appearance.batteryFont, appearance.batteryScale, weight = FontWeight.SemiBold, lineHeight = 1f)
                    BasicText("%", style = side)
                    BasicText(".$hundredths", style = side)
                }
            }
        }
        if (charge?.isPlugged == true && charge.remainingMinutes >= 0) {
            BasicText(
                remainingCharge(charge.remainingMinutes),
                Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 3.dp),
                style = type(0.48f, colour, appearance.batteryFont, appearance.batteryScale, lineHeight = 1f),
            )
        }
    }
}

private fun remainingCharge(minutes: Int): String = when {
    minutes < 1 -> "<1m"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

@Composable
private fun TransferStrip(dashboard: Dashboard, appearance: Appearance, modifier: Modifier) {
    val shown = appearing(dashboard, 200)
    val transfer = Island.transfer
    val accent = LocalAccent.current
    val isDone = transfer != null && transfer.total > 0 && transfer.done >= transfer.total
    val share = when {
        transfer == null -> 0f
        isDone || transfer.total <= 0 -> 1f
        else -> transfer.done.toFloat() / transfer.total
    }
    val fill = animateFloatAsState(share, tween(320, easing = Curves.grow), label = "transfer")
    val mono = type(0.66f, Color.White, Faces.mono, appearance.statsScale, lineHeight = 1f)
    Column(
        modifier.graphicsLayer { alpha = shown }.padding(horizontal = 3.2.dp),
        verticalArrangement = Arrangement.spacedBy(4.8.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.2.dp)) {
            if (transfer == null) {
                HeldReveal(dashboard, dashboard.dataToday, mono, accent, isGlyphFirst = true) {
                    GlyphIcon(Glyphs.dataToday, Color.White, Modifier.size(15.dp))
                }
            } else {
                GlyphIcon(if (isDone) Glyphs.transferDone else if (transfer.isUpload) Glyphs.upload else Glyphs.download, Color.White, Modifier.size(15.dp))
                BasicText(if (isDone) "Done" else "${(share * 100f).roundToInt()}%", style = mono)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    FadingStart(transfer.label, mono.copy(color = Palette.textMuted))
                }
            }
        }
        if (transfer != null) {
            Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                val corner = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                drawRoundRect(Palette.sunken, cornerRadius = corner)
                drawRoundRect(if (transfer.isUpload) UPLOAD_COLOUR else DOWNLOAD_COLOUR, size = size.copy(width = size.width * fill.value.coerceIn(0f, 1f)), cornerRadius = corner)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.6.dp, Alignment.End)) {
                val hold = transfer.actions.firstOrNull { HOLD_WORDS.containsMatchIn(it.title) }
                if (!isDone && hold != null) {
                    TransferButton(dashboard, "transfer-hold", if (transfer.isPaused) Glyphs.transferResume else Glyphs.transferHold) {
                        Haptics.play(Haptic.Tap)
                        Island.actions.transferAction(hold.index)
                    }
                }
                if (!isDone) {
                    TransferButton(dashboard, "transfer-stop", Glyphs.transferStop) {
                        Haptics.play(Haptic.Tap)
                        val stop = transfer.actions.firstOrNull { STOP_WORDS.containsMatchIn(it.title) }
                        if (stop != null) Island.actions.transferAction(stop.index) else Island.actions.dismissNotification(transfer.key)
                    }
                }
            }
        }
    }
}

@Composable
private fun FadingStart(text: String, style: TextStyle) {
    BasicText(
        text,
        Modifier
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = (22.4f * density).coerceAtMost(size.width)
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                    size = size.copy(width = fade),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            },
        style = style,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun TransferButton(dashboard: Dashboard, id: String, glyph: Glyph, onTap: () -> Unit) {
    Box(
        Modifier
            .size(30.dp, 16.dp)
            .control(dashboard, id, 0.95f, onTap = onTap)
            .border(1.dp, Palette.borderControl, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Palette.textBody, Modifier.size(10.dp))
    }
}

@Composable
private fun UsbBubble(dashboard: Dashboard, geometry: PanelGeometry, appearance: Appearance, modifier: Modifier) {
    val accent = Palette.toggles.getValue("usb")
    val isOn = dashboard.isOn("usb")
    val colour = animateColorAsState(if (isOn) accent else Palette.textMuted, tween(200), label = "usb")
    val shape = SquircleShape(24.dp)
    Row(
        modifier
            .pop(dashboard, geometry.transferCentre, geometry.height, PopKind.Plain)
            .control(dashboard, "usb", 0.96f, onHold = { dashboard.holdMode("usb") }) { dashboard.press("usb") }
            .clip(shape)
            .background(appearance.pillFill)
            .border(1.dp, if (isOn) lerp(Palette.border, accent, 0.5f) else Palette.border, shape)
            .padding(horizontal = 12.8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyphs.usb, colour.value, Modifier.size(18.dp))
        BasicText(
            if (isOn) "file transfer" else "charging only",
            style = type(0.66f, Palette.textBody, Faces.mono, appearance.statsScale, lineHeight = 1f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SystemSection(dashboard: Dashboard, geometry: PanelGeometry, appearance: Appearance) {
    val lift = animateFloatAsState(
        if (dashboard.isSoloOut) geometry.height / 2f - geometry.levelsCentre else 0f,
        tween(Dashboard.SOLO_TRAVEL, easing = Curves.split),
        label = "soloLift",
    )
    Row(
        Modifier
            .offset { IntOffset(0, (geometry.gridTop * Island.density).roundToInt()) }
            .size(geometry.width.dp, GRID_TALL.dp),
        horizontalArrangement = Arrangement.spacedBy(Dashboard.GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Dashboard.GAP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dashboard.MODES.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(KNOB_GAP.dp)) {
                    pair.forEach { name -> ModeButton(dashboard, name, geometry.modeCentre(row), geometry, appearance) }
                }
            }
        }
        Box(
            Modifier
                .padding(end = LEVELS_INSET.dp)
                .requiredSize(LEVELS_WIDE.dp, LEVELS_TALL.dp)
                .graphicsLayer { translationY = lift.value * density },
        ) {
            Levels(dashboard, geometry)
        }
    }
}

private fun ringReading(dashboard: Dashboard, name: String): String {
    val attached = Island.connectivity
    val switches = dashboard.toggles.switches
    return when (name) {
        "bluetooth" -> {
            val paired = attached?.paired
            when {
                paired != null -> paired.names.takeIf { it.isNotEmpty() }?.let { it[dashboard.pairedTurn % it.size] } ?: "connected"
                switches["bluetooth"] == true -> WAITING.getValue("bluetooth")
                else -> ""
            }
        }
        "wifi" -> when {
            attached?.link == "wifi" && attached.ssid.isNotEmpty() -> attached.ssid
            switches["wifi"] == true -> WAITING.getValue("wifi")
            else -> ""
        }
        "mobile" -> if (switches["mobile"] != true) "" else attached?.generation?.takeIf { it.isNotEmpty() } ?: WAITING.getValue("mobile")
        "hotspot" -> when {
            attached?.isHotspot == true -> "hotspot"
            switches["hotspot"] == true -> WAITING.getValue("hotspot")
            else -> ""
        }
        "plane" -> if (switches["plane"] == true) "offline" else ""
        "gps" -> if (switches["gps"] == true) "GPS" else ""
        else -> ""
    }
}

private fun modeGlyph(name: String, isOn: Boolean): Glyph = when (name) {
    "hotspot" -> Glyphs.hotspot
    "plane" -> Glyphs.plane
    "mobile" -> Glyphs.mobile
    "gps" -> Glyphs.gps
    "wifi" -> if (isOn) Island.status.wifiGlyph(Island.connectivity) else Glyphs.wifiOff
    "bluetooth" -> Glyphs.bluetooth
    else -> Glyphs.usb
}

@Composable
private fun ModeButton(dashboard: Dashboard, name: String, centre: Float, geometry: PanelGeometry, appearance: Appearance) {
    val accent = Palette.toggles.getValue(name)
    val isOn = dashboard.isOn(name)
    val isAvailable = dashboard.isAvailable(name)
    val reading = ringReading(dashboard, name)
    val isSearching = reading.isNotEmpty() && reading == WAITING[name]
    val colour = animateColorAsState(if (isOn) accent else Palette.textMuted, tween(200), label = "mode")
    val ringColour = animateColorAsState(if (isOn) lerp(Palette.textBody, accent, 0.7f) else Palette.textFaint, tween(200), label = "ring")
    val turn = rememberTurn(dashboard, name, isOn)
    Box(
        Modifier
            .size(GRID_COLUMN.dp, ((GRID_TALL - Dashboard.GAP * 2f) / 3f).dp)
            .pop(dashboard, centre, geometry.height, PopKind.Turning)
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.4f }
            .control(dashboard, "mode-$name", 0.96f, isEnabled = isAvailable, onHold = { dashboard.holdMode(name) }) { dashboard.press(name) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(GRID_ICON.dp)
                .graphicsLayer {
                    rotationZ = turn.rotation
                    val grown = 0.55f + 0.45f * turn.growth
                    scaleX = grown
                    scaleY = grown
                }
                .background(appearance.pillFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(modeGlyph(name, turn.shownOn), colour.value, Modifier.size((GRID_ICON * 0.62f).dp))
        }
        Ring(
            if (isSearching) "" else reading,
            isSearching,
            ringColour.value,
            appearance,
            Modifier.requiredSize(RING_SIZE.dp).graphicsLayer {
                rotationZ = turn.rotation
                val grown = 0.55f + 0.45f * turn.growth
                scaleX = grown
                scaleY = grown
            },
        )
        val paired = Island.connectivity?.paired
        if (name == "bluetooth" && paired != null && paired.names.size == 1 && paired.charge >= 0) {
            BasicText(
                paired.charge.toString(),
                Modifier.align(Alignment.CenterStart).offset(x = (GRID_COLUMN / 2f - GRID_ICON / 2f + 13f).dp),
                style = TextStyle(
                    color = if (paired.charge <= BATTERY_CRITICAL) PAIRED_CRITICAL else Color.White,
                    fontFamily = Faces.mono,
                    fontSize = (8f * appearance.connectorsScale).sp,
                    lineHeight = (8f * appearance.connectorsScale).sp,
                ),
            )
        }
    }
}

private class Turn(val rotation: Float, val growth: Float, val shownOn: Boolean)

@Composable
private fun rememberTurn(dashboard: Dashboard, name: String, isOn: Boolean): Turn {
    val count = dashboard.turns[name] ?: 0
    val rotation = remember { Animatable(0f) }
    var shownOn by remember { mutableStateOf(isOn) }
    val origin = remember { intArrayOf(count) }
    LaunchedEffect(count) {
        if (count == origin[0]) return@LaunchedEffect
        rotation.snapTo(0f)
        launch {
            delay(Dashboard.KNOB_TURN / 2L)
            shownOn = dashboard.isOn(name)
        }
        rotation.animateTo(360f, tween(Dashboard.KNOB_TURN, easing = Curves.enter))
        rotation.snapTo(0f)
    }
    LaunchedEffect(isOn) {
        if (!rotation.isRunning) shownOn = isOn
    }
    val isTurning = rotation.isRunning
    return Turn(rotation.value, if (isTurning) (rotation.value / 360f).coerceIn(0f, 1.2f) else 1f, shownOn)
}

private class RingCharacter(val character: String, val turn: Float, val at: Int, val tilt: Float)

private fun ringLayout(reading: String): List<RingCharacter> {
    if (reading.isEmpty()) return emptyList()
    val characters = reading.map(Char::toString)
    val slots = (2f * PI.toFloat() * RING_RADIUS / RING_ADVANCE).roundToInt()
    val run = characters.size + RING_GAP
    val copies = max(1, floor(slots.toFloat() / run).toInt())
    val step = 360f / (copies * run)
    return (0 until copies).flatMap { copy ->
        characters.mapIndexed { index, character ->
            val tilt = (Random.nextFloat() * 0.6f + 0.4f) * RING_POP_TILT * if (Random.nextBoolean()) -1f else 1f
            RingCharacter(character, (copy * run + index) * step, Random.nextInt(RING_POP_SPREAD + 1), tilt)
        }
    }
}

@Composable
private fun Ring(reading: String, isSearching: Boolean, colour: Color, appearance: Appearance, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    var shown by remember { mutableStateOf(ringLayout(reading)) }
    var shownReading by remember { mutableStateOf(reading) }
    val arrive = remember { Animatable(1f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(reading) {
        if (reading == shownReading) return@LaunchedEffect
        if (shown.isNotEmpty()) {
            leave.snapTo(0f)
            leave.animateTo(1f, tween(RING_OUT, easing = LinearEasing))
        }
        shownReading = reading
        shown = ringLayout(reading)
        leave.snapTo(0f)
        arrive.snapTo(0f)
        arrive.animateTo(1f, tween(RING_POP_SPREAD + RING_POP_IN, easing = LinearEasing))
    }
    val transition = rememberInfiniteTransition(label = "ring")
    val spin = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(RING_SPIN, easing = LinearEasing)), label = "spin")
    val hop = transition.animateFloat(0f, DOT_HOP.toFloat(), infiniteRepeatable(tween(DOT_HOP, easing = LinearEasing)), label = "hop")
    val style = TextStyle(color = colour, fontFamily = Faces.mono, fontSize = (9f * appearance.connectorsScale).sp)
    Canvas(modifier) {
        val unit = size.minDimension / (RING_CENTER * 2f)
        val centre = Offset(size.width / 2f, size.height / 2f)
        if (isSearching) {
            for (index in 0 until RING_DOTS) {
                val phase = (((hop.value - index * DOT_DELAY) % DOT_HOP) + DOT_HOP) % DOT_HOP / DOT_HOP
                val (opacity, outward, grown) = dotHop(phase)
                if (opacity <= 0f) continue
                rotate(index * 360f / RING_DOTS, centre) {
                    drawCircle(colour.copy(alpha = colour.alpha * opacity), 1.6f * unit * grown, Offset(centre.x, centre.y - (RING_RADIUS + outward) * unit))
                }
            }
        }
        if (shown.isEmpty()) return@Canvas
        rotate(spin.value, centre) {
            shown.forEach { character ->
                val growth = if (leave.value > 0f) {
                    val local = ((leave.value * RING_OUT - character.at) / RING_POP_OUT).coerceIn(0f, 1f)
                    1f - Curves.grow.transform(local)
                } else {
                    val local = ((arrive.value * (RING_POP_SPREAD + RING_POP_IN) - character.at) / RING_POP_IN).coerceIn(0f, 1f)
                    Curves.split.transform(local)
                }
                if (growth <= 0f) return@forEach
                val layout = measurer.measure(character.character, style)
                rotate(character.turn, centre) {
                    translate(centre.x, centre.y - RING_RADIUS * unit) {
                        rotate(character.tilt * (1f - growth), Offset.Zero) {
                            scale(growth, Offset.Zero) {
                                drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dotHop(phase: Float): Triple<Float, Float, Float> = when {
    phase < 0.0625f -> Triple(0.4f * phase / 0.0625f, 4f * phase / 0.125f, 1f + 0.45f * phase / 0.125f)
    phase < 0.125f -> Triple(0.4f + 0.6f * (phase - 0.0625f) / 0.0625f, 4f * phase / 0.125f, 1f + 0.45f * phase / 0.125f)
    phase < 0.1875f -> Triple(1f - 0.6f * (phase - 0.125f) / 0.0625f, 4f * (0.25f - phase) / 0.125f, 1f + 0.45f * (0.25f - phase) / 0.125f)
    phase < 0.25f -> Triple(0.4f - 0.4f * (phase - 0.1875f) / 0.0625f, 4f * (0.25f - phase) / 0.125f, 1f + 0.45f * (0.25f - phase) / 0.125f)
    else -> Triple(0f, 0f, 1f)
}

private class LevelFrame(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun levelFrame(name: String, isCall: Boolean, held: String?): LevelFrame {
    val half = LEVELS_WIDE / 2f + KNOB_GAP / 2f
    if (held != null) {
        val inset = LEVELS_WIDE * 0.1f
        return if (held == "brightness") LevelFrame(0f, 0f, inset, 0f) else LevelFrame(inset, 0f, 0f, 0f)
    }
    return when (name) {
        "brightness" -> LevelFrame(0f, 0f, half, 0f)
        "volume" -> LevelFrame(half, if (isCall) LEVELS_TALL / 2f + LEVEL_SPLIT / 2f else 0f, 0f, 0f)
        else -> LevelFrame(half, 0f, 0f, if (isCall) LEVELS_TALL / 2f + LEVEL_SPLIT / 2f else LEVELS_TALL - 2f)
    }
}

@Composable
private fun Levels(dashboard: Dashboard, geometry: PanelGeometry) {
    val isCall = dashboard.toggles.isCall
    val held = dashboard.soloLevel
    var isSettled by remember { mutableStateOf(true) }
    LaunchedEffect(held) {
        isSettled = false
        delay(Dashboard.SOLO_TRAVEL.toLong())
        isSettled = true
    }
    LEVELS.forEach { name ->
        val frame = levelFrame(name, isCall, held)
        val spec = tween<Float>(Dashboard.SOLO_TRAVEL, easing = Curves.split)
        val left = animateFloatAsState(frame.left, spec, label = "left")
        val top = animateFloatAsState(frame.top, spec, label = "top")
        val right = animateFloatAsState(frame.right, spec, label = "right")
        val bottom = animateFloatAsState(frame.bottom, spec, label = "bottom")
        val isHidden = (held != null && held != name && isSettled) ||
            (name == "callVolume" && !isCall && held != name && LEVELS_TALL - top.value - bottom.value <= 2.5f)
        /* Dropped from composition while hidden, the far bar came back with a fresh pop and grew in from a random scale instead of sliding out from behind the held one. */
        Level(dashboard, name, geometry, held == name, isHidden, { left.value }, { top.value }, { right.value }, { bottom.value })
    }
}

@Composable
private fun Level(
    dashboard: Dashboard,
    name: String,
    geometry: PanelGeometry,
    isHeld: Boolean,
    isHidden: Boolean,
    left: () -> Float,
    top: () -> Float,
    right: () -> Float,
    bottom: () -> Float,
) {
    val accent = LocalAccent.current
    val share = dashboard.levelShare(name)
    val isDragging = dashboard.draggedLevels.containsKey(name)
    var isHolding by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (isDragging) 1.03f else 1f, tween(200, easing = Curves.split), label = "levelScale")
    val ground = if (isHolding || isHeld) Color.Black else Island.appearance.pillFill
    val glyph = when (name) {
        "brightness" -> Glyphs.levelBrightness
        "volume" -> Glyphs.levelVolume
        else -> Glyphs.levelCallVolume
    }
    Box(
        Modifier
            .layout { measurable, _ ->
                val density = Island.density
                val wide = ((LEVELS_WIDE - left() - right()) * density).roundToInt().coerceAtLeast(1)
                val tall = ((LEVELS_TALL - top() - bottom()) * density).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(wide, tall))
                layout(placeable.width, placeable.height) { placeable.place((left() * density).roundToInt(), (top() * density).roundToInt()) }
            }
            .zIndex(if (isHeld) 1f else 0f)
            .pop(dashboard, geometry.levelsCentre, geometry.height, PopKind.Level)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (isHidden) 0f else 1f
            }
            .pointerInput(name, isHidden) {
                if (isHidden) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dashboard.isControlTouch = true
                    dashboard.isLevelTouch = true
                    isHolding = true
                    val tall = size.height.toFloat()
                    val from = down.position.y
                    val start = dashboard.levelShare(name)
                    var sent = Dashboard.valueAt(name, start).roundToInt()
                    var isDraggingNow = false
                    val solo = Island.scope.launch {
                        delay(Dashboard.SOLO_HOLD)
                        dashboard.enterSolo(name)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val travelled = from - change.position.y
                        if (!isDraggingNow) {
                            if (abs(travelled) / density < LEVEL_SLOP) continue
                            isDraggingNow = true
                            solo.cancel()
                            dashboard.enterSolo(name)
                        }
                        change.consume()
                        val position = (start + travelled / tall * 100f).coerceIn(0f, 100f)
                        dashboard.dragLevel(name, position)
                        val value = Dashboard.valueAt(name, position).roundToInt()
                        if (abs(value - sent) < LEVEL_STEP && value != 0 && value != 100) continue
                        sent = value
                        Island.actions.setLevel(name, value, false)
                    }
                    solo.cancel()
                    if (isDraggingNow) dashboard.settleLevel(name)
                    isHolding = false
                    dashboard.leaveSolo()
                }
            }
            .clip(RoundedCornerShape(999.dp))
            .background(ground)
            .border(2.dp, ground, RoundedCornerShape(999.dp)),
    ) {
        Box(Modifier.matchParentSize().drawWithContent {
            val fill = size.height * share / 100f
            drawRect(accent, topLeft = Offset(0f, size.height - fill), size = size.copy(height = fill))
        })
        GlyphIcon(glyph, if (share > 0f) Palette.textBright else Palette.textMuted, Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(24.dp))
        Box(
            Modifier.matchParentSize().drawWithContent {
                val fill = size.height * share / 100f
                clipRect(top = size.height - fill) { this@drawWithContent.drawContent() }
            },
        ) {
            GlyphIcon(glyph, Color.Black, Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(24.dp))
        }
    }
}

@Composable
private fun KnobRow(dashboard: Dashboard, geometry: PanelGeometry, appearance: Appearance) {
    val windowWide = geometry.width + Dashboard.MARGIN * 2f
    Box(
        Modifier
            .offset { IntOffset(0, (geometry.knobTop * Island.density).roundToInt()) }
            /* size() was held to the panel's width, so the window bled past the left margin only and left both margins as a gap on the right; requiredSize overhangs both sides equally. */
            .requiredSize(windowWide.dp, Dashboard.KNOB_SIZE.dp)
            .pointerInput(dashboard) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dashboard.stopKnobGlide()
                    dashboard.isKnobSwiped = false
                    val from = down.position.x
                    var start = dashboard.knobShift
                    var isSwiped = false
                    var lastX = from
                    var lastAt = down.uptimeMillis
                    var speed = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val x = change.position.x
                        if (!isSwiped) {
                            if (abs(x - from) / density < Dashboard.KNOB_DRAG_SLOP) continue
                            isSwiped = true
                            dashboard.isKnobSwiped = true
                            start = dashboard.knobShift - (x - from) / density
                        }
                        val span = change.uptimeMillis - lastAt
                        if (span > 0) speed = (x - lastX) / density / span
                        lastX = x
                        lastAt = change.uptimeMillis
                        change.consume()
                        dashboard.shiftKnobs(start + (x - from) / density)
                    }
                    if (isSwiped) dashboard.glideKnobs(speed)
                }
            },
    ) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = dashboard.knobShift * density },
            horizontalArrangement = Arrangement.spacedBy(Dashboard.KNOB_ROW_GAP.dp),
        ) {
            for (copy in 0 until Dashboard.KNOB_COPIES) {
                Dashboard.KNOBS.forEachIndexed { index, name ->
                    Knob(dashboard, name, copy * Dashboard.KNOBS.size + index, geometry, appearance)
                }
            }
        }
    }
}

@Composable
private fun Knob(dashboard: Dashboard, name: String, slot: Int, geometry: PanelGeometry, appearance: Appearance) {
    val accent = Palette.toggles[name] ?: LocalAccent.current
    val isOpener = name == "comdirect"
    val isOn = !isOpener && dashboard.isOn(name)
    val isAvailable = dashboard.isAvailable(name)
    val turn = rememberTurn(dashboard, name, isOn)
    val colour = animateColorAsState(
        when {
            isOn -> accent
            name == "mic" -> MIC_OFF
            else -> Palette.textIcon
        },
        tween(200),
        label = "knob",
    )
    val ring = animateColorAsState(if (isOn) accent else Color.Transparent, tween(200), label = "knobRing")
    Box(
        Modifier
            .size(Dashboard.KNOB_SIZE.dp)
            .pop(dashboard, geometry.knobTop + Dashboard.KNOB_SIZE / 2f, geometry.height, PopKind.Knob)
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.4f }
            .control(dashboard, "knob-$slot", 0.94f, isEnabled = isAvailable) {
                if (dashboard.isKnobSwiped) return@control
                if (isOpener) dashboard.openApp(Dashboard.COMDIRECT) else dashboard.press(name)
            }
            .clip(CircleShape)
            .background(appearance.pillFill)
            .border(1.dp, Palette.border, CircleShape)
            .drawWithContent {
                drawContent()
                val width = 2.dp.toPx()
                drawCircle(ring.value, size.minDimension / 2f - width / 2f, style = Stroke(width))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(KNOB_ICON.dp).graphicsLayer { rotationZ = turn.rotation }) {
            if (name == "recording") {
                GlyphIcon(RECORDING_RING, colour.value, Modifier.fillMaxSize())
                GlyphIcon(RECORDING_DOT, if (turn.shownOn) RECORD_RED else colour.value, Modifier.fillMaxSize())
            } else {
                GlyphIcon(knobGlyph(name, turn.shownOn), colour.value, Modifier.fillMaxSize())
            }
        }
    }
}

private fun knobGlyph(name: String, isOn: Boolean): Glyph = when (name) {
    "augenkomfort" -> Glyphs.augenkomfort
    "modus" -> Glyphs.modus
    "dim" -> Glyphs.dim
    "rotate" -> Glyphs.rotate
    "mic" -> if (isOn) Glyphs.mic else Glyphs.offMic
    "saver" -> Glyphs.saver
    else -> Glyphs.comdirect
}

private val RECORDING_RING = Glyph(0f, 0f, 24f, 24f, listOf(Glyphs.recording.layers[0]))
private val RECORDING_DOT = Glyph(0f, 0f, 24f, 24f, listOf(Glyphs.recording.layers[1]))

@Composable
fun StatusActions(dashboard: Dashboard) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(11.2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).popIn(STATUS_ACTION_DELAY).pressable(pressedScale = 0.9f, haptic = Haptic.Tap) {
                Island.actions.openControlPanel()
                dashboard.close()
            },
        ) { GlyphIcon(Glyphs.settings, Palette.textBright, Modifier.fillMaxSize()) }
        Box(
            Modifier.size(26.dp).popIn(STATUS_ACTION_DELAY + Durations.POP_IN_STAGGER).pressable(pressedScale = 0.9f, haptic = Haptic.Expand) {
                Island.actions.openPowerMenu()
                dashboard.close()
            },
        ) { GlyphIcon(Glyphs.power, POWER_RED, Modifier.fillMaxSize()) }
    }
}

/* Mirrors DATE_FORMATS in the retired js/clock.js and the Date format labels in panel.html: the preference is an index into this list. */
private val WEEKDAYS = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
private val WEEKDAY_NAMES = listOf("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag")
private val MONTHS = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
private val MONTH_NAMES = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

fun dateText(format: Int, calendar: java.util.Calendar): String {
    fun pad(value: Int) = value.toString().padStart(2, '0')
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val month = calendar.get(java.util.Calendar.MONTH)
    val year = calendar.get(java.util.Calendar.YEAR)
    val weekday = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
    return when (format) {
        1 -> "$day.${month + 1}."
        2 -> "${pad(day)}.${pad(month + 1)}."
        3 -> "${pad(day)}.${pad(month + 1)}.${pad(year % 100)}"
        4 -> "${pad(day)}.${pad(month + 1)}.$year"
        5 -> "${pad(day)}/${pad(month + 1)}/${pad(year % 100)}"
        6 -> "${pad(day)}/${pad(month + 1)}/$year"
        7 -> "${pad(day)}-${pad(month + 1)}-${pad(year % 100)}"
        8 -> "${pad(day)}-${pad(month + 1)}-$year"
        9 -> "$day ${MONTHS[month]}"
        10 -> "$day ${MONTHS[month]} $year"
        11 -> "$day. ${MONTH_NAMES[month]} $year"
        12 -> "${WEEKDAYS[weekday]}, ${pad(day)}.${pad(month + 1)}.${pad(year % 100)}"
        13 -> "${WEEKDAYS[weekday]} $day ${MONTHS[month]}"
        14 -> "${WEEKDAY_NAMES[weekday]}, $day.${month + 1}."
        else -> "${WEEKDAYS[weekday]}, $day.${month + 1}."
    }
}

private class ChargeBubble(val top: Float, val size: Float, val delay: Float, val seconds: Float)

/* Mirrors the eight .charge-bubble spans in the retired pill.html: their rows, sizes, delays and periods. */
private val CHARGE_BUBBLES = listOf(
    ChargeBubble(0.22f, 5f, 0f, 2.2f),
    ChargeBubble(0.64f, 4f, 0.5f, 2.6f),
    ChargeBubble(0.40f, 7f, 0.9f, 2.0f),
    ChargeBubble(0.78f, 5f, 1.4f, 2.4f),
    ChargeBubble(0.30f, 4f, 1.8f, 2.8f),
    ChargeBubble(0.55f, 6f, 0.3f, 2.3f),
    ChargeBubble(0.15f, 3f, 1.1f, 2.5f),
    ChargeBubble(0.70f, 8f, 2.0f, 2.1f),
)
private val CHARGE_RISE: Easing = CubicBezierEasing(0.45f, 0f, 0.7f, 1f)
private const val CHARGE_LOOP = 1_000_000f

private val LEVELS = listOf("brightness", "volume", "callVolume")
private val WAITING = mapOf("bluetooth" to "nothing paired", "wifi" to "not connected", "hotspot" to "nobody joined", "mobile" to "standby")
private val HOLD_WORDS = Regex("pause|pausieren|anhalten|resume|fortsetzen|weiter", RegexOption.IGNORE_CASE)
private val STOP_WORDS = Regex("cancel|abbrechen|abbruch|stop|stopp|beenden", RegexOption.IGNORE_CASE)

private val SCRIM = Color(6, 6, 8).copy(alpha = 0.42f)
private val BATTERY_WELL = Color(10, 9, 13).copy(alpha = 0.85f)
private val BATTERY_LOW_EDGE = Color(0xFFFF3B30)
private val RAM_COLOUR = Color(0xFF8A5BFD)
private const val VITAL_DELAY = 180
private const val VITAL_STAGGER = 70
private const val VITAL_GAUGE = 420
private const val VITAL_GAUGE_LAG = 120
private const val VITAL_DROP = 10f
private const val STATUS_ACTION_DELAY = 120
private val DOWNLOAD_COLOUR = Color(0xFFF09B3A)
private val UPLOAD_COLOUR = Color(0xFF3A8CFF)
private val PAIRED_CRITICAL = Color(0xFFB33A3A)
private val MIC_OFF = Color(0xFFFF3B30)
private val RECORD_RED = Color(0xFFE5342B)
private val POWER_RED = Color(0xFFE5342B)

/* Mirrors BATTERY_CRITICAL in the retired js/status.js. */
private const val BATTERY_CRITICAL = 19
private const val FROST_STEPS = 12
private const val FAST_DROP = 220
private const val FAST_LIFT = 14f
private const val SOLO_SHRINK = 130
private const val SOLO_SHRINK_STEP = 14L
private const val SOLO_SWELL = 300
private const val SOLO_SWELL_STEP = 16L
private const val STATS_SHARE = 0.27f
private const val TOGGLE_SHARE = 0.275f
private const val STATS_DROP = 12f
private const val STATS_INSET = 10f
private const val WEATHER_TALL = 17.6f
private const val VITALS_TALL = 80f
private const val TRANSFER_TALL = 50f
private const val SIDE_WIDE = 150f
private const val GRID_TALL = 233f
private const val GRID_ICON = 60f
private const val GRID_COLUMN = 80f
private const val RING_SIZE = 78f
private const val KNOB_GAP = 5f
private const val KNOB_ICON = 27f
private const val LEVELS_WIDE = 108f
private const val LEVELS_TALL = 285f
private const val LEVELS_INSET = 14f
private const val LEVEL_SPLIT = 10f
private const val LEVEL_SLOP = 8f
private const val LEVEL_STEP = 1
private const val DIVIDER_ROOM = 34f
private const val DIVIDER_SHIFT = -18f
private const val DIVIDER_SHARE = 0.46f
private const val MODS_SIZE = 22f
private const val MODS_TOP = -18f
private const val RING_RADIUS = 30f
private const val RING_CENTER = 39f
private const val RING_ADVANCE = 5.6f
private const val RING_GAP = 3
private const val RING_DOTS = 20
private const val RING_POP_SPREAD = 220
private const val RING_POP_TILT = 18f
private const val RING_POP_IN = 460
private const val RING_POP_OUT = 150
private const val RING_OUT = RING_POP_SPREAD + RING_POP_OUT
private const val RING_SPIN = 16_000
private const val DOT_HOP = 3800
private const val DOT_DELAY = 190f
