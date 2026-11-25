package com.v.island.bubble

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as Extent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Palette
import com.v.island.vas.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private fun lerp(from: Rect, to: Rect, progress: Float) = Rect(
    from.left + (to.left - from.left) * progress,
    from.top + (to.top - from.top) * progress,
    from.right + (to.right - from.right) * progress,
    from.bottom + (to.bottom - from.bottom) * progress,
)

private fun Rect.scaled(density: Float) = Rect(left * density, top * density, right * density, bottom * density)

private fun circle(centre: Offset, side: Float) = Rect(centre.x - side / 2f, centre.y - side / 2f, centre.x + side / 2f, centre.y + side / 2f)

/* Both pills flew home the way they came out and reached the bar as two shapes; they round off early on the way up, close on one point and land as one slightly smaller circle. */
private fun flight(from: Rect, home: Offset, side: Float, progress: Float): Rect {
    val flown = 1f - progress
    val round = (flown * ROUNDING).coerceAtMost(1f)
    val width = from.width + (side - from.width) * round
    val height = from.height + (side - from.height) * round
    val centre = from.center + (home - from.center) * flown
    return Rect(centre.x - width / 2f, centre.y - height / 2f, centre.x + width / 2f, centre.y + height / 2f)
}

/* The flashlight was a size of Main and stopped every running mod while it stood open; it is a pill of its own that travels out of Main to the right edge and back, and Main keeps its mods meanwhile. */
@Composable
fun TorchView(bubble: MainBubble) {
    val isOpen = bubble.isTorchOpen
    val travel = animateFloatAsState(
        if (isOpen) 1f else 0f,
        if (isOpen) tween(LONG_GROW, easing = Curves.open) else tween(MainBubble.TORCH_FLIGHT, easing = FastOutSlowInEasing),
        label = "torchTravel",
    )
    val content = animateFloatAsState(if (isOpen) 1f else 0f, if (isOpen) snap() else tween(CONTENT_LEAVE), label = "torchContent")
    val home = bubble.torchRect()

    Blob(Island.liquid) {
        val progress = travel.value
        if (progress <= 0.01f) return@Blob null
        val shape = if (isOpen) lerp(bubble.mainRect(), home, progress) else flight(home, bubble.mainRect().center, bubble.lentSide(), progress)
        BlobShape(shape.scaled(Island.density), minOf(shape.width, shape.height) / 2f * Island.density, Palette.border)
    }

    SideEffect {
        if (isOpen) Island.regions[Region.Torch] = Rect(home.left - GRAB, home.top - GRAB, home.right + GRAB, home.bottom + GRAB)
        else Island.regions.remove(Region.Torch)
    }

    if (travel.value <= 0.01f) return
    Box(
        Modifier
            .offset { IntOffset(((home.left - GRAB) * Island.density).roundToInt(), ((home.top - GRAB) * Island.density).roundToInt()) }
            .size((home.width + GRAB * 2f).dp, (home.height + GRAB * 2f).dp)
            .graphicsLayer { alpha = content.value }
            .pointerInput(bubble) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = bubble.torchRect().top - GRAB
                    bubble.startTorchSlide(down.position.y / Island.density + origin)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        bubble.slideTorch(change.position.y / Island.density + origin)
                    }
                    bubble.finishTorchSlide()
                }
            },
        contentAlignment = Alignment.Center,
    ) { TorchFace(bubble, isOpen) }
}

/* The levels were plain outlined rings and a dot that jumped only once the finger lifted; they are the WebView's rounded slots, filling as the dot nears them, and the dot and slots melt through one blur and threshold like #switch-goo did. */
@Composable
private fun TorchFace(bubble: MainBubble, isOpen: Boolean) {
    val count = bubble.torchCount()
    val slot = MainBubble.TORCH_SLOT
    val gap = MainBubble.TORCH_SLOT_GAP
    val length = bubble.torchSliderLength()
    val shown = animateFloatAsState(bubble.torchAt, if (bubble.isTorchSliding) snap() else tween(TORCH_SETTLE, easing = Curves.grow), label = "torchDot")
    val pops = remember(count) { List(count) { Animatable(0f) } }
    LaunchedEffect(count, isOpen) {
        if (!isOpen) return@LaunchedEffect
        val middle = (count - 1) / 2f
        pops.forEach { it.snapTo(0f) }
        pops.forEachIndexed { index, pop ->
            launch {
                delay(LONG_GROW + (abs(index - middle) * POP_STAGGER).toLong())
                pop.animateTo(1f, tween(POP_DURATION, easing = Curves.bubble))
            }
        }
    }
    Box(Modifier.size(slot.dp, length.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = RAIL_OPACITY }) {
            val unit = slot.dp.toPx()
            val step = (slot + gap).dp.toPx()
            for (index in 0 until count) {
                val pop = pops[index].value
                if (pop <= 0f) continue
                val top = (count - 1 - index) * step
                val centre = Offset(unit / 2f, top + unit / 2f)
                withTransform({
                    rotate((1f - pop) * POP_TURN, centre)
                    scale(pop, pop, centre)
                }) { drawRoundRect(Color.White, Offset(0f, top), Extent(unit, unit), CornerRadius(SLOT_RADIUS.dp.toPx())) }
            }
        }
        Canvas(
            Modifier
                .requiredSize((slot + GOO_ROOM * 2f).dp, (length + GOO_ROOM * 2f).dp)
                .graphicsLayer {
                    val blur = GOO_BLUR.dp.toPx()
                    renderEffect = RenderEffect.createChainEffect(
                        RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(GOO_MATRIX)),
                        RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL),
                    ).asComposeRenderEffect()
                },
        ) {
            val room = GOO_ROOM.dp.toPx()
            val unit = slot.dp.toPx()
            val step = (slot + gap).dp.toPx()
            val at = shown.value
            val centreOf = { place: Float -> Offset(room + unit / 2f, room + (count - 1 - place) * step + unit / 2f) }
            for (index in 0 until count) {
                val pop = pops[index].value
                val near = max(0f, 1f - abs(at - index)) * pop
                if (near <= 0f) continue
                val side = unit * near
                val centre = centreOf(index.toFloat())
                withTransform({ rotate((1f - pop) * POP_TURN, centre) }) {
                    drawRoundRect(Color.White, centre - Offset(side / 2f, side / 2f), Extent(side, side), CornerRadius(SLOT_RADIUS.dp.toPx() * near))
                }
            }
            val dotPop = pops[at.roundToInt().coerceIn(0, count - 1)].value
            if (dotPop > 0f) drawCircle(Color.White, unit / 4f * dotPop, centreOf(at))
        }
    }
}

