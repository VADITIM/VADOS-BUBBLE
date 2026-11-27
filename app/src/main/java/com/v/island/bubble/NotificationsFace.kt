package com.v.island.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.positionInParent
import com.v.island.vas.Durations
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v.island.vas.Curves
import com.v.island.vas.Haptic
import com.v.island.vas.Haptics
import com.v.island.vas.Palette
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

fun agoText(postedAt: Long): String {
    if (postedAt <= 0L) return ""
    val minutes = (System.currentTimeMillis() - postedAt) / 60_000
    if (minutes < 1) return "now"
    return "${minutes}m"
}

/* The menu grew out of Main's glass, so Main and the menu were one bound pill; the menu is its own layer of entry cards on the frost, and Main shrinks to the punch hole or stays as the live mod. */
@Composable
fun NotificationsMenu(bubble: MainBubble) {
    val isOpen = bubble.size == Size.Notifications
    val frame = bubble.menuFrame()
    val leaving = remember { Animatable(1f) }
    LaunchedEffect(isOpen) {
        if (isOpen) {
            leaving.snapTo(0f)
            return@LaunchedEffect
        }
        leaving.animateTo(1f, tween(MENU_FADE, easing = Curves.leave))
    }
    var isDropping by remember { mutableStateOf(false) }
    LaunchedEffect(isOpen) {
        if (!isOpen) return@LaunchedEffect
        isDropping = true
        delay(MENU_DROP.toLong())
        isDropping = false
    }
    // Every change in the list's height restarted the overshooting drop on the menu's top, so a swipe or an unfold bounced the whole view; only the open drops, and after it the top follows the rows, which animate themselves.
    val top = animateFloatAsState(frame.top, if (isDropping) tween(MENU_DROP, easing = Curves.open) else androidx.compose.animation.core.snap(), label = "menuTop")
    val left = Island.screenWidth / 2f - frame.width / 2f
    SideEffect {
        if (isOpen) Island.regions[Region.Menu] = Rect(left, frame.top, left + frame.width, frame.top + frame.height)
        else Island.regions.remove(Region.Menu)
    }
    if (!isOpen && leaving.value >= 1f) return
    Box(
        Modifier
            .offset { IntOffset((left * Island.density).roundToInt(), (top.value * Island.density).roundToInt()) }
            .size(frame.width.dp, frame.height.dp)
            .graphicsLayer { alpha = if (isOpen) 1f else 1f - leaving.value }
            .pointerInput(bubble) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val density = Island.density
                    val origin = Offset(left, top.value)
                    bubble.onDown(down.position.x / density + origin.x, down.position.y / density + origin.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!change.isConsumed) bubble.onUp() else bubble.onReleasedOnControl()
                            break
                        }
                        bubble.onMove(change.position.x / density + origin.x, change.position.y / density + origin.y)
                    }
                }
            },
    ) { NotificationsFace(bubble, Island.appearance) }
}

@Composable
fun NotificationsHoming(bubble: MainBubble) {
    val cards = bubble.homing
    cards.forEachIndexed { index, card ->
        key(bubble.homingRound, index) { HomingPill(bubble, card, index, index == cards.lastIndex) }
    }
}

@Composable
private fun HomingPill(bubble: MainBubble, card: Rect, index: Int, isLast: Boolean) {
    val travel = remember { Animatable(0f) }
    val isSystem = remember { bubble.systemBounds.values.any { it == card } }
    LaunchedEffect(Unit) {
        delay(index * HOMING_STAGGER)
        travel.animateTo(1f, tween(HOMING_MILLISECONDS, easing = androidx.compose.animation.core.LinearEasing))
        if (isLast) {
            bubble.land()
            bubble.homing = emptyList()
        }
    }
    if (travel.value >= 1f) return
    Blob(Island.liquid) {
        val progress = ((travel.value - HOMING_HOLD) / (1f - HOMING_HOLD)).coerceIn(0f, 1f)
        val pill = bubble.target()
        /* A system notice narrowed across the whole flight, so it stayed wide most of the way home; it snaps narrow first and travels as a small pill. */
        val narrow = Curves.powerTwoOut.transform(if (isSystem) (progress / SYSTEM_NARROW_SHARE).coerceAtMost(1f) else progress)
        val rise = Curves.sweep.transform(progress)
        val squash = Curves.powerTwoIn.transform(progress)
        val wide = card.width + (pill.width - card.width) * narrow
        val tall = card.height + (pill.height - card.height) * squash
        val x = card.center.x + (Island.screenWidth / 2f + pill.shift - card.center.x) * rise
        val y = card.center.y + (pill.top + pill.height / 2f - card.center.y) * rise
        val density = Island.density
        BlobShape(Rect((x - wide / 2f) * density, (y - tall / 2f) * density, (x + wide / 2f) * density, (y + tall / 2f) * density), minOf(tall / 2f, 22f) * density, bubble.border())
    }
}

