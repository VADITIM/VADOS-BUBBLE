package com.v.island.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
    val wasClosed = remember { BooleanArray(1) { true } }
    val previous = remember { arrayOf(bubble.size) }
    val isReturning = isClosed && !wasClosed[0]
    /* Mirrors --grow-ms on the grown sizes and html.notifications-homing in pill.css: the big boxes open and go home over 460ms, not the pill's 340. */
    val growth = if (bubble.size in LONG_SIZES || previous[0] in LONG_SIZES) LONG_GROW else Durations.GROW
    SideEffect {
        wasClosed[0] = isClosed
        previous[0] = bubble.size
    }
    /* A grown box collapsing on the 1.7 overshoot undershot the closed pill and slammed back, and the reach curve home still bounced too hard, so every return home rides the gentler settle curve; opening had no bounce at all on the grow curve, and the Clock's 1.7 overshoot was a little too much, so it takes a 1.5 one. */
    val easing = if (isReturning) Curves.settle else if (isClosed) Curves.bubble else Curves.open
    val follow = if (bubble.isDragging) snap<Float>() else tween(growth, easing = easing)
    val width = animateFloatAsState(target.width, follow, label = "width")
    val height = animateFloatAsState(target.height, tween(growth, easing = easing), label = "height")
    val top = animateFloatAsState(target.top, tween(growth, easing = if (isClosed) Curves.settle else Curves.open), label = "top")
    val shift = animateFloatAsState(target.shift, follow, label = "shift")
    val radius = animateFloatAsState(target.radius, tween(growth, easing = FastOutSlowInEasing), label = "radius")
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
        val upper = top.value + bubble.drag.y + bubble.bumpLift
        BlobShape(
            Rect(left * density, upper * density, (left + grownWidth) * density, (upper + grownHeight) * density),
            radius.value * scale.value * density,
            bubble.border(),
        )
    }

    val padLeft = centre + target.shift - target.width / 2f - GRAB - BLEED
    val padWidth = target.width + (GRAB + BLEED) * 2f
    val padTop = 0f
    val padHeight = target.top + target.height + GRAB
    SideEffect { Island.regions[Region.Main] = Rect(padLeft, padTop, padLeft + padWidth, padTop + padHeight) }

    Box(
        Modifier
            .offset { IntOffset((padLeft * Island.density).roundToInt(), (padTop * Island.density).roundToInt()) }
            .size(padWidth.dp, padHeight.dp)
            .pointerInput(bubble, padTop) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val density = Island.density
                    bubble.onDown((down.position.x / density) + padLeft, down.position.y / density + padTop)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!change.isConsumed) bubble.onUp() else bubble.onReleasedOnControl()
                            break
                        }
                        bubble.onMove(change.position.x / density + padLeft, change.position.y / density + padTop)
                    }
                }
            },
    ) {
        Box(
            Modifier
                /* The pad is sized to the target, so collapsing it shrank under a box still the open size, and Compose centred the oversized box in it: the menu's contents slid up out of the liquid on the way home. */
                .wrapContentSize(Alignment.TopStart, unbounded = true)
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
                        placeable.place(left.roundToInt(), ((top.value - padTop) * density).roundToInt())
                    }
                }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationX = bubble.drag.x * Island.density
                    translationY = (bubble.drag.y + bubble.bumpLift) * Island.density
                    clip = true
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(radius.value.dp)
                },
        ) {
            /* Laid out at the animated size, every face re-centred on each frame of the growth and its contents drifted in from the corner; at the target size only the clip moves. */
            Box(Modifier.layout { measurable, constraints ->
                val density = Island.density
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(
                        (maxOf(target.width, width.value) * density).roundToInt().coerceAtLeast(1),
                        (maxOf(target.height, height.value) * density).roundToInt().coerceAtLeast(1),
                    ),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((constraints.maxWidth - placeable.width) / 2, 0) }
            }) {
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
}

private val LONG_SIZES = setOf(Size.Player, Size.TimerPanel, Size.TripPanel, Size.Notifications, Size.Picture, Size.AlertCentre)
const val LONG_GROW = 460
/* A grown face kept its open size while it faded over the collapsing bubble; it goes in the first frames of the collapse, as .face:not(.showing) does. */
private const val GROWN_FADE = 90
private const val PLAYER_FOLD_HANDOVER = 120

private val CLOSED_FACES = setOf(Face.Idle, Face.Media, Face.Timer, Face.Call, Face.Trip, Face.TorchIdle)

private fun faceTransition(from: Face, to: Face): ContentTransform {
    val isHandOver = from in CLOSED_FACES && to in CLOSED_FACES
    if (isHandOver) {
        return ContentTransform(
            scaleIn(tween(Durations.POP_IN, Durations.POP_OUT, Curves.enter)) + fadeIn(tween(120, Durations.POP_OUT)),
            scaleOut(tween(Durations.POP_OUT, easing = Curves.leave)) + fadeOut(tween(Durations.POP_OUT)),
            sizeTransform = SizeTransform(clip = false) { _, _ -> snap() },
        )
    }
    /* Closing the player faded it out in 90ms and popped the small face in, so nothing showed the artwork and wave going home; the player stays for the collapse and folds its art and wave into the small face's places, which only then fades in over them, and opening unfolds them from there. */
    if (from == Face.Media && to == Face.Player) {
        return ContentTransform(EnterTransition.None, fadeOut(tween(PLAYER_FOLD_HANDOVER)), sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
    }
    if (from == Face.Player && to == Face.Media) {
        return ContentTransform(fadeIn(tween(PLAYER_FOLD_HANDOVER, LONG_GROW - PLAYER_FOLD_HANDOVER)), fadeOut(tween(PLAYER_FOLD_HANDOVER, LONG_GROW)), sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
    }
    val arriving = if (to in CLOSED_FACES) Durations.COLLAPSE / 2 else Durations.CONTENT_DROP
    return ContentTransform(fadeIn(tween(160, arriving)), fadeOut(tween(if (to in CLOSED_FACES) GROWN_FADE else 120)), sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
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
        Face.TorchIdle -> TorchIdleFace(appearance)
    }
}
