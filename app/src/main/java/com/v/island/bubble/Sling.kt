package com.v.island.bubble

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/* Mirrors js/sling.js: the one draw-and-release every slung bubble flies on. */
object Sling {
    val DRAW_MILLISECONDS = 180f to 240f
    val DRAW_BACK = 26f to 44f
    const val DRAW_SQUEEZE = 0.94f
    val SHOT_MILLISECONDS = 300f to 420f
    const val SHOT_ANGLE = 10f
    val SHOT_OVER = 14f to 30f
    const val SHOT_PUNCH = 1.16f
    const val THROW_WINDOW = 64L
    val THROW_SPEED = 0.3f to 2.5f
    val THROW_MILLISECONDS = 440f to 300f
    val THROW_LEAD = 0.12f to 0.7f
    val THROW_OVER = 6f to 40f
    const val THROW_TURN = 60f
    const val THROW_CIRCLE = 0.4f
    const val SHOT_DECAY = 5.2f
    const val SHOT_LEAD = 0.62f
    const val SAMPLES = 26
    const val PUNCH_SHARE = 0.34f
    const val SETTLE_FROM = 0.78f
    val DRAW_EASE = CubicBezierEasing(0.5f, 0f, 0.75f, 0.2f)
    val POP_EASE = CubicBezierEasing(0.2f, 1.9f, 0.4f, 1f)
    val SHOT_EASE = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

    class Frame(val x: Float, val y: Float, val scale: Float)

    class Shot(val hold: Offset, val launch: Float, val milliseconds: Int, val over: Float, val reach: Float)

    fun between(range: Pair<Float, Float>) = range.first + Random.nextFloat() * (range.second - range.first)

    private fun blend(range: Pair<Float, Float>, share: Float) = range.first + (range.second - range.first) * share

    fun smoothly(step: Float) = step * step * (3f - 2f * step)

    private fun crestOf(swing: Float, launch: Float): Float {
        val lag = (SHOT_DECAY - launch) / swing
        val at = (atan(lag) + PI.toFloat() - atan(SHOT_DECAY / swing)) / swing
        return hypot(1f, lag) * (swing / hypot(SHOT_DECAY, swing)) * exp(-SHOT_DECAY * at)
    }

    private fun swingFor(share: Float, launch: Float): Float {
        var low = 0.1f
        var high = 40f
        repeat(24) {
            val middle = (low + high) / 2f
            if (crestOf(middle, launch) < share) low = middle else high = middle
        }
        return (low + high) / 2f
    }

    private fun wall(value: Float, low: Float, high: Float, keep: Float) = min(max(value, min(low, keep)), max(high, keep))

    /* A trail of (x, y, uptime) in dp; the throw is read off its last THROW_WINDOW rather than the whole gesture. */
    fun throwOf(trail: List<Triple<Float, Float, Long>>): Offset? {
        if (trail.size < 2) return null
        val last = trail.last()
        var reference = trail.first()
        for (index in trail.size - 2 downTo 0) {
            reference = trail[index]
            if (last.third - reference.third >= THROW_WINDOW) break
        }
        val elapsed = max(1L, last.third - reference.third).toFloat()
        return Offset((last.first - reference.first) / elapsed, (last.second - reference.second) / elapsed)
    }

    fun throwShot(velocity: Offset, end: Offset, room: Rect, radius: Float): Shot {
        val reach = hypot(end.x, end.y).takeIf { it > 0f } ?: 1f
        val speed = hypot(velocity.x, velocity.y)
        val power = ((speed - THROW_SPEED.first) / (THROW_SPEED.second - THROW_SPEED.first)).coerceIn(0f, 1f)
        val aim = atan2(end.y, end.x)
        val thrown = if (speed > 0f) atan2(velocity.y, velocity.x) else aim
        val turn = atan2(sin(thrown - aim), cos(thrown - aim))
        val limit = THROW_TURN * PI.toFloat() / 180f
        val heading = aim + turn.coerceIn(-limit, limit)
        val headingX = cos(heading)
        val headingY = sin(heading)
        val fitX = when {
            headingX > 0f -> (room.right - radius) / headingX
            headingX < 0f -> (room.left + radius) / headingX
            else -> Float.MAX_VALUE
        }
        val fitY = when {
            headingY > 0f -> (room.bottom - radius) / headingY
            headingY < 0f -> (room.top + radius) / headingY
            else -> Float.MAX_VALUE
        }
        val lead = max(1f, minOf(reach * blend(THROW_LEAD, power), fitX, fitY))
        val milliseconds = blend(THROW_MILLISECONDS, power).toInt()
        return Shot(
            Offset(headingX * lead, headingY * lead),
            min(SHOT_DECAY, speed * milliseconds / (2f * lead)),
            milliseconds,
            blend(THROW_OVER, power),
            reach,
        )
    }

