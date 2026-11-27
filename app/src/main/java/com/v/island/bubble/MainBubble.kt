package com.v.island.bubble

import android.os.SystemClock
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.v.island.vas.Curves
import com.v.island.vas.Durations
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

enum class Mod { Timer, Media, Call, Trip, Torch, Transfer }

enum class TransferStage { None, Yielding, Leading, Finishing, Returning }

enum class Mode { Idle, Haptic, Alert, Extended, Torch }

enum class Size(val isClosed: Boolean) {
    Idle(true), Playing(true), Timing(true), Calling(true), Travelling(true),
    Alert(false), Image(false), AlertCentre(false), Picture(false), Player(false), TimerPanel(false), TripPanel(false), Notifications(false), TorchIdle(true), Transferring(true), TransferPanel(false),
}

enum class Face { Idle, Media, Timer, Call, Trip, Alert, Player, TimerPanel, TripPanel, Notifications, TorchIdle, Transfer, TransferPanel }

data class Frame(val width: Float, val height: Float, val radius: Float, val top: Float, val shift: Float)

data class RowLayout(
    val main: Float,
    val satellite: Float,
    val mainX: Float,
    val satelliteX: Float,
    val inSide: Int,
    val farX: Float,
    val farFade: Float,
    val edge: Int,
    val reach: Float,
    val push: Float,
)

data class SatellitePlacement(val mod: Mod, val x: Float, val width: Float, val birth: Float, val opacity: Float)

data class NoticeGroup(val id: String, val head: Notice, val entries: List<Notice>)

@Stable
class MainBubble {
    var mode by mutableStateOf(Mode.Idle)
        private set
    var size by mutableStateOf(Size.Idle)
        private set
    var face by mutableStateOf(Face.Idle)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set
    var alertLines by mutableStateOf<List<String>>(emptyList())
        private set
    var modOrder by mutableStateOf(listOf(Mod.Timer, Mod.Media, Mod.Call, Mod.Trip))
        private set
    var swap by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var isHolding by mutableStateOf(false)
        private set
    var isPressing by mutableStateOf(false)
        private set
    var drag by mutableStateOf(Offset.Zero)
        private set
    var groups by mutableStateOf<List<NoticeGroup>>(emptyList())
    var systemNotices by mutableStateOf<List<Notice>>(emptyList())
        private set
    var pictureHeight by mutableFloatStateOf(320f)
        private set
    var notificationsHeight by mutableFloatStateOf(320f)
        private set
    var notificationsDrop by mutableFloatStateOf(0f)
    var isNotificationsPulled = false
        private set
    private var notificationsRoom = 0f
    private var isMenuPull = false
    private var hasDroppedMenu = false
        private set
    private var alertWanted by mutableFloatStateOf(ALERT_HEIGHT)
    private var notificationsList = -1f
    var accent by mutableStateOf(Color.White)
        private set
    var isScrubbing by mutableStateOf(false)
    var scrubPosition by mutableStateOf(0L)
    val notificationsScroll = ScrollState(0)
    var notificationsOverscroll by mutableFloatStateOf(0f)
    private var bounceJob: Job? = null
    val rowTops = HashMap<String, Float>()
    val rowHeights = HashMap<String, Float>()
    val rowBounds = HashMap<String, androidx.compose.ui.geometry.Rect>()
    val systemBounds = HashMap<String, androidx.compose.ui.geometry.Rect>()
    var homing by mutableStateOf(emptyList<androidx.compose.ui.geometry.Rect>())
    var homingRound by mutableIntStateOf(0)
        private set

    private var satelliteSide by mutableIntStateOf(1)
    private var swapDirection = 1
    private var swapPassed = false

    private var dwellJob: Job? = null
    private var goneJob: Job? = null
    private var holdJob: Job? = null
    private var homeJob: Job? = null
    private var holdArmedAt = 0L

    private var startX = 0f
    private var startY = 0f
    private var hasMoved = false
    private var hasHeld = false
    private var hasSwiped = false
    private var hasPulled = false
    private var hasDismissed = false
    private var isOffCards = false
    private var hasWandered = false
    private var holdPending = false
    private var pullDrop = 0f
    private var scrollOrigin = 0
    private var scrollMode = ScrollMode.Undecided
    private var scrollFromY = 0f
    private var lastScrollAt = 0L
    private val scrollVelocity = androidx.compose.ui.input.pointer.util.VelocityTracker()
    private var flingJob: Job? = null
    var expandedGroup by mutableStateOf<String?>(null)
    var unfoldedApps by mutableStateOf(emptySet<String>())
    var pulledApp by mutableStateOf<String?>(null)
        private set
    var clusterPull by mutableFloatStateOf(0f)
        private set
    private var pressedCluster: String? = null
    private var isPressedFolded = false
    private var isPullOpening = false
    private var pullFromY = 0f
    private var pullJob: Job? = null
    var arrival: String? = null
    var arrivalRound by mutableIntStateOf(0)
        private set
    var isAtEnd = true
    var listOverflow = 0
    private var gate = DragGate()
    private var closedInTouch = false
    private var closedOutsideAt = -1_000L
    private var notificationsClosedAt = -1_000L

    private val appearance get() = Island.appearance

    /* A folded torch was its own size beside the mods and left an active Media hiding it, and an open one stopped every mod although it stands apart at the right edge; it is the leading mod only while folded, every other mod a satellite beside it. */
    val liveMods: List<Mod> get() {
        val own = if (isTorchShown && isTorchDocked) listOf(Mod.Torch) + modOrder.filter(::isLive) else modOrder.filter(::isLive)
        return if (isTransferLeading) listOf(Mod.Transfer) + own else own
    }

    var transferStage by mutableStateOf(TransferStage.None)
        private set
    var transferShown by mutableStateOf<Transfer?>(null)
        private set
    var yieldTravel by mutableFloatStateOf(0f)
        private set
    private var yielded: Mod? = null
    private var yieldBirth = 0f
    private var transferJob: Job? = null

    val isTransferLeading: Boolean get() = transferStage == TransferStage.Leading || transferStage == TransferStage.Finishing || transferStage == TransferStage.Returning

    /* A transfer was a mark in the Status bubble nobody watched; it leads Main, and a mod already there first clears its readings, slides its glyph aside and is shot out as a satellite holding it, so the transfer never lands on top of something still drawn. */
    private fun syncTransfer() {
        val incoming = Island.transfer
        when (transferStage) {
            TransferStage.None -> if (incoming != null && incoming.state == TransferState.Active) beginTransfer(incoming)
            TransferStage.Yielding, TransferStage.Leading -> when {
                incoming == null -> finishTransfer(TransferState.Failed)
                incoming.state != TransferState.Active && incoming.key == transferShown?.key -> finishTransfer(incoming.state, incoming)
                incoming.state == TransferState.Active -> {
                    if (incoming.key != transferShown?.key) transferPace = null
                    transferShown = incoming
                    paceTransfer(incoming)
                }
            }
            TransferStage.Finishing, TransferStage.Returning -> Unit
        }
    }

    /* The time left is the app's own when its notification says it; otherwise it is measured off how fast the bar has moved since the transfer was first seen. */
    private var transferPace: Pair<Long, Float>? = null
    var transferLeft by mutableStateOf<Long?>(null)
        private set

    private fun paceTransfer(incoming: Transfer) {
        val ratio = if (incoming.total > 0 && !incoming.isIndeterminate) incoming.done.toFloat() / incoming.total else return
        val now = SystemClock.uptimeMillis()
        val start = transferPace
        if (start == null || incoming.isPaused || ratio < start.second) {
            transferPace = now to ratio
            transferLeft = null
            return
        }
        val gained = ratio - start.second
        val spent = now - start.first
        transferLeft = if (gained > 0f && spent >= PACE_FLOOR) ((1f - ratio) / gained * spent).toLong() else null
    }

    fun transferAction(isStopping: Boolean) {
        val transfer = transferShown ?: return
        Haptics.play(Haptic.Tap)
        if (isStopping) {
            val stop = transfer.actions.firstOrNull { TRANSFER_STOP_WORDS.containsMatchIn(it.title) }
            if (stop != null) Island.actions.transferAction(stop.index) else Island.actions.dismissNotification(transfer.key)
            return
        }
        transfer.actions.firstOrNull { TRANSFER_HOLD_WORDS.containsMatchIn(it.title) }?.let { Island.actions.transferAction(it.index) }
        transferPace = null
    }

    private fun beginTransfer(incoming: Transfer) {
        transferShown = incoming
        transferPace = null
        transferLeft = null
        paceTransfer(incoming)
        val leader = liveMods.firstOrNull()
        yielded = leader
        yieldBirth = glyphAligned(layout())
        if (leader == null || !size.isClosed || mode != Mode.Idle) {
            transferStage = TransferStage.Leading
            settleTransfer()
            return
        }
        val box = layout()
        yieldTravel = if (satelliteSide > 0) box.main - appearance.compactHeight else -(glyphSize(appearance) + glyphInset(appearance))
        yieldBirth = glyphAligned(box) + yieldTravel
        transferStage = TransferStage.Yielding
        transferJob = Island.scope.launch {
            delay(TRANSFER_YIELD_POP + TRANSFER_YIELD_SLIDE)
            transferStage = TransferStage.Leading
            settleTransfer()
        }
    }

