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

    /**
     * How far the liquid reaches, as a percentage of the deviation the skin melts at when two
     * shapes are touching. Shapes bridge at roughly twice the deviation, so this is directly
     * how far apart two bubbles can be and still neck.
     */
    const val GOO = "gooStrength"

    /** How much wider than the bare bubble a mod makes it. */
    const val MOD_WIDTH = "modWidthDp"

    /**
     * Whether the row stands aside while the Now bubble is out. On, the bubble is pushed
     * right until its left edge is at the punch hole and the row is allowed one satellite
     * instead of two; off, the row keeps its place and the Now bubble takes only the bar
     * that is actually free. 1 or 0 — the store holds ints.
     */
    const val NOW_PUSHES = "nowPushesRow"

    /**
     * Where the bubble has been carried to, in dp from the punch hole. Kept because a bubble
     * that went home every time the phone slept was never really put anywhere — persistence is
     * part of the feature rather than a polish pass on it.
     */
    const val CARRY_X = "carriedX"
    const val CARRY_Y = "carriedY"

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
        NOTIFICATION_IDENTITY to 2,
        GOO to 100,
        MOD_WIDTH to 56,
        NOW_PUSHES to 1,
        CARRY_X to 0,
        CARRY_Y to 0
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
