package com.v.island.bubble

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

enum class NowPhase { Idle, Arriving, Rest, Leaving }

data class NowStand(val isInPanel: Boolean, val isExpanded: Boolean, val isLockOpen: Boolean)

/* Mirrors js/lock.js: the media mod leaves Main for its own bubble on the keyguard and in the Dashboard, slung out of Main and thrown home again. */
@Stable
class NowBubble {
    var phase by mutableStateOf(NowPhase.Idle)
        private set
    var isInPanel by mutableStateOf(false)
        private set
    var isExpanded by mutableStateOf(false)
        private set
    var isLockOpen by mutableStateOf(false)
        private set
    private var isHomeHeld by mutableStateOf(false)
    private var isLocked = false

    var poseX by mutableFloatStateOf(0f)
        private set
    var poseY by mutableFloatStateOf(0f)
        private set
    var poseScale by mutableFloatStateOf(1f)
        private set
    var ballShare by mutableFloatStateOf(0f)
        private set
    var metaShown by mutableFloatStateOf(1f)
        private set
    var timelineShown by mutableFloatStateOf(1f)
        private set
    var opacity by mutableFloatStateOf(1f)
        private set
    var labelsGeneration by mutableIntStateOf(0)
        private set
    var areLabelsShown by mutableStateOf(true)
        private set
    var isPressing by mutableStateOf(false)
    var isScrubbing by mutableStateOf(false)
    var scrubPosition by mutableStateOf(0L)

    private var flight: Job? = null
    private var isMelting = false
    private var meltJob: Job? = null

    /* The leave flips isInPanel before it flies, so every size read off the live flags described the lock-screen bubble while the panel's pill was still the one in the air. */
    private var flown by mutableStateOf<NowStand?>(null)

    private val stand: NowStand get() = flown ?: NowStand(isInPanel, isExpanded, isLockOpen)

    val isCompact: Boolean get() = stand.isInPanel && !stand.isExpanded

    fun isStolen(mod: Mod): Boolean = mod == Mod.Media && (isInPanel || isLocked || isHomeHeld)

    val isWanted: Boolean get() = Island.media != null && (isLocked || isInPanel)

    val isShowing: Boolean get() = phase != NowPhase.Idle

    val isOpenFace: Boolean get() = !stand.isInPanel && stand.isLockOpen

    val restWidth: Float get() = if (isCompact) COMPACT_WIDTH else Island.screenWidth - INSET * 2f

    val restHeight: Float get() = when {
        stand.isInPanel -> if (stand.isExpanded) HEIGHT else COMPACT_HEIGHT
        stand.isLockOpen -> OPEN_HEIGHT
        else -> HEIGHT
    }

    val restRadius: Float get() = when {
        isCompact -> COMPACT_RADIUS
        stand.isInPanel -> PANEL_RADIUS
        stand.isLockOpen -> OPEN_RADIUS
        else -> RADIUS
    }

    val restBottom: Float get() = if (stand.isInPanel) Island.dashboard.nowBottom else Island.screenHeight - BOTTOM

    fun rest(): Rect {
        val left = (Island.screenWidth - restWidth) / 2f
        return Rect(left, restBottom - restHeight, left + restWidth, restBottom)
    }

    private fun mainRect(): Rect {
        val frame = Island.bubble.target()
        val left = Island.screenWidth / 2f + frame.shift - frame.width / 2f
        return Rect(left, frame.top, left + frame.width, frame.top + frame.height)
    }

    fun onLock(locked: Boolean) {
        if (isLocked == locked) return
        isLocked = locked
        if (!locked && !isInPanel && phase != NowPhase.Idle) {
            isHomeHeld = true
            flown = stand
            leave(null)
            return
        }
        if (!locked && !isInPanel) isLockOpen = false
        Island.bubble.onModsChanged()
        settle()
    }

    fun enterPanel() {
        isInPanel = true
        if (Island.media == null) isExpanded = false
        isLockOpen = false
        Island.bubble.onModsChanged()
        if (Island.media == null) return
        arrive()
    }