    private fun finishTransfer(state: TransferState, last: Transfer? = null) {
        transferJob?.cancel()
        if (last != null) transferShown = last
        transferShown = transferShown?.copy(state = state)
        transferStage = TransferStage.Finishing
        settleTransfer()
        transferJob = Island.scope.launch {
            delay(if (state == TransferState.Done) TRANSFER_FINISH_DONE else TRANSFER_FINISH_FAILED)
            val back = yielded?.takeIf(::isLive)
            val isMerging = back != null && size.isClosed && mode == Mode.Idle
            if (isMerging) {
                transferStage = TransferStage.Returning
                delay(TRANSFER_MERGE)
            }
            val mergedAt = glyphAligned(layout())
            transferStage = TransferStage.None
            transferShown = null
            yielded = null
            yieldTravel = 0f
            settleTransfer()
            /* The mod coming home was composed afresh at Main's edge while its satellite vanished; its glyph travels over from where the satellite melted in and its readings pop back. */
            if (isMerging && back != null && liveMods.firstOrNull() == back) swapArrival = back to mergedAt - glyphAligned(layout())
            syncTransfer()
        }
    }

    private fun settleTransfer() {
        if (mode == Mode.Idle || mode == Mode.Torch) toClosed()
    }

    /* Folding the torch put Main into its folded face the moment the finger lifted, so Main changed under the flashlight still flying home; Main keeps what it was until the flight lands, and with nothing of its own to show it lends itself to the torch as a hidden circle the torch grows out of and lands back into. */
    /* Leaving the Dashboard drew Main's idle pill at the punch hole while the Now pill was still flying home to become it, so two pills stood at once; Main stays lent until the drop melts into it. */
    val isLent: Boolean get() = ((isTorchOpen || isTorchFlying) && !isTorchDocked || Island.now.isHomeHeld) && size == Size.Idle && modOrder.none(::isLive)

    var isTorchFlying by mutableStateOf(false)
        private set
    var isTorchDocked by mutableStateOf(false)
        private set
    private var wasTorchOpen = false
    private var flightJob: Job? = null

    var landing by mutableFloatStateOf(0f)
        private set

    private fun syncTorch() {
        val isOpen = isTorchOpen
        if (isOpen == wasTorchOpen) return
        wasTorchOpen = isOpen
        flightJob?.cancel()
        if (isOpen) {
            isTorchFlying = false
            isTorchDocked = false
            return
        }
        isTorchFlying = true
        flightJob = Island.scope.launch {
            delay(TORCH_FLIGHT.toLong())
            val hadMods = modOrder.any(::isLive)
            isTorchFlying = false
            isTorchDocked = isTorchShown && isTorchFolded
            toClosed()
            if (hadMods) land()
        }
    }

