package com.v.island.bubble

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as Extent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Palette
import com.v.island.vas.pressable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/* The levels were plain outlined rings and a dot that jumped only once the finger lifted; they are the WebView's rounded slots, filling as the dot nears them, and the dot and slots melt through one blur and threshold like #switch-goo did. */
@Composable
fun TorchFace(bubble: MainBubble) {
    val count = bubble.torchCount()
    val slot = MainBubble.TORCH_SLOT
    val gap = MainBubble.TORCH_SLOT_GAP
    val width = bubble.torchSliderWidth()
    val shown = animateFloatAsState(bubble.torchAt, if (bubble.isTorchSliding) snap() else tween(TORCH_SETTLE, easing = Curves.grow), label = "torchDot")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width.dp, slot.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = RAIL_OPACITY }) {
                val unit = slot.dp.toPx()
                val step = (slot + gap).dp.toPx()
                for (index in 0 until count) {
                    drawRoundRect(Color.White, Offset(index * step, 0f), Extent(unit, unit), CornerRadius(SLOT_RADIUS.dp.toPx()))
                }
            }
            Canvas(
                Modifier
                    .requiredSize((width + GOO_ROOM * 2f).dp, (slot + GOO_ROOM * 2f).dp)
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
                for (index in 0 until count) {
                    val near = max(0f, 1f - abs(at - index))
                    if (near <= 0f) continue
                    val side = unit * near
                    val centre = Offset(room + index * step + unit / 2f, room + unit / 2f)
                    drawRoundRect(Color.White, centre - Offset(side / 2f, side / 2f), Extent(side, side), CornerRadius(SLOT_RADIUS.dp.toPx() * near))
                }
                drawCircle(Color.White, unit / 4f, Offset(room + at * step + unit / 2f, room + unit / 2f))
            }
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
            style = type(0.62f, Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.02f),
        )
    }
}

@Composable
fun TorchOffView(bubble: MainBubble) {
    val isShown = bubble.mode == Mode.Torch && bubble.size == Size.Torch
    val rise = animateFloatAsState(
        if (isShown) 1f else 0f,
        if (isShown) tween(LONG_GROW, OFF_DELAY, Curves.open) else tween(OFF_LEAVE, easing = Curves.leave),
        label = "torchOff",
    )
    val home = bubble.torchFrame()
    val restTop = home.top - MainBubble.TORCH_OFF_GAP - MainBubble.TORCH_OFF_HEIGHT
    /* The Off pill sank back into the flashlight's old place on the way out while the flashlight itself flew up to the bar; it leaves upward into the main pill and only rises out of the flashlight on the way in. */
    val origin = if (isShown) home.top + home.height / 2f else Island.appearance.grab + Island.appearance.compactHeight / 2f
    val hiddenTop = origin - MainBubble.TORCH_OFF_HEIGHT / 2f
    val left = Island.screenWidth / 2f - MainBubble.TORCH_OFF_WIDTH / 2f

    Blob(Island.liquid) {
        val progress = rise.value
        if (progress <= 0.01f) return@Blob null
        val density = Island.density
        val grown = OFF_SEED + (1f - OFF_SEED) * progress.coerceIn(0f, 1.2f)
        val width = MainBubble.TORCH_OFF_WIDTH * grown
        val height = MainBubble.TORCH_OFF_HEIGHT * grown
        val top = hiddenTop + (restTop - hiddenTop) * progress + (MainBubble.TORCH_OFF_HEIGHT - height) / 2f
        val x = Island.screenWidth / 2f - width / 2f
        BlobShape(Rect(x * density, top * density, (x + width) * density, (top + height) * density), height / 2f * density, Palette.border, progress.coerceIn(0f, 1f))
    }

    SideEffect {
        if (isShown) Island.regions[Region.Torch] = Rect(left, restTop, left + MainBubble.TORCH_OFF_WIDTH, restTop + MainBubble.TORCH_OFF_HEIGHT)
        else Island.regions.remove(Region.Torch)
    }

    if (rise.value <= 0.01f) return
    Box(
        Modifier
            .offset { IntOffset((left * Island.density).roundToInt(), ((hiddenTop + (restTop - hiddenTop) * rise.value) * Island.density).roundToInt()) }
            .size(MainBubble.TORCH_OFF_WIDTH.dp, MainBubble.TORCH_OFF_HEIGHT.dp)
            .graphicsLayer { alpha = ((rise.value - 0.5f) * 2f).coerceIn(0f, 1f) }
            .pressable(pressedScale = 0.92f, haptic = Haptic.Dismiss) { if (isShown) Island.actions.setTorch(0) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Off", style = type(0.86f, Palette.danger, Island.appearance.mainFont, Island.appearance.mainScale, spacing = 0.04f))
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
private const val OFF_DELAY = 160
private const val OFF_LEAVE = 200
private const val OFF_SEED = 0.4f
private const val IDLE_END = 10f