    fun leavePanel(thrown: Offset?) {
        if (!isInPanel) return
        val leaving = stand
        isInPanel = false
        if (isLocked) {
            Island.bubble.onModsChanged()
            arrive()
            return
        }
        if (phase == NowPhase.Idle || Island.media == null) {
            Island.bubble.onModsChanged()
            land()
            return
        }
        isHomeHeld = true
        flown = leaving
        leave(thrown)
    }

    fun settle() {
        when {
            isWanted && phase == NowPhase.Idle -> arrive()
            !isWanted && Island.media == null && phase != NowPhase.Idle && phase != NowPhase.Leaving -> land()
        }
    }

    fun toggle() {
        if (isInPanel) isExpanded = !isExpanded else isLockOpen = !isLockOpen
    }

    fun open() {
        if (isInPanel) isExpanded = true else isLockOpen = true
    }

    fun close() {
        if (isInPanel) isExpanded = false else isLockOpen = false
    }

    private fun launchFlight(work: suspend () -> Unit) {
        flight?.cancel()
        meltJob?.cancel()
        flight = Island.scope.launch(AndroidUiDispatcher.Main) { work() }
    }

    private suspend fun run(milliseconds: Int, easing: Easing = LinearEasing, delayMilliseconds: Int = 0, step: (Float) -> Unit) {
        animate(0f, 1f, animationSpec = tween(milliseconds, delayMilliseconds, easing)) { value, _ -> step(value) }
    }

    private fun arrive() {
        val to = rest()
        val from = mainRect()
        val ball = min(to.width, to.height)
        val drop = min(from.width, from.height) / ball * 0.9f
        val offset = from.center - to.center
        val reach = offset.getDistance().coerceAtLeast(1f)
        val launch = Sling.launchFrom(offset)
        val back = Sling.between(Sling.DRAW_BACK)
        val backOffset = Offset(-launch.x * back, -launch.y * back)
        val hold = offset + launch * (reach * Sling.SHOT_LEAD)
        val frames = Sling.frames(offset, hold, backOffset, drop, Sling.DRAW_SQUEEZE, Sling.between(Sling.SHOT_OVER), reach)
        val milliseconds = (Sling.between(Sling.SHOT_MILLISECONDS) * ARRIVE_STRETCH).toInt()
        val opening = (Sling.between(Sling.DRAW_MILLISECONDS) * ARRIVE_STRETCH).toInt()
        phase = NowPhase.Arriving
        opacity = 1f
        ballShare = 1f
        metaShown = 0f
        timelineShown = 0f
        areLabelsShown = false
        applyFrame(frames.first())
        launchFlight {
            run(milliseconds) { applyFrame(Sling.at(frames, it), drift(to.center, from.center) * (1f - it)) }
            /* Taps were refused until the labels finished typing although the pill had already landed; it is at rest from the moment the flight ends. */
            phase = NowPhase.Rest
            coroutineScope {
                launch {
                    run(opening, Sling.POP_EASE) {
                        poseX = backOffset.x * (1f - it)
                        poseY = backOffset.y * (1f - it)
                        poseScale = Sling.DRAW_SQUEEZE + (1f - Sling.DRAW_SQUEEZE) * it
                    }
                }
                launch { run(opening, Sling.SHOT_EASE) { ballShare = 1f - it } }
                launch { run(SPRIG_MILLISECONDS, Sling.POP_EASE, opening) { metaShown = it } }
                launch { run(SPRIG_MILLISECONDS, Sling.POP_EASE, opening + SPRIG_STAGGER) { timelineShown = it } }
                launch {
                    delay((opening + SPRIG_STAGGER + TYPE_IN).toLong())
                    labelsGeneration += 1
                    areLabelsShown = true
                }
            }
        }
    }

