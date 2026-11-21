package com.v.island.bubble

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import com.v.island.vas.Palette
import com.v.island.vas.pressable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val KEYWORDS = listOf(
    "reel", "reels", "story", "stories", "post", "live", "photo", "video", "gif", "sticker", "voice message", "audio",
    "call", "missed call", "attachment", "document", "location", "poll", "event", "invite", "mention", "reply", "reaction",
    "foto", "sprachnachricht", "anruf", "umfrage", "einladung",
)
private val LINK_PATTERN = Regex("""\bhttps?://\S+|\bwww\.\S+""", RegexOption.IGNORE_CASE)
private val TIME_PATTERN = Regex(
    listOf(
        """\d{1,2}:\d{2}(?::\d{2})?""",
        """\d{1,2}\.\d{1,2}\.(?:\d{2,4})?""",
        """\d{4}-\d{2}-\d{2}""",
        """\d{1,2}\.?\s?(?:Jan|Feb|Mär|Mar|Apr|Mai|May|Jun|Jul|Aug|Sep|Okt|Oct|Nov|Dez|Dec)[a-zä]*\.?""",
    ).joinToString("|"),
    RegexOption.IGNORE_CASE,
)
private val MONEY_PATTERN = Regex("""[€$£¥]\s?\d[\d.,]*|\d[\d.,]*\s?(?:[€$£¥]|(?:EUR|USD|GBP|CHF)\b)""", RegexOption.IGNORE_CASE)
private val KEYWORD_PATTERN = Regex("""\b(?:${KEYWORDS.joinToString("|") { Regex.escape(it) }})\b""", RegexOption.IGNORE_CASE)

fun markUp(message: String, accent: Color): AnnotatedString {
    val marks = sortedMapOf<Int, Pair<Int, SpanStyle>>()
    fun claim(pattern: Regex, style: SpanStyle) = pattern.findAll(message).forEach { found ->
        val isFree = marks.none { (start, mark) -> found.range.first < mark.first && found.range.last + 1 > start }
        if (isFree) marks[found.range.first] = found.range.last + 1 to style
    }
    claim(LINK_PATTERN, SpanStyle(color = Palette.link))
    claim(TIME_PATTERN, SpanStyle(background = Palette.markTime))
    claim(MONEY_PATTERN, SpanStyle(background = Palette.markMoney))
    claim(KEYWORD_PATTERN, SpanStyle(color = accent))
    return buildAnnotatedString {
        append(message)
        marks.forEach { (start, mark) -> addStyle(mark.second, start, mark.first) }
    }
}

