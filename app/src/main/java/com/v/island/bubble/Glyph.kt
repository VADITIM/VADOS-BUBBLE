package com.v.island.bubble

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

enum class LayerKind { Fill, FillEvenOdd, Stroke }

@Immutable
class GlyphLayer(pathData: String, val kind: LayerKind, val strokeWidth: Float) {
    val path: Path = PathParser().parsePathString(pathData).toPath().apply {
        if (kind == LayerKind.FillEvenOdd) fillType = PathFillType.EvenOdd
    }
}

@Immutable
class Glyph(val left: Float, val top: Float, val width: Float, val height: Float, val layers: List<GlyphLayer>)

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = minOf(size.width / glyph.width, size.height / glyph.height)
        val offsetX = (size.width - glyph.width * scale) / 2f
        val offsetY = (size.height - glyph.height * scale) / 2f
        translate(offsetX, offsetY) {
            withTransform({ scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero); translate(-glyph.left, -glyph.top) }) {
                glyph.layers.forEach { layer ->
                    if (layer.kind == LayerKind.Stroke) {
                        drawPath(layer.path, color, style = Stroke(layer.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else {
                        drawPath(layer.path, color)
                    }
                }
            }
        }
    }
}
