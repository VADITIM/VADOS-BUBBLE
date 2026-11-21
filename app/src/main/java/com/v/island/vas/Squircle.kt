package com.v.island.vas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

class SquircleShape(private val radius: Dp = 12.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val reach = min(with(density) { radius.toPx() } * SPREAD, min(size.width, size.height) / 2f)
        val path = Path()
        val corners = listOf(
            Triple(size.width - reach, reach, 0.0),
            Triple(size.width - reach, size.height - reach, 90.0),
            Triple(reach, size.height - reach, 180.0),
            Triple(reach, reach, 270.0),
        )
        var isFirst = true
        for ((centreX, centreY, start) in corners) {
            for (step in 0..STEPS) {
                val angle = Math.toRadians(start - 90.0 + 90.0 * step / STEPS)
                val cosine = cos(angle)
                val sine = sin(angle)
                val x = centreX + reach * sign(cosine) * abs(cosine).pow(2.0 / EXPONENT)
                val y = centreY + reach * sign(sine) * abs(sine).pow(2.0 / EXPONENT)
                if (isFirst) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
                isFirst = false
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    private companion object {
        const val EXPONENT = 5.0
        const val STEPS = 12
        const val SPREAD = 1.8f
    }
}
