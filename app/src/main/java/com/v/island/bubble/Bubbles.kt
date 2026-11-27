package com.v.island.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Palette
import com.v.island.vas.pressable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
private fun FloatingBubble(
    region: Region,
    target: Frame,
    border: Color,
    scale: Float,
    drag: () -> Offset,
    onDown: (Float, Float) -> Unit,
    onMove: (Float, Float) -> Unit,
    onUp: (Boolean) -> Unit,
    isBare: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val isEaten = Island.isMainOpen
    val width = animateFloatAsState(target.width, tween(Durations.GROW, easing = Curves.bubble), label = "width")
    val height = animateFloatAsState(target.height, tween(Durations.GROW, easing = Curves.bubble), label = "height")
    val left = animateFloatAsState(target.shift, tween(Durations.GROW, easing = Curves.split), label = "left")
    val top = animateFloatAsState(target.top, tween(Durations.GROW, easing = Curves.bubble), label = "top")
    val radius = animateFloatAsState(target.radius, tween(Durations.GROW, easing = FastOutSlowInEasing), label = "radius")
    val grown = animateFloatAsState(scale, tween(320, easing = Curves.split), label = "scale")

    if (!isBare) Blob(Island.liquid) {
        val density = Island.density
        val offset = drag()
        val wide = width.value * grown.value
        val tall = height.value * grown.value
        val x = left.value + (width.value - wide) / 2f + offset.x
        val y = top.value + (height.value - tall) / 2f + offset.y
        BlobShape(Rect(x * density, y * density, (x + wide) * density, (y + tall) * density), radius.value * grown.value * density, border)
    }

    val padLeft = target.shift - GRACE
    val padWidth = target.width + GRACE * 2f
    val padHeight = target.top + target.height + GRAB
    SideEffect { if (isEaten) Island.regions.remove(region) else Island.regions[region] = Rect(padLeft, 0f, padLeft + padWidth, padHeight) }

    Box(
        Modifier
            .offset { IntOffset((padLeft * Island.density).roundToInt(), 0) }
            .size(if (isEaten) 0.dp else padWidth.dp, if (isEaten) 0.dp else padHeight.dp)
            .pointerInput(region) { followTouch(padLeft, onDown, onMove, onUp) },
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .layout { measurable, _ ->
                    val density = Island.density
                    val placeable = measurable.measure(
                        Constraints.fixed((width.value * density).roundToInt().coerceAtLeast(1), (height.value * density).roundToInt().coerceAtLeast(1)),
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.place(((left.value - padLeft) * density).roundToInt(), (top.value * density).roundToInt())
                    }
                }
                .graphicsLayer {
                    val offset = drag()
                    scaleX = grown.value
                    scaleY = grown.value
                    transformOrigin = TransformOrigin.Center
                    translationX = offset.x * Island.density
                    translationY = offset.y * Island.density
                    clip = true
                    shape = RoundedCornerShape(radius.value.dp)
                },
            content = content,
        )
    }
}

private suspend fun PointerInputScope.followTouch(
    padLeft: Float,
    onDown: (Float, Float) -> Unit,
    onMove: (Float, Float) -> Unit,
    onUp: (Boolean) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val density = Island.density
    onDown(down.position.x / density + padLeft, down.position.y / density)
    var travel = 0f
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        travel = maxOf(travel, hypot(change.position.x - down.position.x, change.position.y - down.position.y) / density)
        if (!change.pressed) {
            onUp(!change.isConsumed && travel < TAP_SLOP && !Island.wasCancelled)
            break
        }
        onMove(change.position.x / density + padLeft, change.position.y / density)
    }
}

private const val GRACE = 10f
private const val TAP_SLOP = 12f

@Composable
fun ClockView(clock: ClockBubble) {
    val appearance = Island.appearance
    val target = clock.target()
    FloatingBubble(
        Region.Clock, target, Palette.border,
        if (clock.isPressing) 1.03f else 1f, { clock.drag },
        clock::onDown, clock::onMove, clock::onUp,
        isBare = isBarePill() && !clock.isNowStanding,
    ) {
        AnimatedContent(
            targetState = if (clock.isCornered) 0 else if (clock.isOpen) 2 else if (clock.isNowStanding) 1 else 0,
            transitionSpec = { (fadeIn(tween(200, 120)) + scaleIn(tween(Durations.POP_IN, 120, Curves.enter), 0.6f)).togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), 0.6f)) using SizeTransform(clip = false) { _, _ -> snap() } },
            contentAlignment = Alignment.Center,
            label = "clock",
            modifier = Modifier.fillMaxSize(),
        ) { face ->
            when (face) {
                2 -> NowPanel(clock, appearance)
                1 -> NowClosed(clock, appearance)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClockFace(clock, appearance)
                }
            }
        }
    }
}

