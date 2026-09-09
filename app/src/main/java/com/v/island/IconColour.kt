package com.v.island

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

















object IconColour {

    
    private const val GRID = 24

    
    private const val MIN_SATURATION = 0.18f
    private const val MIN_VALUE = 0.20f

    
    private const val HUE_BUCKETS = 18

    
    private const val FLOOR_VALUE = 0.66f

    
    private val known = mutableMapOf<String, String?>()

    
    fun of(packages: PackageManager, packageName: String): String? =
        known.getOrPut(packageName) {
            runCatching { readColour(packages.getApplicationIcon(packageName)) }.getOrNull()
        }

    private fun readColour(icon: Drawable): String? {
        val bitmap = Bitmap.createBitmap(GRID, GRID, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).let { canvas ->
            icon.setBounds(0, 0, GRID, GRID)
            icon.draw(canvas)
        }

        
        
        val weights = FloatArray(HUE_BUCKETS)
        val saturations = FloatArray(HUE_BUCKETS)
        val values = FloatArray(HUE_BUCKETS)
        val hues = FloatArray(HUE_BUCKETS)
        val hsv = FloatArray(3)

        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 128) continue
                Color.colorToHSV(pixel, hsv)
                if (hsv[1] < MIN_SATURATION || hsv[2] < MIN_VALUE) continue
                val bucket = ((hsv[0] / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
                val weight = hsv[1] * hsv[2]
                weights[bucket] += weight
                hues[bucket] += hsv[0] * weight
                saturations[bucket] += hsv[1] * weight
                values[bucket] += hsv[2] * weight
            }
        }
        bitmap.recycle()

        val winner = weights.indices.maxByOrNull { weights[it] } ?: return null
        if (weights[winner] <= 0f) return null
        val weight = weights[winner]
        hsv[0] = hues[winner] / weight
        hsv[1] = saturations[winner] / weight
        hsv[2] = maxOf(values[winner] / weight, FLOOR_VALUE)
        return String.format("#%06x", Color.HSVToColor(hsv) and 0xffffff)
    }
}
