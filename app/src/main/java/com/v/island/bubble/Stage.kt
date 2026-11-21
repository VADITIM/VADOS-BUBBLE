package com.v.island.bubble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.v.island.vas.LocalAccent

@Composable
fun IslandStage() {
    val appearance = Island.appearance
    CompositionLocalProvider(LocalAccent provides appearance.accent) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (Island.isLandscape) 0f else 1f }) {
            DashboardScrim(Island.dashboard)
            LiquidLayer(
                Island.liquid,
                fill = { Island.appearance.pillFill },
                melt = { Island.appearance.melt * Island.density },
                hairline = Island.density,
            )
            SatellitesView(Island.bubble)
            DashboardPanel(Island.dashboard)
            ClockView(Island.clock)
            StatusView(Island.status)
            ClearButton(Island.bubble)
            MainBubbleView(Island.bubble)
        }
    }
}