/* The time and the corner were two faces cross-faded by AnimatedContent, so opening the Dashboard rebuilt the whole reading; the hours and minutes are the same at both sizes, and only the seconds change form and the date arrives, as handOverClock in js/clock.js did. */
@Composable
private fun ClockFace(clock: ClockBubble, appearance: Appearance) {
    val ink = Color.White
    val clockScale = appearance.clockScale * bareGrow()
    val isCornered = clock.isCornered
    val now = rememberNow(1000)
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
    fun pad(value: Int) = value.toString().padStart(2, '0')
    val seconds = pad(calendar.get(java.util.Calendar.SECOND))
    val time = "${calendar.get(java.util.Calendar.HOUR_OF_DAY)}:${pad(calendar.get(java.util.Calendar.MINUTE))}"
    /* The bubble slid under the player and shrank to nothing, so the Clock vanished while the player was open; it stays where it is and only its characters turn out, one by one, and back in when the player closes. */
    val isAway = Island.isMainOpen
    val corner = animateFloatAsState(if (isCornered) 1f else 0f, tween(Durations.GROW, easing = Curves.split), label = "clockCorner")
    val grow = 1f + (CORNER_GROW - 1f) * corner.value
    var secondsForm by remember { mutableStateOf(isCornered) }
    val secondsPop = remember { Animatable(1f) }
    LaunchedEffect(isCornered) {
        if (secondsForm == isCornered) return@LaunchedEffect
        secondsPop.animateTo(0f, tween(SECONDS_POP_OUT, easing = Curves.leave))
        secondsForm = isCornered
        secondsPop.animateTo(1f, tween(Durations.POP_IN, easing = Curves.enter))
    }
    val timeStyle = type(0.68f, ink, appearance.clockFont, clockScale, weight = FontWeight.Medium, lineHeight = 1.2f)
    val digit = type(SECONDS_SIZE, ink, appearance.clockFont, clockScale, weight = FontWeight.Medium, lineHeight = SECONDS_LINE)
    val dateStyle = type(0.62f * CORNER_GROW, ink, appearance.clockFont, clockScale, lineHeight = 1.2f)
    val date = dateText(appearance.dateFormat, calendar)
    val measurer = rememberTextMeasurer()
    fun wide(text: String, style: androidx.compose.ui.text.TextStyle) = measurer.measure(text, style).size.width / Island.density
    val timeWide = wide(time, timeStyle)
    val smallWide = SECONDS_GAP + maxOf(wide(seconds.substring(0, 1), digit), wide(seconds.substring(1, 2), digit))
    val bigWide = wide(":$seconds", timeStyle)
    val dateTall = measurer.measure(date, dateStyle).size.height / Island.density + DATE_TOP
    // Both widths were read off the laid-out content, which only switched form partway through the grow, so the pill retargeted mid-flight and jumped; they are measured up front from the text alone.
    SideEffect {
        clock.timeWidth = timeWide + smallWide + 22.4f
        clock.cornerWidth = maxOf(Dashboard.CORNER_WIDE_FLOOR, maxOf(timeWide + bigWide, minOf(DATE_WIDE, wide(date, dateStyle))) + CORNER_PADDING * 2f)
    }
    // The time sat in a column that only made room for the seconds and date once they arrived, so it jumped sideways on the swap and stayed low enough to cut the date; it is placed for the corner's final seconds and date from the start of the grow.
    Layout(
        {
            AwayText(
                time,
                /* The corner line height was read off the grow over CORNER_GROW - 1, which is 0/0 once the corner stopped growing the clock, and the NaN crashed every frame; it follows its own 0 to 1 progress. */
                type(0.68f * grow, ink, appearance.clockFont, clockScale, weight = FontWeight.Medium, lineHeight = 1.5f - 0.3f * corner.value),
                isAway,
                0,
            )
            Box(
                Modifier.graphicsLayer {
                    val pop = secondsPop.value
                    alpha = pop.coerceIn(0f, 1f)
                    scaleX = SECONDS_POP_FLOOR + (1f - SECONDS_POP_FLOOR) * pop
                    scaleY = scaleX
                    rotationZ = (1f - pop) * SECONDS_TURN
                },
            ) {
                if (secondsForm) {
                    PopText(":$seconds", type(0.68f * CORNER_GROW, ink, appearance.clockFont, clockScale, weight = FontWeight.Medium, lineHeight = 1.2f), 0)
                } else {
                    Column(Modifier.offset(x = SECONDS_GAP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(seconds.substring(0, 1), Modifier.popAway(isAway, time.length), style = digit)
                        BasicText(
                            seconds.substring(1, 2),
                            Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val pull = SECONDS_PULL.dp.roundToPx()
                                layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
                            }.popAway(isAway, time.length + 1),
                            style = digit,
                        )
                    }
                }
            }
            // A hidden AnimatedVisibility lays out no child at all, so the layout found two children instead of three and crashed; the date stands in a Box that is always there.
            Box {
                AnimatedVisibility(isCornered && secondsForm, enter = EnterTransition.None, exit = fadeOut(tween(DATE_FADE))) {
                    Box(Modifier.padding(top = DATE_TOP.dp).widthIn(max = DATE_WIDE.dp).clipToBounds().graphicsLayer { alpha = 0.75f }) {
                        PopText(date, dateStyle, 0, Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                    }
                }
            }
        },
        Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val loose = Constraints()
        val timePlaced = measurables[0].measure(loose)
        val secondsPlaced = measurables[1].measure(loose)
        val datePlaced = measurables[2].measure(loose)
        val progress = corner.value
        val wideNow = timePlaced.width + (smallWide + (bigWide - smallWide) * progress) * density
        val tallNow = timePlaced.height + dateTall * progress * density
        layout(constraints.maxWidth, constraints.maxHeight) {
            val left = ((constraints.maxWidth - wideNow) / 2f).roundToInt()
            val top = ((constraints.maxHeight - tallNow) / 2f).roundToInt()
            timePlaced.place(left, top)
            secondsPlaced.place(left + timePlaced.width, top + (timePlaced.height - secondsPlaced.height) / 2)
            datePlaced.place((constraints.maxWidth - datePlaced.width) / 2, top + timePlaced.height)
        }
    }
}

/* Mirrors the seconds' popOut and popIn in the retired js/clock.js; the turn is the small rotation the pop arrives with. */
private const val SECONDS_POP_OUT = 160
private const val SECONDS_POP_FLOOR = 0.6f
private const val SECONDS_TURN = -14f
private const val DATE_FADE = 140
private const val DATE_WIDE = 70f
private const val DATE_TOP = 2.4f
/* The stacked seconds were small with a loose gap between the digits; they are larger and set tighter, the stack standing as tall as it did. */
private const val SECONDS_SIZE = 0.42f
private const val SECONDS_LINE = 0.54f
/* The stacked seconds stood at 60% of their ink, so they read as a separate dimmer label; they take the same ink as the hours and minutes. */
private const val SECONDS_GAP = 2f
/* Shrinking the digits' line height did not close the step between them, since each one still stood as tall as its glyph; the lower digit is drawn up into the upper one's box. */
private const val SECONDS_PULL = 3f

/* Mirrors --corner-grow-scale and the corner's 0.7rem side padding in the retired pill.css. */
private const val CORNER_GROW = 1.0f
private const val CORNER_PADDING = 9f

@Composable
private fun NowClosed(clock: ClockBubble, appearance: Appearance) {
    val recording = Island.recording
    val now = rememberNow(1000)
    Row(
        Modifier.fillMaxSize().padding(horizontal = glyphInset(appearance).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(glyphSize(appearance).dp)) {
            drawCircle(Color.White.copy(alpha = if (recording?.isPaused == true) 0.55f else 1f), size.minDimension * 7f / 24f)
        }
        BasicText(clockReading(now - (recording?.since ?: now)), style = type(0.62f, Palette.textBright, appearance.clockFont, appearance.clockScale))
    }
}

@Composable
private fun NowPanel(clock: ClockBubble, appearance: Appearance) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RevealLabel(
            "RECORDING",
            type(0.63f, Palette.textLabel, appearance.clockFont, spacing = 0.19f),
            appearance.accent,
            modifier = Modifier.align(Alignment.Start),
        )
        val now = rememberNow(1000)
        BasicText(clockReading(now - (Island.recording?.since ?: now)), style = type(1.6f, Palette.textBright, appearance.clockFont, spacing = 0.08f))
        PillButton("Stop", true, Palette.danger, appearance) { clock.stopRecording() }
    }
}

