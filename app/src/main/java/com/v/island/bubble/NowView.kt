package com.v.island.bubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Faces
import com.v.island.vas.Palette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun NowView(now: NowBubble) {
    val isLocked = Island.isLocked
    LaunchedEffect(isLocked) { now.onLock(isLocked) }
    val isWanted = now.isWanted
    LaunchedEffect(isWanted) { now.settle() }

    var held by remember { mutableStateOf<Media?>(null) }
    Island.media?.let { held = it }
    val media = held

    val width = animateFloatAsState(now.restWidth, tween(NowBubble.GROW, easing = Curves.split), label = "nowWidth")
    val height = animateFloatAsState(now.restHeight, tween(NowBubble.GROW, easing = Curves.bubble), label = "nowHeight")
    val radius = animateFloatAsState(now.restRadius, tween(NowBubble.GROW), label = "nowRadius")
    val bottom = animateFloatAsState(now.restBottom, tween(NowBubble.GROW, easing = Curves.grow), label = "nowBottom")
    val press = animateFloatAsState(if (now.isPressing) 1.03f else 1f, tween(320, easing = Curves.split), label = "nowPress")

    val isTouchable = (now.phase == NowPhase.Rest || now.phase == NowPhase.Arriving) && Island.media != null
    SideEffect {
        if (isTouchable) Island.regions[Region.Now] = now.rest() else Island.regions.remove(Region.Now)
    }
    if (!now.isShowing || media == null) return

    fun box(): Pair<Rect, Float> {
        val wide = width.value
        val tall = height.value
        val ball = min(wide, tall)
        val share = now.ballShare
        val shapeWide = wide + (ball - wide) * share
        val shapeTall = tall + (ball - tall) * share
        val scale = now.poseScale * press.value
        val centreX = Island.screenWidth / 2f + now.poseX
        val centreY = bottom.value - tall / 2f + now.poseY
        val corner = radius.value + (ball / 2f - radius.value) * share
        return Rect(centreX - shapeWide * scale / 2f, centreY - shapeTall * scale / 2f, centreX + shapeWide * scale / 2f, centreY + shapeTall * scale / 2f) to corner * scale
    }

    Blob(Island.liquid) {
        val (rect, corner) = box()
        val density = Island.density
        BlobShape(Rect(rect.left * density, rect.top * density, rect.right * density, rect.bottom * density), corner * density, Palette.border, now.opacity)
    }

    val appearance = Island.appearance
    Box(
        Modifier
            .offset {
                val (rect, _) = box()
                IntOffset((rect.left * Island.density).roundToInt(), (rect.top * Island.density).roundToInt())
            }
            .layoutAt { box().first }
            .graphicsLayer {
                val (_, corner) = box()
                alpha = now.opacity
                clip = true
                shape = RoundedCornerShape(corner.dp)
            }
            .pointerInput(now) { followNow(now) },
    ) {
        Box(Modifier.wrapContentSize(unbounded = true).requiredSize(width.value.dp, height.value.dp).graphicsLayer {
            val scale = now.poseScale * press.value
            scaleX = scale
            scaleY = scale
        }.align(Alignment.Center)) {
            when {
                now.isCompact -> CompactNow(now, media)
                now.isInPanel || !now.isLockOpen -> RestNow(now, media, appearance, isOpen = false)
                else -> RestNow(now, media, appearance, isOpen = true)
            }
        }
        if (now.ballShare > 0f && media.art != null) {
            Image(media.art, null, Modifier.fillMaxSize().graphicsLayer { alpha = now.ballShare.coerceIn(0f, 1f) }, contentScale = ContentScale.Crop)
        }
    }
}

private fun Modifier.layoutAt(rect: () -> Rect): Modifier = this.then(
    Modifier.layout { measurable, _ ->
        val box = rect()
        val density = Island.density
        val wide = (box.width * density).roundToInt().coerceAtLeast(1)
        val tall = (box.height * density).roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(wide, tall))
        layout(wide, tall) { placeable.place(0, 0) }
    },
)

private fun Modifier.sprig(shown: () -> Float): Modifier = graphicsLayer {
    val share = shown()
    alpha = share.coerceIn(0f, 1f)
    translationY = (1f - share) * NowBubble.LEAVE_DROP * density
    val scale = 0.94f + 0.06f * share
    scaleX = scale
    scaleY = scale
}

