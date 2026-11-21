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
import androidx.compose.ui.Modifier
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

@Composable
fun FadingLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text, modifier.fadeEnd(), style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

@Composable
fun RevealLabel(text: String, style: TextStyle, bar: Color, delayMilliseconds: Int = 0, modifier: Modifier = Modifier) {
    val sweep = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
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
        FadingLine(text, style)
    }
}

@Composable
fun TypedText(text: String, style: TextStyle, delayMilliseconds: Int, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        if (text.isEmpty()) return@LaunchedEffect
        delay(delayMilliseconds.toLong())
        val interval = minOf(TYPE_CHARACTER, TYPE_BUDGET / text.length.toFloat())
        val start = System.nanoTime()
        while (shown < text.length) {
            delay(16)
            shown = ((System.nanoTime() - start) / 1_000_000f / interval).toInt().coerceAtMost(text.length)
        }
    }
    BasicText(text.take(shown), modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Clip)
}

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
