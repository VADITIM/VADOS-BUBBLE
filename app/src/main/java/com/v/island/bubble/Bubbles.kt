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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Palette
import com.v.island.vas.pressable
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
    content: @Composable BoxScope.() -> Unit,
) {
    val isEaten = Island.isPlayerOpen
    val width = animateFloatAsState(target.width, tween(Durations.GROW, easing = Curves.bubble), label = "width")
    val height = animateFloatAsState(target.height, tween(Durations.GROW, easing = Curves.bubble), label = "height")
    val left = animateFloatAsState(target.shift, tween(Durations.GROW, easing = Curves.split), label = "left")
    val top = animateFloatAsState(target.top, tween(Durations.GROW, easing = Curves.bubble), label = "top")
    val radius = animateFloatAsState(target.radius, tween(Durations.GROW, easing = FastOutSlowInEasing), label = "radius")
    val grown = animateFloatAsState(scale, tween(320, easing = Curves.split), label = "scale")

    Blob(Island.liquid) {
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
    val isCornered = clock.isCornered
    val now = rememberNow(1000)
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
    fun pad(value: Int) = value.toString().padStart(2, '0')
    val seconds = pad(calendar.get(java.util.Calendar.SECOND))
    val time = "${calendar.get(java.util.Calendar.HOUR_OF_DAY)}:${pad(calendar.get(java.util.Calendar.MINUTE))}"
    /* The bubble slid under the player and shrank to nothing, so the Clock vanished while the player was open; it stays where it is and only its characters turn out, one by one, and back in when the player closes. */
    val isAway = Island.isPlayerOpen
    val grow = animateFloatAsState(if (isCornered) CORNER_GROW else 1f, tween(Durations.GROW, easing = Curves.split), label = "clockGrow")
    var secondsForm by remember { mutableStateOf(isCornered) }
    val secondsPop = remember { Animatable(1f) }
    LaunchedEffect(isCornered) {
        if (secondsForm == isCornered) return@LaunchedEffect
        secondsPop.animateTo(0f, tween(SECONDS_POP_OUT, easing = Curves.leave))
        secondsForm = isCornered
        if (isCornered) secondsPop.snapTo(1f) else secondsPop.animateTo(1f, tween(Durations.POP_IN, easing = Curves.enter))
    }
    Column(
        Modifier.wrapContentWidth(unbounded = true).onSizeChanged {
            val measured = it.width / Island.density
            if (isCornered) clock.cornerWidth = maxOf(Dashboard.CORNER_WIDE_FLOOR, measured + CORNER_PADDING * 2f)
            else clock.timeWidth = measured + 22.4f
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AwayText(
                time,
                type(0.68f * grow.value, Color.White, appearance.clockFont, appearance.clockScale, weight = FontWeight.Medium, lineHeight = 1.5f - 0.3f * (grow.value - 1f) / (CORNER_GROW - 1f)),
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
                    PopText(":$seconds", type(0.68f * CORNER_GROW, Color.White, appearance.clockFont, appearance.clockScale, weight = FontWeight.Medium, lineHeight = 1.2f), 0)
                } else {
                    Column(Modifier.padding(start = 2.dp).graphicsLayer { alpha = 0.6f }, horizontalAlignment = Alignment.CenterHorizontally) {
                        val digit = type(0.29f, Color.White, appearance.clockFont, appearance.clockScale, weight = FontWeight.Medium, lineHeight = 0.95f)
                        BasicText(seconds.substring(0, 1), Modifier.popAway(isAway, time.length), style = digit)
                        BasicText(seconds.substring(1, 2), Modifier.popAway(isAway, time.length + 1), style = digit)
                    }
                }
            }
        }
        AnimatedVisibility(isCornered, enter = EnterTransition.None, exit = fadeOut(tween(DATE_FADE))) {
            Box(Modifier.padding(top = 2.4.dp).widthIn(max = 70.dp).clipToBounds().graphicsLayer { alpha = 0.75f }) {
                PopText(
                    dateText(appearance.dateFormat, calendar),
                    type(0.62f * CORNER_GROW, Color.White, appearance.clockFont, appearance.clockScale, lineHeight = 1.2f),
                    Durations.POP_IN / 4,
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                )
            }
        }
    }
}

/* Mirrors the seconds' popOut and popIn in the retired js/clock.js; the turn is the small rotation the pop arrives with. */
private const val SECONDS_POP_OUT = 160
private const val SECONDS_POP_FLOOR = 0.6f
private const val SECONDS_TURN = -14f
private const val DATE_FADE = 140

