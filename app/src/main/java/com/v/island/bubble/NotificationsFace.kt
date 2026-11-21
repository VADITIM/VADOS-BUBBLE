package com.v.island.bubble

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import com.v.island.vas.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

fun agoText(postedAt: Long): String {
    if (postedAt <= 0L) return ""
    val minutes = (System.currentTimeMillis() - postedAt) / 60_000
    if (minutes < 1) return "now"
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    return if (hours < 24) "${hours}h" else "${hours / 24}d"
}

@Composable
fun NotificationsFace(bubble: MainBubble, appearance: Appearance) {
    Column(Modifier.fillMaxSize().padding(start = 6.4.dp, end = 6.4.dp, top = 14.4.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RevealLabel("NOTIFICATIONS", type(0.63f, Palette.textLabel, appearance.headingFont, appearance.headingScale, spacing = 0.19f), appearance.accent)
            val total = bubble.groups.sumOf { it.entries.size }
            if (total > 0) BasicText(total.toString(), style = type(0.63f, Palette.textMuted, appearance.headingFont, appearance.headingScale))
        }
        Spacer(Modifier.size(8.dp))
        if (bubble.groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BasicText("Nothing waiting", style = type(0.75f, Palette.textFaint, appearance.contentFont, appearance.contentScale))
            }
            return@Column
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(bubble.notificationsScroll, enabled = false)) {
            bubble.groups.forEachIndexed { index, group ->
                key(group.id) { NotificationRow(bubble, group, index, appearance) }
            }
        }
    }
}

@Composable
private fun NotificationRow(bubble: MainBubble, group: NoticeGroup, index: Int, appearance: Appearance) {
    val entry = group.head
    val delayMilliseconds = ROW_ENTER_DELAY + minOf(index, ROW_STAGGER_LIMIT) * ROW_STAGGER
    val slide = remember { Animatable(0f) }
    val arrival = remember { Animatable(0f) }
    var isPressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(delayMilliseconds.toLong())
        arrival.animateTo(1f, tween(360, easing = Curves.enter))
    }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                translationX = slide.value * density
                alpha = arrival.value * (1f - (abs(slide.value) / SWIPE_AWAY).coerceIn(0f, 1f))
                translationY = (1f - arrival.value) * 10f * density
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressing) Palette.textBright.copy(alpha = 0.11f) else androidx.compose.ui.graphics.Color.Transparent)
            .pointerInput(group.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressing = true
                    var isSwiping = false
                    var isScrolling = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val across = (change.position.x - down.position.x) / density
                        val vertical = (change.position.y - down.position.y) / density
                        if (!isSwiping && !isScrolling) {
                            if (abs(across) > SWIPE_SLOP && abs(across) > abs(vertical)) isSwiping = true
                            else if (abs(vertical) > SWIPE_SLOP) isScrolling = true
                        }
                        if (isSwiping || isScrolling) isPressing = false
                        if (isSwiping) scope.launch { slide.snapTo(across) }
                        if (!change.pressed) {
                            isPressing = false
                            if (isSwiping) {
                                change.consume()
                                if (abs(across) > SWIPE_DISMISS) {
                                    Haptics.play(Haptic.Dismiss)
                                    scope.launch {
                                        slide.animateTo(SWIPE_AWAY * kotlin.math.sign(across), tween(200, easing = Curves.grow))
                                        bubble.dismissGroup(group)
                                    }
                                } else scope.launch { slide.animateTo(0f, tween(260, easing = Curves.enter)) }
                            } else if (!isScrolling) {
                                change.consume()
                                bubble.openGroup(group)
                            }
                            break
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val badge = entry.appIcon ?: entry.icon
        if (badge != null) {
            Image(badge, null, Modifier.size(26.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(26.dp).clip(CircleShape).background(entry.accent), contentAlignment = Alignment.Center) {
                BasicText(entry.appName.trim().take(1).uppercase(), style = type(0.7f, Palette.ground, appearance.headingFont))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RevealLabel(
                    entry.appName.ifEmpty { entry.app }.uppercase(),
                    type(0.6f, entry.accent, appearance.headingFont, appearance.headingScale, spacing = 0.16f),
                    entry.accent,
                    delayMilliseconds,
                    Modifier.weight(1f, fill = false),
                )
                if (group.entries.size > 1) {
                    BasicText(group.entries.size.toString(), style = type(0.6f, Palette.textMuted, appearance.headingFont, appearance.headingScale))
                }
                val newest = group.entries.maxOf { it.postedAt }
                val age = agoText(newest)
                if (age.isNotEmpty()) BasicText(age, style = type(0.6f, Palette.textFaint, appearance.headingFont, appearance.headingScale))
            }
            if (entry.title.isNotEmpty()) {
                TypedText(entry.title, type(0.72f, Palette.textBright, appearance.contentFont, appearance.contentScale, lineHeight = 1.35f), delayMilliseconds + BODY_TYPE_AFTER, maxLines = 1)
            }
            val lines = group.entries.flatMap { one ->
                if (one.messages.isNotEmpty()) one.messages.map { it.text } else listOf(one.text.ifEmpty { if (entry.title.isEmpty()) one.title else "" })
            }.filter { it.isNotEmpty() }
            lines.forEach { line ->
                TypedText(line, type(0.72f, Palette.textBody, appearance.contentFont, appearance.contentScale, lineHeight = 1.4f), delayMilliseconds + BODY_TYPE_AFTER + 120)
            }
        }
    }
}

private const val ROW_ENTER_DELAY = 104
private const val ROW_STAGGER = 30
private const val ROW_STAGGER_LIMIT = 10
private const val BODY_TYPE_AFTER = 174
private const val SWIPE_SLOP = 10f
private const val SWIPE_DISMISS = 90f
private const val SWIPE_AWAY = 400f
