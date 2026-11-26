package com.v.island.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TransferFace(bubble: MainBubble, appearance: Appearance) {
    val kept = remember { arrayOfNulls<Transfer>(1) }
    bubble.transferShown?.let { kept[0] = it }
    val transfer = kept[0] ?: return
    val isStay = LocalIsStayFace.current
    val accent = if (transfer.isUpload) com.v.island.vas.Palette.upload else com.v.island.vas.Palette.download
    val direction = if (transfer.isUpload) -1f else 1f
    val glyph = glyphSize(appearance)
    val reach = appearance.compactHeight

    val drop = remember { Animatable(if (isStay) 1f else 0f) }
    val bounce = remember { Animatable(1f) }
    val exit = remember { Animatable(0f) }
    val ring = remember { Animatable(if (isStay) 1f else 0f) }
    val check = remember { Animatable(0f) }
    val sparkle = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val ratio = if (transfer.total > 0) transfer.done.toFloat() / transfer.total else 0f
    val fill = animateFloatAsState(if (transfer.state == TransferState.Done) 1f else ratio, tween(FILL_MILLISECONDS, easing = Curves.grow), label = "transferFill")
    val loop = rememberInfiniteTransition(label = "transferLoop")
    val phase = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(PULSE_MILLISECONDS, easing = LinearEasing), RepeatMode.Restart), label = "transferPhase")
    val spin = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(SPIN_MILLISECONDS, easing = LinearEasing), RepeatMode.Restart), label = "transferSpin")

    LaunchedEffect(Unit) {
        if (isStay) return@LaunchedEffect
        launch {
            delay(MainBubble.TRANSFER_RING_AFTER)
            ring.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        }
        drop.animateTo(1f, tween(MainBubble.TRANSFER_ARROW_DROP, easing = Curves.bubble))
        bounce.animateTo(LAND_PULSE, tween(LAND_PULSE_MILLISECONDS, easing = Curves.powerTwoOut))
        bounce.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    LaunchedEffect(transfer.state) {
        when (transfer.state) {
            TransferState.Active -> Unit
            TransferState.Done -> {
                delay(FILL_MILLISECONDS.toLong())
                launch { sparkle.animateTo(1f, tween(MainBubble.TRANSFER_SPARKLE, easing = Curves.powerTwoOut)) }
                check.animateTo(1f, tween(MainBubble.TRANSFER_CHECK, easing = Curves.sweep))
                delay((MainBubble.TRANSFER_SPARKLE - MainBubble.TRANSFER_CHECK).toLong())
                launch { leave.animateTo(1f, tween(MainBubble.TRANSFER_ARROW_EXIT, easing = Curves.leave)) }
                exit.animateTo(1f, tween(MainBubble.TRANSFER_ARROW_EXIT, easing = Curves.leave))
            }
            TransferState.Failed -> {
                launch { leave.animateTo(1f, tween(MainBubble.TRANSFER_ARROW_EXIT, easing = Curves.leave)) }
                exit.animateTo(1f, tween(MainBubble.TRANSFER_ARROW_EXIT, easing = Curves.leave))
            }
        }
    }

    ModRow(appearance, glyphInset(appearance)) {
        Box(
            Modifier.size(glyph.dp).graphicsLayer {
                val isPulsing = transfer.state == TransferState.Active && drop.value >= 1f
                val breath = if (isPulsing) 1f + PULSE_DEPTH * sin(phase.value * 2f * PI.toFloat()) else 1f
                translationY = ((drop.value - 1f) + exit.value) * direction * reach * density
                scaleX = bounce.value * breath
                scaleY = bounce.value * breath
                alpha = 1f - exit.value
            },
        ) {
            GlyphIcon(if (transfer.isUpload) Glyphs.arrowUp else Glyphs.arrowDown, accent, Modifier.size(glyph.dp))
        }
        Canvas(
            Modifier.size(glyph.dp).graphicsLayer {
                val shown = ring.value * (1f - leave.value)
                scaleX = shown
                scaleY = shown
                alpha = (1f - leave.value).coerceIn(0f, 1f)
            },
        ) {
            val stroke = RING_STROKE.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val corner = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val box = Size(radius * 2f, radius * 2f)
            val unchecked = 1f - check.value
            drawCircle(Color.White.copy(alpha = unchecked), radius, style = Stroke(stroke))
            if (transfer.isIndeterminate && transfer.state == TransferState.Active) {
                drawArc(accent, -90f - spin.value * 360f, -SPINNER_SWEEP, false, corner, box, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                drawArc(accent.copy(alpha = unchecked), -90f, -360f * fill.value.coerceIn(0f, 1f), false, corner, box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (check.value > 0f) {
                val start = Offset(size.width * 0.27f, size.height * 0.53f)
                val knee = Offset(size.width * 0.44f, size.height * 0.7f)
                val end = Offset(size.width * 0.75f, size.height * 0.34f)
                val first = (knee - start).getDistance()
                val second = (end - knee).getDistance()
                val drawn = check.value * (first + second)
                val width = CHECK_STROKE.dp.toPx()
                drawLine(accent, start, start + (knee - start) * (drawn / first).coerceAtMost(1f), width, StrokeCap.Round)
                if (drawn > first) drawLine(accent, knee, knee + (end - knee) * ((drawn - first) / second), width, StrokeCap.Round)
            }
            if (sparkle.value > 0f && sparkle.value < 1f) {
                val spread = sparkle.value
                repeat(SPARKS) { index ->
                    val angle = index * 2f * PI.toFloat() / SPARKS - PI.toFloat() / 2f
                    val along = Offset(cos(angle), sin(angle))
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val inner = radius * (1f + SPARK_REACH * spread * 0.4f)
                    val outer = radius * (1f + SPARK_REACH * spread)
                    drawLine(accent.copy(alpha = 1f - spread), centre + along * inner, centre + along * outer, SPARK_STROKE.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

private const val FILL_MILLISECONDS = 260
private const val PULSE_MILLISECONDS = 1400
private const val PULSE_DEPTH = 0.07f
private const val SPIN_MILLISECONDS = 900
private const val SPINNER_SWEEP = 90f
private const val LAND_PULSE = 1.24f
private const val LAND_PULSE_MILLISECONDS = 110
private const val RING_STROKE = 2f
private const val CHECK_STROKE = 2.4f
private const val SPARKS = 8
private const val SPARK_REACH = 0.45f
private const val SPARK_STROKE = 1.4f