    fun frames(
        start: Offset,
        hold: Offset,
        end: Offset,
        scaleStart: Float,
        scaleEnd: Float,
        over: Float,
        reach: Float,
        launch: Float = SHOT_DECAY,
        room: Rect? = null,
        extent: (Float) -> Pair<Float, Float> = { 0f to 0f },
    ): List<Frame> {
        fun pointAt(travel: Float): Offset {
            val rest = 1f - travel
            return Offset(
                rest * rest * start.x + 2f * rest * travel * hold.x + travel * travel * end.x,
                rest * rest * start.y + 2f * rest * travel * hold.y + travel * travel * end.y,
            )
        }
        var share = (over / reach).coerceIn(0.004f, 0.35f)
        if (room != null) {
            val landing = extent(1f)
            val radiusX = landing.first * scaleEnd / 2f
            val radiusY = landing.second * scaleEnd / 2f
            while (share > 0.004f) {
                val point = pointAt(1f + share)
                if (point.x - radiusX >= room.left && point.x + radiusX <= room.right && point.y - radiusY >= room.top && point.y + radiusY <= room.bottom) break
                share *= 0.8f
            }
        }
        val swing = swingFor(max(0.004f, share), launch)
        val lag = (SHOT_DECAY - launch) / swing
        return List(SAMPLES) { index ->
            val step = index / (SAMPLES - 1f)
            val wound = if (step <= SETTLE_FROM) 1f else 1f - smoothly((step - SETTLE_FROM) / (1f - SETTLE_FROM))
            val travel = 1f - exp(-SHOT_DECAY * step) * (cos(swing * step) + lag * sin(swing * step)) * wound
            val rest = 1f - travel
            val punch = sin(min(1f, step / PUNCH_SHARE) * PI.toFloat()).pow(2)
            val sizing = scaleStart + (scaleEnd - scaleStart) * (1f - rest * rest)
            var scale = sizing * (1f + (SHOT_PUNCH - 1f) * punch)
            var point = pointAt(travel)
            if (room != null) {
                val box = extent(step)
                scale = minOf(scale, room.width / box.first, room.height / box.second)
                point = Offset(
                    wall(point.x, room.left + box.first * scale / 2f, room.right - box.first * scale / 2f, end.x),
                    wall(point.y, room.top + box.second * scale / 2f, room.bottom - box.second * scale / 2f, end.y),
                )
            }
            Frame(point.x, point.y, scale)
        }
    }

    fun at(frames: List<Frame>, progress: Float): Frame {
        val position = progress.coerceIn(0f, 1f) * (frames.size - 1)
        val index = position.toInt().coerceAtMost(frames.size - 2)
        val share = position - index
        val from = frames[index]
        val to = frames[index + 1]
        return Frame(from.x + (to.x - from.x) * share, from.y + (to.y - from.y) * share, from.scale + (to.scale - from.scale) * share)
    }

    /* The launch line, turned SHOT_ANGLE to a random side, from a bubble at `offset` from where it is flying to. */
    fun launchFrom(offset: Offset): Offset {
        val reach = hypot(offset.x, offset.y).takeIf { it > 0f } ?: 1f
        val side = if (Random.nextBoolean()) -1f else 1f
        val angle = side * SHOT_ANGLE * PI.toFloat() / 180f
        return Offset(
            (cos(angle) * -offset.x - sin(angle) * -offset.y) / reach,
            (sin(angle) * -offset.x + cos(angle) * -offset.y) / reach,
        )
    }
}
