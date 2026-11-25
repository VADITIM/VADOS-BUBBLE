package com.v.island.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v.island.vas.Palette
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

fun glyphSize(appearance: Appearance) = appearance.compactHeight - 10f

fun glyphInset(appearance: Appearance) = (appearance.compactHeight - glyphSize(appearance)) / 2f - 1f

@Composable
fun IdleFace(appearance: Appearance) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        val count = Island.unread
        if (count > 0) {
            BasicText(
                if (count > 9) "9+" else count.toString(),
                Modifier.padding(start = 11.dp),
                style = type(0.6f, Palette.textMuted, appearance.mainFont, appearance.mainScale, spacing = 0.08f),
            )
        }
    }
}

@Composable
private fun ModRow(appearance: Appearance, endPadding: Float, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(start = glyphInset(appearance).dp, end = endPadding.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun ArtCircle(media: Media?, diameter: Float, modifier: Modifier = Modifier) {
    val art = media?.art
    Box(modifier.size(diameter.dp).clip(CircleShape).background(Palette.sunken)) {
        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/* The tile stood at the glyph's height with 5 of pill around it and read as small; it is as tall as the pill less a slight padding, the same above, below and to its left. */
fun mediaTileHeight(appearance: Appearance) = appearance.compactHeight - 2f * MEDIA_TILE_INSET

fun mediaTileWidth(appearance: Appearance) = mediaTileHeight(appearance) * MEDIA_TILE_RATIO

/* The closed Media face was a round thumbnail, so nothing showed whose song it was; the artwork is a wide tile, padded from the pill by the same distance on every side and rounded concentric with it, and a new song pushes the old tile out past the left edge while the new one follows in. */
@Composable
fun MediaFace(appearance: Appearance) {
    val media = Island.media
    val padding = (appearance.compactHeight - glyphSize(appearance)) / 2f
    val tile = mediaTileWidth(appearance)
    val travel = MEDIA_TILE_INSET + tile
    Row(
        Modifier.fillMaxSize().padding(end = padding.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = media,
            modifier = Modifier.size(travel.dp, mediaTileHeight(appearance).dp).clipToBounds().fadeStart(MEDIA_TILE_INSET.dp),
            contentKey = { song -> song?.let { it.title + "|" + it.artist } },
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = {
                val spec = tween<IntOffset>(Durations.MORPH, easing = Curves.sweep)
                val shift = { _: Int -> -(travel * Island.density).toInt() }
                (slideInHorizontally(spec, shift) + fadeIn(tween(Durations.MORPH))) togetherWith (slideOutHorizontally(spec, shift) + fadeOut(tween(Durations.MORPH)))
            },
            label = "mediaTile",
        ) { song ->
            Box(Modifier.size(tile.dp, mediaTileHeight(appearance).dp).clip(RoundedCornerShape((appearance.compactHeight / 2f - MEDIA_TILE_INSET).dp)).background(Palette.sunken)) {
                val art = song?.art
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Wave(media, bars = EQUALIZER_BARS, ratio = { 1f }, modifier = Modifier.size(glyphSize(appearance).dp))
    }
}

@Composable
fun TimerGlyph(timer: Timer?, accent: Color, diameter: Float) {
    val now = rememberNow(1000)
    val isRunning = timer?.endsAt != null && !timer.isPaused
    val step = if (isRunning) floor((now % SWEEP_CYCLE) / (SWEEP_CYCLE / 12f)) else 0f
    Canvas(Modifier.size(diameter.dp)) {
        val ring = 2.dp.toPx()
        drawCircle(accent, radius = size.minDimension / 2f - ring / 2f, style = Stroke(ring))
        rotate(step * 30f) {
            val handWidth = 2.dp.toPx()
            drawRect(
                accent,
                topLeft = Offset(size.width / 2f - handWidth / 2f, size.height * 0.15f),
                size = Size(handWidth, size.height * 0.35f),
            )
        }
    }
}

fun remainingText(timer: Timer?, now: Long): String {
    timer ?: return ""
    if (timer.endsAt == null && timer.remaining == null) return timer.summary.substringBefore('/').trim()
    val left = timer.endsAt?.let { max(0L, Math.round((it - now) / 1000.0)) } ?: Math.round((timer.remaining ?: 0L) / 1000.0)
    val hours = left / 3600
    val minutes = (left % 3600) / 60
    val seconds = left % 60
    val pad = { value: Long -> value.toString().padStart(2, '0') }
    return if (hours > 0) "$hours:${pad(minutes)}:${pad(seconds)}" else "$minutes:${pad(seconds)}"
}

@Composable
fun TimerFace(appearance: Appearance) {
    val timer = Island.timer
    val now = rememberNow(1000)
    val accent = timer?.accent ?: appearance.accent
    ModRow(appearance, 6.4f) {
        TimerGlyph(timer, accent, glyphSize(appearance))
        FadingLine(
            remainingText(timer, now),
            type(0.62f, if (timer?.isPaused == true) Palette.textMuted else Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.02f),
            Modifier.widthIn(max = 104.dp),
        )
    }
}

@Composable
fun Avatar(call: Call?, accent: Color, diameter: Float) {
    val avatar = call?.avatar
    Box(Modifier.size(diameter.dp).clip(CircleShape).background(accent.copy(alpha = 0.85f))) {
        if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun CallFace(appearance: Appearance) {
    val call = Island.call
    val now = rememberNow(1000)
    ModRow(appearance, 6.4f) {
        Avatar(call, call?.accent ?: appearance.accent, glyphSize(appearance))
        BasicText(
            clockReading(now - (call?.since ?: now)),
            style = type(0.62f, Palette.textBright, appearance.mainFont, appearance.mainScale, spacing = 0.02f),
        )
    }
}

@Composable
fun TripMark(accent: Color, diameter: Float, appearance: Appearance) {
    Box(
        Modifier.size(diameter.dp, (diameter * 0.72f).dp).border(2.dp, accent, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "DB",
            style = type(diameter * 0.4f / 16f, accent, appearance.mainFont, weight = FontWeight.ExtraBold, spacing = -0.01f, lineHeight = 1f),
        )
    }
}

fun tripRemaining(trip: Trip?, now: Long): String {
    val endsAt = trip?.endsAt ?: return trip?.arrival.orEmpty()
    val minutes = max(0L, (endsAt - now + 59_999) / 60_000)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}

@Composable
fun TripFace(appearance: Appearance) {
    val trip = Island.trip
    val now = rememberNow(1000)
    ModRow(appearance, 6.4f) {
        TripMark(trip?.accent ?: appearance.accent, glyphSize(appearance), appearance)
        FadingLine(tripRemaining(trip, now), type(0.62f, Palette.textBright, appearance.mainFont, appearance.mainScale), Modifier.widthIn(max = 104.dp))
    }
}

const val SWEEP_CYCLE = 12_000L
const val MEDIA_TILE_RATIO = 1.5f
const val MEDIA_TILE_INSET = 3f
const val EQUALIZER_BARS = 5
const val WAVE_BARS = 54
private const val WAVE_BEAT = 480f
private const val WAVE_REST = 0.12f

private fun pulse(since: Float) = if (since < 45f) sin(since / 45f * PI.toFloat() / 2f) else exp(-(since - 45f) / 150f)

fun waveShape(media: Media?): FloatArray {
    val song = media?.let { it.title + "|" + it.artist }.orEmpty()
    var seed = 2166136261L.toInt()
    song.forEach { character -> seed = (seed xor character.code) * 16777619 }
    var level = 0.5f
    return FloatArray(WAVE_BARS) {
        seed = (seed xor (seed ushr 15)) * -2048144777
        level = level * 0.5f + ((seed ushr 8) and 0xffff) / 65535f * 0.5f
        0.24f + level * 0.76f
    }
}

@Composable
fun Wave(media: Media?, bars: Int, ratio: () -> Float, modifier: Modifier = Modifier) {
    val shape = remember(media?.title, media?.artist) { waveShape(media) }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying = media?.isPlaying == true
    val energy = remember { floatArrayOf(WAVE_REST, 0f) }
    LaunchedEffect(isPlaying, Island.isStageHidden) {
        if (Island.isStageHidden) return@LaunchedEffect
        while (true) {
            withFrameMillis { frame = it }
            if (!isPlaying && energy[0] <= WAVE_REST + 0.005f) break
        }
    }
    val accent = media?.accent ?: Color.White
    Canvas(modifier) {
        val now = frame.toFloat()
        val position = media?.let { it.position + if (it.isPlaying) android.os.SystemClock.elapsedRealtime() - it.receivedAt else 0L } ?: 0L
        val since = (position % WAVE_BEAT.toLong()).toFloat()
        energy[1] += ((if (isPlaying) pulse(since) else 0f) - energy[1]) * 0.35f
        val wanted = if (isPlaying) 0.36f + energy[1] * 0.16f else WAVE_REST
        energy[0] += (wanted - energy[0]) * 0.24f
        val middle = size.height / 2f
        val step = size.width / bars
        val thickness = max(2f, step * 0.62f)
        val dot = max(2f, thickness * 0.7f)
        val played = ratio()
        for (index in 0 until bars) {
            val share = (index + 0.5f) / bars
            val x = index * step + (step - thickness) / 2f
            /* Any bar count but the timeline's drew every bar whole, so the opening player's wave, counting its bars up, showed fully played until the last frame; what is played is the ratio alone. */
            if (share > played) {
                drawCircle(Color.White.copy(alpha = 0.32f), dot / 2f, Offset(x + thickness / 2f, middle))
                continue
            }
            val form = shape[min(WAVE_BARS - 1, (share * WAVE_BARS).toInt())]
            val ripple = if (isPlaying) pulse((since - index * 7 + WAVE_BEAT) % WAVE_BEAT) else 0f
            val wobble = if (isPlaying) 0.5f + 0.28f * sin(now / (170f + form * 90f) + index * 2.1f) + 0.22f * sin(now / (95f + form * 40f) + index * 0.6f) else 0f
            val bass = ripple * (0.35f + 0.65f * (1f - share).pow(1.4f))
            val level = min(1f, form * (energy[0] * 0.55f + wobble * 0.3f + bass * 0.45f))
            val reach = dot / 2f + max(0f, level * (middle - 2f) - dot / 2f)
            drawRoundRect(
                accent,
                topLeft = Offset(x, middle - reach),
                size = Size(thickness, reach * 2f),
                cornerRadius = CornerRadius(min(thickness / 2f, reach) * 0.6f),
            )
        }
    }
}

@Composable
fun SatelliteFace(mod: Mod, appearance: Appearance, diameter: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (mod) {
            Mod.Media -> ArtCircle(Island.media, diameter - 6f)
            Mod.Timer -> TimerGlyph(Island.timer, Island.timer?.accent ?: appearance.accent, diameter - 8f)
            Mod.Call -> Avatar(Island.call, Island.call?.accent ?: appearance.accent, diameter - 6f)
            Mod.Trip -> TripMark(Island.trip?.accent ?: appearance.accent, diameter - 6f, appearance)
            Mod.Torch -> GlyphIcon(Glyphs.torch, Color.White, Modifier.size((diameter - 8f).dp))
        }
    }
}

@Composable
fun RingBorder(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, color, CircleShape))
}