@Composable
fun NotificationsFace(bubble: MainBubble, appearance: Appearance) {
    // The rows were laid out at the menu's live width, which overshoots past 340 while it grows, so their text re-wrapped and they changed height after landing; they are laid out at the settled width and the pill only clips them.
    Column(Modifier.fillMaxHeight().layout { measurable, constraints ->
        val settled = (MainBubble.GROWN_WIDTH * density).roundToInt()
        val placeable = measurable.measure(constraints.copy(minWidth = settled, maxWidth = settled))
        layout(constraints.maxWidth, placeable.height) { placeable.place((constraints.maxWidth - settled) / 2, 0) }
    }.padding(start = 6.4.dp, end = 6.4.dp, top = 12.dp, bottom = 8.dp)) {
        if (bubble.groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BasicText("Nothing waiting", style = type(0.75f, Palette.textFaint, appearance.contentFont, appearance.contentScale))
            }
            return@Column
        }
        /* The scroll viewport sliced the cards at the list's edges, and stretching it a screen past both covered Main, the bar and the clear button so they lost their taps; it reaches up to the bar and down to the system notices, the frost between being untouchable anyway. */
        LaunchedEffect(bubble.notificationsScroll.maxValue) { if (bubble.isAtEnd) bubble.notificationsScroll.scrollTo(bubble.notificationsScroll.maxValue) }
        val origin = remember { FloatArray(2) }
        Column(Modifier.fillMaxWidth().weight(1f).layout { measurable, constraints ->
            val menu = bubble.menuFrame()
            val above = ((menu.top + LIST_TOP - MainBubble.NOTIFICATIONS_TOP).coerceAtLeast(0f) * density).roundToInt()
            val below = ((bubble.clearTop() - bubble.systemOffset() - menu.top - menu.height + LIST_FOOT).coerceAtLeast(0f) * density).roundToInt()
            val side = (Island.screenWidth * density).roundToInt()
            bubble.listOverflow = above + below
            val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + above + below, maxHeight = constraints.maxHeight + above + below, minWidth = constraints.minWidth + side * 2, maxWidth = constraints.maxWidth + side * 2))
            layout(placeable.width - side * 2, placeable.height - above - below) { placeable.place(-side, -above) }
        }.onGloballyPositioned { at ->
            val position = at.positionInRoot()
            origin[0] = position.x / Island.density
            origin[1] = position.y / Island.density
        }.pointerInput(bubble) {
            /* The list's overflow past the menu's box stood over the shield but answered nothing, so a tap or a drag in the frost above the system notices was swallowed; a touch there is the menu's own, scrolling it or closing it on a tap off the cards. */
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val density = Island.density
                val menu = bubble.menuFrame()
                val x = down.position.x / density + origin[0]
                val y = down.position.y / density + origin[1]
                val menuLeft = Island.screenWidth / 2f + menu.shift - menu.width / 2f
                if (y >= menu.top && y <= menu.top + menu.height && x >= menuLeft && x <= menuLeft + menu.width) return@awaitEachGesture
                bubble.onDown(x, y)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!change.isConsumed) bubble.onUp() else bubble.onReleasedOnControl()
                        break
                    }
                    bubble.onMove(change.position.x / density + origin[0], change.position.y / density + origin[1])
                }
            }
        }.verticalScroll(bubble.notificationsScroll, enabled = false).layout { measurable, constraints ->
            val menu = bubble.menuFrame()
            val above = ((menu.top + LIST_TOP - MainBubble.NOTIFICATIONS_TOP).coerceAtLeast(0f) * density).roundToInt()
            val below = ((bubble.clearTop() - bubble.systemOffset() - menu.top - menu.height + LIST_FOOT).coerceAtLeast(0f) * density).roundToInt()
            val side = (Island.screenWidth * density).roundToInt()
            val placeable = measurable.measure(constraints.copy(minHeight = 0, minWidth = constraints.minWidth - side * 2, maxWidth = constraints.maxWidth - side * 2))
            layout(placeable.width + side * 2, placeable.height + above + below) { placeable.place(side, above) }
        }) {
            Column(Modifier.fillMaxWidth().graphicsLayer { translationY = bubble.notificationsOverscroll }.onSizeChanged { bubble.reportNotificationsList(it.height / Island.density) }) {
                val clusters = bubble.groups.groupBy { it.head.owner }
                /* The newest card stood at the top under the bar while the thumb works at the bottom; the apps run oldest to newest so the newest sits last, each app's cards still led by its latest. */
                val shown = clusters.values.reversed().flatten().filter { group ->
                    val cluster = clusters.getValue(group.head.owner)
                    cluster.size == 1 || group.head.owner in bubble.unfoldedApps || cluster.first() === group
                }
                shown.forEachIndexed { index, group ->
                    val app = group.head.owner
                    val clusterSize = clusters.getValue(app).size
                    val hidden = if (app in bubble.unfoldedApps) 0 else clusterSize - 1
                    val depth = shown.subList(0, index).takeLastWhile { it.head.owner == app }.size
                    val isClusterEnd = index == shown.lastIndex || shown[index + 1].head.owner != app
                    key(group.id) { NotificationRow(bubble, group, index - maxOf(0, shown.size - DROP_LIMIT), index == shown.lastIndex, depth, isClusterEnd, clusterSize, hidden, appearance) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationRow(bubble: MainBubble, group: NoticeGroup, index: Int, isLast: Boolean, depth: Int, isClusterEnd: Boolean, clusterSize: Int, hidden: Int, appearance: Appearance) {
    val entry = group.head
    val isClusterStart = depth == 0
    /* Unfolding a folded card mounted its hidden rows as if the menu had just opened, so they dropped out of the punch hole although they were stacked behind the tapped card; they slide down out from under it with their text already written. */
    val isUnfolding = remember { bubble.pulledApp == entry.owner && depth > 0 }
    val isArriving = remember { bubble.arrival == group.id }
    /* Unfolding ran on a tap as a fixed animation the finger could not hold; the hidden rows follow a drag on the card, each a step behind the one above it, and settle open or shut when it lets go. */
    fun unfolded(): Float {
        if (depth == 0 || bubble.pulledApp != entry.owner) return 1f
        val lead = minOf((depth - 1) * PULL_STAGGER, PULL_STAGGER_LIMIT)
        return ((bubble.clusterPull - lead) / (1f - lead)).coerceIn(0f, 1f)
    }
    val dropDelay = ROW_ENTER_DELAY + minOf(maxOf(index, 0), ROW_STAGGER_LIMIT) * ROW_STAGGER
    val delayMilliseconds = if (isUnfolding || isArriving) 0 else dropDelay + DROP_REVEAL_AT
    val isDropped = index in 0 until DROP_LIMIT && !isUnfolding && !isArriving
    val slide = remember { Animatable(if (isArriving) -1f else 0f) }
    /* A notice arriving while the menu was open dropped out of Main as if the menu had just opened; it slides in from the left edge and snaps into its place. */
    LaunchedEffect(bubble.arrivalRound) {
        if (bubble.arrival != group.id) return@LaunchedEffect
        bubble.arrival = null
        slide.snapTo(-1f)
        slide.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = ARRIVE_DAMPING, stiffness = ARRIVE_STIFFNESS))
    }
    val drag = remember { Animatable(0f) }
    val trash = remember { Animatable(0f) }
    val collapse = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val isMenuOpen = bubble.size == Size.Notifications
    LaunchedEffect(isMenuOpen) { if (!isMenuOpen) leave.animateTo(1f, tween(ROW_LEAVE, easing = Curves.leave)) }
    androidx.compose.runtime.DisposableEffect(group.id) { onDispose { bubble.rowBounds.remove(group.id) } }
    val iconPop = remember { Animatable(0f) }
    val stackOut = remember { List(STACK_LAYERS) { Animatable(0f) } }
    var hasStacked by remember { mutableStateOf(isUnfolding || isArriving) }
    /* Closing the menu sent a folded card home as one pill the height of its stacked edges as well, and the edges stood in place on the glass; the edges slide out from under their card when it lands and tuck back up into it on close. */
    LaunchedEffect(hidden > 0, isMenuOpen) {
        if (hidden == 0) {
            stackOut.forEach { it.snapTo(0f) }
            return@LaunchedEffect
        }
        if (!isMenuOpen) {
            stackOut.forEach { launch { it.animateTo(0f, tween(STACK_TUCK, easing = Curves.powerTwoIn)) } }
            return@LaunchedEffect
        }
        // A cluster pulled shut unmounted its rows over edges that had been reset behind the card, so the rows vanished and the edges bounced out again; the shut rows end exactly on the edges, which stand out at once.
        if (hasStacked) {
            stackOut.forEach { it.snapTo(1f) }
            return@LaunchedEffect
        }
        delay((dropDelay + DROP_REVEAL_AT + DROP_REVEAL).toLong())
        hasStacked = true
        stackOut.forEachIndexed { layer, out ->
            launch {
                delay(layer * STACK_STAGGER)
                out.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = STACK_DAMPING, stiffness = STACK_STIFFNESS))
            }
        }
    }
    var isPressing by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var held by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var trashSide by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var fullHeight by remember { mutableStateOf(0f) }
    val isExpanded = bubble.expandedGroup == group.id
    val ceiling = Island.screenHeight * ROW_CEILING_SHARE
    val isCut = !isExpanded && fullHeight > ceiling
    var isResizing by remember { mutableStateOf(false) }
    var seenExpanded by remember { mutableStateOf(isExpanded) }
    if (seenExpanded != isExpanded) {
        seenExpanded = isExpanded
        isResizing = true
    }
    LaunchedEffect(seenExpanded, isResizing) {
        if (!isResizing) return@LaunchedEffect
        delay(ROW_RESIZE + 40L)
        isResizing = false
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (isUnfolding || isArriving) {
            reveal.snapTo(1f)
            iconPop.snapTo(1f)
            return@LaunchedEffect
        }
        delay(dropDelay.toLong())
        launch { delay(DROP_REVEAL_AT.toLong()); reveal.animateTo(1f, tween(DROP_REVEAL, easing = Curves.powerTwoOut)) }
        launch { delay(DROP_REVEAL_AT + ICON_POP_AFTER); iconPop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = ICON_POP_DAMPING, stiffness = ICON_POP_STIFFNESS)) }
        drop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = DROP_DAMPING, stiffness = DROP_STIFFNESS))
    }
    val isRevealed by remember { androidx.compose.runtime.derivedStateOf { reveal.value >= 1f } }
    val isOut by remember { androidx.compose.runtime.derivedStateOf { unfolded() >= 1f } }
    val isBacked = (isRevealed || !isDropped) && isOut && bubble.size == Size.Notifications
    val card = remember { arrayOfNulls<Rect>(1) }
    /* The cards sat on a fixed black at a fixed alpha with nothing blurred behind them, unlike every other pill; they take the settings' fill and lend their box to a blur pane like the liquid does. */
    val isGlassed = (isRevealed || !isDropped) && bubble.size == Size.Notifications
    SideEffect {
        Island.glass[group.id] = glass@{
            if (!isGlassed) return@glass null
            val density = Island.density
            val out = unfolded()
            // A row being pulled in or out dropped its card's blur and fill for the whole pull, so a cluster pulled shut went transparent on the first frame; the blur follows the growing slice the row draws instead.
            if (out < 1f) {
                val box = bubble.rowBounds[group.id] ?: return@glass null
                val inset = (1f - out) * minOf(depth, STACK_LAYERS) * STACK_INSET
                val slice = Rect((box.left + inset) * density, box.top * density, (box.right - inset) * density, box.bottom * density)
                if (slice.height <= 0f || slice.width <= 0f) return@glass null
                return@glass BlobShape(slice, minOf(ROW_CORNER.value * density, slice.height / 2f), Color.Transparent, (1f - leave.value) * (STACK_SHARE + (1f - STACK_SHARE) * out))
            }
            val bounds = card[0] ?: return@glass null
            val top = maxOf(bounds.top, MainBubble.NOTIFICATIONS_TOP * density)
            val bottom = minOf(bounds.bottom, (bubble.clearTop() - bubble.systemOffset()) * density)
            if (bottom <= top) return@glass null
            BlobShape(Rect(bounds.left, top, bounds.right, bottom), ROW_CORNER.value * density, Color.Transparent, (1f - leave.value) * (1f - collapse.value))
        }
    }
    androidx.compose.runtime.DisposableEffect(group.id) { onDispose { Island.glass.remove(group.id) } }
    if (isDropped && !isRevealed) Blob(Island.liquid) {
        val card = bubble.rowBounds[group.id] ?: return@Blob null
        val menu = bubble.menuFrame()
        if (card.top > menu.top + menu.height) return@Blob null
        val pill = bubble.target()
        val travel = drop.value
        val wide = DROP_WIDTH + (card.width - DROP_WIDTH) * travel
        val tall = DROP_HEIGHT + (card.height - DROP_HEIGHT) * travel
        val x = Island.screenWidth / 2f + pill.shift + (card.center.x - Island.screenWidth / 2f - pill.shift) * travel
        val y = pill.top + pill.height + (card.center.y - pill.top - pill.height) * travel
        val density = Island.density
        BlobShape(Rect((x - wide / 2f) * density, (y - tall / 2f) * density, (x + wide / 2f) * density, (y + tall / 2f) * density), minOf(tall / 2f, 22f) * density, bubble.border())
    }
    val snap = remember { Animatable(0f) }
    fun offset() = if (isDragging) held + snap.value * trashSide else drag.value
    Box(
        Modifier.fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val out = unfolded()
                val floor = if (depth in 1..STACK_LAYERS && out < 1f) (STACK_PEEK + CLUSTER_SEAM) * density else 0f
                layout(placeable.width, maxOf(placeable.height * (1f - collapse.value) * out, floor).roundToInt()) { placeable.place(0, 0) }
            }
            /* The row clipped on both axes to hide what a fold or a collapse had taken off its height, so a card swiped out was sliced at the list's side; it clips only top and bottom. */
            .graphicsLayer {
                clip = true
                shape = ROW_CLIP
            }
            .onPlaced { bubble.rowTops[group.id] = it.positionInParent().y }
            .onSizeChanged { bubble.rowHeights[group.id] = it.height.toFloat() }
            .padding(start = 5.6.dp, end = 5.6.dp, bottom = if (isLast) 0.dp else if (isClusterEnd) CLUSTER_GAP.dp else CLUSTER_SEAM.dp)
            .onGloballyPositioned { at ->
                val bounds = at.boundsInRoot()
                val stack = if (hidden > 0) CLUSTER_SEAM + minOf(hidden, STACK_LAYERS) * STACK_PEEK else 0f
                bubble.rowBounds[group.id] = Rect(bounds.left / Island.density, bounds.top / Island.density, bounds.right / Island.density, bounds.bottom / Island.density - stack)
            }
            .graphicsLayer {
                alpha = 1f - collapse.value
                translationX = slide.value * size.width
            }
            .drawBehind {
                val out = unfolded()
                if (depth == 0 || out >= 1f) return@drawBehind
                val inset = (1f - out) * minOf(depth, STACK_LAYERS) * STACK_INSET * density
                val half = size.height / 2f
                val top = androidx.compose.ui.geometry.CornerRadius(minOf(ROW_CORNER.toPx() * out, half))
                val bottom = androidx.compose.ui.geometry.CornerRadius(minOf(ROW_CORNER.toPx(), half))
                val fill = Island.appearance.pillFill
                drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, 0f, size.width - inset, size.height, top, top, bottom, bottom)) }, fill.copy(alpha = fill.alpha * (STACK_SHARE + (1f - STACK_SHARE) * out)))
            },
    ) {
        if (isDragging || drag.value != 0f || trash.value != 0f) Box(Modifier.matchParentSize().padding(horizontal = TRASH_INSET.dp), contentAlignment = if (trashSide > 0) Alignment.CenterStart else Alignment.CenterEnd) {
            GlyphIcon(
                TRASH,
                Palette.danger,
                Modifier.size(TRASH_SIZE.dp).graphicsLayer {
                    val peek = (abs(offset()) / SWIPE_DISMISS).coerceIn(0f, 1f) * TRASH_PEEK
                    val pop = trash.value
                    alpha = maxOf(peek, pop.coerceIn(0f, 1f))
                    scaleX = 0.5f + 0.2f * peek / TRASH_PEEK + 0.3f * pop
                    scaleY = scaleX
                    translationX = -trashSide * (1f - maxOf(peek / TRASH_PEEK, pop).coerceIn(0f, 1f)) * TRASH_TRAVEL * density
                },
            )
        }
        Column {
        Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { translationX = offset() * density }
            .animateContentSize(if (isResizing) tween(ROW_RESIZE, easing = SMOOTH) else androidx.compose.animation.core.snap())
            .heightIn(max = if (isExpanded) androidx.compose.ui.unit.Dp.Infinity else ceiling.dp)
            .clip(RoundedCornerShape(ROW_CORNER))
            .graphicsLayer { compositingStrategy = if (isCut) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                if (!isCut) return@drawWithContent
                val fade = ROW_FADE * density
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade), blendMode = BlendMode.DstIn)
            }
            .background(if (!isBacked) Color.Transparent else if (isPressing) Island.appearance.pillFill.copy(alpha = minOf(1f, Island.appearance.pillFill.alpha + ROW_PRESS_DARKEN)) else Island.appearance.pillFill)
            .onGloballyPositioned { at ->
                card[0] = at.boundsInRoot()
                Island.onBlobsDrawn()
            }
            .border(1.dp, Palette.textBright.copy(alpha = if (isBacked) ROW_EDGE else 0f), RoundedCornerShape(ROW_CORNER))
            .pointerInput(group.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val holdAt = down.uptimeMillis + Durations.HOLD
                    bubble.pressCluster(if (clusterSize > 1) entry.owner else null, hidden > 0)
                    isPressing = true
                    var isSwiping = false
                    var isScrolling = false
                    var isArmed = false
                    var across = 0f
                    var vertical = 0f
                    while (true) {
                        val event = if (isSwiping || isScrolling) awaitPointerEvent()
                        else withTimeoutOrNull(maxOf(1L, holdAt - android.os.SystemClock.uptimeMillis())) { awaitPointerEvent() }
                        if (event == null) {
                            isPressing = false
                            bubble.openGroup(group)
                            do {
                                val rest = awaitPointerEvent()
                                rest.changes.forEach { it.consume() }
                            } while (rest.changes.any { it.pressed })
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        /* The swipe was read inside the row it slides, so every pixel the row moved took as much off the finger's local position and the row lagged and jumped; the finger's travel is summed from its moves, which are measured in the same frame on both ends. */
                        across += change.positionChange().x / density
                        vertical += change.positionChange().y / density
                        if (!isSwiping && !isScrolling) {
                            if (abs(across) > SWIPE_SLOP && abs(across) > abs(vertical)) {
                                isSwiping = true
                                isDragging = true
                                scope.launch { trash.stop(); drag.stop(); snap.snapTo(0f) }
                            } else if (abs(vertical) > SWIPE_SLOP) isScrolling = true
                        }
                        if (isSwiping || isScrolling) isPressing = false
                        if (isSwiping) {
                            held = across
                            if (across != 0f && !isArmed) trashSide = kotlin.math.sign(across).toInt()
                            val shouldArm = abs(across) > SWIPE_DISMISS
                            if (shouldArm != isArmed) {
                                isArmed = shouldArm
                                /* Crossing the line only lit the trash, so nothing in the hand said the card would go; the card snaps a step further out with a tick, and snaps back with another when pulled under it. */
                                if (shouldArm) {
                                    Haptics.play(Haptic.Release)
                                    scope.launch { trash.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = TRASH_DAMPING, stiffness = TRASH_STIFFNESS)) }
                                    scope.launch { snap.animateTo(SWIPE_SNAP, androidx.compose.animation.core.spring(dampingRatio = SNAP_DAMPING, stiffness = SNAP_STIFFNESS)) }
                                } else {
                                    Haptics.play(Haptic.Tap)
                                    scope.launch { trash.animateTo(0f, tween(160, easing = Curves.leave)) }
                                    scope.launch { snap.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = SNAP_DAMPING, stiffness = SNAP_STIFFNESS)) }
                                }
                            }
                        }
                        if (!change.pressed) {
                            isPressing = false
                            if (isSwiping) {
                                change.consume()
                                val from = held + snap.value * trashSide
                                val side = kotlin.math.sign(from)
                                val isThrown = isArmed
                                scope.launch {
                                    drag.snapTo(from)
                                    snap.snapTo(0f)
                                    isDragging = false
                                    if (isThrown) {
                                        Haptics.play(Haptic.Dismiss)
                                        launch {
                                            trash.animateTo(TRASH_FLASH, tween(120, easing = Curves.powerTwoOut))
                                            trash.animateTo(0f, tween(200, easing = Curves.leave))
                                        }
                                        drag.animateTo(side * SWIPE_AWAY, tween(SWIPE_OUT, easing = Curves.powerTwoIn))
                                        collapse.animateTo(1f, tween(ROW_COLLAPSE, easing = SMOOTH))
                                        bubble.dismissGroup(group)
                                    } else drag.animateTo(0f, tween(210, easing = Curves.grow))
                                }
                            } else if (!isScrolling && !change.isConsumed) {
                                change.consume()
                                bubble.toggleGroup(group)
                            }
                            break
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = ROW_PADDING.dp)
            .graphicsLayer {
                val hazy = maxOf(1f - reveal.value, leave.value)
                alpha = reveal.value * (1f - leave.value) * ((unfolded() - UNFOLD_TEXT_FROM) / (1f - UNFOLD_TEXT_FROM)).coerceIn(0f, 1f)
                scaleX = 1f - ROW_LEAVE_SHRINK * leave.value
                scaleY = scaleX
                translationY = -ROW_LEAVE_RISE * leave.value * density
                renderEffect = if (hazy > 0.01f) androidx.compose.ui.graphics.BlurEffect(hazy * CONTENT_BLUR * density, hazy * CONTENT_BLUR * density, androidx.compose.ui.graphics.TileMode.Decal) else null
            },
        horizontalArrangement = Arrangement.spacedBy(13.6.dp),
    ) {
        /* Rows after the first in an app's card had no icon at all, so a card of several senders read as one blurred block; each shows its sender's picture and the first keeps the app's. */
        val badge = if (isClusterStart) entry.appIcon ?: entry.icon else entry.icon ?: entry.appIcon
        val popping = Modifier.graphicsLayer {
            scaleX = iconPop.value
            scaleY = iconPop.value
            rotationZ = (1f - iconPop.value) * ICON_POP_TILT
        }
        if (badge != null) {
            Image(badge, null, popping.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(popping.size(40.dp).clip(CircleShape).background(entry.accent), contentAlignment = Alignment.Center) {
                BasicText(entry.appName.ifEmpty { entry.app }.trim().take(1).uppercase(), style = type(0.9f, Palette.ground, appearance.headingFont))
            }
        }
        Column(Modifier.weight(1f).onSizeChanged { fullHeight = it.height / Island.density + ROW_PADDING * 2f }) {
            val age = agoText(group.entries.maxOf { it.postedAt })
            val ageStyle = type(0.68f, Palette.textMuted, appearance.headingFont, appearance.headingScale)
            if (isClusterStart) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (hidden > 0) {
                    BasicText(
                        "+$hidden",
                        Modifier.clip(RoundedCornerShape(50))
                            .background(entry.accent.copy(alpha = STACK_CHIP))
                            .padding(horizontal = 8.dp, vertical = 1.6.dp),
                        style = type(0.62f, entry.accent, appearance.headingFont, appearance.headingScale, weight = FontWeight.Bold),
                    )
                }
                if (age.isNotEmpty()) BasicText(age, style = ageStyle)
            }
            val typedFrom = delayMilliseconds
            Row(Modifier.fillMaxWidth().padding(top = if (isClusterStart) 3.2.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.title.isNotEmpty()) {
                    val interval = if (isUnfolding) 0f else typeInterval(entry.title.length) * MENU_TYPE_SHARE
                    TypedText(entry.title, type(0.95f, Palette.textBright, FontFamily.SansSerif, appearance.headingScale, lineHeight = 1.3f), typedFrom, Modifier.weight(1f), interval = interval)
                } else Spacer(Modifier.weight(1f))
                if (!isClusterStart && age.isNotEmpty()) BasicText(age, Modifier.padding(top = 2.dp), style = ageStyle)
            }
            val lines = group.entries.flatMap { one ->
                if (one.messages.isNotEmpty()) one.messages.map { it.text } else listOf(one.text.ifEmpty { if (entry.title.isEmpty()) one.title else "" })
            }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val interval = if (isUnfolding) 0f else typeInterval(lines.sumOf { it.length }) * MENU_TYPE_SHARE
                var typedBefore = 0
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.2.dp)) {
                    lines.forEach { line ->
                        TypedText(
                            line,
                            type(0.86f, Palette.textBody, FontFamily.SansSerif, appearance.contentScale, lineHeight = 1.45f),
                            typedFrom + (typedBefore * interval).toInt(),
                            interval = interval,
                        )
                        typedBefore += line.length
                    }
                }
            }
            if (isExpanded) {
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.4.dp), verticalArrangement = Arrangement.spacedBy(6.4.dp)) {
                    group.head.actions.forEachIndexed { index, action ->
                        PillButton(action.title, index == 0, entry.accent, appearance) { bubble.groupAction(group, action) }
                    }
                    PillButton("Dismiss", group.head.actions.isEmpty(), entry.accent, appearance) {
                        Haptics.play(Haptic.Dismiss)
                        bubble.dismissGroup(group)
                    }
                }
            }
        }
    }
        if (hidden > 0) Column(Modifier.zIndex(-1f).fillMaxWidth().graphicsLayer { translationX = offset() * density }.padding(top = CLUSTER_SEAM.dp)) {
            for (layer in 1..minOf(hidden, STACK_LAYERS)) Box(
                Modifier.fillMaxWidth()
                    .graphicsLayer {
                        val out = stackOut[layer - 1].value
                        alpha = out.coerceIn(0f, 1f)
                        translationY = -(1f - out) * (CLUSTER_SEAM + layer * STACK_PEEK) * density
                    }
                    .padding(horizontal = (STACK_INSET * layer).dp)
                    .height(STACK_PEEK.dp)
                    .clip(RoundedCornerShape(bottomStart = STACK_PEEK.dp, bottomEnd = STACK_PEEK.dp))
                    .background(if ((isRevealed || !isDropped) && isOut) Island.appearance.pillFill.copy(alpha = Island.appearance.pillFill.alpha * STACK_SHARE) else Color.Transparent),
            )
        }
        }
    }
}

