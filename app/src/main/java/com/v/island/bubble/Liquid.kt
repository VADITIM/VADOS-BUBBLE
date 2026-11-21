package com.v.island.bubble

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

class BlobShape(
    val rect: Rect,
    val radius: Float,
    val border: Color,
    val opacity: Float = 1f,
)

@Stable
class Liquid {
    val sources = mutableStateListOf<() -> BlobShape?>()

    fun shapes(): List<BlobShape> = sources.mapNotNull { it() }.filter { it.opacity > 0.01f && it.rect.width > 0.5f }
}

@Composable
fun Blob(liquid: Liquid, shape: () -> BlobShape?) {
    DisposableEffect(liquid) {
        liquid.sources.add(shape)
        onDispose { liquid.sources.remove(shape) }
    }
}

private const val MAXIMUM_BLOBS = 16

private val LIQUID_SHADER = """
uniform float2 origin;
uniform float count;
uniform float4 boxes[$MAXIMUM_BLOBS];
uniform float4 looks[$MAXIMUM_BLOBS];
uniform float4 borders[$MAXIMUM_BLOBS];
uniform float4 fill;
uniform float melt;
uniform float hairline;

float roundBox(float2 point, float2 half, float radius) {
    float2 q = abs(point) - half + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

half4 main(float2 coordinate) {
    float2 point = coordinate + origin;
    float distance = 100000.0;
    float4 border = float4(0.0);
    float weights = 0.0;
    float opacity = 0.0;
    for (int index = 0; index < $MAXIMUM_BLOBS; index++) {
        if (float(index) >= count) break;
        float4 box = boxes[index];
        float4 look = looks[index];
        float own = roundBox(point - box.xy, box.zw, look.x) + (1.0 - look.y) * box.w;
        float blend = clamp(0.5 + 0.5 * (own - distance) / melt, 0.0, 1.0);
        distance = mix(own, distance, blend) - melt * blend * (1.0 - blend);
        float weight = exp(-max(own, 0.0) / max(melt, 1.0));
        border += borders[index] * weight;
        opacity += look.y * weight;
        weights += weight;
    }
    border /= max(weights, 0.0001);
    opacity = clamp(opacity / max(weights, 0.0001), 0.0, 1.0);
    float inside = clamp(0.5 - distance, 0.0, 1.0);
    float core = clamp(0.5 - (distance + hairline), 0.0, 1.0);
    float4 color = mix(border, fill, core);
    float alpha = inside * color.a * opacity;
    return half4(color.rgb * alpha, alpha);
}
"""

@Composable
fun LiquidLayer(liquid: Liquid, fill: () -> Color, melt: () -> Float, hairline: Float, modifier: Modifier = Modifier) {
    val shader = remember { RuntimeShader(LIQUID_SHADER) }
    val boxes = remember { FloatArray(MAXIMUM_BLOBS * 4) }
    val looks = remember { FloatArray(MAXIMUM_BLOBS * 4) }
    val borders = remember { FloatArray(MAXIMUM_BLOBS * 4) }
    val brush = remember { ShaderBrush(shader) }
    Canvas(modifier.fillMaxSize()) {
        val shapes = liquid.shapes().take(MAXIMUM_BLOBS)
        if (shapes.isEmpty()) {
            Island.onBlobsDrawn()
            return@Canvas
        }
        val spread = melt() * 2f + 4f
        var area = shapes.first().rect
        shapes.forEachIndexed { index, shape ->
            area = Rect(
                minOf(area.left, shape.rect.left), minOf(area.top, shape.rect.top),
                maxOf(area.right, shape.rect.right), maxOf(area.bottom, shape.rect.bottom),
            )
            boxes[index * 4] = shape.rect.center.x
            boxes[index * 4 + 1] = shape.rect.center.y
            boxes[index * 4 + 2] = shape.rect.width / 2f
            boxes[index * 4 + 3] = shape.rect.height / 2f
            looks[index * 4] = shape.radius.coerceAtMost(minOf(shape.rect.width, shape.rect.height) / 2f)
            looks[index * 4 + 1] = shape.opacity
            borders[index * 4] = shape.border.red
            borders[index * 4 + 1] = shape.border.green
            borders[index * 4 + 2] = shape.border.blue
            borders[index * 4 + 3] = shape.border.alpha
        }
        val bounds = area.inflate(spread)
        val color = fill()
        shader.setFloatUniform("origin", bounds.left, bounds.top)
        shader.setFloatUniform("count", shapes.size.toFloat())
        shader.setFloatUniform("boxes", boxes)
        shader.setFloatUniform("looks", looks)
        shader.setFloatUniform("borders", borders)
        shader.setFloatUniform("fill", color.red, color.green, color.blue, color.alpha)
        shader.setFloatUniform("melt", melt().coerceAtLeast(0.01f))
        shader.setFloatUniform("hairline", hairline)
        drawContext.transform.translate(bounds.left, bounds.top)
        drawRect(brush, Offset.Zero, Size(bounds.width, bounds.height))
        drawContext.transform.translate(-bounds.left, -bounds.top)
        Island.onBlobsDrawn()
    }
}
