package com.v.island.bubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import com.v.island.vas.Palette
import com.v.island.vas.pressable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

private val KEYWORDS = listOf(
    "reel", "reels", "story", "stories", "post", "live", "photo", "video", "gif", "sticker", "voice message", "audio",
    "call", "missed call", "attachment", "document", "location", "poll", "event", "invite", "mention", "reply", "reaction",
    "foto", "sprachnachricht", "anruf", "umfrage", "einladung",
)
private val LINK_PATTERN = Regex("""\bhttps?://\S+|\bwww\.\S+""", RegexOption.IGNORE_CASE)
private val TIME_PATTERN = Regex(
    listOf(
        """\d{1,2}:\d{2}(?::\d{2})?""",
        """\d{1,2}\.\d{1,2}\.(?:\d{2,4})?""",
        """\d{4}-\d{2}-\d{2}""",
        """\d{1,2}\.?\s?(?:Jan|Feb|Mär|Mar|Apr|Mai|May|Jun|Jul|Aug|Sep|Okt|Oct|Nov|Dez|Dec)[a-zä]*\.?""",
    ).joinToString("|"),
    RegexOption.IGNORE_CASE,
)
private val MONEY_PATTERN = Regex("""[€$£¥]\s?\d[\d.,]*|\d[\d.,]*\s?(?:[€$£¥]|(?:EUR|USD|GBP|CHF)\b)""", RegexOption.IGNORE_CASE)
private val KEYWORD_PATTERN = Regex("""\b(?:${KEYWORDS.joinToString("|") { Regex.escape(it) }})\b""", RegexOption.IGNORE_CASE)

fun markUp(message: String, accent: Color): AnnotatedString {
    val marks = sortedMapOf<Int, Pair<Int, SpanStyle>>()
    fun claim(pattern: Regex, style: SpanStyle) = pattern.findAll(message).forEach { found ->
        val isFree = marks.none { (start, mark) -> found.range.first < mark.first && found.range.last + 1 > start }
        if (isFree) marks[found.range.first] = found.range.last + 1 to style
    }
    claim(LINK_PATTERN, SpanStyle(color = Palette.link))
    claim(TIME_PATTERN, SpanStyle(background = Palette.markTime))
    claim(MONEY_PATTERN, SpanStyle(background = Palette.markMoney))
    claim(KEYWORD_PATTERN, SpanStyle(color = accent))
    return buildAnnotatedString {
        append(message)
        marks.forEach { (start, mark) -> addStyle(mark.second, start, mark.first) }
    }
}

@Composable
fun AlertFace(bubble: MainBubble, appearance: Appearance) {
    val shown = bubble.notice ?: return
    val isPicture = bubble.size == Size.Picture
    val isCentred = bubble.size == Size.AlertCentre
    val isLarge = isPicture
    /* The card is sized off what its content asks for, as measureAlert did, so the content is measured free of the box it stands in; only the picture card keeps a box of its own. */
    val isMeasured = bubble.size == Size.Alert || isCentred
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            (if (isMeasured) Modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically, unbounded = true).onSizeChanged { bubble.reportAlertHeight(it.height / Island.density) } else Modifier.fillMaxSize())
                .padding(horizontal = if (isLarge) 17.6.dp else 15.2.dp, vertical = if (isLarge) 14.4.dp else 9.9.dp),
            verticalArrangement = if (isPicture) Arrangement.spacedBy(9.6.dp) else Arrangement.spacedBy(6.4.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MainBubble.HOLE_GAP.dp)) {
                Row(Modifier.padding(horizontal = 7.2.dp, vertical = 2.4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = shown.icon
                    if (icon != null && appearance.identity != 1) {
                        Image(icon, null, Modifier.size(if (isLarge) 24.dp else 18.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.size(7.2.dp))
                    }
                    if (appearance.identity != 0) {
                        FadingLine(
                            shown.appName.uppercase(),
                            type(0.63f, shown.accent, appearance.headingFont, appearance.headingScale, spacing = 0.19f),
                        )
                    }
                }
                /* Mirrors #title in pill.css: the sender stands at the right end and only fades from the left once it runs out of room. */
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    FadingLine(
                        bubble.whoSent(shown),
                        type(0.68f, Palette.textMuted, appearance.headingFont, appearance.headingScale, spacing = 0.04f),
                    )
                }
            }
            val isStacked = bubble.alertLines.size > 1
            Box(
                if (isStacked && !isCentred) Modifier.heightIn(max = (Island.screenHeight * MainBubble.CENTRE_CEILING - ALERT_HEAD_ROOM).dp).clipToBounds().fadeTop() else Modifier,
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true)) {
                    bubble.alertLines.forEach { line ->
                        BasicText(
                            markUp(line, shown.accent),
                            style = type(0.78f, Palette.textPrimary, appearance.contentFont, appearance.contentScale, lineHeight = 1.4f),
                            maxLines = if (isCentred) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            val picture = shown.picture
            if (picture != null) {
                Image(
                    picture,
                    null,
                    (if (isCentred) Modifier.fillMaxWidth().aspectRatio(picture.width.toFloat() / maxOf(1, picture.height)) else Modifier.fillMaxWidth().weight(1f))
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = if (isPicture || isCentred) ContentScale.Fit else ContentScale.Crop,
                )
            }
        }
    }
}

