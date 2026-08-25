package com.v.island

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Single source of truth for the user-tunable pill appearance. */
object Preferences {

    const val WIDTH = "widthDp"
    const val HEIGHT = "heightDp"
    const val HORIZONTAL_OFFSET = "horizontalOffsetDp"
    const val VERTICAL_OFFSET = "verticalOffsetDp"
    const val RED = "red"
    const val GREEN = "green"
    const val BLUE = "blue"
    const val BACKGROUND_ALPHA = "backgroundAlpha"

    /** Radius in dp of the system blur behind the window. 0 = plain transparency. */
    const val BLUR = "blurRadius"

    /** 0 = icon only, 1 = name only, 2 = both. */
    const val NOTIFICATION_IDENTITY = "notificationIdentity"

    private const val STORE = "island"

    val defaults = mapOf(
        WIDTH to 130,
        HEIGHT to 34,
        HORIZONTAL_OFFSET to 0,
        VERTICAL_OFFSET to 12,
        RED to 0,
        GREEN to 0,
        BLUE to 0,
        BACKGROUND_ALPHA to 100,
        BLUR to 0,
        NOTIFICATION_IDENTITY to 2
    )

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun get(preferences: SharedPreferences, key: String): Int =
        preferences.getInt(key, defaults.getValue(key))

    fun set(preferences: SharedPreferences, key: String, value: Int) {
        if (key in defaults) preferences.edit().putInt(key, value).apply()
    }

    fun asJson(preferences: SharedPreferences): JSONObject =
        JSONObject().apply { defaults.keys.forEach { put(it, get(preferences, it)) } }

    /** The rgba() string for the pill's background layer. Content opacity is never touched. */
    fun backgroundCss(preferences: SharedPreferences): String {
        val red = get(preferences, RED)
        val green = get(preferences, GREEN)
        val blue = get(preferences, BLUE)
        val alpha = get(preferences, BACKGROUND_ALPHA) / 100f
        return "rgba($red,$green,$blue,$alpha)"
    }
}