@Composable
fun AlertFace(bubble: MainBubble, appearance: Appearance) {
    val shown = bubble.notice ?: return
    val isPicture = bubble.size == Size.Picture
    val isLarge = isPicture
    Column(
        Modifier.fillMaxSize().padding(horizontal = if (isLarge) 17.6.dp else 15.2.dp, vertical = if (isLarge) 14.4.dp else 9.9.dp),
        verticalArrangement = if (isPicture) Arrangement.spacedBy(9.6.dp) else Arrangement.spacedBy(6.4.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MainBubble.HOLE_GAP.dp)) {
            Row(Modifier.padding(horizontal = 7.2.dp, vertical = 2.4.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = shown.icon
                if (icon != null && appearance.identity != 1) {
                    Image(icon, null, Modifier.size(if (isLarge) 24.dp else 18.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.size(7.2.dp))
                }
                if (appearance.identity != 0) {
                    FadingLine(
                        shown.appName.uppercase(),
                        type(0.63f, shown.accent, appearance.headingFont, appearance.headingScale, spacing = 0.19f),
                    )
                }
            }
            FadingLine(
                bubble.whoSent(shown),
                type(0.68f, Palette.textMuted, appearance.headingFont, appearance.headingScale, spacing = 0.04f),
                Modifier.weight(1f),
            )
        }
        val isStacked = bubble.alertLines.size > 1
        Column(
            (if (isStacked) Modifier.weight(1f, fill = false).fadeTop() else Modifier),
            verticalArrangement = Arrangement.Bottom,
        ) {
            bubble.alertLines.forEach { line ->
                BasicText(
                    markUp(line, shown.accent),
                    style = type(0.78f, Palette.textPrimary, appearance.contentFont, appearance.contentScale, lineHeight = 1.4f),
                    maxLines = 3,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        val picture = shown.picture
        if (picture != null) {
            Image(
                picture,
                null,
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)),
                contentScale = if (isPicture) ContentScale.Fit else ContentScale.Crop,
            )
        }
    }
}

@Composable
fun PlayerFace(bubble: MainBubble, appearance: Appearance) {
    val media = Island.media ?: return
    rememberNow(500)
    val position = if (bubble.isScrubbing) bubble.scrubPosition
    else media.position + if (media.isPlaying) android.os.SystemClock.elapsedRealtime() - media.receivedAt else 0L
    val ratio = if (media.duration > 0) (position.toFloat() / media.duration).coerceIn(0f, 1f) else 0f
    var artDrag by remember { mutableFloatStateOf(0f) }
    val artShift by animateFloatAsState(artDrag, tween(210, easing = Curves.grow), label = "art")
    var waveWidth by remember { mutableIntStateOf(1) }
    val band by animateFloatAsState(if (bubble.isScrubbing) 110f else 52f, tween(220, easing = Curves.grow), label = "band")
    Box(Modifier.fillMaxSize()) {
        val art = media.art
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationX = artShift * density
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.13f to Color.Black, 0.87f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.09f to Color.Black, 0.91f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
                .background(Palette.sunken)
                .pointerInput(media.packageName) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var across = 0f
                        var isSwiping = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            across = (change.position.x - down.position.x) / density
                            if (!isSwiping && abs(across) > PLAYER_HOLD_OFF) isSwiping = true
                            if (isSwiping) artDrag = across * PLAYER_DRAG_FOLLOW
                            if (!change.pressed) {
                                if (isSwiping) {
                                    change.consume()
                                    if (abs(across) > PLAYER_SWIPE) {
                                        Haptics.play(Haptic.Tap)
                                        Island.actions.mediaControl(if (across < 0) "next" else "previous")
                                        artDrag = sign(across) * PLAYER_THROW
                                    }
                                    artDrag = 0f
                                } else {
                                    change.consume()
                                    Haptics.play(Haptic.Tap)
                                    Island.actions.mediaControl(if (media.isPlaying) "pause" else "play")
                                }
                                break
                            }
                        }
                    }
                },
        ) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.08f), Color.Transparent)))
                .padding(start = 16.8.dp, end = 16.8.dp, top = 13.6.dp, bottom = 41.6.dp),
        ) {
            TypedText(media.title, type(1.02f, Palette.textBright, appearance.mainFont, appearance.mainScale, lineHeight = 1.25f), 0, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            TypedText(media.artist.uppercase(), type(0.8f, media.accent, appearance.mainFont, appearance.mainScale, spacing = 0.1f, lineHeight = 1.25f), 120, maxLines = 1)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = (52f - band / 2f).dp).fillMaxWidth().height(band.dp)
                .pointerInput(media.duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (media.duration <= 0) return@awaitEachGesture
                        down.consume()
                        waveWidth = size.width
                        bubble.isScrubbing = true
                        bubble.scrubPosition = seekAt(down.position.x, waveWidth, media.duration)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            bubble.scrubPosition = seekAt(change.position.x, waveWidth, media.duration)
                            if (!change.pressed) {
                                Haptics.play(Haptic.Tap)
                                Island.actions.mediaSeek(bubble.scrubPosition)
                                break
                            }
                        }
                        bubble.isScrubbing = false
                    }
                },
        ) {
            Wave(media, WAVE_BARS, ratio = { ratio }, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.6f))))
                .padding(start = 16.8.dp, end = 16.8.dp, top = 32.dp, bottom = 9.6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = type(0.7f, Palette.textMuted, appearance.mainFont, appearance.mainScale, spacing = 0.08f)
            BasicText(clockReading(position), style = style)
            if (media.app == "telegram" || media.app == "whatsapp") SpeedToggle(style)
            BasicText(clockReading(media.duration), style = style)
        }
    }
}