/* Cards scaled out of Main with their text already drawn and flew home as separate small pills, so the menu never read as Main's own glass; each card's silhouette drops out of Main as liquid and grows into place before its content blurs in, and on close the content blurs out and the silhouettes narrow, rise and squash into Main as one merge. The first cut waited 40ms before dropping, stood the settled silhouettes blank for a quarter second, blurred the card's backing into a frosted halo while the backing and silhouette crossfaded, typed the body only after the title, and lifted Main off the top edge on arrival; the drop starts at once on a stiffer spring, only the content blurs in over the silhouette and the backing takes over in one frame, title and body type together in half the time, and Main only swells. */
private const val DROP_DAMPING = 0.6f
private const val DROP_STIFFNESS = 520f
private const val DROP_WIDTH = 36f
private const val DROP_HEIGHT = 18f
private const val DROP_REVEAL_AT = 140
private const val DROP_REVEAL = 140
private const val MENU_TYPE_SHARE = 0.5f
private const val DROP_LIMIT = 6
private const val CONTENT_BLUR = 10f

/* The rows waited for the menu's glass to grow before arriving; the menu has no glass of its own now, so they arrive at once. */
private const val ROW_ENTER_DELAY = 60
private const val ROW_STAGGER = 40
private const val ROW_STAGGER_LIMIT = 10
private const val SWIPE_SLOP = 10f
private const val ICON_POP_AFTER = 110L
private const val ICON_POP_DAMPING = 0.48f
private const val ICON_POP_STIFFNESS = 340f
private const val ICON_POP_TILT = -75f
private const val SWIPE_DISMISS = 72f
private val ROW_CLIP = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rectangle(Rect(-size.width * 2f, 0f, size.width * 3f, size.height))
}
private const val SWIPE_SNAP = 26f
private const val SNAP_DAMPING = 0.55f
private const val SNAP_STIFFNESS = 700f
private const val ARRIVE_DAMPING = 0.68f
private const val ARRIVE_STIFFNESS = 420f
private const val SWIPE_AWAY = 400f
private const val SWIPE_OUT = 200
private const val ROW_COLLAPSE = 220
/* The whole menu only faded on close, so each card's text hung in place while its silhouette had already started home; each card's content leaves on its own, shrinking and rising toward Main as it blurs out, and the silhouettes travel home in 280ms instead of 400. */
private const val ROW_LEAVE = 110
private const val ROW_LEAVE_SHRINK = 0.08f
private const val ROW_LEAVE_RISE = 10f
private const val PULL_STAGGER = 0.15f
private const val PULL_STAGGER_LIMIT = 0.5f
private const val UNFOLD_TEXT_FROM = 0.5f
private val SMOOTH = CubicBezierEasing(0.2f, 1f, 0.35f, 1f)
private const val STACK_TUCK = 160
private const val STACK_STAGGER = 60L
private const val STACK_DAMPING = 0.45f
private const val STACK_STIFFNESS = 420f
private const val TRASH_SIZE = 22f
private const val TRASH_INSET = 22f
private const val TRASH_TRAVEL = 14f
private const val TRASH_PEEK = 0.4f
private const val TRASH_FLASH = 1.35f
private const val TRASH_DAMPING = 0.45f
private const val TRASH_STIFFNESS = 520f
private const val ROW_CEILING_SHARE = 0.2f
private const val ROW_PADDING = 12.8f
private const val ROW_FADE = 28f
private const val ROW_RESIZE = 300
private const val CLUSTER_GAP = 10f
private const val MENU_FADE = 110
private const val HOMING_STAGGER = 20L
private const val HOMING_MILLISECONDS = 280
private const val HOMING_HOLD = 0.1f
private const val SYSTEM_NARROW_SHARE = 0.3f
private const val MENU_DROP = 420
private const val STACK_INSET = 16f
private const val STACK_PEEK = 6f
/* A folded card showed one peeking edge however many it hid, and its rows unfolded as one squashed strip with flattened corners; it shows an edge per hidden card up to three, each unfolding row grows out of its own edge with its corners sized to its height, and the rows under it are pushed down. */
private const val STACK_LAYERS = 2
private const val LIST_TOP = 12f
private const val LIST_FOOT = 8f
private const val STACK_SHARE = 0.68f
private const val ROW_PRESS_DARKEN = 0.12f
private const val ROW_EDGE = 0.07f
private const val STACK_CHIP = 0.16f
private const val CLUSTER_SEAM = 2.4f

/* Cards of one app squared their touching corners to 7 and rounded them back as the cluster folded and unfolded, which flickered mid-motion; every card keeps the one radius. */
private val ROW_CORNER = 22.dp
