package com.v.island.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import kotlin.math.roundToInt

/* Mirrors GRAB and BLEED in BubbleService: the touch pad stands this far either side of the bubble, and GRAB below it. */
const val GRAB = 8f
const val BLEED = 14f

@Composable
fun MainBubbleView(bubble: MainBubble) {
    val appearance = Island.appearance
    val target = bubble.target()
    val isClosed = bubble.size.isClosed
    val easing = if (isClosed) Curves.bubble else Curves.grow
    val follow = if (bubble.isDragging) snap<Float>() else tween(Durations.GROW, easing = easing)
    val width = animateFloatAsState(target.width, follow, label = "width")
    val height = animateFloatAsState(target.height, tween(Durations.GROW, easing = easing), label = "height")
    val top = animateFloatAsState(target.top, tween(Durations.GROW, easing = if (isClosed) Curves.reach else Curves.grow), label = "top")
    val shift = animateFloatAsState(target.shift, follow, label = "shift")
    val radius = animateFloatAsState(target.radius, tween(Durations.GROW, easing = FastOutSlowInEasing), label = "radius")
    val scale = animateFloatAsState(bubble.holdScale, tween(Durations.HOLD, easing = Curves.bubble), label = "hold")
    val rise = animateFloatAsState(
        if (bubble.isRisen) MainBubble.EDGE_OVER else 0f,
        if (bubble.isRisen) tween(Durations.CONTENT_DROP, Durations.GROW, Curves.grow) else tween(Durations.CONTENT_DROP, easing = Curves.grow),
        label = "rise",
    )
    val centre = Island.screenWidth / 2f

    Blob(Island.liquid) {
        val density = Island.density
        val grownWidth = width.value * scale.value
        val grownHeight = height.value * scale.value
        val left = centre + shift.value - grownWidth / 2f + bubble.drag.x
        val upper = top.value + bubble.drag.y
        BlobShape(
            Rect(left * density, upper * density, (left + grownWidth) * density, (upper + grownHeight) * density),
            radius.value * scale.value * density,
            bubble.border(),
        )
    }

    val padLeft = centre + target.shift - target.width / 2f - GRAB - BLEED
    val padWidth = target.width + (GRAB + BLEED) * 2f
    val padHeight = target.top + target.height + GRAB
    SideEffect { Island.regions[Region.Main] = Rect(padLeft, 0f, padLeft + padWidth, padHeight) }

    Box(
        Modifier
            .offset { IntOffset((padLeft * Island.density).roundToInt(), 0) }
            .size(padWidth.dp, padHeight.dp)
            .pointerInput(bubble) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val density = Island.density
                    bubble.onDown((down.position.x / density) + padLeft, down.position.y / density)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!change.isConsumed) bubble.onUp() else bubble.onReleasedOnControl()
                            break
                        }
                        bubble.onMove(change.position.x / density + padLeft, change.position.y / density)
                    }
                }
            },
    ) {
        Box(
            Modifier
                .layout { measurable, _ ->
                    val density = Island.density
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(
                            (width.value * density).roundToInt().coerceAtLeast(1),
                            (height.value * density).roundToInt().coerceAtLeast(1),
                        ),
                    )
                    layout(placeable.width, placeable.height) {
                        val left = (centre + shift.value - width.value / 2f - padLeft) * density
                        placeable.place(left.roundToInt(), (top.value * density).roundToInt())
                    }
                }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationX = bubble.drag.x * Island.density
                    translationY = bubble.drag.y * Island.density
                    clip = true
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(radius.value.dp)
                },
        ) {
            AnimatedContent(
                targetState = bubble.face,
                transitionSpec = { faceTransition(initialState, targetState) },
                label = "face",
                modifier = Modifier.fillMaxSize(),
            ) { face ->
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    val drop = (rise.value * Island.density).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - drop).coerceAtLeast(0)))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, drop) }
                }) { FaceContent(face, bubble, appearance) }
            }
        }
    }
}

private val CLOSED_FACES = setOf(Face.Idle, Face.Media, Face.Timer, Face.Call, Face.Trip)

private fun faceTransition(from: Face, to: Face): ContentTransform {
    val isHandOver = from in CLOSED_FACES && to in CLOSED_FACES
    if (isHandOver) {
        return ContentTransform(
            scaleIn(tween(Durations.POP_IN, Durations.POP_OUT, Curves.enter)) + fadeIn(tween(120, Durations.POP_OUT)),
            scaleOut(tween(Durations.POP_OUT, easing = Curves.leave)) + fadeOut(tween(Durations.POP_OUT)),
            sizeTransform = SizeTransform(clip = false),
        )
    }
    val arriving = if (to in CLOSED_FACES) Durations.COLLAPSE / 2 else Durations.CONTENT_DROP
    return ContentTransform(fadeIn(tween(160, arriving)), fadeOut(tween(120)), sizeTransform = SizeTransform(clip = false))
}

@Composable
private fun FaceContent(face: Face, bubble: MainBubble, appearance: Appearance) {
    when (face) {
        Face.Idle -> IdleFace(appearance)
        Face.Media -> MediaFace(appearance)
        Face.Timer -> TimerFace(appearance)
        Face.Call -> CallFace(appearance)
        Face.Trip -> TripFace(appearance)
        Face.Alert -> AlertFace(bubble, appearance)
        Face.Player -> PlayerFace(bubble, appearance)
        Face.TimerPanel -> TimerPanel(bubble, appearance)
        Face.TripPanel -> TripPanel(bubble, appearance)
        Face.Notifications -> NotificationsFace(bubble, appearance)
    }
}
