package com.v.island.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
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
    Column(Modifier.fillMaxSize().padding(start = 6.4.dp, end = 6.4.dp, top = 12.dp, bottom = 8.dp)) {
        if (bubble.groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BasicText("Nothing waiting", style = type(0.75f, Palette.textFaint, appearance.contentFont, appearance.contentScale))
            }
            return@Column
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(bubble.notificationsScroll, enabled = false)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { bubble.reportNotificationsList(it.height / Island.density) }) {
                bubble.groups.forEachIndexed { index, group ->
                    key(group.id) { NotificationRow(bubble, group, index, index == bubble.groups.lastIndex, appearance) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(bubble: MainBubble, group: NoticeGroup, index: Int, isLast: Boolean, appearance: Appearance) {
    val entry = group.head
    val delayMilliseconds = ROW_ENTER_DELAY + minOf(index, ROW_STAGGER_LIMIT) * ROW_STAGGER
    val slide = remember { Animatable(0f) }
    val arrival = remember { Animatable(0f) }
    var isPressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(delayMilliseconds.toLong())
        arrival.animateTo(1f, tween(191, easing = ROW_ARRIVE))
    }
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 5.6.dp, end = 5.6.dp, bottom = if (isLast) 0.dp else 6.4.dp)
            .graphicsLayer {
                translationX = slide.value * density
                alpha = arrival.value * (1f - (abs(slide.value) / SWIPE_AWAY).coerceIn(0f, 1f))
                translationY = (1f - arrival.value) * 10f * density
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.textBright.copy(alpha = if (isPressing) 0.11f else 0.05f))
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
            .padding(horizontal = 12.dp, vertical = 12.8.dp),
        horizontalArrangement = Arrangement.spacedBy(13.6.dp),
    ) {
        val badge = entry.appIcon ?: entry.icon
        if (badge != null) {
            Image(badge, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(entry.accent), contentAlignment = Alignment.Center) {
                BasicText(entry.appName.ifEmpty { entry.app }.trim().take(1).uppercase(), style = type(0.9f, Palette.ground, appearance.headingFont))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RevealLabel(
                    entry.appName.ifEmpty { entry.app }.uppercase(),
                    type(0.68f, entry.accent, appearance.headingFont, appearance.headingScale, spacing = 0.14f, weight = FontWeight.Bold),
                    entry.accent,
                    delayMilliseconds + 70,
                    Modifier.weight(1f, fill = false),
                )
                if (group.entries.size > 1) {
                    BasicText(
                        group.entries.size.toString(),
                        Modifier.border(1.dp, Palette.borderControl, RoundedCornerShape(50)).padding(horizontal = 6.4.dp),
                        style = type(0.62f, Palette.textMuted, appearance.headingFont, appearance.headingScale),
                    )
                }
                Spacer(Modifier.weight(1f))
                val age = agoText(group.entries.maxOf { it.postedAt })
                if (age.isNotEmpty()) BasicText(age, style = type(0.68f, Palette.textLabel, appearance.headingFont, appearance.headingScale))
            }
            val typedFrom = delayMilliseconds + BODY_TYPE_AFTER
            var titleTyping = 0f
            if (entry.title.isNotEmpty()) {
                val interval = typeInterval(entry.title.length)
                titleTyping = interval * entry.title.length
                TypedText(entry.title, type(0.95f, Palette.textBright, appearance.headingFont, appearance.headingScale, lineHeight = 1.3f), typedFrom, Modifier.padding(top = 3.2.dp), interval = interval)
            }
            val lines = group.entries.flatMap { one ->
                if (one.messages.isNotEmpty()) one.messages.map { it.text } else listOf(one.text.ifEmpty { if (entry.title.isEmpty()) one.title else "" })
            }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val interval = typeInterval(lines.sumOf { it.length })
                var typedBefore = 0
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.2.dp)) {
                    lines.forEach { line ->
                        TypedText(
                            line,
                            type(0.86f, Palette.textBody, appearance.contentFont, appearance.contentScale, lineHeight = 1.45f),
                            typedFrom + titleTyping.toInt() + (typedBefore * interval).toInt(),
                            interval = interval,
                        )
                        typedBefore += line.length
                    }
                }
            }
        }
    }
}

/* Mirrors the notification-row-in keyframes in pill.css. */
private val ROW_ARRIVE = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

private const val ROW_ENTER_DELAY = 104
private const val ROW_STAGGER = 30
private const val ROW_STAGGER_LIMIT = 10
private const val BODY_TYPE_AFTER = 174
private const val SWIPE_SLOP = 10f
private const val SWIPE_DISMISS = 90f
private const val SWIPE_AWAY = 400f
