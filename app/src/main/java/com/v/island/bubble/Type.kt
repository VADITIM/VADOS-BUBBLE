package com.v.island.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import kotlinx.coroutines.delay

@Composable
fun rem(value: Float): TextUnit = with(LocalDensity.current) { (value * 16f).dp.toSp() }

@Composable
fun type(
    size: Float,
    color: Color,
    family: FontFamily,
    scale: Float = 1f,
    spacing: Float = 0f,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 1.5f,
): TextStyle {
    val fontSize = rem(size * scale)
    return TextStyle(
        color = color,
        fontFamily = family,
        fontSize = fontSize,
        fontWeight = weight,
        letterSpacing = rem(spacing),
        lineHeight = fontSize * lineHeight,
        fontFeatureSettings = "tnum",
    )
}

fun Modifier.fadeEnd(reach: Dp = 22.dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = reach.toPx().coerceAtMost(size.width)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
            topLeft = Offset(size.width - fade, 0f),
            size = Size(fade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

fun Modifier.fadeStart(reach: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = reach.toPx().coerceAtMost(size.width)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
            size = Size(fade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

fun Modifier.fadeTop(reach: Dp = 24.dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = reach.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }

/* The fade was drawn on every line, so a string with room to spare still lost its last characters; it is drawn only once the text runs out of room. */
@Composable
fun FadingLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    var isOverflowing by remember { androidx.compose.runtime.mutableStateOf(false) }
    BasicText(
        text,
        if (isOverflowing) modifier.fadeEnd() else modifier,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { isOverflowing = it.didOverflowWidth },
    )
}

@Composable
fun RevealLabel(text: String, style: TextStyle, bar: Color, delayMilliseconds: Int = 0, modifier: Modifier = Modifier) {
    Reveal(text, bar, delayMilliseconds, modifier) { FadingLine(text, style) }
}

@Composable
fun Reveal(key: Any, bar: Color, delayMilliseconds: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sweep = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMilliseconds.toLong())
        sweep.animateTo(1f, tween(Durations.REVEAL_BAR, easing = Curves.sweep))
    }
    Box(
        modifier.drawWithContent {
            val progress = sweep.value
            val textShown = progress >= Durations.REVEAL_TEXT_AT.toFloat() / Durations.REVEAL_BAR
            if (textShown) drawContent()
            val grow = 0.456f
            val (start, end) = if (progress < grow) 0f to progress / grow else (progress - grow) / (1f - grow) to 1f
            if (progress < 1f) {
                drawRect(bar, topLeft = Offset(size.width * start, 0f), size = Size(size.width * (end - start), size.height))
            }
        },
    ) {
        content()
    }
}

/* Writing the text in character by character re-wrapped the row on every frame and pushed the rows under it; the whole text is laid out from the start and only the untyped rest is left uncoloured, as typeBody in tabs.js did. */
@Composable
fun TypedText(text: String, style: TextStyle, delayMilliseconds: Int, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, interval: Float = typeInterval(text.length), isLine: Boolean = false) {
    var shown by remember(text) { mutableIntStateOf(0) }
    var isTyping by remember(text) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(text) {
        if (text.isEmpty()) return@LaunchedEffect
        delay(delayMilliseconds.toLong())
        isTyping = true
        val start = System.nanoTime()
        while (shown < text.length) {
            delay(16)
            shown = ((System.nanoTime() - start) / 1_000_000f / interval).toInt().coerceAtMost(text.length)
        }
        isTyping = false
    }
    TypedRun(text, shown, isTyping, style, modifier, maxLines, isLine)
}

/* The menu typed with no caret and Media expanded with its own; every typed text draws the same caret now, a sharp bar 0.08em wide (V/AS components/09-typewriter.md §3; the 0.42em block of §10 read as a cursor block, not a |), blinking at 250ms, standing after the last typed character only while typing. */
@Composable
fun TypedRun(text: String, shown: Int, isTyping: Boolean, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, isLine: Boolean = false) {
    var isOverflowing by remember { androidx.compose.runtime.mutableStateOf(false) }
    var layout by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var isCaretLit by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(isTyping) {
        isCaretLit = true
        while (isTyping) {
            delay(CARET_BLINK)
            isCaretLit = !isCaretLit
        }
    }
    val typed = androidx.compose.ui.text.buildAnnotatedString {
        append(text.substring(0, shown))
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent)) { append(text.substring(shown)) }
    }
    BasicText(
        typed,
        (if (isLine && isOverflowing) modifier.fadeEnd() else modifier).drawWithContent {
            drawContent()
            val laidOut = layout ?: return@drawWithContent
            if (!isTyping || !isCaretLit || laidOut.layoutInput.text.length != text.length) return@drawWithContent
            val offset = shown.coerceIn(0, text.length)
            val em = style.fontSize.toPx()
            /* A one-line text cut by maxLines still put its caret on line 1, which the layout never kept, and crashed the app; the line is held to the last one laid out. */
            val baseline = laidOut.getLineBaseline(laidOut.getLineForOffset(offset).coerceAtMost(laidOut.lineCount - 1))
            val left = laidOut.getCursorRect(offset).left + if (offset > 0) em * CARET_GAP else 0f
            drawRect(style.color, topLeft = Offset(left, baseline + em * CARET_DROP - em), size = Size(em * CARET_WIDTH, em))
        },
        style = style,
        maxLines = if (isLine) 1 else maxLines,
        softWrap = !isLine,
        overflow = TextOverflow.Clip,
        onTextLayout = {
            isOverflowing = it.didOverflowWidth
            layout = it
        },
    )
}

