package com.v.island.bubble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.v.island.vas.LocalAccent

@Composable
fun IslandStage() {
    val appearance = Island.appearance
    CompositionLocalProvider(LocalAccent provides appearance.accent) {
        val isLandscape = Island.isLandscape
        Box(Modifier.fillMaxSize()) {
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