    private fun leave(thrown: Offset?) {
        val from = rest()
        val to = mainRect()
        val ball = min(from.width, from.height)
        val offset = to.center - from.center
        val reach = offset.getDistance().coerceAtLeast(1f)
        val drop = min(to.width, to.height) / ball * 0.9f
        phase = NowPhase.Leaving
        isPressing = false
        isScrubbing = false
        isMelting = false
        val entering = { step: Sling.Frame, wide: Float ->
            if (!isMelting && enteredShare(from.center, step, wide, ball) >= CATCH_ENTERED) {
                isMelting = true
                meltJob = Island.scope.launch(AndroidUiDispatcher.Main) { melt() }
            }
        }
        launchFlight {
            if (thrown != null) {
                val room = Rect(-from.center.x, -from.center.y, Island.screenWidth - from.center.x, Island.screenHeight - from.center.y)
                val shot = Sling.throwShot(thrown, offset, room, ball / 2f)
                val closing = { step: Float -> Sling.smoothly(min(1f, step / Sling.THROW_CIRCLE)) }
                val frames = Sling.frames(
                    Offset.Zero, shot.hold, offset, 1f, drop, shot.over * LEAVE_OVER_SHARE, shot.reach, shot.launch, room,
                ) { step -> (from.width + (ball - from.width) * closing(step)) to from.height }
                coroutineScope {
                    launch { dropReadings((shot.milliseconds * Sling.THROW_CIRCLE).toInt()) }
                    run(shot.milliseconds) {
                        val frame = Sling.at(frames, it)
                        applyFrame(frame, drift(from.center, to.center) * it)
                        ballShare = closing(it)
                        entering(frame, from.width + (ball - from.width) * closing(it))
                    }
                }
            } else {
                val launch = Sling.launchFrom(-offset)
                val back = Sling.between(Sling.DRAW_BACK)
                val backOffset = Offset(-launch.x * back, -launch.y * back)
                val drawing = Sling.between(Sling.DRAW_MILLISECONDS).toInt()
                coroutineScope {
                    launch { dropReadings(drawing) }
                    launch { run(drawing, Sling.SHOT_EASE) { ballShare = it } }
                    run(drawing, Sling.DRAW_EASE) {
                        poseX = backOffset.x * it
                        poseY = backOffset.y * it
                        poseScale = 1f - (1f - Sling.DRAW_SQUEEZE) * it
                    }
                }
                val hold = backOffset + launch * (reach * Sling.SHOT_LEAD)
                val room = Rect(-from.center.x, -from.center.y, Island.screenWidth - from.center.x, Island.screenHeight - from.center.y)
                val frames = Sling.frames(backOffset, hold, offset, Sling.DRAW_SQUEEZE, drop, Sling.between(Sling.SHOT_OVER) * LEAVE_OVER_SHARE, reach, room = room) { ball to ball }
                run(Sling.between(Sling.SHOT_MILLISECONDS).toInt()) {
                    val frame = Sling.at(frames, it)
                    applyFrame(frame, drift(from.center, to.center) * it)
                    entering(frame, ball)
                }
            }
            if (!isMelting) {
                isMelting = true
                melt()
            }
        }
    }

    /* Mirrors enteredShare in js/liquid.js: how much of the drop already stands inside Main, which is when the two read as one body and the mod changes hands. */
    private fun enteredShare(centre: Offset, frame: Sling.Frame, wide: Float, tall: Float): Float {
        val main = mainRect()
        val halfWide = wide * frame.scale / 2f
        val halfTall = tall * frame.scale / 2f
        val x = centre.x + frame.x
        val y = centre.y + frame.y
        val across = min(x + halfWide, main.right) - maxOf(x - halfWide, main.left)
        val down = min(y + halfTall, main.bottom) - maxOf(y - halfTall, main.top)
        if (across <= 0f || down <= 0f) return 0f
        return across * down / (halfWide * 2f * halfTall * 2f)
    }

