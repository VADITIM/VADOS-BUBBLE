package com.v.island

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

/**
 * The one colour an app is, read off its own launcher icon.
 *
 * [AppStyles] names the colour for the apps worth naming it for, and everything else used to be
 * white — which is honest and is also every app on the phone wearing one identity. An icon already
 * carries the answer: whatever a person recognises the app by at a glance is the colour that takes
 * up the most of it. So an unnamed app is asked rather than guessed at, and the named ones stay
 * named because a brand colour is not always the loudest thing in its own icon.
 *
 * What is thrown away matters more than what is kept. Grey, black and white are dropped before the
 * count — nearly every icon is mostly one of the three, and counting them answers "grey" for a
 * whole phone — and so is the transparent margin an adaptive icon is drawn with. What is left is
 * bucketed by hue rather than by exact colour, because an icon is a gradient far more often than it
 * is a flat fill and thirty shades of one blue have to count as one blue or they lose to a flat
 * accent covering a tenth of the area.
 */
object IconColour {

    /** Big enough for a logo to survive it, small enough that the whole read is a few hundred pixels. */
    private const val GRID = 24

    /** Below either of these a pixel is a shade rather than a colour, and is not what anything is recognised by. */
    private const val MIN_SATURATION = 0.18f
    private const val MIN_VALUE = 0.20f

    /** Twenty degrees a bucket: wide enough to hold one brand's whole gradient, narrow enough to keep a red apart from an orange. */
    private const val HUE_BUCKETS = 18

    /** A colour this dark is invisible on a black bar, so the reading is lifted to where it can be seen — the hue is the identity and the brightness is only how it is being shown. */
    private const val FLOOR_VALUE = 0.66f

    /** One read per package for the life of the process: an icon does not change, and this runs on the notification thread. */
    private val known = mutableMapOf<String, String?>()

    /** The app's colour as `#rrggbb`, or null when it has no icon or its icon has no colour at all. */
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

        // Weighted by how colourful each pixel is, not by how many there are: a pale wash covering
        // the whole icon must not outvote the saturated mark standing in the middle of it.
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