@Composable
fun TorchIdleFace(appearance: Appearance) {
    val torch = Island.torch
    Row(
        Modifier.fillMaxSize().padding(start = glyphInset(appearance).dp, end = IDLE_END.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyphs.torch, Color.White, Modifier.size(glyphSize(appearance).dp))
        BasicText(
            if (torch?.isDimmable == true) "${torch.step}/${torch.steps}" else "On",
            style = type(IDLE_TYPE, Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.02f),
        )
    }
}

/* The Off pill only slid its top between the flashlight and the bar and kept its text to the end, so it left out of step with the main pill and its label rode it home; it travels as a whole box to and from Main, its label gone in the first frames. */
@Composable
fun TorchOffView(bubble: MainBubble) {
    val isShown = bubble.isTorchOpen
    val rise = animateFloatAsState(
        if (isShown) 1f else 0f,
        if (isShown) tween(LONG_GROW, OFF_DELAY, Curves.open) else tween(MainBubble.TORCH_FLIGHT, easing = FastOutSlowInEasing),
        label = "torchOff",
    )
    val label = animateFloatAsState(
        if (isShown) 1f else 0f,
        if (isShown) tween(LABEL_ARRIVE, OFF_DELAY + LONG_GROW / 2) else tween(CONTENT_LEAVE),
        label = "torchOffLabel",
    )
    val rest = bubble.torchOffRect()
    val placed = {
        val progress = rise.value
        if (isShown) lerp(circle(bubble.torchRect().center, MainBubble.TORCH_OFF_HEIGHT * OFF_SEED), rest, progress)
        else flight(rest, bubble.mainRect().center, bubble.lentSide(), progress)
    }

    Blob(Island.liquid) {
        if (rise.value <= 0.01f) return@Blob null
        val shape = placed()
        BlobShape(shape.scaled(Island.density), minOf(shape.width, shape.height) / 2f * Island.density, Palette.border)
    }

    SideEffect {
        if (isShown) Island.regions[Region.TorchOff] = rest
        else Island.regions.remove(Region.TorchOff)
    }

    if (rise.value <= 0.01f) return
    val shape = placed()
    Box(
        Modifier
            .offset { IntOffset((shape.left * Island.density).roundToInt(), (shape.top * Island.density).roundToInt()) }
            .size(shape.width.coerceAtLeast(1f).dp, shape.height.coerceAtLeast(1f).dp)
            .graphicsLayer { alpha = label.value }
            .pressable(pressedScale = 0.92f, haptic = Haptic.Dismiss) { if (isShown) Island.actions.setTorch(0) },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyphs.torchOff, Palette.danger, Modifier.size(OFF_GLYPH.dp))
    }
}

/* Mirrors the 260ms left and transform transitions on .now-slot and .now-dot, #now-rail's 0.24 opacity, the slot's 0.62rem radius and #switch-goo's stdDeviation 5 and alpha 22 -10 in pill.css and pill.html. */
private const val TORCH_SETTLE = 260
private const val RAIL_OPACITY = 0.24f
private const val SLOT_RADIUS = 10f
private const val GOO_BLUR = 5f
private const val GOO_ROOM = 10f
private val GOO_MATRIX = ColorMatrix(
    floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 22f, -10f * 255f,
    ),
)
/* The Off pill grew out of Main beside the flashlight; it leaves the flashlight's side once the flashlight has landed. */
private const val OFF_DELAY = 300
private const val OFF_SEED = 0.3f
private const val OFF_GLYPH = 24f
private const val ROUNDING = 2.5f
/* The folded level sat at the glyph's type size and reached into the punch hole on a narrow pill. */
private const val IDLE_TYPE = 0.52f
private const val LABEL_ARRIVE = 160
private const val CONTENT_LEAVE = 70
private const val IDLE_END = 10f
private const val POP_STAGGER = 70f
private const val POP_DURATION = 320
private const val POP_TURN = -90f