    private suspend fun dropReadings(milliseconds: Int) = coroutineScope {
        launch { run((milliseconds * SPRIG_LEAVE).toInt(), Sling.SHOT_EASE) { metaShown = 1f - it } }
        launch { run((milliseconds * SPRIG_LEAVE).toInt(), Sling.SHOT_EASE, (milliseconds * LEAVE_STAGGER).toInt()) { timelineShown = 1f - it } }
    }

    private suspend fun melt() {
        Island.bubble.bump()
        isHomeHeld = false
        if (!isInPanel && !isLocked) isLockOpen = false
        Island.bubble.onModsChanged()
        run(MERGE_FADE) { opacity = 1f - it }
        land()
    }

    private fun land() {
        flight?.cancel()
        flown = null
        isMelting = false
        phase = NowPhase.Idle
        isHomeHeld = false
        poseX = 0f
        poseY = 0f
        poseScale = 1f
        ballShare = 0f
        opacity = 1f
        metaShown = 1f
        timelineShown = 1f
    }

    /* A flight was aimed at the rest and Main boxes as they stood at launch, so the Dashboard settling or Main moving mid-flight left the drop landing beside them; the pose follows both while it flies. */
    private fun drift(launchRest: Offset, launchMain: Offset): Offset = (launchRest - rest().center) + (mainRect().center - launchMain)

    private fun applyFrame(frame: Sling.Frame, shift: Offset = Offset.Zero) {
        poseX = frame.x + shift.x
        poseY = frame.y + shift.y
        poseScale = frame.scale
    }

    fun hold() {
        Haptics.play(Haptic.Expand)
        Island.media?.packageName?.takeIf { it.isNotEmpty() }?.let(Island.actions::openApp)
        if (isInPanel) Island.dashboard.close()
    }

    fun swipe(across: Float, down: Float): Boolean {
        if (kotlin.math.abs(across) > SWIPE && kotlin.math.abs(across) > kotlin.math.abs(down)) {
            Haptics.play(Haptic.Expand)
            Island.actions.mediaControl(if (across > 0f) "previous" else "next")
            return true
        }
        if (kotlin.math.abs(down) > SWIPE && kotlin.math.abs(down) > kotlin.math.abs(across)) {
            if (isInPanel && down < 0f) return true
            val isBig = if (isInPanel) isExpanded else isLockOpen
            if ((isBig || isInPanel) && down > 0f) {
                Haptics.play(Haptic.Expand)
                Island.actions.mediaControl(if (Island.media?.isPlaying == true) "pause" else "play")
                return true
            }
            Haptics.play(Haptic.Tap)
            if (isBig || down > 0f) close() else open()
            return true
        }
        return false
    }

    companion object {
        /* Mirrors LOCK_HEIGHT, LOCK_COMPACT_HEIGHT and LOCK_OPEN_HEIGHT in js/lock.js and --lock-* in pill.css. */
        const val HEIGHT = 124f
        const val COMPACT_HEIGHT = 44f
        const val OPEN_HEIGHT = 546f
        const val COMPACT_WIDTH = 190f
        const val RADIUS = 34f
        const val OPEN_RADIUS = 44f
        const val PANEL_RADIUS = 26f
        const val COMPACT_RADIUS = COMPACT_HEIGHT / 2f
        const val ARRIVE_STRETCH = 1.6f
        const val LEAVE_OVER_SHARE = 0.4f
        const val INSET = 16f
        const val BOTTOM = 20f
        const val PANEL_GAP = 16f
        const val GROW = 340
        const val SWIPE = 24f
        const val HOLD_BLOCK = 18f
        const val SPRIG_MILLISECONDS = 460
        const val SPRIG_STAGGER = 80
        const val TYPE_IN = 140
        const val MERGE_FADE = 200
        const val LEAVE_STAGGER = 0.08f
        const val SPRIG_LEAVE = 0.5f
        const val LEAVE_DROP = 20f
        /* Mirrors CATCH_ENTERED in js/liquid.js. */
        const val CATCH_ENTERED = 0.8f
    }
}