@Composable
private fun Art(media: Media, modifier: Modifier, corner: Float) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(Palette.sunken)) {
        media.art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun Labels(now: NowBubble, media: Media, titleSize: Float, artistSize: Float, artistGap: Float, isCentred: Boolean) {
    val align = if (isCentred) TextAlign.Center else TextAlign.Start
    val titleStyle = type(titleSize, Palette.textBright, Faces.mono).copy(textAlign = align)
    val artistStyle = type(artistSize, media.accent, Faces.mono, spacing = 0.08f).copy(textAlign = align)
    Column(Modifier.fillMaxWidth()) {
        if (now.areLabelsShown) {
            key(now.labelsGeneration, media.title, media.artist) {
                TypedText(media.title, titleStyle, 0, Modifier.fillMaxWidth(), isLine = true)
                TypedText(media.artist.uppercase(), artistStyle, 120, Modifier.fillMaxWidth().padding(top = artistGap.dp), isLine = true)
            }
        } else {
            BasicText(" ", style = titleStyle)
            BasicText(" ", Modifier.padding(top = artistGap.dp), style = artistStyle)
        }
    }
}

@Composable
private fun CompactNow(now: NowBubble, media: Media) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Art(media, Modifier.size(26.dp), 8f)
        Box(Modifier.weight(1f).sprig { now.metaShown }) {
            if (now.areLabelsShown) key(now.labelsGeneration, media.title) {
                TypedText(media.title, type(0.74f, Palette.textBright, Faces.mono), 0, Modifier.fillMaxWidth(), isLine = true)
            }
        }
    }
}

@Composable
private fun RestNow(now: NowBubble, media: Media, appearance: Appearance, isOpen: Boolean) {
    Column(Modifier.fillMaxSize()) {
        if (isOpen) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Art(media, Modifier.fillMaxWidth().aspectRatio(1f), 24f)
                Box(Modifier.sprig { now.metaShown }) { Labels(now, media, 1.15f, 0.72f, 5.6f, isCentred = true) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 13.6.dp, top = 13.6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Art(media, Modifier.size(44.dp), 8f)
                Box(Modifier.weight(1f).sprig { now.metaShown }) { Labels(now, media, 0.88f, 0.66f, 3.2f, isCentred = false) }
            }
        }
        Spacer(Modifier.weight(1f))
        Timeline(now, media, isOpen)
    }
}

@Composable
private fun Timeline(now: NowBubble, media: Media, isOpen: Boolean) {
    rememberNow(500)
    val position = if (now.isScrubbing) now.scrubPosition
    else media.position + if (media.isPlaying) android.os.SystemClock.elapsedRealtime() - media.receivedAt else 0L
    val ratio = if (media.duration > 0) (position.toFloat() / media.duration).coerceIn(0f, 1f) else 0f
    val tall = animateFloatAsState(if (now.isScrubbing) 52f else if (isOpen) 44f else 28f, tween(140), label = "waveTall")
    Column(
        Modifier.fillMaxWidth()
            .padding(start = (if (isOpen) 25.6f else 17.6f).dp, end = (if (isOpen) 25.6f else 17.6f).dp, bottom = (if (isOpen) 22.4f else 14.4f).dp)
            .sprig { now.timelineShown }
            .pointerInput(media.duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (media.duration <= 0) return@awaitEachGesture
                    down.consume()
                    now.isScrubbing = true
                    now.scrubPosition = seekAt(down.position.x, size.width, media.duration)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        now.scrubPosition = seekAt(change.position.x, size.width, media.duration)
                        if (!change.pressed) {
                            com.v.island.vas.Haptics.play(com.v.island.vas.Haptic.Tap)
                            Island.actions.mediaSeek(now.scrubPosition)
                            break
                        }
                    }
                    now.isScrubbing = false
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 5.6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = type(0.6f, Palette.textMuted, Faces.mono, spacing = 0.1f)
            BasicText(clockReading(position), style = style)
            BasicText(clockReading(media.duration), style = style)
        }
        Wave(media, WAVE_BARS, ratio = { ratio }, modifier = Modifier.fillMaxWidth().height(tall.value.dp))
    }
}

private fun seekAt(x: Float, width: Int, duration: Long): Long =
    ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * duration).roundToInt().toLong()

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.followNow(now: NowBubble) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = true)
    if (now.phase != NowPhase.Rest) return@awaitEachGesture
    now.isPressing = true
    var isHeld = false
    var isSwiped = false
    var travel = 0f
    val startedAt = down.uptimeMillis
    while (true) {
        val remaining = Durations.HOLD - (android.os.SystemClock.uptimeMillis() - startedAt)
        val event = if (!isHeld && !isSwiped && travel <= NowBubble.HOLD_BLOCK && remaining > 0) {
            withTimeoutOrNull(remaining) { awaitPointerEvent() }
        } else awaitPointerEvent()
        if (event == null) {
            isHeld = true
            now.isPressing = false
            now.hold()
            continue
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (change.isConsumed) {
            now.isPressing = false
            break
        }
        val across = (change.position.x - down.position.x) / density
        val drop = (change.position.y - down.position.y) / density
        travel = maxOf(travel, hypot(across, drop))
        if (!change.pressed) {
            now.isPressing = false
            if (!isHeld && !isSwiped && travel <= NowBubble.HOLD_BLOCK && !Island.wasCancelled) {
                com.v.island.vas.Haptics.play(com.v.island.vas.Haptic.Tap)
                now.toggle()
            }
            break
        }
        if (isHeld || isSwiped || travel <= NowBubble.HOLD_BLOCK) continue
        now.isPressing = false
        if (now.swipe(across, drop)) isSwiped = true
        if (abs(across) > NowBubble.SWIPE || abs(drop) > NowBubble.SWIPE) isSwiped = true
    }
    now.isPressing = false
}