/* Mirrors --corner-grow-scale and the corner's 0.7rem side padding in the retired pill.css. */
private const val CORNER_GROW = 1.15f
private const val CORNER_PADDING = 11.2f

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
                            Box(Modifier.padding(start = if (index == 0) 0.dp else if (slot is StatusSlot.Battery) 5.4.dp else 1.9.dp)) {
                                StatusSlotView(slot, appearance, Island.isPlayerOpen, index * 2)
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
        AwayText(slot.text, type(0.56f, Color.White, appearance.statusFont, appearance.statusScale, weight = FontWeight.Bold), isAway, order)
        return
    }
    Box(Modifier.popAway(isAway, order)) { StatusSlotContent(slot, appearance) }
}

@Composable
private fun StatusSlotContent(slot: StatusSlot, appearance: Appearance) {
    val glyph = appearance.compactHeight - 17f
    val modGlyph = appearance.compactHeight - 10f
    when (slot) {
        is StatusSlot.Mark -> GlyphIcon(slot.glyph, slot.tint, Modifier.size(glyph.dp))
        is StatusSlot.Reading -> BasicText(slot.text, style = type(0.56f, Color.White, appearance.statusFont, appearance.statusScale, weight = FontWeight.Bold))
        is StatusSlot.Paired -> Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(slot.charge.toString(), style = type(0.5f, if (slot.charge <= 19) Palette.danger else Color.White, appearance.statusFont, appearance.statusScale))
            GlyphIcon(Glyphs.bluetooth, Color.White, Modifier.size(glyph.dp))
        }
        is StatusSlot.Announce -> GlyphIcon(
            when (slot.event) {
                BatteryEvent.Plugged -> Glyphs.batteryCharging
                BatteryEvent.Low -> Glyphs.batteryMark("M7.5 10V14" + Glyphs.BATTERY_CELL)
                BatteryEvent.Critical -> Glyphs.batteryMark(Glyphs.BATTERY_CRITICAL)
            },
            StatusBubble.ANNOUNCE_COLOURS.getValue(slot.event),
            Modifier.size(modGlyph.dp),
        )
        is StatusSlot.Battery -> MiniBattery(slot.charge, modGlyph, appearance)
    }
}