private val SPEEDS = listOf(1f, 1.5f, 2f)

@Composable
private fun SpeedToggle(style: androidx.compose.ui.text.TextStyle) {
    var index by remember { mutableIntStateOf(0) }
    BasicText(
        (if (SPEEDS[index] % 1f == 0f) SPEEDS[index].roundToInt().toString() else SPEEDS[index].toString()) + "×",
        Modifier.pressable {
            index = (index + 1) % SPEEDS.size
            Island.actions.mediaSpeed(SPEEDS[index])
        },
        style = style,
    )
}

private fun seekAt(x: Float, width: Int, duration: Long): Long =
    ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * duration).roundToInt().toLong()

private const val PLAYER_HOLD_OFF = 17f
private const val PLAYER_SWIPE = 105f
private const val PLAYER_DRAG_FOLLOW = 0.85f
private const val PLAYER_THROW = 340f

@Composable
fun PillButton(title: String, isLead: Boolean, accent: Color, appearance: Appearance, onClick: () -> Unit) {
    val color = if (isLead) accent else Palette.textBody
    BasicText(
        title.uppercase(),
        Modifier.pressable(pressedScale = 0.96f, haptic = null, onClick = onClick)
            .border(1.dp, if (isLead) accent else Palette.borderControl, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.4.dp, vertical = 6.4.dp),
        style = type(0.63f, color, appearance.mainFont, appearance.mainScale, spacing = 0.14f),
    )
}

@Composable
fun TimerPanel(bubble: MainBubble, appearance: Appearance) {
    val timer = Island.timer ?: return
    val now = rememberNow(1000)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 17.6.dp),
        verticalArrangement = Arrangement.spacedBy(11.2.dp, Alignment.CenterVertically),
    ) {
        RevealLabel(timer.appName.ifEmpty { "Clock" }.uppercase(), type(0.6f, timer.accent, appearance.mainFont, appearance.mainScale, spacing = 0.19f), timer.accent)
        BasicText(
            remainingText(timer, now),
            style = type(1.6f, if (timer.isPaused) Palette.textMuted else Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.08f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
            timer.actions.forEachIndexed { index, action ->
                PillButton(action.title, index == 0, timer.accent, appearance) { bubble.timerAction(action) }
            }
        }
    }
}

@Composable
fun TripPanel(bubble: MainBubble, appearance: Appearance) {
    val trip = Island.trip ?: return
    val now = rememberNow(1000)
    val body = type(0.7f, Palette.textBody, appearance.mainFont, appearance.mainScale)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 17.6.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            RevealLabel(trip.appName.uppercase(), type(0.6f, trip.accent, appearance.mainFont, appearance.mainScale, spacing = 0.19f), trip.accent)
            BasicText(tripRemaining(trip, now), style = type(1.3f, Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.06f))
        }
        trip.train?.let { FadingLine(it, type(1f, Palette.textBright, appearance.mainFont, appearance.mainScale)) }
        if (trip.origin != null || trip.destination != null) {
            FadingLine(listOfNotNull(trip.origin, trip.destination).joinToString(" → "), body)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.8.dp)) {
            trip.arrival?.let { BasicText(it, style = body) }
            trip.delay?.let { BasicText(it, style = body.copy(color = trip.accent)) }
            trip.platform?.let { BasicText("Gl. $it", style = body) }
        }
        trip.exitStop?.let { FadingLine("Ausstieg $it", body) }
        trip.nextStop?.let { FadingLine("Nächster Halt $it", body) }
        Column(Modifier.weight(1f).fadeTop(12.dp)) {
            trip.lines.forEach { FadingLine(it, type(0.62f, Palette.textMuted, appearance.mainFont, appearance.mainScale)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
            trip.actions.forEachIndexed { index, action ->
                PillButton(action.title, index == 0, trip.accent, appearance) { bubble.tripAction(action) }
            }
        }
    }
}
