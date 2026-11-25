package com.v.island.bubble

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.v.island.vas.Durations
import com.v.island.vas.LocalAccent

@Composable
fun IslandStage() {
    val appearance = Island.appearance
    CompositionLocalProvider(LocalAccent provides appearance.accent) {
        val isLandscape = Island.isLandscape
        Box(Modifier.fillMaxSize()) {
            BarScrim()
            if (!isLandscape) PanelShield(Island.bubble)
            DashboardScrim(Island.dashboard)
            LiquidLayer(
                Island.liquid,
                fill = { Island.appearance.pillFill },
                melt = { Island.appearance.melt * Island.density },
                hairline = Island.density,
            )
            if (!isLandscape) SatellitesView(Island.bubble)
            DashboardPanel(Island.dashboard)
            if (!isLandscape) NowView(Island.now)
            ClockView(Island.clock)
            StatusView(Island.status)
            if (!isLandscape) {
                SystemPill(Island.bubble)
                ClearButton(Island.bubble)
                MainBubbleView(Island.bubble)
                TorchView(Island.bubble)
                TorchOffView(Island.bubble)
                DarkAlertView()
                AlertBand(Island.bubble)
            }
        }
    }
}

@Composable
private fun BarScrim() {
    val shown = animateFloatAsState(if (Island.appearance.hasSidePills) 0f else 1f, tween(Durations.GROW), label = "barScrim")
    if (shown.value <= 0f) return
    Box(
        Modifier.fillMaxWidth()
            .height((Island.appearance.grab + Island.appearance.compactHeight + BAR_SCRIM_FADE).dp)
            .graphicsLayer { alpha = shown.value }
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = BAR_SCRIM_ALPHA), 0.55f to Color.Black.copy(alpha = BAR_SCRIM_ALPHA * 0.6f), 1f to Color.Transparent)),
    )
}

// A white scrim over a light page left white-on-white glyphs unreadable; the scrim is always black and dark enough to carry white ink.
private const val BAR_SCRIM_ALPHA = 0.6f
private const val BAR_SCRIM_FADE = 24f
