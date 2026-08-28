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

    /**
     * Settled on the phone rather than reasoned about: a narrower, shorter bubble that sits
     * closer to the cutout, and glass rather than a solid black pill — 80% alpha over a 60dp
     * blur is what makes it read as part of the screen instead of a sticker on it.
     *
     * Only a first install and a fresh Reset take these. A phone that has been used has its own
     * values in SharedPreferences and will not move, which is correct and is also why changing a
     * default here is never visible on the device it was tuned on.
     */
    val defaults = mapOf(
        WIDTH to 85,
        HEIGHT to 30,
        HORIZONTAL_OFFSET to 0,
        VERTICAL_OFFSET to 3,
        RED to 0,
        GREEN to 0,
        BLUE to 0,
        BACKGROUND_ALPHA to 80,
        BLUR to 60,
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