private const val ALERT_HEAD_ROOM = 60f

/* Mirrors #alert-zone and the focused band in the retired BubbleService: the strip a pull-to-focus is heard on, and the whole screen once the Alert is focused or dark. */
@Composable
fun AlertBand(bubble: MainBubble) {
    val band = bubble.alertBand()
    SideEffect {
        if (band != null) Island.regions[Region.Alert] = band else Island.regions.remove(Region.Alert)
    }
    if (band == null) return
    val current = rememberUpdatedState(band)
    Box(
        Modifier
            .offset { IntOffset((band.left * Island.density).roundToInt(), (band.top * Island.density).roundToInt()) }
            .size(band.width.dp, band.height.dp)
            .pointerInput(bubble) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun at(position: Offset) = Offset(position.x / density + current.value.left, position.y / density + current.value.top)
                    var last = at(down.position)
                    bubble.onAlertDown(last.x, last.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        last = at(change.position)
                        if (!change.pressed) break
                        bubble.onAlertMove(last.x, last.y)
                    }
                    bubble.onAlertUp(last.x, last.y)
                }
            },
    )
}

/* Mirrors #screen-dark and #dark-alert in pill.css and paintDarkAlert in js/alert.js. */
@Composable
fun DarkAlertView() {
    val isDark = Island.isScreenDark
    val black = animateFloatAsState(if (isDark) 1f else 0f, if (isDark) snap<Float>() else tween(DARK_FADE, easing = Curves.leave), label = "dark")
    val live = Island.darkAlert
    var held by remember { mutableStateOf<DarkAlert?>(null) }
    if (live != null) held = live
    val shown = animateFloatAsState(if (live != null) 1f else 0f, if (live != null) snap<Float>() else tween(DARK_FADE, easing = Curves.leave), label = "darkAlert")
    val width = Island.screenWidth
    val height = Island.screenHeight
    SideEffect { Island.darkAlertBox = Rect(DARK_INSET, height * DARK_TOP, width - DARK_INSET, height * (DARK_TOP + DARK_TALL)) }
    if (black.value <= 0f && shown.value <= 0f) return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = black.value }.background(Color.Black))
    val alert = held ?: return
    val appearance = Island.appearance
    val total = if (alert.isAppending) alert.text.length else alert.title.length + alert.text.length
    val step = minOf(DARK_TYPE_STEP, DARK_TYPE_CEILING / maxOf(total, 1))
    var count by remember(alert.generation) { mutableIntStateOf(0) }
    LaunchedEffect(alert.generation) {
        val start = System.nanoTime() + (if (alert.isAppending) 0 else DARK_TYPE_DELAY) * 1_000_000L
        while (count < total) {
            delay(16)
            count = ((System.nanoTime() - start) / 1_000_000f / step).toInt().coerceIn(0, total)
        }
    }
    val titleShown = if (alert.isAppending) alert.title.length else minOf(count, alert.title.length)
    val textShown = if (alert.isAppending) minOf(count, alert.text.length) else (count - alert.title.length).coerceIn(0, alert.text.length)
    Box(
        Modifier
            .offset { IntOffset((DARK_INSET * Island.density).roundToInt(), (height * DARK_TOP * Island.density).roundToInt()) }
            .size((width - DARK_INSET * 2f).dp, (height * DARK_TALL).dp)
            .graphicsLayer {
                alpha = shown.value
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .drawWithContent {
                drawContent()
                val fade = DARK_FADE_EDGE.dp.toPx()
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height), blendMode = BlendMode.DstIn)
            }
            /* Mirrors justify-content: safe center: centred while it fits, held to the top once it does not. */
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, maxOf(0, (constraints.maxHeight - placeable.height) / 2)) }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Reveal(alert.revealKey, alert.accent) {
                BasicText(alert.appName.uppercase(), style = type(0.8125f, alert.accent, appearance.headingFont, appearance.headingScale, spacing = 0.13f), maxLines = 1, softWrap = false)
            }
            val isTypingTitle = count < alert.title.length && !alert.isAppending
            TypedRun(alert.title, titleShown, isTypingTitle, type(1.5f, Color.White.copy(alpha = 0.62f), appearance.headingFont, appearance.headingScale, lineHeight = 1.25f))
            TypedRun(alert.text, textShown, !isTypingTitle && textShown < alert.text.length, type(1.125f, Color.White.copy(alpha = 0.46f), appearance.contentFont, appearance.contentScale, lineHeight = 1.45f))
        }
    }
}