@Composable
fun StatusView(status: StatusBubble) {
    val appearance = Island.appearance
    val isCornered = Island.dashboard.isOpen
    FloatingBubble(
        Region.Status, status.target(), Palette.border,
        if (status.isPressing) 1.03f else 1f, { status.drag },
        status::onDown, status::onMove, status::onUp,
        isBare = isBarePill(),
    ) {
        /* The settings and power glyphs were scaled in as one Row from its middle, so both grew out of the gap between them; each pops from its own centre now. */
        AnimatedContent(
            targetState = isCornered,
            transitionSpec = { (if (targetState) EnterTransition.None else fadeIn(tween(200, 120)) + scaleIn(tween(Durations.POP_IN, 120, Curves.enter), 0.6f)).togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), 0.6f)) using SizeTransform(clip = false) { _, _ -> snap() } },
            contentAlignment = Alignment.Center,
            label = "status",
            modifier = Modifier.fillMaxSize(),
        ) { cornered ->
            if (cornered) {
                StatusActions(Island.dashboard)
                return@AnimatedContent
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.wrapContentWidth(unbounded = true).onSizeChanged { status.contentWidth = it.width / Island.density },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    status.slots().forEachIndexed { index, slot ->
                        key(slot.name) {
                            Box(Modifier.padding(start = if (index == 0) 0.dp else if (slot is StatusSlot.Battery) 4.dp else 1.dp)) {
                                StatusSlotView(slot, appearance, Island.isMainOpen, index * 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSlotView(slot: StatusSlot, appearance: Appearance, isAway: Boolean, order: Int) {
    if (slot is StatusSlot.Reading) {
        // The reading was inked white regardless of the bar, so it vanished over a light page while the bodies were off.
        AwayText(slot.text, type(0.56f, Color.White, appearance.statusFont, appearance.statusScale * appearance.iconScale(slot.icon) * bareGrow(), weight = FontWeight.Bold), isAway, order)
        return
    }
    Box(Modifier.popAway(isAway, order)) { StatusSlotContent(slot, appearance) }
}

@Composable
private fun StatusSlotContent(slot: StatusSlot, appearance: Appearance) {
    val ink = Color.White
    val iconScale = appearance.iconScale(slot.icon)
    val glyph = (appearance.compactHeight - 13f) * bareGrow() * iconScale
    val modGlyph = (appearance.compactHeight - 8f) * bareGrow() * iconScale
    when (slot) {
        is StatusSlot.Mark -> GlyphIcon(slot.glyph, if (slot.tint == Color.White) ink else slot.tint, Modifier.size(glyph.dp))
        is StatusSlot.Reading -> BasicText(slot.text, style = type(0.56f, ink, appearance.statusFont, appearance.statusScale * iconScale * bareGrow(), weight = FontWeight.Bold))
        is StatusSlot.Paired -> Box(Modifier.size(modGlyph.dp), contentAlignment = Alignment.Center) {
            val colour = StatusBubble.levelColour(slot.charge)
            Canvas(Modifier.fillMaxSize()) {
                val width = 1.dp.toPx()
                val ring = Size(size.width - width, size.height - width)
                val corner = Offset(width / 2f, width / 2f)
                drawArc(colour.copy(alpha = 0.22f), 0f, 360f, false, corner, ring, style = Stroke(width))
                drawArc(colour, -90f, 360f * slot.charge.coerceIn(0, 100) / 100f, false, corner, ring, style = Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            GlyphIcon(Glyphs.bluetooth, ink, Modifier.size((modGlyph * 0.72f).dp))
        }
        is StatusSlot.Battery -> {
            val charge = when (slot.announcing) {
                BatteryEvent.Plugged -> slot.charge.copy(isPlugged = true)
                BatteryEvent.Low, BatteryEvent.Critical -> slot.charge.copy(isPlugged = false)
                null -> slot.charge
            }
            val colour = when {
                appearance.isBatteryWhite -> Color.White
                slot.announcing != null -> StatusBubble.ANNOUNCE_COLOURS.getValue(slot.announcing)
                else -> StatusBubble.chargeColour(charge)
            }
            MiniBattery(charge, if (isBarePill()) modGlyph * BARE_BATTERY else modGlyph, colour)
        }
    }
}

@Composable
private fun MiniBattery(charge: Charge, modGlyph: Float, target: Color) {
    val colour by androidx.compose.animation.animateColorAsState(target, tween(300), label = "batteryColour")
    val fill = animateFloatAsState(charge.percent / 100f, tween(500), label = "charge")
    val bolt = animateFloatAsState(if (charge.isPlugged) 1f else 0f, BOLT_POP, label = "bolt")
    Canvas(Modifier.size((modGlyph * 1.25f).dp, (modGlyph * 0.92f).dp).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val outline = 1.5.dp.toPx()
        val tall = size.height * 0.66f
        val capWide = 1.75.dp.toPx()
        val bodyWide = size.width - capWide - 1.dp.toPx()
        val top = (size.height - tall) / 2f
        val corner = tall * 0.32f
        drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(bodyWide, tall), CornerRadius(corner))
        drawRoundRect(Color.White, Offset(outline / 2f, top + outline / 2f), Size(bodyWide - outline, tall - outline), CornerRadius(corner - outline / 2f), style = Stroke(outline))
        drawRoundRect(Color.White, Offset(size.width - capWide, size.height / 2f - tall * 0.2f), Size(capWide, tall * 0.4f), CornerRadius(capWide / 2f))
        val inset = outline + 1.25.dp.toPx()
        val well = bodyWide - inset * 2f
        drawRoundRect(
            colour,
            Offset(inset, top + inset),
            Size((well * fill.value).coerceIn(well * 0.08f, well), tall - inset * 2f),
            CornerRadius((corner - inset).coerceAtLeast(1.dp.toPx())),
        )
        if (bolt.value <= 0.01f) return@Canvas
        val boltWide = tall * 0.78f
        val left = bodyWide / 2f - boltWide / 2f
        val path = Path().apply {
            moveTo(left + boltWide * 0.70f, 0f)
            lineTo(left + boltWide * 0.04f, size.height * 0.58f)
            lineTo(left + boltWide * 0.46f, size.height * 0.56f)
            lineTo(left + boltWide * 0.30f, size.height)
            lineTo(left + boltWide * 0.96f, size.height * 0.42f)
            lineTo(left + boltWide * 0.54f, size.height * 0.44f)
            close()
        }
        scale(bolt.value, Offset(bodyWide / 2f, size.height / 2f)) {
            drawPath(path, Color.Black, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Clear)
            drawPath(path, StatusBubble.CHARGING_COLOUR)
        }
    }
}

private fun isBarePill() = !Island.appearance.hasSidePills

private fun bareGrow() = if (isBarePill()) Island.appearance.bareScale else 1f

private const val BARE_BATTERY = 1.2f

val BOLT_POP = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.45f, stiffness = 420f)

@Composable
fun SatellitesView(bubble: MainBubble) {
    val appearance = Island.appearance
    val placements = bubble.satellites()
    val centre = Island.screenWidth / 2f
    placements.forEach { placement ->
        key(placement.mod) {
            val follow = if (bubble.isDragging) androidx.compose.animation.core.snap<Float>() else tween(380, easing = Curves.split)
            val x = animateFloatAsState(placement.x, follow, label = "satellite")
            val width = animateFloatAsState(placement.width, follow, label = "satelliteWidth")
            val born = remember(placement.mod) { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(placement.mod) { born.animateTo(1f, tween(380, easing = Curves.split)) }
            val height = appearance.compactHeight * MainBubble.SATELLITE_SHARE
            val lift = appearance.grab + (appearance.compactHeight - height) / 2f
            Blob(Island.liquid) {
                val density = Island.density
                val at = placement.birth + (x.value - placement.birth) * born.value
                val wide = width.value
                val left = centre + at - wide / 2f
                val top = lift
                BlobShape(
                    Rect(left * density, top * density, (left + wide) * density, (top + height) * density),
                    height / 2f * density,
                    Color.Transparent,
                    placement.opacity,
                )
            }
            Box(
                Modifier
                    .offset { IntOffset(((centre + placement.birth + (x.value - placement.birth) * born.value - width.value / 2f) * Island.density).roundToInt(), (lift * Island.density).roundToInt()) }
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed((width.value * Island.density).roundToInt().coerceAtLeast(1), (height * Island.density).roundToInt()))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = placement.opacity * born.value }
                    .clip(CircleShape),
            ) {
                SatelliteFace(placement.mod, appearance, height)
            }
        }
    }
    bubble.dots().forEach { (mod, x) ->
        key("dot", mod) {
            val at = animateFloatAsState(x, tween(380, easing = Curves.split), label = "dot")
            Box(
                Modifier
                    .offset { IntOffset(((centre + at.value - MainBubble.DOT_SIZE / 2f) * Island.density).roundToInt(), ((appearance.grab + appearance.compactHeight / 2f - MainBubble.DOT_SIZE / 2f) * Island.density).roundToInt()) }
                    .size(MainBubble.DOT_SIZE.dp)
                    .background(bubble.accentOf(mod), CircleShape),
            )
        }
    }
}

/* The clear button hung under the menu and the system notices sat in glass at the foot, so both moved with every refit and stood apart from the entry cards; the button stands in the bottom right corner and the notices are cards like the entries. */
@Composable
fun ClearButton(bubble: MainBubble) {
    val isShown = bubble.size == com.v.island.bubble.Size.Notifications && bubble.groups.isNotEmpty()
    val top = bubble.clearTop()
    val left = Island.screenWidth / 2f + MainBubble.GROWN_WIDTH / 2f - MainBubble.CLEAR_SIZE - CORNER_INSET
    val grown = animateFloatAsState(if (isShown) 1f else 0f, if (isShown) spring(dampingRatio = CLEAR_DAMPING, stiffness = CLEAR_STIFFNESS) else tween(180, easing = Curves.leave), label = "clearGrow")
    if (grown.value <= 0.01f && !isShown) return
    val countLeft = left + (MainBubble.CLEAR_SIZE - COUNT_SIZE) / 2f
    val countTop = top - COUNT_GAP - COUNT_SIZE
    Box(
        Modifier
            .offset { IntOffset((countLeft * Island.density).roundToInt(), (countTop * Island.density).roundToInt()) }
            .size(COUNT_SIZE.dp)
            .graphicsLayer {
                scaleX = grown.value
                scaleY = grown.value
                alpha = grown.value.coerceIn(0f, 1f)
            }
            .background(Island.appearance.pillFill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val total = bubble.groups.sumOf { it.entries.size }
        if (total > 0) BasicText(total.toString(), style = type(0.62f, Palette.textBright, Island.appearance.headingFont, Island.appearance.headingScale))
    }
    SideEffect {
        if (isShown) Island.regions[Region.Clear] = Rect(left, top, left + MainBubble.CLEAR_SIZE, top + MainBubble.CLEAR_SIZE)
        else Island.regions.remove(Region.Clear)
    }
    Box(
        Modifier
            .offset { IntOffset((left * Island.density).roundToInt(), (top * Island.density).roundToInt()) }
            .size(MainBubble.CLEAR_SIZE.dp)
            .graphicsLayer {
                scaleX = grown.value
                scaleY = grown.value
                alpha = grown.value.coerceIn(0f, 1f)
            }
            .then(if (isShown) Modifier.pressableClear(bubble) else Modifier)
            .background(Island.appearance.pillFill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(TRASH, Palette.danger, Modifier.size(20.dp))
    }
}

@Composable
fun SystemPill(bubble: MainBubble) {
    val isShown = bubble.size == com.v.island.bubble.Size.Notifications && bubble.systemNotices.isNotEmpty()
    val height = bubble.systemHeight()
    val restTop = bubble.systemTop()
    /* A notch bitten around the count and clear bubbles read as broken glass; the count stands on the clear button and every notice stops a gap short of that column. */
    val trim = if (bubble.groups.isNotEmpty()) MainBubble.CLEAR_SIZE + CORNER_INSET + COUNT_GAP else 12f
    val settled = animateFloatAsState(restTop, tween(MainBubble.CLEAR_TOP_MILLISECONDS, easing = Curves.grow), label = "systemTop")
    val left = Island.screenWidth / 2f - MainBubble.GROWN_WIDTH / 2f
    val content = animateFloatAsState(if (isShown) 1f else 0f, if (isShown) tween(240, 60, Curves.split) else tween(160, easing = Curves.leave), label = "systemContent")
    if (content.value <= 0.01f && !isShown) return
    SideEffect {
        if (isShown) Island.regions[Region.System] = Rect(left, restTop, left + MainBubble.GROWN_WIDTH - trim + 12f, restTop + height)
        else Island.regions.remove(Region.System)
    }
    Column(
        Modifier
            .offset { IntOffset((left * Island.density).roundToInt(), (settled.value * Island.density).roundToInt()) }
            .size(MainBubble.GROWN_WIDTH.dp, height.dp)
            .padding(start = 12.dp, end = trim.dp, top = MainBubble.SYSTEM_PAD.dp, bottom = MainBubble.SYSTEM_PAD.dp),
        verticalArrangement = Arrangement.spacedBy(SYSTEM_SEAM.dp),
    ) {
        val notices = bubble.systemNotices
        notices.forEachIndexed { index, entry ->
            key(entry.key) {
                val tint = systemTint(entry)
                val drop = remember { Animatable(0f) }
                val widen = remember { Animatable(0f) }
                val reveal = remember { Animatable(0f) }
                val leave = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(SYSTEM_DROP_AFTER + index * SYSTEM_STAGGER)
                    /* The pop to full width ran on its own clock, so it fired mid-fall and the drop's bounce and the widen's bounce landed apart; the widen and the title wait for the silhouette to reach its place. */
                    launch {
                        androidx.compose.runtime.snapshotFlow { drop.value }.first { it >= SYSTEM_LANDED }
                        launch { widen.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = SYSTEM_WIDEN_DAMPING, stiffness = SYSTEM_WIDEN_STIFFNESS)) }
                        kotlinx.coroutines.delay(SYSTEM_REVEAL_AFTER_LANDING)
                        reveal.animateTo(1f, tween(SYSTEM_REVEAL, easing = Curves.powerTwoOut))
                    }
                    drop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = SYSTEM_DROP_DAMPING, stiffness = SYSTEM_DROP_STIFFNESS))
                }
                LaunchedEffect(isShown) { if (!isShown) leave.animateTo(1f, tween(SYSTEM_LEAVE, easing = Curves.leave)) }
                androidx.compose.runtime.DisposableEffect(entry.key) {
                    onDispose {
                        bubble.systemBounds.remove(entry.key)
                        Island.glass.remove(entry.key)
                    }
                }
                SideEffect {
                    Island.glass[entry.key] = glass@{
                        if (!isShown || reveal.value < 1f) return@glass null
                        val card = bubble.systemBounds[entry.key] ?: return@glass null
                        val density = Island.density
                        BlobShape(Rect(card.left * density, card.top * density, card.right * density, card.bottom * density), SYSTEM_OUTER * density, Color.Transparent, 1f - leave.value)
                    }
                }
                val isRevealed by remember { androidx.compose.runtime.derivedStateOf { reveal.value >= 1f } }
                if (isShown && !isRevealed) Blob(Island.liquid) {
                    val card = bubble.systemBounds[entry.key] ?: return@Blob null
                    val pill = bubble.target()
                    val travel = drop.value
                    val wide = SYSTEM_DROP_WIDTH + (card.width - SYSTEM_DROP_WIDTH) * systemWidth(drop.value, widen.value)
                    val tall = SYSTEM_DROP_HEIGHT + (card.height - SYSTEM_DROP_HEIGHT) * travel
                    val x = Island.screenWidth / 2f + pill.shift + (card.center.x - Island.screenWidth / 2f - pill.shift) * travel
                    val y = pill.top + pill.height + (card.center.y - pill.top - pill.height) * travel
                    val density = Island.density
                    BlobShape(Rect((x - wide / 2f) * density, (y - tall / 2f) * density, (x + wide / 2f) * density, (y + tall / 2f) * density), minOf(tall / 2f, SYSTEM_OUTER) * density, bubble.border())
                }
                Row(
                    Modifier.fillMaxWidth().height(MainBubble.SYSTEM_ROW.dp - SYSTEM_SEAM.dp)
                        .onGloballyPositioned { at ->
                            val bounds = at.boundsInRoot()
                            bubble.systemBounds[entry.key] = Rect(bounds.left / Island.density, bounds.top / Island.density, bounds.right / Island.density, bounds.bottom / Island.density)
                        }
                        .graphicsLayer {
                            val card = bubble.systemBounds[entry.key] ?: return@graphicsLayer
                            val pill = bubble.target()
                            val behind = 1f - drop.value
                            translationX = (Island.screenWidth / 2f + pill.shift - card.center.x) * behind * density
                            translationY = (pill.top + pill.height - card.center.y) * behind * density
                            scaleX = (SYSTEM_DROP_WIDTH + (card.width - SYSTEM_DROP_WIDTH) * systemWidth(drop.value, widen.value)) / card.width
                            scaleY = (SYSTEM_DROP_HEIGHT + (card.height - SYSTEM_DROP_HEIGHT) * drop.value) / card.height
                        }
                        .then(if (isShown) Modifier.pressable(pressedScale = 1.02f, haptic = null) { bubble.openSystem(entry) } else Modifier)
                        .clip(RoundedCornerShape(SYSTEM_OUTER.dp))
                        .background(if (isShown && isRevealed) Island.appearance.pillFill else Color.Transparent)
                        .padding(start = SYSTEM_ICON_INSET.dp, end = 12.dp)
                        .graphicsLayer {
                            val hazy = maxOf(1f - reveal.value, leave.value)
                            alpha = reveal.value * (1f - leave.value)
                            scaleX = 1f - SYSTEM_LEAVE_SHRINK * leave.value
                            scaleY = scaleX
                            translationY = -SYSTEM_LEAVE_RISE * leave.value * density
                            renderEffect = if (hazy > 0.01f) androidx.compose.ui.graphics.BlurEffect(hazy * SYSTEM_BLUR * density, hazy * SYSTEM_BLUR * density, androidx.compose.ui.graphics.TileMode.Decal) else null
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(Modifier.size(26.dp).background(tint.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                        val badge = entry.icon ?: entry.appIcon
                        if (badge != null) androidx.compose.foundation.Image(badge, null, Modifier.size(15.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
                    }
                    ScrambleText(entry.title.ifEmpty { entry.appName }, type(0.72f, Palette.textBright, Island.appearance.headingFont, Island.appearance.headingScale), (SYSTEM_DROP_AFTER + index * SYSTEM_STAGGER + SYSTEM_REVEAL_AT).toInt(), Modifier.weight(1f))
                }
            }
        }
    }
}

private const val CORNER_INSET = 12f
private const val CLEAR_DAMPING = 0.55f
private const val CLEAR_STIFFNESS = 420f
/* The system notices faded up in place as one block while the cards above dropped out of Main, and each carried a second line nobody read; each notice drops out of Main's bottom edge as liquid after the cards, its title blurs in over it, it leaves and flies home like a card, and it shows its title alone. The long drop bounced hard on the cards' spring and the title sat at the resting place while the silhouette was still falling; the drop is near critically damped and the notice rides the silhouette, its title scrambling in, its icon nearer the edge and no tint dot. */
private const val SYSTEM_DROP_AFTER = 120L
private const val SYSTEM_STAGGER = 30L
private const val SYSTEM_DROP_DAMPING = 0.74f
private const val SYSTEM_DROP_STIFFNESS = 620f
private const val SYSTEM_LANDED = 0.92f
private const val SYSTEM_REVEAL_AFTER_LANDING = 40L
private const val SYSTEM_WIDEN_DAMPING = 0.6f
private const val SYSTEM_WIDEN_STIFFNESS = 800f
private const val SYSTEM_FALL_WIDEN = 0.2f

private fun systemWidth(drop: Float, widen: Float): Float {
    val falling = SYSTEM_FALL_WIDEN * drop.coerceIn(0f, 1f)
    return falling + (1f - falling) * widen
}
private const val SYSTEM_ICON_INSET = 5f
/* The notice rode its silhouette at full width, so once its title blurred in it stood wider than the narrowing drop under it; it is scaled to the silhouette's size for the whole drop. */
private const val SYSTEM_DROP_WIDTH = 36f
private const val SYSTEM_DROP_HEIGHT = 18f
private const val SYSTEM_REVEAL_AT = 260L
private const val SYSTEM_REVEAL = 140
private const val SYSTEM_BLUR = 10f
private const val SYSTEM_LEAVE = 110
private const val SYSTEM_LEAVE_SHRINK = 0.08f
private const val SYSTEM_LEAVE_RISE = 10f
/* The notices stood 2.4dp apart with inner corners squared off, so the stack read as one joined slab; each is its own capsule with a clear gap. */
private const val SYSTEM_SEAM = 6f
private const val SYSTEM_OUTER = 18f

private fun systemTint(entry: Notice): Color {
    val words = "${entry.packageName} ${entry.title} ${entry.text}".lowercase()
    return SYSTEM_TINTS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(words) }?.second ?: SYSTEM_TINT_PLAIN
}

private val SYSTEM_TINTS = listOf(
    Regex("warn|fehler|error|sicherheit|security|überhitz|overheat") to Color(0xFFFF3B30),
    Regex("lädt|laden|charg|akku|battery|batterie") to Color(0xFF5BFD5B),
    Regex("usb|mtp|datei.?übertrag|file transfer") to Color(0xFF3A8CFF),
    Regex("download|herunterl|upload|hochlad") to Color(0xFFF09B3A),
    Regex("screenshot|bildschirmfoto|aufnahme|record") to Color(0xFFB28CFF),
    Regex("update|aktualisier") to Color(0xFF4FC3F7),
    Regex("wlan|wi-fi|wifi|bluetooth|hotspot|netzwerk|network|vpn") to Color(0xFF40C4FF),
    Regex("alarm|wecker|timer|stoppuhr|stopwatch") to Color(0xFFFFB300),
)
private val SYSTEM_TINT_PLAIN = Color(0xFFB8B8C4)

/* A tap beside the open menu went through to the app underneath and closed the menu as well, and a tap beside the open player reached nothing at all and left it open; while either is open the rest of the screen answers only by closing it. */
@Composable
fun PanelShield(bubble: MainBubble) {
    val isShown = bubble.size == com.v.island.bubble.Size.Notifications || bubble.size == com.v.island.bubble.Size.Player
    SideEffect {
        if (isShown) Island.regions[Region.Shield] = Rect(0f, 0f, Island.screenWidth, Island.screenHeight)
        else Island.regions.remove(Region.Shield)
    }
    if (!isShown) return
    Box(
        Modifier.fillMaxSize().pointerInput(bubble) {
            awaitEachGesture {
                val probe = awaitFirstDown(requireUnconsumed = false)
                android.util.Log.d("IslandBubble", "probe shield x=${probe.position.x / Island.density} y=${probe.position.y / Island.density}")
                probe.consume()
                val fromX = probe.position.x / Island.density
                val fromY = probe.position.y / Island.density
                if (bubble.pullsNotifications(fromX, fromY)) {
                    var hasDropped = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == probe.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val down = (change.position.y / Island.density - fromY) * if (bubble.isNotificationsPulled) -1f else 1f
                        if (!hasDropped && down > MainBubble.PULL_TRIGGER && down > abs(change.position.x / Island.density - fromX)) {
                            hasDropped = true
                            bubble.dropNotifications()
                        }
                    }
                    if (!hasDropped) bubble.onOutsideTap()
                    return@awaitEachGesture
                }
                /* The shield closed the open panel only once the finger lifted, so a tap outside felt late; it closes on the touch and swallows the rest of the gesture. */
                bubble.onOutsideTap()
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        },
    )
}

private fun Modifier.pressableClear(bubble: MainBubble) = pressable(pressedScale = 1.03f, haptic = null) { bubble.clearAll() }

private const val COUNT_SIZE = 34f
private const val COUNT_GAP = 6f

val TRASH = Glyph(0f, 0f, 24f, 24f, listOf(GlyphLayer("M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5", LayerKind.Stroke, 1.6f)))