@Composable
private fun MiniBattery(charge: Charge, modGlyph: Float, appearance: Appearance) {
    val colour = StatusBubble.chargeColour(charge)
    val fill = animateFloatAsState(charge.percent / 100f, tween(500), label = "charge")
    val isBare = !appearance.showBatteryPercent
    val tall = modGlyph * if (isBare) 0.67f else 0.8f
    val wide = modGlyph * if (isBare) 1.1f else 1.3f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(wide.dp, tall.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val outline = 1.5.dp.toPx()
                val corner = modGlyph * 0.17f * density
                drawRoundRect(Color(0xD92A2832), cornerRadius = CornerRadius(corner))
                val inner = outline + 1.dp.toPx()
                val well = size.width - outline * 2f
                drawRoundRect(
                    colour,
                    topLeft = Offset(inner, inner),
                    size = Size(minOf(well * fill.value, well - 2.dp.toPx()), size.height - inner * 2f),
                    cornerRadius = CornerRadius((corner - 2.5f * density).coerceAtLeast(density)),
                )
                drawRoundRect(colour, topLeft = Offset(outline / 2f, outline / 2f), size = Size(size.width - outline, size.height - outline), cornerRadius = CornerRadius(corner - outline / 2f), style = Stroke(outline))
            }
            if (!isBare) BasicText("${charge.percent}%", style = type(0.6f, Color.Black, appearance.statusFont, appearance.statusScale, weight = FontWeight.Bold, lineHeight = 1f))
        }
        Box(Modifier.padding(start = 1.dp).size(1.5.dp, (tall * 0.34f).dp).background(colour, RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp)))
    }
}

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
            val height = appearance.compactHeight
            val accent = bubble.accentOf(placement.mod)
            val isLive = when (placement.mod) {
                Mod.Media -> Island.media?.isPlaying == true
                Mod.Timer -> Island.timer?.endsAt != null
                else -> true
            }
            Blob(Island.liquid) {
                val density = Island.density
                val at = placement.birth + (x.value - placement.birth) * born.value
                val wide = width.value
                val left = centre + at - wide / 2f
                val top = appearance.grab
                BlobShape(
                    Rect(left * density, top * density, (left + wide) * density, (top + height) * density),
                    height / 2f * density,
                    if (isLive) accent.copy(alpha = 0.6f) else Palette.border,
                    placement.opacity,
                )
            }
            Box(
                Modifier
                    .offset { IntOffset(((centre + placement.birth + (x.value - placement.birth) * born.value - width.value / 2f) * Island.density).roundToInt(), (appearance.grab * Island.density).roundToInt()) }
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

@Composable
fun ClearButton(bubble: MainBubble) {
    val isShown = bubble.size == com.v.island.bubble.Size.Notifications && bubble.groups.isNotEmpty()
    val box = bubble.target()
    val restTop = box.top + box.height + MainBubble.CLEAR_GAP
    val settled = animateFloatAsState(restTop, tween(MainBubble.CLEAR_TOP_MILLISECONDS, easing = Curves.grow), label = "clearTop")
    val left = Island.screenWidth / 2f - MainBubble.CLEAR_SIZE / 2f
    /* Mirrors #notifications-clear in pill.css: it slides out from under the menu and grows from its top edge after the menu has opened, and goes back up under it on the way home. */
    val drop = animateFloatAsState(if (isShown) 1f else 0f, if (isShown) tween(380, 220, Curves.split) else tween(220, easing = Curves.grow), label = "clearDrop")
    val grown = animateFloatAsState(if (isShown) 1f else 0f, if (isShown) tween(320, 220, Curves.split) else tween(220, easing = Curves.grow), label = "clearGrow")
    val glyph = animateFloatAsState(if (isShown) 1f else 0f, if (isShown) tween(0, 380) else tween(120), label = "clearGlyph")
    if (grown.value <= 0.01f && !isShown) return
    fun top() = settled.value - (1f - drop.value) * (MainBubble.CLEAR_SIZE + MainBubble.CLEAR_GAP)
    val countLeft = left + MainBubble.CLEAR_SIZE + COUNT_GAP
    fun countTop() = top() + (MainBubble.CLEAR_SIZE - COUNT_SIZE) / 2f
    Blob(Island.liquid) {
        val density = Island.density
        val diameter = MainBubble.CLEAR_SIZE * grown.value
        val x = left + (MainBubble.CLEAR_SIZE - diameter) / 2f
        val y = top()
        BlobShape(Rect(x * density, y * density, (x + diameter) * density, (y + diameter) * density), diameter / 2f * density, Palette.border)
    }
    Blob(Island.liquid) {
        val density = Island.density
        val diameter = COUNT_SIZE * grown.value
        val x = countLeft + (COUNT_SIZE - diameter) / 2f
        val y = countTop()
        BlobShape(Rect(x * density, y * density, (x + diameter) * density, (y + diameter) * density), diameter / 2f * density, Palette.border)
    }
    Box(
        Modifier
            .offset { IntOffset((countLeft * Island.density).roundToInt(), (countTop() * Island.density).roundToInt()) }
            .size(COUNT_SIZE.dp)
            .graphicsLayer {
                scaleX = grown.value
                scaleY = grown.value
                alpha = glyph.value
            },
        contentAlignment = Alignment.Center,
    ) {
        val total = bubble.groups.sumOf { it.entries.size }
        if (total > 0) BasicText(total.toString(), style = type(0.62f, Palette.textBright, Island.appearance.headingFont, Island.appearance.headingScale))
    }
    SideEffect {
        if (isShown) Island.regions[Region.Clear] = Rect(left, restTop, left + MainBubble.CLEAR_SIZE, restTop + MainBubble.CLEAR_SIZE)
        else Island.regions.remove(Region.Clear)
    }
    Box(
        Modifier
            .offset { IntOffset((left * Island.density).roundToInt(), (top() * Island.density).roundToInt()) }
            .size(MainBubble.CLEAR_SIZE.dp)
            .graphicsLayer {
                scaleX = grown.value
                scaleY = grown.value
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .then(if (isShown) Modifier.pressableClear(bubble) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(TRASH, Palette.textBright, Modifier.size(20.dp).graphicsLayer { alpha = glyph.value })
    }
}

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
                awaitFirstDown(requireUnconsumed = false).consume()
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

private val TRASH = Glyph(0f, 0f, 24f, 24f, listOf(GlyphLayer("M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5", LayerKind.Stroke, 1.6f)))