/* Mirrors .tw-caret in V/AS components/09-typewriter.md §3 (width 0.08em, margin-left 0.04em, translateY 0.08em); the blink is V/AS's 250ms Android caret. */
private const val CARET_WIDTH = 0.08f
private const val CARET_GAP = 0.04f
private const val CARET_DROP = 0.08f
private const val CARET_BLINK = 250L

@Composable
fun Modifier.popIn(delayMilliseconds: Int): Modifier {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMilliseconds.toLong())
        pop.animateTo(1f, tween(Durations.POP_IN, easing = Curves.enter))
    }
    return graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
        alpha = pop.value.coerceIn(0f, 1f)
    }
}

@Composable
fun PopText(text: String, style: TextStyle, delayMilliseconds: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier) {
        text.forEachIndexed { index, character ->
            androidx.compose.runtime.key(index) {
                BasicText(character.toString(), Modifier.popIn(delayMilliseconds + index * Durations.POP_STAGGER), style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun Modifier.popAway(isAway: Boolean, order: Int): Modifier {
    val pop = androidx.compose.animation.core.animateFloatAsState(
        if (isAway) 0f else 1f,
        if (isAway) tween(AWAY_OUT, order * AWAY_STAGGER, Curves.leave) else tween(Durations.POP_IN, order * Durations.POP_STAGGER, Curves.enter),
        label = "away",
    )
    return graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
        alpha = pop.value.coerceIn(0f, 1f)
        rotationZ = (1f - pop.value) * AWAY_TURN
    }
}

@Composable
fun AwayText(text: String, style: TextStyle, isAway: Boolean, firstOrder: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier) {
        text.forEachIndexed { index, character ->
            androidx.compose.runtime.key(index) {
                BasicText(character.toString(), Modifier.popAway(isAway, firstOrder + index), style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}

private const val AWAY_TURN = -40f
private const val AWAY_OUT = 90
private const val AWAY_STAGGER = 8

@Composable
fun ScrambleText(text: String, style: TextStyle, delayMilliseconds: Int, modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    var previous by remember { androidx.compose.runtime.mutableStateOf("") }
    val starts = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var now by remember { mutableLongStateOf(0L) }
    var lineWidth by remember { mutableIntStateOf(0) }
    var roomWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        val from = firstDifference(previous, text)
        val count = text.length - from
        val stagger = if (count > 1) minOf(SCRAMBLE_STAGGER, maxOf(0L, SCRAMBLE_BUDGET - SCRAMBLE_SPIN) / (count - 1)) else SCRAMBLE_STAGGER
        val begin = System.currentTimeMillis() + if (previous.isEmpty()) delayMilliseconds else 0
        val placed = List(text.length) { index -> if (index < from) starts.getOrElse(index) { SCRAMBLE_SETTLED } else begin + (index - from) * stagger }
        starts.clear()
        starts.addAll(placed)
        previous = text
        val end = (placed.maxOrNull() ?: 0L) + SCRAMBLE_SPIN
        do {
            now = androidx.compose.runtime.withFrameMillis { System.currentTimeMillis() }
        } while (now < end)
    }
    val hidden = style.copy(color = Color.Transparent)
    Box((if (lineWidth > roomWidth) modifier.fadeEnd() else modifier).clipToBounds().onSizeChanged { roomWidth = it.width }) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().wrapContentWidth(if (lineWidth > roomWidth) Alignment.Start else alignment, unbounded = true).onSizeChanged { lineWidth = it.width }) {
            text.forEachIndexed { index, actual ->
                val local = (now - (starts.getOrNull(index) ?: Long.MAX_VALUE)).toFloat() / SCRAMBLE_SPIN
                Box {
                    BasicText(actual.toString(), style = hidden, maxLines = 1, softWrap = false)
                    if (actual != ' ' && local > 0f) {
                        val isResolved = local >= SCRAMBLE_SETTLE
                        val eased = 1f - (1f - local.coerceAtMost(1f)).let { it * it * it }
                        val step = (local / SCRAMBLE_SETTLE * SCRAMBLE_CYCLES).toInt()
                        BasicText(
                            if (isResolved) actual.toString() else SCRAMBLE_SYMBOLS[(index * 7 + step * 3) % SCRAMBLE_SYMBOLS.length].toString(),
                            Modifier.matchParentSize().wrapContentWidth(unbounded = true).graphicsLayer { rotationZ = if (local >= 1f) 0f else (1f - eased) * 360f },
                            style = style,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private fun firstDifference(previous: String, next: String): Int {
    val shared = minOf(previous.length, next.length)
    for (index in 0 until shared) if (previous[index] != next[index]) return index
    return shared
}

/* Mirrors scrambleSymbols in the shell's config/Appearance.qml and the scramble timings in its config/Motion.qml; the budget caps a long title the way typeBudget does there. */
private const val SCRAMBLE_SYMBOLS = "&$~|=+-/\\#*%"
private const val SCRAMBLE_STAGGER = 45L
private const val SCRAMBLE_SPIN = 260L
private const val SCRAMBLE_SETTLE = 0.65f
private const val SCRAMBLE_CYCLES = 4
private const val SCRAMBLE_BUDGET = 900L
private const val SCRAMBLE_SETTLED = -1_000_000_000L

fun typeInterval(characters: Int): Float = minOf(TYPE_CHARACTER, TYPE_BUDGET / characters.coerceAtLeast(1))

private const val TYPE_CHARACTER = 6f
private const val TYPE_BUDGET = 400f

@Composable
fun rememberNow(everyMilliseconds: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isHidden = Island.isStageHidden
    LaunchedEffect(everyMilliseconds, isHidden) {
        if (isHidden) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(everyMilliseconds - now % everyMilliseconds + 20)
        }
    }
    return now
}

fun clockReading(milliseconds: Long): String {
    val total = maxOf(0L, Math.round(milliseconds / 1000.0))
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
