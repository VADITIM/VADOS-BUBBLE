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
    val isMenu = bubble.size == Size.Notifications
    /* Opening the Dashboard shrank Main to the punch hole on the closed pill's bouncy curve with its face still drawn, slower-looking than the menu's retract; both sweep down to a bare dot the same way. */
    val isPunch = isMenu || bubble.isPunched
    val wasMenu = previous[0] == Size.Notifications
    SideEffect {
        wasClosed[0] = isClosed
        previous[0] = bubble.size
    }
    /* A grown box collapsing on the 1.7 overshoot undershot the closed pill and slammed back, and the reach curve home still bounced too hard, so every return home rides the gentler settle curve; opening had no bounce at all on the grow curve, and the Clock's 1.7 overshoot was a little too much, so it takes a 1.5 one. */
    /* Main shrank to the punch hole on the open curve with its face still drawn, and the first drop sat over it, so the shrink never read; it sweeps down to a bare dot and only its face's return waits for the cards to melt home. */
    val easing = if (isPunch) Curves.sweep else if (isReturning) Curves.settle else if (isClosed) Curves.bubble else Curves.open
    val faceShown = animateFloatAsState(if (isPunch) 0f else 1f, if (isPunch) tween(MENU_FACE_OUT) else tween(MENU_FACE_IN, if (wasMenu) MENU_FACE_RETURN else 0), label = "menuFace")
    val isAlertHome = isClosed && previous[0] in ALERT_SIZES && bubble.homing.isNotEmpty()
    val follow = if (bubble.isDragging || isAlertHome) snap<Float>() else tween(growth, easing = easing)
    val width = animateFloatAsState(target.width, follow, label = "width")
    val height = animateFloatAsState(target.height, if (isAlertHome) snap() else tween(growth, easing = easing), label = "height")
    val top = animateFloatAsState(target.top, if (isAlertHome) snap() else tween(growth, easing = if (isPunch) Curves.sweep else if (isClosed) Curves.settle else Curves.open), label = "top")
    val shift = animateFloatAsState(target.shift, follow, label = "shift")
    val radius = animateFloatAsState(target.radius, if (isAlertHome) snap() else tween(growth, easing = FastOutSlowInEasing), label = "radius")
    val held = animateFloatAsState(bubble.holdScale, tween(Durations.HOLD, easing = Curves.bubble), label = "hold")
    val scale = androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { held.value * (1f + bubble.landing) } }
    val shown = animateFloatAsState(if (bubble.isLent) 0f else 1f, if (bubble.isLent) tween(Durations.GROW) else snap(), label = "lent")
    val rise = animateFloatAsState(
        if (bubble.isRisen) MainBubble.EDGE_OVER else 0f,
        if (bubble.isRisen) tween(Durations.CONTENT_DROP, Durations.GROW, Curves.grow) else tween(Durations.CONTENT_DROP, easing = Curves.grow),
        label = "rise",
    )
    val centre = Island.screenWidth / 2f
    val topTargets = remember { floatArrayOf(target.top, target.top) }
    if (topTargets[0] != target.top) {
        topTargets[1] = topTargets[0]
        topTargets[0] = target.top
    }

    Blob(Island.liquid) {
        val density = Island.density
        // Main coming back down from past the screen edge as the Dashboard left overshot its resting top on the settle curve and bared the punch hole above it; the overshoot is taken as extra height upward so the top edge never passes its rest.
        val cover = if (topTargets[1] < topTargets[0]) (top.value - topTargets[0]).coerceAtLeast(0f) else 0f
        val grownWidth = width.value * scale.value
        val grownHeight = height.value * scale.value + cover
        val left = centre + shift.value - grownWidth / 2f + bubble.drag.x
        val upper = top.value - cover + bubble.drag.y + bubble.bumpLift
        BlobShape(
            Rect(left * density, upper * density, (left + grownWidth) * density, (upper + grownHeight) * density),
            radius.value * scale.value * density,
            bubble.border(),
            shown.value,
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
                    alpha = shown.value * faceShown.value
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
                targetState = bubble.mainFace,
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

private val ALERT_SIZES = setOf(Size.Alert, Size.AlertCentre, Size.Image)
private val LONG_SIZES = setOf(Size.Player, Size.TimerPanel, Size.TripPanel, Size.TransferPanel, Size.Picture, Size.AlertCentre)
const val LONG_GROW = 460
private const val MENU_FACE_OUT = 100
private const val MENU_FACE_IN = 180
private const val MENU_FACE_RETURN = 300
/* A grown face kept its open size while it faded over the collapsing bubble; it goes in the first frames of the collapse, as .face:not(.showing) does. */
private const val GROWN_FADE = 90
private const val PLAYER_FOLD_HANDOVER = 120

private val CLOSED_FACES = setOf(Face.Idle, Face.Media, Face.Timer, Face.Call, Face.Trip, Face.TorchIdle, Face.Transfer)

private fun faceTransition(from: Face, to: Face): ContentTransform {
    val isHandOver = from in CLOSED_FACES && to in CLOSED_FACES
    /* The transfer's arrow and ring make their own entrance, and the face they replace has already cleared itself in the yield; a pop on top of that drew the arrival twice. */
    if (isHandOver && to == Face.Transfer) {
        return ContentTransform(EnterTransition.None, fadeOut(tween(Durations.POP_OUT)), sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
    }
    if (isHandOver && to == Face.Media && Island.bubble.mediaEntrance == MediaEntrance.Placed) {
        return ContentTransform(EnterTransition.None, fadeOut(tween(Durations.POP_OUT)), sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
    }
    if (isHandOver && Island.bubble.isSwapArriving) {
        return ContentTransform(EnterTransition.None, fadeOut(tween(Durations.POP_OUT)), sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
    }
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
        Face.Transfer -> TransferFace(bubble, appearance)
        Face.TransferPanel -> TransferPanel(bubble, appearance)
    }
}