    fun land() {
        Island.scope.launch(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(LAND_MILLISECONDS, easing = androidx.compose.animation.core.LinearEasing)) { step, _ ->
                landing = if (step < 0.25f) LAND_SWELL * BUMP_RISE.transform(step / 0.25f)
                else LAND_SWELL * (1f - com.v.island.vas.Curves.bubble.transform((step - 0.25f) / 0.75f))
            }
            landing = 0f
        }
    }

    var clearedMods by mutableStateOf(emptySet<Mod>())
        private set

    fun isLive(mod: Mod): Boolean = isPresent(mod) && mod !in clearedMods && !Island.now.isStolen(mod)

    val isTorchShown: Boolean get() = (Island.torch?.step ?: 0) > 0 && !Island.dashboard.isOpen

    val isTorchOpen: Boolean get() = isTorchShown && !isTorchFolded

    var torchAt by mutableFloatStateOf(0f)
        private set
    var isTorchSliding by mutableStateOf(false)
        private set
    private var torchTicked = 0
    private var torchSwipeBase = 0
    private var torchSwipeLevel = 0
    var isTorchFolded by mutableStateOf(false)
        private set

    fun torchCount(): Int = Island.torch?.let { if (it.isDimmable) it.steps else 1 } ?: 1

    fun torchSliderLength(): Float = torchCount() * TORCH_SLOT + (torchCount() - 1) * TORCH_SLOT_GAP

    fun torchRect(): Rect {
        val height = max(TORCH_MINIMUM_HEIGHT, torchSliderLength() + TORCH_PAD * 2f)
        val right = Island.screenWidth - TORCH_EDGE
        val top = Island.screenHeight * TORCH_RISE - height / 2f
        return Rect(right - TORCH_WIDTH, top, right, top + height)
    }

    fun torchOffRect(): Rect {
        val torch = torchRect()
        val right = torch.left - TORCH_OFF_GAP
        val top = torch.center.y - TORCH_OFF_HEIGHT / 2f
        return Rect(right - TORCH_OFF_WIDTH, top, right, top + TORCH_OFF_HEIGHT)
    }

    fun lentSide(): Float = appearance.compactHeight * TORCH_LANDING

    fun mainRect(): Rect {
        val box = target()
        val left = Island.screenWidth / 2f + box.shift - box.width / 2f
        return Rect(left, box.top, left + box.width, box.top + box.height)
    }

    private fun torchIndexAt(y: Float): Float {
        val bottom = torchRect().center.y + torchSliderLength() / 2f
        return ((bottom - y - TORCH_SLOT / 2f) / (TORCH_SLOT + TORCH_SLOT_GAP)).coerceIn(0f, torchCount() - 1f)
    }

    fun startTorchSlide(y: Float) {
        if (!isTorchOpen || torchCount() < 2) return
        isTorchSliding = true
        torchTicked = torchAt.roundToInt()
        slideTorch(y)
    }

    fun slideTorch(y: Float) {
        if (!isTorchSliding) return
        torchAt = torchIndexAt(y)
        val nearest = torchAt.roundToInt()
        if (nearest == torchTicked) return
        torchTicked = nearest
        Haptics.play(Haptic.Tap)
        Island.actions.setTorch(nearest + 1)
    }

    private fun foldTorch(isFolded: Boolean) {
        isTorchFolded = isFolded
        isTorchSliding = false
        syncTorch()
        toClosed()
    }

    private fun swipeTorch(x: Float, y: Float) {
        val across = x - startX
        val down = y - startY
        if (hasPulled || hypot(across, down) <= HOLD_BLOCK) return
        hasMoved = true
        if (!hasSwiped && down > PULL_TRIGGER && down > abs(across)) {
            hasPulled = true
            Haptics.play(Haptic.Expand)
            foldTorch(false)
            return
        }
        if (!hasSwiped && abs(across) <= abs(down)) return
        hasSwiped = true
        val wanted = (torchSwipeBase + (across / TORCH_SWIPE_STEP).toInt()).coerceIn(1, torchCount())
        if (wanted == torchSwipeLevel) return
        torchSwipeLevel = wanted
        Haptics.play(Haptic.Tap)
        Island.actions.setTorch(wanted)
    }

    fun finishTorchSlide() {
        if (!isTorchSliding) return
        isTorchSliding = false
        val index = torchAt.roundToInt()
        torchAt = index.toFloat()
        if (index + 1 != Island.torch?.step) Island.actions.setTorch(index + 1)
    }

    private fun readTorch() {
        if (!isTorchSliding) torchAt = ((Island.torch?.step ?: 1) - 1).coerceAtLeast(0).toFloat()
    }

    val hasPresentMods: Boolean get() = modOrder.any(::isPresent)

    fun clearMods() {
        clearedMods = modOrder.filter(::isPresent).toSet()
        if (mode == Mode.Idle) toClosed()
    }

    fun restoreMods() {
        clearedMods = emptySet()
        if (mode == Mode.Idle) toClosed()
    }

    var bumpLift by mutableFloatStateOf(0f)
        private set

    var mediaEntrance = MediaEntrance.Full

    fun takeMediaEntrance(): MediaEntrance = mediaEntrance.also { mediaEntrance = MediaEntrance.Full }

    /* Mirrors bumpPill in js/lock.js: Main gives under the Now bubble melting back into it. */
    fun bump() {
        Island.scope.launch(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(BUMP_MILLISECONDS, easing = androidx.compose.animation.core.LinearEasing)) { step, _ ->
                bumpLift = if (step < 0.3f) -BUMP * BUMP_RISE.transform(step / 0.3f)
                else -BUMP * (1f - com.v.island.vas.Curves.bubble.transform((step - 0.3f) / 0.7f))
            }
            bumpLift = 0f
        }
    }

    private fun isPresent(mod: Mod): Boolean = when (mod) {
        Mod.Transfer -> isTransferLeading
        Mod.Timer -> Island.timer != null
        Mod.Media -> Island.media != null
        Mod.Call -> Island.call != null
        Mod.Trip -> Island.trip != null
        Mod.Torch -> (Island.torch?.step ?: 0) > 0
    }

    fun accentOf(mod: Mod): Color = when (mod) {
        Mod.Timer -> Island.timer?.accent
        Mod.Media -> Island.media?.accent
        Mod.Call -> Island.call?.accent
        Mod.Trip -> Island.trip?.accent
        Mod.Torch -> null
        Mod.Transfer -> transferShown?.let { if (it.isUpload) com.v.island.vas.Palette.upload else com.v.island.vas.Palette.download }
    } ?: appearance.accent

    private fun satelliteSize() = appearance.compactHeight * SATELLITE_SHARE

    private fun modFloor(): Float {
        val glyph = appearance.compactHeight - 10f
        val inset = (appearance.compactHeight - glyph) / 2f - 1f
        if (liveMods.firstOrNull() == Mod.Media) return 2f * (MEDIA_TILE_INSET + mediaTileWidth(appearance)) + HOLE_GAP
        return 2f * (inset + glyph) + HOLE_GAP + 2f
    }

    private fun restingMain(): Float {
        val live = liveMods
        if (live.isEmpty()) return appearance.compactWidth
        val split = appearance.compactWidth + appearance.modWidth + 4f - if (live.size > 1) SPLIT_SHRINK else 0f
        return max(split, modFloor())
    }

    private fun satelliteLimit() = if (Island.clock.isNowStanding && appearance.nowPushes) 1 else 2

    private fun satelliteGap() = if (Island.clock.isNowStanding) SATELLITE_GAP_ASIDE else SATELLITE_GAP

    private fun rowShift(resting: Float): Float {
        if (!Island.clock.isNowStanding || liveMods.isEmpty() || !appearance.nowPushes) return 0f
        val glyph = appearance.compactHeight - 10f
        val inset = (appearance.compactHeight - glyph) / 2f - 1f
        val centre = Island.screenWidth / 2f
        val clear = centre - HOLE_WIDTH / 2f - glyph - inset + CHIP_ROOM
        return max(0f, clear - (centre - resting / 2f))
    }

    fun layout(): RowLayout {
        val live = liveMods
        val resting = restingMain()
        val push = rowShift(resting)
        val satellite = satelliteSize()
        if (live.size < 2) return RowLayout(resting, satellite, push, push, satelliteSide, push, 1f, 0, 0f, push)
        val traded = (resting - satellite) * swap
        val inSide = if (live.size > 2 && satelliteLimit() > 1 && swapDirection < 0 && isDragging) -satelliteSide else satelliteSide
        val edge = inSide
        val reach = (resting + satellite) / 2f + satelliteGap()
        val pressed = reach - (satelliteGap() + SWAP_PRESS) * sin(swap * PI.toFloat())
        val crossed = swap > 0.5f
        val shoved = if (crossed) (1f - swap) * 2f else swap * 2f
        return RowLayout(
            main = resting - traded,
            satellite = satellite + traded,
            mainX = push - edge * swap * pressed,
            satelliteX = push + edge * (1f - swap) * pressed,
            inSide = inSide,
            farX = push + (if (crossed) edge else -edge) * (reach + FAR_PUSH * shoved),
            farFade = min(1f, abs(swap - 0.5f) * 2f),
            edge = edge,
            reach = reach,
            push = push,
        )
    }

    private fun glyphAligned(box: RowLayout) = box.mainX - box.main / 2f + appearance.compactHeight / 2f

    fun satellites(): List<SatellitePlacement> {
        val live = liveMods
        if (live.size < 2 || !size.isClosed) return emptyList()
        val box = layout()
        val isMerging = transferStage == TransferStage.Returning
        val trader = { mod: Mod ->
            val isYielded = mod == yielded
            SatellitePlacement(mod, if (isYielded && isMerging) glyphAligned(box) else box.satelliteX, box.satellite, if (isYielded) yieldBirth else glyphAligned(box), 1f)
        }
        if (live.size == 2 || satelliteLimit() == 1) return listOf(trader(live[1]))
        val inSide = box.inSide
        val traded = live[if (inSide == satelliteSide) 1 else 2]
        val wrapped = live[if (inSide == satelliteSide) 2 else 1]
        return listOf(
            trader(traded),
            SatellitePlacement(
                wrapped, box.farX, satelliteSize(), box.mainX - box.edge * box.main / 2f,
                if (isDragging) box.farFade else 1f,
            ),
        )
    }

    fun dots(): List<Pair<Mod, Float>> {
        val limit = satelliteLimit()
        val spread = mutableMapOf(satelliteSide to mutableListOf<Mod>(), -satelliteSide to mutableListOf())
        liveMods.drop(1 + limit).forEachIndexed { index, mod ->
            spread.getValue(if (limit > 1 && index % 2 != 0) -satelliteSide else satelliteSide).add(mod)
        }
        val box = layout()
        return spread.flatMap { (side, mods) ->
            val start = box.reach + satelliteSize() / 2f + DOT_GAP
            mods.mapIndexed { index, mod -> mod to box.push + side * (start + index * (DOT_SIZE + DOT_GAP) + DOT_SIZE / 2f) }
        }
    }

    val isRisen: Boolean get() = size.isClosed && Island.dashboard.isOpen && liveMods.isNotEmpty()

    val isPunched: Boolean get() = size.isClosed && Island.dashboard.isOpen && liveMods.isEmpty()

    fun target(): Frame {
        /* A live mod kept its full pill while the menu was open, so the cards dropped out of a pill that never gave anything up; Main always shrinks to the punch hole and the cards shoot out while it shrinks. */
        if (size == Size.Notifications) return punchFrame()
        val frame = frame()
        if (isPunched) return punchFrame()
        if (!isRisen) return frame
        return frame.copy(top = -EDGE_OVER, height = appearance.compactHeight + EDGE_OVER)
    }

    private fun punchFrame() = Frame(lentSide(), lentSide(), lentSide() / 2f, appearance.grab + (appearance.compactHeight - lentSide()) / 2f, 0f)

    fun menuFrame() = frame(Size.Notifications)

    val mainFace: Face get() = if (face == Face.Notifications) Face.Idle else face

    private fun frame(of: Size = size): Frame {
        val grab = appearance.grab
        val grown = grab + GROWN_PAD
        return when (of) {
            Size.Idle -> if (isLent) Frame(lentSide(), lentSide(), lentSide() / 2f, grab + (appearance.compactHeight - lentSide()) / 2f, 0f)
            else Frame(appearance.compactWidth, appearance.compactHeight, appearance.compactHeight / 2f, grab, layout().mainX)
            Size.Playing, Size.Timing, Size.Calling, Size.Travelling, Size.TorchIdle, Size.Transferring -> {
                val box = layout()
                Frame(box.main, appearance.compactHeight, appearance.compactHeight / 2f, grab, box.mainX)
            }
            // Stopping at the screen edges left the rounded border showing along them; the glass runs past the edges so only its bottom corners are seen.
            Size.Alert -> Frame(ALERT_WIDTH * ALERT_GROWTH, alertHeight() * ALERT_GROWTH, 24f, grab, 0f)
            Size.Image -> Frame(ALERT_WIDTH, ALERT_IMAGE_HEIGHT, 24f, grab, 0f)
            Size.AlertCentre -> {
                val tall = alertHeight()
                Frame(ALERT_WIDTH, tall, CENTRE_RADIUS, grown + (Island.screenHeight - tall) / 2f - Island.screenHeight * CENTRE_LIFT, 0f)
            }
            Size.Picture -> Frame(GROWN_WIDTH, pictureHeight, 26f, grown, 0f)
            Size.Player -> Frame(GROWN_WIDTH, PLAYER_HEIGHT, 34f, grab, 0f)
            Size.TimerPanel -> Frame(GROWN_WIDTH, 132f, 26f, grown, 0f)
            Size.TripPanel -> Frame(GROWN_WIDTH, 232f, 26f, grown, 0f)
            Size.TransferPanel -> Frame(GROWN_WIDTH, TRANSFER_PANEL_HEIGHT, 26f, grown, 0f)
            Size.Notifications -> Frame(GROWN_WIDTH, notificationsHeight, 26f, grown + notificationsDrop, 0f)
        }
    }

    val holdScale: Float get() = if (isHolding) HOLD_SCALE else 1f

    fun border(): Color = com.v.island.vas.Palette.border

    /* Mirrors measureAlert in js/alert.js: the box is what the content asks for between a floor and a share of the screen, the whole picture's share once focused; it was a fixed 155 that left a short message floating in an empty card. */
    private fun alertHeight(): Float {
        val hasPicture = size == Size.AlertCentre && notice?.picture != null
        val ceiling = Island.screenHeight * if (hasPicture) PICTURE_CENTRE_CEILING else CENTRE_CEILING
        return alertWanted.coerceIn(ALERT_FLOOR, ceiling).roundToInt().toFloat()
    }

    fun reportAlertHeight(height: Float) {
        if (height > 0f) alertWanted = height
    }

    val isAlertCentred: Boolean get() = size == Size.AlertCentre

    /* Mirrors setAlertBand in the retired BubbleService: a standing Alert is answered on a strip at the right edge across the middle, a focused or dark one on the whole screen. */
    fun alertBand(): androidx.compose.ui.geometry.Rect? {
        if (mode != Mode.Alert) return null
        val width = Island.screenWidth
        val height = Island.screenHeight
        if (size == Size.AlertCentre || Island.isScreenDark) return androidx.compose.ui.geometry.Rect(0f, 0f, width, height)
        val tall = height * ALERT_ZONE_HEIGHT_PART
        return androidx.compose.ui.geometry.Rect(width - ALERT_ZONE_WIDTH, (height - tall) / 2f, width, (height + tall) / 2f)
    }

    private var alertActed = false
    private var isAlertTouch = false
    private var lastX = 0f
    private var lastY = 0f

    fun onAlertDown(x: Float, y: Float) {
        startX = x
        startY = y
        pullDrop = 0f
        alertActed = false
    }

    fun onAlertMove(x: Float, y: Float) {
        if (mode != Mode.Alert) return
        val down = y - startY
        val across = abs(x - startX)
        pullDrop = max(pullDrop, down)
        if (alertActed || Island.isScreenDark) return
        if (hypot(across, down) <= HOLD_BLOCK) return
        toy(0f, down)
        if (down < -PULL_TRIGGER && -down > across) {
            alertActed = true
            dismissAlert()
            return
        }
        if (down > PULL_TRIGGER && down > across) {
            alertActed = true
            untoy()
            if (size == Size.AlertCentre) dismissAlert() else centreAlert()
        }
    }

    fun onAlertUp(x: Float, y: Float) {
        untoy()
        if (mode != Mode.Alert) return
        if (Island.wasCancelled) {
            if (!alertActed && size != Size.AlertCentre && pullDrop > PULL_STOLEN) {
                alertActed = true
                centreAlert()
            }
            return
        }
        if (alertActed || hypot(x - startX, y - startY) > ALERT_TAP_SLOP) return
        if (Island.isScreenDark) {
            val isOnText = Island.darkAlertBox?.contains(androidx.compose.ui.geometry.Offset(x, y)) == true
            Island.liftScreenDark()
            if (isOnText) openCurrent()
            return
        }
        if (!holds(x, y)) {
            Island.actions.passTap(x, y)
            return
        }
        dwellJob?.cancel()
        if (notice?.picture != null && size == Size.Image) {
            Haptics.play(Haptic.Expand)
            openPicture()
            return
        }
        openCurrent()
    }

    private fun dismissAlert() {
        untoy()
        Haptics.play(if (size == Size.AlertCentre) Haptic.Release else Haptic.Dismiss)
        toClosed()
    }

    private fun centreAlert() {
        Haptics.play(Haptic.Expand)
        dwellJob?.cancel()
        size = Size.AlertCentre
    }

    private fun closedSizeOf(mod: Mod?) = when (mod) {
        Mod.Timer -> Size.Timing
        Mod.Media -> Size.Playing
        Mod.Call -> Size.Calling
        Mod.Trip -> Size.Travelling
        Mod.Torch -> Size.TorchIdle
        Mod.Transfer -> Size.Transferring
        null -> Size.Idle
    }

    private fun closedFaceOf(mod: Mod?) = when (mod) {
        Mod.Timer -> Face.Timer
        Mod.Media -> Face.Media
        Mod.Call -> Face.Call
        Mod.Trip -> Face.Trip
        Mod.Torch -> Face.TorchIdle
        Mod.Transfer -> Face.Transfer
        null -> Face.Idle
    }

    fun onModsChanged() {
        readTorch()
        if ((Island.torch?.step ?: 0) == 0) isTorchFolded = false
        syncTorch()
        if ((isTorchShown && isTorchDocked) != (mode == Mode.Torch)) {
            toClosed()
            return
        }
        clearedMods = clearedMods.filter(::isPresent).toSet()
        syncTransfer()
        if (mode == Mode.Idle) toClosed()
        if (size == Size.TimerPanel && Island.timer == null) toClosed()
        if (size == Size.Player && Island.media == null) toClosed()
        if (size == Size.TripPanel && Island.trip == null) toClosed()
        if (size == Size.TransferPanel && (transferStage != TransferStage.Leading || transferShown?.state != TransferState.Active)) toClosed()
    }

    fun toClosed() {
        android.util.Log.d("IslandBubble", "probe toClosed size=$size mode=$mode", Throwable())
        dwellJob?.cancel()
        swapArrival = null
        /* An Alert shrank back into Main on the pill's own settle curve while the menu's cards narrowed, rose and squashed home as liquid; the Alert's silhouette goes home the same way and Main stands closed to take it. */
        if (!Island.isScreenDark && (size == Size.Alert || size == Size.AlertCentre || size == Size.Image)) {
            val shown = target()
            val left = Island.screenWidth / 2f + shown.shift - shown.width / 2f + drag.x
            val top = shown.top + drag.y
            homing = listOf(androidx.compose.ui.geometry.Rect(left, top, left + shown.width, top + shown.height))
            homingRound++
        }
        if (Island.isScreenDark) Island.sleepDark()
        val wasOpen = !size.isClosed
        if (wasOpen) closedInTouch = Island.isTouchDown
        if (size == Size.Notifications) {
            notificationsClosedAt = SystemClock.uptimeMillis()
            val menu = menuFrame()
            homing = rowBounds.values.filter { it.height > HOMING_FLOOR && it.top < menu.top + menu.height }.sortedBy { it.top }.take(HOMING_LIMIT) + systemBounds.values.sortedBy { it.top }
            homingRound++
        }
        mode = Mode.Idle
        notice = null
        if ((Island.torch?.step ?: 0) == 0) {
            isTorchFolded = false
            isTorchDocked = false
        }
        readTorch()
        val owner = liveMods.firstOrNull()
        if (owner == Mod.Torch) mode = Mode.Torch
        accent = owner?.let(::accentOf) ?: appearance.accent
        size = closedSizeOf(owner)
        face = closedFaceOf(owner)
    }

    fun toClosedIfExtended() {
        if (mode == Mode.Extended) toClosed()
    }

    private fun becomeExtended() {
        Island.clock.closePanel()
        Island.status.closePanel()
        mode = Mode.Extended
    }

    fun onNotice(next: Notice, isDark: Boolean = false) {
        if (Island.isLandscape || mode == Mode.Torch) return
        if (size == Size.Notifications) {
            addToNotifications(next)
            return
        }
        dwellJob?.cancel()
        goneJob?.cancel()
        val hasImage = next.picture != null
        val shown = notice
        val isAppending = mode == Mode.Alert && !hasImage && size != Size.Image && shown != null &&
            (shown.key == next.key || (next.title.isNotEmpty() && shown.title == next.title && shown.packageName == next.packageName))
        val lines = next.messages.map { it.text }.filter { it.isNotEmpty() }.ifEmpty { listOf(next.text) }
        alertLines = if (isAppending) (alertLines + lines.takeLast(1)).distinct().takeLast(ALERT_LINES) else lines.takeLast(1)
        val wasCentred = mode == Mode.Alert && size == Size.AlertCentre
        mode = Mode.Alert
        notice = next
        accent = next.accent
        face = Face.Alert
        if (isDark) Island.showDarkAlert(next.appName, whoSent(next), lines.lastOrNull().orEmpty(), next.accent, isAppending)
        /* A second message reaching a focused Alert grows it where it stands rather than putting it back on the bar, as onNotificationUpdate did; a dark arrival is read at the centre. */
        size = when {
            wasCentred || (isDark && !hasImage) -> Size.AlertCentre
            hasImage -> Size.Image
            else -> Size.Alert
        }
        Haptics.play(Haptic.Release)
        if (size == Size.AlertCentre && !isDark) return
        dwellJob = Island.scope.launch {
            delay(appearance.dwellMilliseconds)
            toClosed()
        }
    }

    fun onNoticeGone(key: String) {
        if (mode != Mode.Alert || notice?.key != key) return
        goneJob?.cancel()
        goneJob = Island.scope.launch {
            delay(GONE_GRACE)
            toClosed()
        }
    }

    fun whoSent(shown: Notice): String {
        val sender = shown.messages.lastOrNull()?.sender.orEmpty()
        if (sender.isEmpty() || sender == shown.title) return shown.title
        if (shown.packageName == "com.discord") return sender
        val bracketed = Regex("""\(([^)]+)\)\s*$""").find(shown.title)?.groupValues?.get(1)
        val place = (bracketed ?: shown.title.replace(Regex("""#\S+"""), "")).trimEnd(' ', '-', '—', '•', '·').trim()
        return if (place.isNotEmpty() && place != sender) "$sender · $place" else sender
    }

    private fun openPicture() {
        val picture = notice?.picture ?: return
        val ratio = picture.height.toFloat() / max(1, picture.width)
        pictureHeight = min(PICTURE_MAXIMUM_HEIGHT, (GROWN_WIDTH * ratio).roundToInt() + 64f)
        size = Size.Picture
    }

    private fun openCurrent() {
        notice?.key?.let(Island.actions::openNotification)
        Haptics.play(Haptic.Tap)
        toClosed()
    }

    private fun isNotificationsSettling() = SystemClock.uptimeMillis() - notificationsClosedAt < NOTIFICATIONS_HOMING

    fun openNotifications() {
        if (isNotificationsSettling()) return
        val (system, entries) = Island.actions.readNotifications().sortedByDescending { it.postedAt }.partition { it.isSystem }
        systemNotices = system
        val byId = LinkedHashMap<String, MutableList<Notice>>()
        entries.forEach { entry -> byId.getOrPut(entry.owner + "  " + entry.title) { mutableListOf() }.add(entry) }
        groups = clustered(byId.map { (id, list) -> NoticeGroup(id, list.first(), list) })
        notificationsOverscroll = 0f
        expandedGroup = null
        unfoldedApps = emptySet()
        arrival = null
        pullJob?.cancel()
        pulledApp = null
        clusterPull = 0f
        isNotificationsPulled = false
        homing = emptyList()
        becomeExtended()
        face = Face.Notifications
        fitNotifications()
        size = Size.Notifications
        isAtEnd = true
        Island.scope.launch { notificationsScroll.scrollTo(notificationsScroll.maxValue) }
    }

    fun pullsNotifications(x: Float, y: Float): Boolean {
        if (size != Size.Notifications || notificationsRoom < PULL_ROOM) return false
        val tall = Island.screenHeight * ALERT_ZONE_HEIGHT_PART
        return x >= Island.screenWidth - ALERT_ZONE_WIDTH && abs(y - Island.screenHeight / 2f) <= tall / 2f
    }

    /* The pull down was once per opening, so a menu dropped to thumb height was stuck there; the same strip lifts it back with a pull up. */
    fun dropNotifications() {
        isNotificationsPulled = !isNotificationsPulled
        Haptics.play(Haptic.Release)
        fitNotifications()
    }

    fun fitNotifications() {
        val grownTop = appearance.grab + GROWN_PAD
        val tall = Island.screenHeight
        val below = CLEAR_GAP + CLEAR_SIZE + SYSTEM_FOOT + systemOffset()
        val list = if (notificationsList >= 0f) notificationsList else groups.sumOf { group -> rowHeight(group).toDouble() }.toFloat()
        val wanted = if (groups.isEmpty()) NOTIFICATIONS_EMPTY else list + 20f
        val full = tall - NOTIFICATIONS_TOP - below
        val height = min(full, wanted)
        val lowest = tall - height - below
        val highest = min(grownTop, lowest)
        val top = (if (isNotificationsPulled) lowest else highest).roundToInt().toFloat()
        notificationsRoom = lowest - highest
        notificationsHeight = height
        notificationsDrop = top - grownTop
        android.util.Log.d("IslandBubble", "probe fit list=$list wanted=$wanted full=$full height=$height top=$top tall=$tall below=$below scrollMax=${notificationsScroll.maxValue}")
    }

    /* The height was guessed from line counts, so a long message overflowed and a short list left a gap; the menu is now sized off its rows as they are laid out, as notificationsWindow did off scrollHeight. */
    fun reportNotificationsList(height: Float) {
        if (height == notificationsList) return
        notificationsList = height
        if (size == Size.Notifications) fitNotifications()
    }

    private fun rowHeight(group: NoticeGroup): Float {
        val lines = group.entries.sumOf { entry -> max(1, entry.messages.size).coerceAtMost(4) }
        return 70f + lines * 20f
    }

    fun systemHeight() = if (systemNotices.isEmpty()) 0f else SYSTEM_PAD * 2f + systemNotices.size * SYSTEM_ROW

    /* The system notices stood stacked above the clear button, taking a row of the screen the cards could have had; the last one sits on the clear button's line and the notices are cut short around it and the count. */
    fun systemTop() = clearTop() + CLEAR_SIZE / 2f + SYSTEM_ROW / 2f + SYSTEM_PAD - systemHeight()

    fun systemOffset() = if (systemNotices.isEmpty()) 0f else clearTop() - systemTop()

    fun clearTop() = Island.screenHeight - CLEAR_SIZE - SYSTEM_FOOT

    fun openSystem(entry: Notice) {
        Island.actions.openNotification(entry.key)
        Haptics.play(Haptic.Tap)
        toClosed()
    }

    fun dismissGroup(group: NoticeGroup) {
        group.entries.forEach { Island.actions.dismissNotification(it.key) }
        groups = groups - group
        if (groups.isEmpty()) {
            Island.scope.launch {
                delay(220)
                if (size == Size.Notifications) toClosed()
            }
            return
        }
        fitNotifications()
    }

    fun clearAll() {
        if (size != Size.Notifications) return
        groups.flatMap { it.entries }.forEach { Island.actions.dismissNotification(it.key) }
        Haptics.play(Haptic.Dismiss)
        val count = min(groups.size, 8)
        groups = emptyList()
        Island.scope.launch {
            delay(count * CLEAR_STAGGER + CLEAR_FLIGHT)
            if (size == Size.Notifications) toClosed()
        }
    }

    fun openGroup(group: NoticeGroup) {
        hasHeld = true
        Island.actions.openNotification(group.head.key)
        Haptics.play(Haptic.Expand)
        toClosed()
    }

    private fun openPlayer() {
        becomeExtended()
        face = Face.Player
        size = Size.Player
    }

    private fun openTimer() {
        becomeExtended()
        face = Face.TimerPanel
        size = Size.TimerPanel
    }

    private fun openTrip() {
        becomeExtended()
        face = Face.TripPanel
        size = Size.TripPanel
    }

    private fun openTransfer() {
        becomeExtended()
        face = Face.TransferPanel
        size = Size.TransferPanel
    }

    private fun panelMod(): Mod? = when (size) {
        Size.Player -> Mod.Media
        Size.TimerPanel -> Mod.Timer
        Size.TripPanel -> Mod.Trip
        Size.TransferPanel -> Mod.Transfer
        else -> null
    }

    private fun leaveForMod(mod: Mod?) {
        when (mod) {
            Mod.Media -> Island.media?.packageName?.takeIf { it.isNotEmpty() }?.let(Island.actions::openApp)
            Mod.Timer -> Island.timer?.key?.let(Island.actions::openNotification)
            Mod.Call -> Island.call?.key?.let(Island.actions::openNotification)
            Mod.Trip -> Island.trip?.key?.let(Island.actions::openNotification)
            Mod.Transfer -> transferShown?.key?.let(Island.actions::openNotification)
            Mod.Torch, null -> Unit
        }
    }

    fun timerAction(action: Action) {
        Haptics.play(Haptic.Tap)
        Island.actions.timerAction(action.index)
        toClosed()
    }

    fun tripAction(action: Action) {
        Haptics.play(Haptic.Tap)
        Island.actions.tripAction(action.index)
        toClosed()
    }

    fun onPressStarted() {
        closedInTouch = SystemClock.uptimeMillis() - closedOutsideAt < OUTSIDE_LATCH
    }

    fun onOutsideTap() {
        if (isTorchOpen) {
            foldTorch(true)
            closedOutsideAt = SystemClock.uptimeMillis()
        }
        if (mode == Mode.Extended) {
            toClosed()
            closedOutsideAt = SystemClock.uptimeMillis()
        }
    }

    private fun rubberBand(travel: Float): Float {
        val radius = if (size == Size.Notifications) DRAG_RADIUS * NOTIFICATIONS_DRAG_SCALE else DRAG_RADIUS
        val past = max(0f, travel - HOLD_BLOCK)
        return radius * past / (past + radius)
    }

    private fun toy(across: Float, down: Float) {
        val travel = hypot(across, down)
        val scale = if (travel > 0f) rubberBand(travel) / travel else 0f
        if (travel > HOLD_BLOCK) endHold()
        homeJob?.cancel()
        drag = Offset(across * scale, down * scale)
    }

    private fun untoy() {
        if (drag == Offset.Zero) return
        val from = drag
        homeJob?.cancel()
        homeJob = Island.scope.launch {
            animate(0f, 1f, animationSpec = tween(Durations.HOME, easing = Curves.bubble)) { progress, _ ->
                drag = from * (1f - progress)
            }
        }
    }

    private fun endHold() {
        holdJob?.cancel()
        holdPending = false
        isPressing = false
        if (mode == Mode.Haptic && !hasHeld) mode = Mode.Idle
        isHolding = false
    }

    private fun grantHoldWander() {
        if (hasWandered || !holdPending) return
        hasWandered = true
        val served = SystemClock.uptimeMillis() - holdArmedAt
        val step = heldStep ?: return
        holdJob?.cancel()
        holdJob = Island.scope.launch {
            delay(max(0L, Durations.HOLD - served) + HOLD_WANDER)
            step()
        }
    }

    private var heldStep: (() -> Unit)? = null

    fun onDown(x: Float, y: Float) {
        startX = x
        startY = y
        flingJob?.cancel()
        scrollOrigin = notificationsScroll.value
        scrollMode = ScrollMode.Undecided
        scrollVelocity.resetTracking()
        hasMoved = false
        hasHeld = false
        hasSwiped = false
        hasDismissed = false
        hasPulled = false
        hasWandered = false
        pullDrop = 0f
        gate = DragGate()
        heldStep = null
        if (mode == Mode.Torch) {
            torchSwipeBase = Island.torch?.step ?: 1
            torchSwipeLevel = torchSwipeBase
            return
        }
        if (closedInTouch) return
        /* The bubble itself answers an Alert the way its band does, as alertOwnsTouch in bridge.js claimed mainHolds: a pull down on the card focuses it instead of only an upward swipe being heard. */
        isAlertTouch = mode == Mode.Alert
        if (isAlertTouch) {
            lastX = x
            lastY = y
            onAlertDown(x, y)
            return
        }
        android.util.Log.d("IslandBubble", "probe onDown x=$x y=$y holds=${holds(x, y)} mode=$mode size=$size closedInTouch=$closedInTouch")
        isMenuPull = pullsNotifications(x, y)
        hasDroppedMenu = false
        isOffCards = size == Size.Notifications && rowBounds.values.none { it.contains(androidx.compose.ui.geometry.Offset(x, y)) }
        if (mode == Mode.Extended && size == Size.Notifications && !holds(x, y) && !isMenuPull) {
            toClosed()
            closedInTouch = true
            return
        }
        if (mode == Mode.Idle && liveMods.isNotEmpty()) {
            mode = Mode.Haptic
            holdPending = true
            isHolding = true
            heldStep = {
                holdPending = false
                val held = liveMods.firstOrNull()
                if (held == null) endHold() else {
                    hasHeld = true
                    isHolding = false
                    abandonSwap()
                    Haptics.play(Haptic.Expand)
                    leaveForMod(held)
                    toClosed()
                }
            }
        } else if (mode == Mode.Extended && panelMod() != null) {
            isPressing = true
            holdPending = true
            heldStep = {
                holdPending = false
                hasHeld = true
                isPressing = false
                Haptics.play(Haptic.Expand)
                leaveForMod(panelMod())
                toClosed()
            }
        }
        val step = heldStep ?: return
        holdArmedAt = SystemClock.uptimeMillis()
        holdJob = Island.scope.launch {
            delay(Durations.HOLD.toLong())
            step()
        }
    }

    fun onReleasedOnControl() {
        // A scroll let go over a row ended on the row's tap and never reached the fling, so the list stopped dead under the finger; it drifts on here too.
        driftNotifications()
        isAlertTouch = false
        releaseSwap()
        endHold()
        untoy()
    }

    fun holds(x: Float, y: Float): Boolean {
        val box = if (size == Size.Notifications) menuFrame() else target()
        val left = Island.screenWidth / 2f + box.shift - box.width / 2f
        val bottom = if (size == Size.Notifications) maxOf(box.top + box.height, clearTop() - systemOffset()) else box.top + box.height
        return x >= left && x <= left + box.width && y >= box.top && y <= bottom
    }

    fun onMove(x: Float, y: Float) {
        if (mode == Mode.Torch) {
            swipeTorch(x, y)
            return
        }
        if (isAlertTouch) {
            lastX = x
            lastY = y
            onAlertMove(x, y)
            return
        }
        if (hasHeld || closedInTouch) return
        if (!size.isClosed || mode == Mode.Extended) {
            grantHoldWander()
            /* A swipe on the player to skip, pause or close still let the hold run out and opened the app; a panel touch that travels is a gesture, never a hold. */
            if (panelMod() != null && hypot(x - startX, y - startY) > HOLD_BLOCK) endHold()
            if (hasDroppedMenu) return
            if (isMenuPull) {
                val down = if (isNotificationsPulled) startY - y else y - startY
                val across = abs(x - startX)
                if (down > PULL_TRIGGER && down > across) {
                    hasDroppedMenu = true
                    hasMoved = true
                    endHold()
                    dropNotifications()
                    return
                }
                if (hypot(across, down) < SCROLL_SLOP || down > across) return
                isMenuPull = false
            }
            // The list only scrolled from a band along its right edge, so a drag anywhere else did nothing and the menu read as stuck; it scrolls from anywhere once the drag is clearly vertical.
            /* A list that fit its menu could not scroll, so an upward drag on it fell through to the swipe that closes the panel and the menu shut under the finger; the menu always scrolls, rubber-banding when there is nothing to scroll. */
            /* A drag on the frost beside the cards scrolled the list like a drag on a card, so the menu could not be swiped shut anywhere; off the cards an upward swipe closes it and the list stays put. */
            if (size == Size.Notifications && isOffCards && !isMenuPull) {
                if (hypot(x - startX, y - startY) > SCROLL_SLOP) {
                    hasMoved = true
                    endHold()
                }
                if (!hasDismissed && startY - y > PULL_TRIGGER && startY - y > abs(x - startX)) {
                    hasDismissed = true
                    toClosed()
                }
                return
            }
            if (size == Size.Notifications && scrollMode != ScrollMode.Across) {
                if (scrollMode == ScrollMode.Undecided) {
                    val across = abs(x - startX)
                    val vertical = abs(y - startY)
                    if (across > SCROLL_SLOP && across > vertical) scrollMode = ScrollMode.Across
                    else if (vertical > SCROLL_SLOP && pressedCluster != null && (y > startY) == isPressedFolded && pullJob?.isActive != true) {
                        scrollMode = ScrollMode.Pull
                        beginClusterPull(pressedCluster!!, isPressedFolded, y)
                    } else if (vertical > SCROLL_SLOP) {
                        scrollMode = ScrollMode.Along
                        bounceJob?.cancel()
                        val held = notificationsOverscroll
                        scrollOrigin = notificationsScroll.value + (if (held > 0f) -unrubber(held) else unrubber(-held)).roundToInt()
                        scrollFromY = y
                        lastScrollAt = SystemClock.uptimeMillis()
                        scrollVelocity.resetTracking()
                    }
                }
                if (scrollMode == ScrollMode.Pull) {
                    hasMoved = true
                    endHold()
                    val travel = (y - pullFromY) / CLUSTER_PULL_SPAN
                    clusterPull = ((if (isPullOpening) 0f else 1f) + travel).coerceIn(0f, 1f)
                    return
                }
                if (scrollMode == ScrollMode.Along) {
                    hasMoved = true
                    endHold()
                    val now = SystemClock.uptimeMillis()
                    scrollVelocity.addPosition(now, androidx.compose.ui.geometry.Offset(0f, y * Island.density))
                    lastScrollAt = now
                    val wanted = scrollOrigin - (y - scrollFromY) * Island.density * SCROLL_GAIN
                    val ceiling = notificationsScroll.maxValue.toFloat()
                    notificationsScroll.dispatchRawDelta(wanted.coerceIn(0f, ceiling) - notificationsScroll.value.toFloat())
                    isAtEnd = notificationsScroll.value >= notificationsScroll.maxValue
                    notificationsOverscroll = when {
                        wanted < 0f -> rubber(-wanted)
                        wanted > ceiling -> -rubber(wanted - ceiling)
                        else -> 0f
                    }
                    return
                }
                if (scrollMode == ScrollMode.Undecided) return
            }
            val isPanel = panelMod() != null || mode == Mode.Alert
            if (!hasDismissed && isPanel && startY - y > PULL_TRIGGER && abs(x - startX) < 40f) {
                hasDismissed = true
                hasMoved = true
                endHold()
                Haptics.play(Haptic.Dismiss)
                toClosed()
            }
            return
        }
        val down = y - startY
        val across = x - startX
        val sideways = abs(across)
        val isCommitted = gate.offer(x, y)
        if (hypot(sideways, down) <= HOLD_BLOCK) {
            grantHoldWander()
            return
        }
        if (!isCommitted) return
        pullDrop = max(pullDrop, down)
        if (isDragging || hasSwiped) untoy() else toy(across, down)
        if (!hasPulled && size.isClosed && !hasSwiped && !isNotificationsSettling() && !isDragging &&
            down > PULL_TRIGGER && down > sideways
        ) {
            hasPulled = true
            hasMoved = true
            endHold()
            untoy()
            Haptics.play(Haptic.Expand)
            openNotifications()
            return
        }
        val committed = sideways - HOLD_BLOCK
        if (!hasPulled && size.isClosed && committed > 0 && sideways > abs(down)) {
            if (dragSwap(sign(across) * committed)) {
                hasSwiped = true
                hasMoved = true
                return
            }
        }
        if (!hasDismissed && down < -24f && sideways < 40f) {
            hasDismissed = true
            hasMoved = true
            endHold()
            /* Closing the Notifications menu buzzed on every swipe and tap that shut it, which read as an error on something as routine as putting it away; it closes silently. */
            if (size != Size.Notifications) Haptics.play(Haptic.Dismiss)
            toClosed()
        }
    }

    fun onUp() {
        if (mode == Mode.Torch) {
            if (!hasMoved && !Island.wasCancelled) {
                Haptics.play(Haptic.Expand)
                foldTorch(false)
            }
            return
        }
        if (isAlertTouch) {
            isAlertTouch = false
            onAlertUp(lastX, lastY)
            return
        }
        releaseSwap()
        if (Island.wasCancelled) {
            untoy()
            if (!hasPulled && !hasHeld && !closedInTouch && mode != Mode.Extended && !isNotificationsSettling() &&
                size.isClosed && pullDrop > PULL_STOLEN
            ) {
                hasPulled = true
                endHold()
                Haptics.play(Haptic.Expand)
                openNotifications()
                return
            }
            endHold()
            return
        }
        endHold()
        untoy()
        driftNotifications()
        if (hasMoved || hasHeld || closedInTouch) return
        tap()
    }

    private fun tap() {
        if (mode == Mode.Extended) {
            // A tap inside the menu that no row took fell through to closing it, so tapping its empty side shut the menu; only a tap outside it closes it, in onDown.
            /* A tap in the frost between the cards and the system notices landed inside the menu's box and was taken for its empty side, so the menu would not close there; a tap above or below every card closes it. */
            if (size == Size.Notifications && rowBounds.values.none { lastY >= it.top && lastY <= it.bottom }) {
                toClosed()
                return
            }
            if (panelMod() != null || size == Size.Notifications) return
            Haptics.play(Haptic.Tap)
            toClosed()
            return
        }
        if (mode == Mode.Alert) {
            dwellJob?.cancel()
            if (notice?.picture != null && size != Size.Picture) {
                Haptics.play(Haptic.Expand)
                openPicture()
                return
            }
            openCurrent()
            return
        }
        if (isNotificationsSettling()) return
        becomeExtended()
        Haptics.play(Haptic.Expand)
        when (val owner = liveMods.firstOrNull()) {
            Mod.Timer -> openTimer()
            Mod.Media -> openPlayer()
            Mod.Trip -> openTrip()
            Mod.Call -> {
                Island.call?.key?.let(Island.actions::openNotification)
                toClosed()
            }
            Mod.Torch -> foldTorch(false)
            Mod.Transfer -> if (transferStage == TransferStage.Leading && transferShown?.state == TransferState.Active) openTransfer() else toClosed()
            null -> openNotifications()
        }
    }

    private fun driftNotifications() {
        pressedCluster = null
        if (scrollMode == ScrollMode.Pull) {
            settleClusterPull()
            return
        }
        if (scrollMode != ScrollMode.Along || size != Size.Notifications) return
        if (notificationsOverscroll != 0f) {
            scrollMode = ScrollMode.Undecided
            bounceNotifications()
            return
        }
        flingNotifications(if (SystemClock.uptimeMillis() - lastScrollAt > FLING_STALE) 0f else -scrollVelocity.calculateVelocity().y * SCROLL_GAIN * FLING_SHARE)
        scrollMode = ScrollMode.Undecided
    }

    private fun rubberReach() = Island.screenHeight * Island.density * RUBBER_REACH

    private fun rubber(distance: Float): Float = (1f - 1f / (distance * RUBBER_GIVE / rubberReach() + 1f)) * rubberReach()

    private fun unrubber(shown: Float): Float = (rubberReach() / (rubberReach() - shown.coerceAtMost(rubberReach() * 0.99f)) - 1f) * rubberReach() / RUBBER_GIVE

    private fun bounceNotifications() {
        bounceJob = Island.scope.launch {
            androidx.compose.animation.core.animate(notificationsOverscroll, 0f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = RUBBER_DAMPING, stiffness = RUBBER_STIFFNESS)) { value, _ ->
                notificationsOverscroll = value
            }
        }
    }

    private fun clustered(list: List<NoticeGroup>): List<NoticeGroup> = list.groupBy { it.head.owner }.values.flatten()

    private fun addToNotifications(next: Notice) {
        Haptics.play(Haptic.Release)
        if (next.isSystem) {
            systemNotices = listOf(next) + systemNotices.filter { it.key != next.key }
            fitNotifications()
            return
        }
        val id = next.owner + "  " + next.title
        val existing = groups.firstOrNull { it.id == id }
        val group = NoticeGroup(id, next, listOf(next) + existing?.entries.orEmpty().filter { it.key != next.key })
        val scroll = notificationsScroll.value
        val anchor = if (isAtEnd) null else groups.filter { it.id != id && (rowTops[it.id] ?: 0f) + (rowHeights[it.id] ?: 0f) > scroll }.minByOrNull { rowTops[it.id] ?: 0f }?.id
        val held = anchor?.let { scroll - (rowTops[it] ?: 0f) }
        groups = clustered(listOf(group) + groups.filter { it.id != id })
        arrival = id
        arrivalRound++
        fitNotifications()
        if (anchor == null || held == null) return
        Island.scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val top = rowTops[anchor] ?: return@launch
            notificationsScroll.scrollTo((top + held).roundToInt())
        }
    }

    private fun centreGroup(id: String) {
        flingJob?.cancel()
        flingJob = Island.scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val top = rowTops[id] ?: return@launch
            val tall = rowHeights[id] ?: return@launch
            val viewport = (notificationsScroll.viewportSize - listOverflow).toFloat()
            val target = if (tall >= viewport) top else top + tall / 2f - viewport / 2f
            notificationsScroll.animateScrollTo(target.roundToInt().coerceAtLeast(0), tween(CENTRE_GLIDE, easing = com.v.island.vas.Curves.split))
        }
    }

    private fun flingNotifications(speed: Float) {
        if (abs(speed) < FLING_FLOOR) return
        flingJob = Island.scope.launch {
            notificationsScroll.scroll {
                var last = 0f
                AnimationState(0f, speed).animateDecay(exponentialDecay(FLING_FRICTION)) {
                    val step = value - last
                    last = value
                    if (abs(scrollBy(step) - step) > 0.5f) cancelAnimation()
                }
            }
        }
    }

    fun toggleGroup(group: NoticeGroup) {
        Haptics.play(Haptic.Tap)
        isAtEnd = false
        expandedGroup = if (expandedGroup == group.id) null else group.id
        if (expandedGroup == group.id) centreGroup(group.id)
    }

    fun pressCluster(app: String?, isFolded: Boolean) {
        pressedCluster = app
        isPressedFolded = isFolded
    }

    private fun beginClusterPull(app: String, isOpening: Boolean, y: Float) {
        hasMoved = true
        endHold()
        isAtEnd = false
        isPullOpening = isOpening
        pullFromY = y
        clusterPull = if (isOpening) 0f else 1f
        pulledApp = app
        if (isOpening) unfoldedApps = unfoldedApps + app
    }

    private fun settleClusterPull() {
        scrollMode = ScrollMode.Undecided
        val app = pulledApp ?: return
        val isOpen = if (isPullOpening) clusterPull > CLUSTER_OPEN_AT else clusterPull > 1f - CLUSTER_OPEN_AT
        if (isOpen == isPullOpening) Haptics.play(Haptic.Tap)
        pullJob = Island.scope.launch {
            animate(clusterPull, if (isOpen) 1f else 0f, animationSpec = tween(CLUSTER_SETTLE, easing = CLUSTER_EASE)) { value, _ -> clusterPull = value }
            if (!isOpen) unfoldedApps = unfoldedApps - app
            pulledApp = null
        }
    }

    fun groupAction(group: NoticeGroup, action: Action) {
        Haptics.play(Haptic.Tap)
        Island.actions.noticeAction(group.head.key, action.index)
    }

    private fun dragSwap(travelled: Float): Boolean {
        val live = liveMods
        if (live.size < 2 || transferStage != TransferStage.None) return false
        val towards = if (satelliteSide > 0) -travelled else travelled
        var progress = if (swapDirection > 0) towards else -towards
        if (progress < 0 && live.size > 2) {
            swapDirection = -swapDirection
            progress = -progress
        }
        swap = (progress / SWAP_DISTANCE).coerceIn(0f, 1f)
        val isPast = swap > 0.5f
        if (isPast != swapPassed) {
            swapPassed = isPast
            Haptics.play(Haptic.Tap)
            if (isPast) endHold()
        }
        isDragging = true
        return swap > 0.04f
    }

    private fun abandonSwap() {
        if (!isDragging) return
        isDragging = false
        swap = 0f
        swapDirection = 1
        swapPassed = false
    }

    private fun releaseSwap() {
        if (!isDragging) return
        val isCommitted = swap > 0.5f
        swapPassed = false
        val direction = swapDirection
        val isFlanked = liveMods.size > 2
        swap = 0f
        swapDirection = 1
        isDragging = false
        if (!isCommitted) return
        val incoming = satellites().firstOrNull()
        rotate(if (isFlanked) direction else 1)
        if (!isFlanked) satelliteSide = -satelliteSide
        toClosed()
        /* The promoted mod's face was composed afresh, so its glyph popped in at Main's edge while the satellite holding it vanished; the glyph travels over from where the satellite stood. */
        swapArrival = incoming?.takeIf { it.mod == liveMods.firstOrNull() }?.let { it.mod to it.x - glyphAligned(layout()) }
    }

    private var swapArrival: Pair<Mod, Float>? = null

    val isSwapArriving: Boolean get() = swapArrival != null

    fun takeSwapArrival(mod: Mod): Float? = swapArrival?.takeIf { it.first == mod }?.second?.also { swapArrival = null }

    private fun rotate(direction: Int) {
        val live = liveMods
        if (live.size < 2) return
        val spun = if (direction < 0) listOf(live.last()) + live.dropLast(1) else live.drop(1) + live.first()
        modOrder = spun + modOrder.filterNot { it in live }
    }

    fun onAppearanceChanged() {
        if (size == Size.Notifications) fitNotifications()
    }

    private class DragGate {
        private val trail = ArrayDeque<Triple<Float, Float, Long>>()
        private var isCommitted = false

        fun offer(x: Float, y: Float): Boolean {
            if (isCommitted) return true
            val now = SystemClock.uptimeMillis()
            trail.addLast(Triple(x, y, now))
            while (trail.size > 1 && now - trail.first().third > DRAG_WINDOW) trail.removeFirst()
            val first = trail.first()
            isCommitted = hypot(x - first.first, y - first.second) >= DRAG_COMMIT
            return isCommitted
        }
    }

    companion object {
        /* Mirrors GROWN_PAD and --grown-pad in the retired pill.css: a grown bubble hangs this far below the compact one's top. */
        const val GROWN_PAD = 26f
        /* Mirrors --edge-over in the retired pill.css: how far past the screen's top edge the closed bubble rises while the Dashboard stands open. */
        const val EDGE_OVER = 20f
        const val GROWN_WIDTH = 340f
        /* The player stood under the Clock and Status row at 304 tall and pushed most of the screen down; it is 30% shorter and rises into that row, swallowing both bubbles while it is open. */
        const val PLAYER_HEIGHT = 213f
        const val TRANSFER_PANEL_HEIGHT = 128f
        private const val PACE_FLOOR = 1500L
        val TRANSFER_HOLD_WORDS = Regex("pause|pausieren|anhalten|resume|fortsetzen|weiter", RegexOption.IGNORE_CASE)
        val TRANSFER_STOP_WORDS = Regex("cancel|abbrechen|abbruch|stop|stopp|beenden", RegexOption.IGNORE_CASE)
        const val ALERT_WIDTH = 300f
        const val ALERT_GROWTH = 1.02f
        const val ALERT_HEIGHT = 155f
        const val ALERT_IMAGE_HEIGHT = 190f
        const val ALERT_LINES = 4
        /* Mirrors ALERT_FLOOR, CENTRE_CEILING, PICTURE_CENTRE_CEILING, CENTRE_LIFT, ALERT_TAP_SLOP and ZONE_HEIGHT_PART in js/alert.js, and #alert-zone's width in pill.css. */
        const val ALERT_FLOOR = 88f
        const val CENTRE_CEILING = 0.6f
        const val PICTURE_CENTRE_CEILING = 0.8f
        const val CENTRE_LIFT = 0.1f
        const val CENTRE_RADIUS = 28f
        const val ALERT_TAP_SLOP = 10f
        const val ALERT_ZONE_WIDTH = 96f
        const val ALERT_ZONE_HEIGHT_PART = 0.3f
        const val PICTURE_MAXIMUM_HEIGHT = 460f
        const val BUMP = 12f
        const val BUMP_MILLISECONDS = 420
        val BUMP_RISE = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
        const val HOLD_SCALE = 1.14f
        const val HOLD_BLOCK = 18f
        const val HOLD_WANDER = 150L
        const val DRAG_RADIUS = 40f
        const val NOTIFICATIONS_DRAG_SCALE = 0.15f
        const val PULL_TRIGGER = 22f
        const val PULL_STOLEN = 12f
        const val SPLIT_SHRINK = 24f
        const val SWAP_DISTANCE = 80f
        const val SWAP_PRESS = 5f
        const val FAR_PUSH = 17f
        const val SATELLITE_SHARE = 0.8f
        const val SATELLITE_GAP = 8f
        const val SATELLITE_GAP_ASIDE = 4f
        const val CHIP_ROOM = 12f
        /* The hole was reserved at 34 wide and the glyphs kept 40 apart around it, so a closed pill spent a third of its width on air; the S25's cutout spec is 19.3dp wide (dumpsys display), and the glyphs now clear it by about 3dp a side. */
        const val HOLE_WIDTH = 20f
        const val HOLE_GAP = 26f
        const val DOT_SIZE = 6f
        const val DOT_GAP = 5f
        const val GONE_GRACE = 900L
        const val NOTIFICATIONS_HOMING = 380L
        const val PULL_ROOM = 24f
        private const val CLUSTER_PULL_SPAN = 150f
        private const val CLUSTER_OPEN_AT = 0.3f
        private const val CLUSTER_SETTLE = 260
        private val CLUSTER_EASE = androidx.compose.animation.core.CubicBezierEasing(0.2f, 1f, 0.35f, 1f)
        private const val HOMING_FLOOR = 8f
        private const val HOMING_LIMIT = 6
        const val NOTIFICATIONS_TOP = 48f
        const val NOTIFICATIONS_EMPTY = 96f
        const val CLEAR_SIZE = 44f
        const val SYSTEM_ROW = 36f
        const val SYSTEM_PAD = 10f
        const val CLEAR_GAP = 12f
        /* Mirrors the top transition on #notifications-clear in pill.css. */
        const val CLEAR_TOP_MILLISECONDS = 460
        const val CLEAR_STAGGER = 35L
        const val CLEAR_FLIGHT = 240L
        private const val SCROLL_SLOP = 8f
        private const val FLING_FLOOR = 150f
        private const val FLING_STALE = 120L
        private const val FLING_FRICTION = 1.5f
        private const val FLING_SHARE = 0.8f
        private const val SCROLL_GAIN = 1.2f
        private const val RUBBER_REACH = 0.18f
        private const val RUBBER_GIVE = 0.55f
        private const val RUBBER_DAMPING = 0.82f
        private const val RUBBER_STIFFNESS = 420f
        private const val CENTRE_GLIDE = 380
        const val SYSTEM_FOOT = 28f
        const val OUTSIDE_LATCH = 120L
        const val DRAG_COMMIT = 10f
        const val DRAG_WINDOW = 110L
        /* The flashlight at 60% of the screen on 40 slots still sat above the thumb and read small; it stands lower on larger slots than now.js's NOW_SLOT. */
        const val TORCH_SLOT = 48f
        const val TORCH_SLOT_GAP = 16f
        const val TORCH_PAD = 16f
        /* The flashlight lay across the middle of the screen over the mods it stopped; it stands upright at the right edge, the thumb's side, beside whatever Main is doing. */
        const val TORCH_WIDTH = TORCH_SLOT + TORCH_PAD * 2f
        const val TORCH_MINIMUM_HEIGHT = 190f
        const val TORCH_RISE = 0.6f
        const val TORCH_EDGE = 14f
        /* The Off pill was a labelled pill beside the flashlight; it is a round button carrying the struck-through torch. */
        const val TORCH_OFF_WIDTH = 52f
        const val TORCH_OFF_HEIGHT = 52f
        const val TORCH_FLIGHT = 420
        const val TORCH_LANDING = 0.8f
        const val LAND_SWELL = 0.1f
        const val LAND_MILLISECONDS = 460
        const val TORCH_OFF_GAP = 14f
        const val TORCH_SWIPE_STEP = 36f
        const val TRANSFER_YIELD_POP = 150L
        const val TRANSFER_YIELD_SLIDE = 260L
        const val TRANSFER_ARROW_DROP = 420
        const val TRANSFER_RING_AFTER = 260L
        const val TRANSFER_CHECK = 300
        const val TRANSFER_SPARKLE = 420
        const val TRANSFER_ARROW_EXIT = 280
        const val TRANSFER_FINISH_DONE = 1100L
        const val TRANSFER_FINISH_FAILED = 420L
        const val TRANSFER_MERGE = 400L
    }
}

private enum class ScrollMode { Undecided, Along, Across, Pull }