/* Mirrors #dark-alert's insets, TYPE_STEP, TYPE_CEILING and TYPE_DELAY in js/alert.js, and the 360ms fade on #screen-dark. */
private const val DARK_INSET = 36f
private const val DARK_TOP = 0.3f
private const val DARK_TALL = 0.4f
private const val DARK_FADE_EDGE = 40f
private const val DARK_FADE = 360
private const val DARK_TYPE_STEP = 9f
private const val DARK_TYPE_CEILING = 520f
private const val DARK_TYPE_DELAY = 140L

@Composable
fun PlayerFace(bubble: MainBubble, appearance: Appearance) {
    val media = Island.media ?: return
    rememberNow(500)
    val position = if (bubble.isScrubbing) bubble.scrubPosition
    else media.position + if (media.isPlaying) android.os.SystemClock.elapsedRealtime() - media.receivedAt else 0L
    val ratio = if (media.duration > 0) (position.toFloat() / media.duration).coerceIn(0f, 1f) else 0f
    val skip = remember { PlayerSwipe() }
    val scope = rememberCoroutineScope()
    var waveWidth by remember { mutableIntStateOf(1) }
    val band by animateFloatAsState(if (bubble.isScrubbing) 110f else 52f, tween(220, easing = Curves.grow), label = "band")
    Box(Modifier.fillMaxSize()) {
        val art = media.art
        Box(
            /* The swipe was read inside the art it moves, so every pixel of shift moved the finger's local position back by as much and the drag lagged and jumped; the gesture is read before the shift. */
            Modifier.fillMaxSize()
                .pointerInput(skip) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var across = 0f
                        var downward = 0f
                        var axis = PlayerAxis.None
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            across = (change.position.x - down.position.x) / density
                            downward = (change.position.y - down.position.y) / density
                            if (axis == PlayerAxis.None && hypot(across, downward) > PLAYER_HOLD_OFF) {
                                axis = if (abs(across) > abs(downward)) PlayerAxis.Across else PlayerAxis.Along
                            }
                            if (axis == PlayerAxis.Across) skip.follow(scope, across)
                            if (axis == PlayerAxis.Along) skip.pull(scope, downward)
                            if (!change.pressed) {
                                change.consume()
                                when {
                                    axis == PlayerAxis.Across -> skip.release(scope)
                                    axis == PlayerAxis.Along && downward > PLAYER_PULL -> {
                                        Haptics.play(Haptic.Tap)
                                        Island.actions.mediaControl(if (skip.isPausing) "pause" else "play")
                                        skip.landPull(scope)
                                    }
                                    axis == PlayerAxis.Along -> {
                                        skip.dropPull(scope)
                                        if (downward < -PLAYER_PULL) {
                                            Haptics.play(Haptic.Dismiss)
                                            bubble.toClosed()
                                        }
                                    }
                                    axis == PlayerAxis.None -> {
                                        Haptics.play(Haptic.Tap)
                                        bubble.toClosed()
                                    }
                                }
                                break
                            }
                        }
                    }
                }
                .graphicsLayer {
                    translationX = skip.artShift() * density
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.13f to Color.Black, 0.87f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.09f to Color.Black, 0.91f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
                .background(Palette.sunken)
        ) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        SkipArrow(skip, Modifier.align(if (skip.arrowSide > 0) Alignment.CenterStart else Alignment.CenterEnd))
        PlayPauseMark(skip, Modifier.align(Alignment.Center))
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.08f), Color.Transparent)))
                .padding(start = 16.8.dp, end = 16.8.dp, top = 13.6.dp, bottom = 41.6.dp),
        ) {
            ScrambleText(media.title, type(1.02f, Palette.textBright, appearance.mainFont, appearance.mainScale, lineHeight = 1.25f), Durations.CONTENT_DROP)
            Spacer(Modifier.height(4.dp))
            ScrambleText(media.artist.uppercase(), type(0.8f, media.accent, appearance.mainFont, appearance.mainScale, spacing = 0.1f, lineHeight = 1.25f), Durations.CONTENT_DROP + 120)
        }
        Box(
            /* The shorter player left the grown scrub band taller than twice its 52 rest, so its bottom padding went negative and dragging the timeline crashed the app; the band is moved by an offset, which may go below the edge. */
            Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, ((band / 2f - 52f) * density).roundToInt()) }.fillMaxWidth().height(band.dp)
                .pointerInput(media.duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (media.duration <= 0) return@awaitEachGesture
                        down.consume()
                        waveWidth = size.width
                        bubble.isScrubbing = true
                        bubble.scrubPosition = seekAt(down.position.x, waveWidth, media.duration)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            bubble.scrubPosition = seekAt(change.position.x, waveWidth, media.duration)
                            if (!change.pressed) {
                                Haptics.play(Haptic.Tap)
                                Island.actions.mediaSeek(bubble.scrubPosition)
                                break
                            }
                        }
                        bubble.isScrubbing = false
                    }
                },
        ) {
            Wave(media, WAVE_BARS, ratio = { ratio }, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.6f))))
                .padding(start = 16.8.dp, end = 16.8.dp, top = 32.dp, bottom = 9.6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = type(0.7f, Palette.textMuted, appearance.mainFont, appearance.mainScale, spacing = 0.08f)
            BasicText(clockReading(position), style = style)
            if (media.app == "telegram" || media.app == "whatsapp") SpeedToggle(style)
            BasicText(clockReading(media.duration), style = style)
        }
    }
}

