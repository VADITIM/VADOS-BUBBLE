package com.v.island.bubble

import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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

enum class Mod { Timer, Media, Call, Trip }

enum class Mode { Idle, Haptic, Alert, Extended }

enum class Size(val isClosed: Boolean) {
    Idle(true), Playing(true), Timing(true), Calling(true), Travelling(true),
    Alert(false), Image(false), Picture(false), Player(false), TimerPanel(false), TripPanel(false), Notifications(false),
}

enum class Face { Idle, Media, Timer, Call, Trip, Alert, Player, TimerPanel, TripPanel, Notifications }

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
        private set
    var pictureHeight by mutableFloatStateOf(320f)
        private set
    var notificationsHeight by mutableFloatStateOf(320f)
        private set
    var notificationsDrop by mutableFloatStateOf(0f)
        private set
    private var notificationsHead = NOTIFICATIONS_HEAD
    private var notificationsList = -1f
    var accent by mutableStateOf(Color.White)
        private set
    var isScrubbing by mutableStateOf(false)
    var scrubPosition by mutableStateOf(0L)
    val notificationsScroll = ScrollState(0)

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
    private var hasWandered = false
    private var holdPending = false
    private var pullDrop = 0f
    private var scrollOrigin = 0
    private var gate = DragGate()
    private var closedInTouch = false
    private var closedOutsideAt = -1_000L
    private var notificationsClosedAt = -1_000L

    private val appearance get() = Island.appearance

    val liveMods: List<Mod> get() = modOrder.filter(::isLive)

    var clearedMods by mutableStateOf(emptySet<Mod>())
        private set

    fun isLive(mod: Mod): Boolean = isPresent(mod) && mod !in clearedMods && !Island.now.isStolen(mod)

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
        Mod.Timer -> Island.timer != null
        Mod.Media -> Island.media != null
        Mod.Call -> Island.call != null
        Mod.Trip -> Island.trip != null
    }

    fun accentOf(mod: Mod): Color = when (mod) {
        Mod.Timer -> Island.timer?.accent
        Mod.Media -> Island.media?.accent
        Mod.Call -> Island.call?.accent
        Mod.Trip -> Island.trip?.accent
    } ?: appearance.accent

    private fun satelliteSize() = appearance.compactHeight

    private fun modFloor(): Float {
        val glyph = appearance.compactHeight - 10f
        val inset = (appearance.compactHeight - glyph) / 2f - 1f
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

    private fun glyphAligned(box: RowLayout) = box.mainX - box.main / 2f + satelliteSize() / 2f

    fun satellites(): List<SatellitePlacement> {
        val live = liveMods
        if (live.size < 2 || !size.isClosed) return emptyList()
        val box = layout()
        val trader = { mod: Mod -> SatellitePlacement(mod, box.satelliteX, box.satellite, glyphAligned(box), 1f) }
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

    val isRisen: Boolean get() = size.isClosed && Island.dashboard.isOpen

    fun target(): Frame {
        val frame = frame()
        if (!isRisen) return frame
        return frame.copy(top = -EDGE_OVER, height = appearance.compactHeight + EDGE_OVER)
    }

    private fun frame(): Frame {
        val grab = appearance.grab
        val grown = grab + GROWN_PAD
        return when (size) {
            Size.Idle -> Frame(appearance.compactWidth, appearance.compactHeight, appearance.compactHeight / 2f, grab, layout().mainX)
            Size.Playing, Size.Timing, Size.Calling, Size.Travelling -> {
                val box = layout()
                Frame(box.main, appearance.compactHeight, appearance.compactHeight / 2f, grab, box.mainX)
            }
            Size.Alert -> Frame(ALERT_WIDTH, ALERT_HEIGHT, 24f, grab, 0f)
            Size.Image -> Frame(ALERT_WIDTH, ALERT_IMAGE_HEIGHT, 24f, grab, 0f)
            Size.Picture -> Frame(GROWN_WIDTH, pictureHeight, 26f, grown, 0f)
            Size.Player -> Frame(GROWN_WIDTH, 304f, 34f, grown, 0f)
            Size.TimerPanel -> Frame(GROWN_WIDTH, 132f, 26f, grown, 0f)
            Size.TripPanel -> Frame(GROWN_WIDTH, 232f, 26f, grown, 0f)
            Size.Notifications -> Frame(GROWN_WIDTH, notificationsHeight, 26f, grown + notificationsDrop, 0f)
        }
    }

    val holdScale: Float get() = if (isHolding) HOLD_SCALE else 1f

    fun border(): Color = if (mode == Mode.Alert) accent else com.v.island.vas.Palette.border

    private fun closedSizeOf(mod: Mod?) = when (mod) {
        Mod.Timer -> Size.Timing
        Mod.Media -> Size.Playing
        Mod.Call -> Size.Calling
        Mod.Trip -> Size.Travelling
        null -> Size.Idle
    }

    private fun closedFaceOf(mod: Mod?) = when (mod) {
        Mod.Timer -> Face.Timer
        Mod.Media -> Face.Media
        Mod.Call -> Face.Call
        Mod.Trip -> Face.Trip
        null -> Face.Idle
    }

    fun onModsChanged() {
        clearedMods = clearedMods.filter(::isPresent).toSet()
        if (mode == Mode.Idle) toClosed()
        if (size == Size.TimerPanel && Island.timer == null) toClosed()
        if (size == Size.Player && Island.media == null) toClosed()
        if (size == Size.TripPanel && Island.trip == null) toClosed()
    }

    fun toClosed() {
        dwellJob?.cancel()
        val wasOpen = !size.isClosed
        if (wasOpen) closedInTouch = Island.isTouchDown
        if (size == Size.Notifications) notificationsClosedAt = SystemClock.uptimeMillis()
        mode = Mode.Idle
        notice = null
        notificationsDrop = 0f
        val owner = liveMods.firstOrNull()
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

    fun onNotice(next: Notice) {
        if (Island.isLandscape) return
        dwellJob?.cancel()
        goneJob?.cancel()
        val hasImage = next.picture != null
        val shown = notice
        val isAppending = mode == Mode.Alert && !hasImage && size != Size.Image && shown != null &&
            (shown.key == next.key || (next.title.isNotEmpty() && shown.title == next.title && shown.packageName == next.packageName))
        val lines = next.messages.map { it.text }.filter { it.isNotEmpty() }.ifEmpty { listOf(next.text) }
        alertLines = if (isAppending) (alertLines + lines.takeLast(1)).distinct().takeLast(ALERT_LINES) else lines.takeLast(1)
        mode = Mode.Alert
        notice = next
        accent = next.accent
        face = Face.Alert
        size = if (hasImage) Size.Image else Size.Alert
        Haptics.play(Haptic.Release)
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
        val entries = Island.actions.readNotifications()
        val byId = LinkedHashMap<String, MutableList<Notice>>()
        entries.forEach { entry -> byId.getOrPut(entry.app + "  " + entry.title) { mutableListOf() }.add(entry) }
        groups = byId.map { (id, list) -> NoticeGroup(id, list.first(), list) }
        Island.unread = 0
        becomeExtended()
        face = Face.Notifications
        fitNotifications()
        size = Size.Notifications
        Island.scope.launch { notificationsScroll.scrollTo(0) }
    }

    fun fitNotifications() {
        val highest = appearance.grab + GROWN_PAD
        val tall = Island.screenHeight
        val below = CLEAR_GAP + CLEAR_SIZE
        val floor = (tall * 0.35f).roundToInt().toFloat()
        val ceiling = (tall * 0.85f).roundToInt() - below
        val list = if (notificationsList >= 0f) notificationsList else groups.sumOf { group -> rowHeight(group).toDouble() }.toFloat()
        val wanted = notificationsHead + list + 20f
        val height = max(floor, min(ceiling, wanted))
        val lowest = max(highest, tall - height - below - NOTIFICATIONS_FOOT)
        val top = (lowest + (highest - lowest) * appearance.notificationsRise).roundToInt().toFloat()
        notificationsHeight = height
        notificationsDrop = top - highest
    }

    /* The height was guessed from line counts, so a long message overflowed and a short list left a gap; the menu is now sized off its rows as they are laid out, as notificationsWindow did off scrollHeight. */
    fun reportNotificationsHead(height: Float) {
        if (height == notificationsHead) return
        notificationsHead = height
        if (size == Size.Notifications) fitNotifications()
    }

    fun reportNotificationsList(height: Float) {
        if (height == notificationsList) return
        notificationsList = height
        if (size == Size.Notifications) fitNotifications()
    }

    private fun rowHeight(group: NoticeGroup): Float {
        val lines = group.entries.sumOf { entry -> max(1, entry.messages.size).coerceAtMost(4) }
        return 70f + lines * 20f
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
        Island.actions.openNotification(group.head.key)
        Haptics.play(Haptic.Tap)
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

    private fun panelMod(): Mod? = when (size) {
        Size.Player -> Mod.Media
        Size.TimerPanel -> Mod.Timer
        Size.TripPanel -> Mod.Trip
        else -> null
    }

    private fun leaveForMod(mod: Mod?) {
        when (mod) {
            Mod.Media -> Island.media?.packageName?.takeIf { it.isNotEmpty() }?.let(Island.actions::openApp)
            Mod.Timer -> Island.timer?.key?.let(Island.actions::openNotification)
            Mod.Call -> Island.call?.key?.let(Island.actions::openNotification)
            Mod.Trip -> Island.trip?.key?.let(Island.actions::openNotification)
            null -> Unit
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
        scrollOrigin = notificationsScroll.value
        hasMoved = false
        hasHeld = false
        hasSwiped = false
        hasDismissed = false
        hasPulled = false
        hasWandered = false
        pullDrop = 0f
        gate = DragGate()
        heldStep = null
        if (closedInTouch) return
        if (mode == Mode.Extended && size == Size.Notifications && !holds(x, y)) {
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
        releaseSwap()
        endHold()
        untoy()
    }

    fun holds(x: Float, y: Float): Boolean {
        val box = target()
        val left = Island.screenWidth / 2f + box.shift - box.width / 2f
        return x >= left && x <= left + box.width && y >= box.top && y <= box.top + box.height
    }

    fun onMove(x: Float, y: Float) {
        if (hasHeld || closedInTouch) return
        if (!size.isClosed || mode == Mode.Extended) {
            grantHoldWander()
            if (size == Size.Notifications && isInScrollBand(startX)) {
                hasMoved = true
                endHold()
                val wanted = (scrollOrigin - (y - startY) * Island.density).roundToInt()
                notificationsScroll.dispatchRawDelta(wanted - notificationsScroll.value.toFloat())
                return
            }
            val isPanel = size == Size.Notifications || panelMod() != null || mode == Mode.Alert
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
            Haptics.play(Haptic.Dismiss)
            toClosed()
        }
    }

    fun onUp() {
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
        if (hasMoved || hasHeld || closedInTouch) return
        tap()
    }

    private fun tap() {
        if (mode == Mode.Extended) {
            if (panelMod() != null) return
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
            null -> openNotifications()
        }
    }

    private fun isInScrollBand(x: Float): Boolean {
        val box = target()
        val right = Island.screenWidth / 2f + box.width / 2f
        return x >= right - Island.screenWidth * SCROLL_BAND
    }

    private fun dragSwap(travelled: Float): Boolean {
        val live = liveMods
        if (live.size < 2) return false
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
        rotate(if (isFlanked) direction else 1)
        if (!isFlanked) satelliteSide = -satelliteSide
        toClosed()
    }

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
        const val ALERT_WIDTH = 300f
        const val ALERT_HEIGHT = 155f
        const val ALERT_IMAGE_HEIGHT = 190f
        const val ALERT_LINES = 4
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
        const val SATELLITE_GAP = 8f
        const val SATELLITE_GAP_ASIDE = 4f
        const val CHIP_ROOM = 12f
        const val HOLE_WIDTH = 34f
        const val HOLE_GAP = 40f
        const val DOT_SIZE = 6f
        const val DOT_GAP = 5f
        const val GONE_GRACE = 900L
        const val NOTIFICATIONS_HOMING = 460L
        const val NOTIFICATIONS_FOOT = 16f
        const val NOTIFICATIONS_HEAD = 34f
        const val CLEAR_SIZE = 44f
        const val CLEAR_GAP = 12f
        const val CLEAR_STAGGER = 35L
        const val CLEAR_FLIGHT = 240L
        const val SCROLL_BAND = 0.15f
        const val OUTSIDE_LATCH = 120L
        const val DRAG_COMMIT = 10f
        const val DRAG_WINDOW = 110L
    }
}
