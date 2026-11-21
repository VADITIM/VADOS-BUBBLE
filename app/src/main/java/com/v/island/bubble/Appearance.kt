package com.v.island.bubble

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.v.island.Preferences
import com.v.island.vas.Faces

@Immutable
data class Appearance(
    val compactWidth: Float = 60f,
    val compactHeight: Float = 30f,
    val grab: Float = 3f,
    val pillFill: Color = Color(0f, 0f, 0f, 0.7f),
    val accent: Color = Color.White,
    val melt: Float = MELT_MAXIMUM,
    val modWidth: Float = 40f,
    val dwellMilliseconds: Long = 5_700,
    val identity: Int = 2,
    val nowPushes: Boolean = false,
    val notificationsRise: Float = 0.5f,
    val isClearCornered: Boolean = false,
    val dateFormat: Int = 0,
    val blurRadius: Int = 50,
    val scrimBlurRadius: Int = 35,
    val clockFont: FontFamily = Faces.mono,
    val mainFont: FontFamily = Faces.mono,
    val satelliteFont: FontFamily = Faces.mono,
    val statusFont: FontFamily = Faces.mono,
    val headingFont: FontFamily = Faces.mono,
    val contentFont: FontFamily = Faces.mono,
    val clockScale: Float = 1f,
    val mainScale: Float = 1f,
    val satelliteScale: Float = 1f,
    val statusScale: Float = 1f,
    val headingScale: Float = 1f,
    val contentScale: Float = 1f,
    val showBatteryPercent: Boolean = false,
) {
    companion object {
        /* Mirrors MELT_MAX in the retired js/state.js: the goo preference is a share of it. */
        const val MELT_MAXIMUM = 6f

        fun read(preferences: SharedPreferences): Appearance {
            fun value(key: String) = Preferences.get(preferences, key)
            fun scale(key: String) = (value(key).takeIf { it > 0 } ?: 100) / 100f
            return Appearance(
                compactWidth = value(Preferences.WIDTH).toFloat(),
                compactHeight = value(Preferences.HEIGHT).toFloat(),
                grab = value(Preferences.VERTICAL_OFFSET).toFloat(),
                pillFill = Color(
                    value(Preferences.RED) / 255f,
                    value(Preferences.GREEN) / 255f,
                    value(Preferences.BLUE) / 255f,
                    value(Preferences.BACKGROUND_ALPHA) / 100f,
                ),
                accent = Color(0xFF000000 or (value(Preferences.ACCENT).toLong() and 0xFFFFFF)),
                melt = MELT_MAXIMUM * value(Preferences.GOO) / 100f,
                modWidth = value(Preferences.MOD_WIDTH).toFloat(),
                dwellMilliseconds = maxOf(500L, value(Preferences.ALERT_DWELL) * 100L),
                identity = value(Preferences.NOTIFICATION_IDENTITY),
                nowPushes = value(Preferences.NOW_PUSHES) == 1,
                notificationsRise = value(Preferences.NOTIFICATIONS_RISE) / 100f,
                isClearCornered = value(Preferences.NOTIFICATIONS_CLEAR_CORNER) == 1,
                dateFormat = value(Preferences.DATE_FORMAT),
                blurRadius = value(Preferences.BLUR),
                scrimBlurRadius = value(Preferences.SCRIM_BLUR),
                clockFont = Faces.stack(value(Preferences.FONT_CLOCK)),
                mainFont = Faces.stack(value(Preferences.FONT_MAIN)),
                satelliteFont = Faces.stack(value(Preferences.FONT_SATELLITE)),
                statusFont = Faces.stack(value(Preferences.FONT_STATUS)),
                headingFont = Faces.stack(value(Preferences.FONT_NOTIFICATION_HEADING)),
                contentFont = Faces.stack(value(Preferences.FONT_NOTIFICATION_CONTENT)),
                clockScale = scale(Preferences.FONT_SIZE_CLOCK),
                mainScale = scale(Preferences.FONT_SIZE_MAIN),
                satelliteScale = scale(Preferences.FONT_SIZE_SATELLITE),
                statusScale = scale(Preferences.FONT_SIZE_STATUS),
                headingScale = scale(Preferences.FONT_SIZE_NOTIFICATION_HEADING),
                contentScale = scale(Preferences.FONT_SIZE_NOTIFICATION_CONTENT),
                showBatteryPercent = value(Preferences.STATUS_BATTERY_PERCENT) == 1,
            )
        }
    }
}