private val SPEEDS = listOf(1f, 1.5f, 2f)

@Composable
private fun SpeedToggle(style: androidx.compose.ui.text.TextStyle) {
    var index by remember { mutableIntStateOf(0) }
    BasicText(
        (if (SPEEDS[index] % 1f == 0f) SPEEDS[index].roundToInt().toString() else SPEEDS[index].toString()) + "×",
        Modifier.pressable {
            index = (index + 1) % SPEEDS.size
            Island.actions.mediaSpeed(SPEEDS[index])
        },
        style = style,
    )
}

private fun seekAt(x: Float, width: Int, duration: Long): Long =
    ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * duration).roundToInt().toLong()

private enum class PlayerAxis { None, Across, Along }

private class PlayerSwipe {
    val drag = Animatable(0f)
    val kick = Animatable(0f)
    val push = Animatable(0f)
    val arrow = Animatable(0f)
    var armedSide by mutableIntStateOf(0)
    var arrowSide by mutableIntStateOf(1)

    fun artShift(): Float = drag.value + kick.value + push.value

    fun arrowShift(): Float = (1f - arrow.value) * -arrowSide * SKIP_ARROW_TRAVEL + push.value

    fun follow(scope: CoroutineScope, across: Float) {
        scope.launch { drag.snapTo(across * PLAYER_DRAG_FOLLOW) }
        val side = if (abs(across) > PLAYER_SWIPE) sign(across).toInt() else 0
        if (side == armedSide) return
        armedSide = side
        if (side == 0) {
            scope.launch { arrow.animateTo(0f, tween(160, easing = Curves.leave)) }
            return
        }
        arrowSide = side
        Haptics.play(Haptic.Release)
        scope.launch {
            kick.animateTo(side * SKIP_BOUNCE, tween(90, easing = Curves.powerTwoOut))
            kick.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 520f))
        }
        scope.launch { arrow.animateTo(1f, tween(Durations.CONTENT_DROP + 60, easing = Curves.grow)) }
    }

    val pulled = Animatable(0f)
    var isPullArmed by mutableStateOf(false)
    var isPausing by mutableStateOf(true)

    fun pull(scope: CoroutineScope, downward: Float) {
        if (pulled.value == 0f && !pulled.isRunning) isPausing = Island.media?.isPlaying == true
        scope.launch { pulled.snapTo((downward / PLAYER_PULL).coerceIn(0f, 1f)) }
        val isArmed = downward > PLAYER_PULL
        if (isArmed == isPullArmed) return
        isPullArmed = isArmed
        if (isArmed) Haptics.play(Haptic.Release)
    }

    fun landPull(scope: CoroutineScope) {
        isPullArmed = false
        scope.launch {
            pulled.animateTo(PULL_FLASH, tween(120, easing = Curves.powerTwoOut))
            pulled.animateTo(0f, tween(260, easing = Curves.leave))
        }
    }

    fun dropPull(scope: CoroutineScope) {
        isPullArmed = false
        scope.launch { pulled.animateTo(0f, tween(160, easing = Curves.leave)) }
    }

    fun release(scope: CoroutineScope) {
        val side = armedSide
        armedSide = 0
        if (side == 0) {
            scope.launch { drag.animateTo(0f, tween(210, easing = Curves.grow)) }
            scope.launch { arrow.animateTo(0f, tween(160, easing = Curves.leave)) }
            return
        }
        val title = Island.media?.title
        Haptics.play(Haptic.Tap)
        Island.actions.mediaControl(if (side > 0) "next" else "previous")
        scope.launch {
            push.animateTo(side * PLAYER_THROW, tween(SKIP_OUT, easing = Curves.powerTwoIn))
            withTimeoutOrNull(SKIP_WAIT) { snapshotFlow { Island.media?.title }.first { it != title } }
            drag.snapTo(0f)
            arrow.snapTo(0f)
            push.snapTo(-side * PLAYER_THROW)
            push.animateTo(0f, tween(Durations.MORPH, easing = Curves.grow))
        }
    }
}

