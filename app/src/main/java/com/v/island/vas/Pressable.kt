package com.v.island.vas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

fun Modifier.pressable(
    pressedScale: Float = 0.95f,
    haptic: Haptic? = Haptic.Tap,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val click = rememberUpdatedState(onClick)
    val longClick = rememberUpdatedState(onLongClick)
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                scope.launch { scale.animateTo(pressedScale, tween(Durations.PRESS_DOWN, easing = Curves.powerTwoOut)) }
                tryAwaitRelease()
                scope.launch { scale.animateTo(1f, tween(Durations.PRESS_UP, easing = Curves.enter)) }
            },
            onLongPress = longClick.value?.let { { _ -> it() } },
            onTap = {
                haptic?.let(Haptics::play)
                click.value()
            },
        )
    }
}