/* A pull down paused or played with nothing on the artwork to say it would; the mark it will set slides down with the finger, settles when armed and flashes on release. */
@Composable
private fun PlayPauseMark(skip: PlayerSwipe, modifier: Modifier) {
    val isPausing = skip.isPausing
    Canvas(
        modifier.size(PULL_MARK_SIZE.dp).graphicsLayer {
            val shown = skip.pulled.value
            alpha = shown.coerceIn(0f, 1f)
            scaleX = 0.6f + 0.4f * shown
            scaleY = scaleX
            translationY = (shown.coerceAtMost(1f) - 1f) * PULL_MARK_TRAVEL * density
        },
    ) {
        drawCircle(Color.Black.copy(alpha = 0.45f))
        val unit = size.minDimension
        if (isPausing) {
            val bar = androidx.compose.ui.geometry.Size(unit * 0.12f, unit * 0.4f)
            val top = (unit - bar.height) / 2f
            val corner = androidx.compose.ui.geometry.CornerRadius(bar.width / 2f)
            drawRoundRect(Color.White, Offset(unit * 0.34f, top), bar, corner)
            drawRoundRect(Color.White, Offset(unit * 0.54f, top), bar, corner)
        } else {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(unit * 0.38f, unit * 0.3f)
                lineTo(unit * 0.72f, unit * 0.5f)
                lineTo(unit * 0.38f, unit * 0.7f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

@Composable
private fun SkipArrow(skip: PlayerSwipe, modifier: Modifier) {
    val side = skip.arrowSide
    Canvas(
        modifier.padding(horizontal = SKIP_ARROW_INSET.dp).size(SKIP_ARROW_SIZE.dp).graphicsLayer {
            translationX = skip.arrowShift() * density
            alpha = skip.arrow.value.coerceIn(0f, 1f)
        },
    ) {
        val width = 2.4.dp.toPx()
        val middle = size.height / 2f
        val tip = if (side > 0) size.width * 0.85f else size.width * 0.15f
        val tail = if (side > 0) size.width * 0.15f else size.width * 0.85f
        val head = size.width * 0.3f * side
        drawLine(Color.White, Offset(tail, middle), Offset(tip, middle), width, StrokeCap.Round)
        drawLine(Color.White, Offset(tip - head, middle - size.height * 0.3f), Offset(tip, middle), width, StrokeCap.Round)
        drawLine(Color.White, Offset(tip - head, middle + size.height * 0.3f), Offset(tip, middle), width, StrokeCap.Round)
    }
}

private const val PLAYER_HOLD_OFF = 17f
private const val PLAYER_SWIPE = 74f
private const val PLAYER_PULL = 48f
private const val PLAYER_DRAG_FOLLOW = 1f
private const val PLAYER_THROW = 340f
private const val SKIP_BOUNCE = 14f
private const val SKIP_ARROW_TRAVEL = 48f
private const val SKIP_ARROW_INSET = 22f
private const val SKIP_ARROW_SIZE = 26f
private const val SKIP_OUT = 200
private const val SKIP_WAIT = 900L
private const val PULL_FLASH = 1.3f
private const val PULL_MARK_SIZE = 52f
private const val PULL_MARK_TRAVEL = 24f

@Composable
fun PillButton(title: String, isLead: Boolean, accent: Color, appearance: Appearance, onClick: () -> Unit) {
    val color = if (isLead) accent else Palette.textBody
    BasicText(
        title.uppercase(),
        Modifier.pressable(pressedScale = 0.96f, haptic = null, onClick = onClick)
            .border(1.dp, if (isLead) accent else Palette.borderControl, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.4.dp, vertical = 6.4.dp),
        style = type(0.63f, color, appearance.mainFont, appearance.mainScale, spacing = 0.14f),
    )
}

@Composable
fun TimerPanel(bubble: MainBubble, appearance: Appearance) {
    val timer = Island.timer ?: return
    val now = rememberNow(1000)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 17.6.dp),
        verticalArrangement = Arrangement.spacedBy(11.2.dp, Alignment.CenterVertically),
    ) {
        RevealLabel(timer.appName.ifEmpty { "Clock" }.uppercase(), type(0.6f, timer.accent, appearance.mainFont, appearance.mainScale, spacing = 0.19f), timer.accent)
        BasicText(
            remainingText(timer, now),
            style = type(1.6f, if (timer.isPaused) Palette.textMuted else Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.08f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
            timer.actions.forEachIndexed { index, action ->
                PillButton(action.title, index == 0, timer.accent, appearance) { bubble.timerAction(action) }
            }
        }
    }
}

@Composable
fun TripPanel(bubble: MainBubble, appearance: Appearance) {
    val trip = Island.trip ?: return
    val now = rememberNow(1000)
    val body = type(0.7f, Palette.textBody, appearance.mainFont, appearance.mainScale)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 17.6.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            RevealLabel(trip.appName.uppercase(), type(0.6f, trip.accent, appearance.mainFont, appearance.mainScale, spacing = 0.19f), trip.accent)
            BasicText(tripRemaining(trip, now), style = type(1.3f, Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.06f))
        }
        trip.train?.let { FadingLine(it, type(1f, Palette.textBright, appearance.mainFont, appearance.mainScale)) }
        if (trip.origin != null || trip.destination != null) {
            FadingLine(listOfNotNull(trip.origin, trip.destination).joinToString(" → "), body)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.8.dp)) {
            trip.arrival?.let { BasicText(it, style = body) }
            trip.delay?.let { BasicText(it, style = body.copy(color = trip.accent)) }
            trip.platform?.let { BasicText("Gl. $it", style = body) }
        }
        trip.exitStop?.let { FadingLine("Ausstieg $it", body) }
        trip.nextStop?.let { FadingLine("Nächster Halt $it", body) }
        Column(Modifier.weight(1f).fadeTop(12.dp)) {
            trip.lines.forEach { FadingLine(it, type(0.62f, Palette.textMuted, appearance.mainFont, appearance.mainScale)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
            trip.actions.forEachIndexed { index, action ->
                PillButton(action.title, index == 0, trip.accent, appearance) { bubble.tripAction(action) }
            }
        }
    }
}
