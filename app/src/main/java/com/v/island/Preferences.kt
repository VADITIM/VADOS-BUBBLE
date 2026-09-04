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

    /**
     * The one colour everything on the bar is lit in — a live switch, a mod's glyph, an open
     * panel's accent. Stored as 0xRRGGBB in a single int because the store holds ints and because
     * it is picked as a colour rather than as three numbers: the three sliders above it are the
     * pill's *background*, which is a different thing and stays where it is.
     */
    const val ACCENT = "accentColor"

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
     * How long an alert stands on the bar before it closes itself, in tenths of a second — the
     * store holds ints and a slider that counted milliseconds would be four thousand steps wide.
     * It is a setting because how long is long enough is a reading speed, and that is the one
     * number here nobody else can guess.
     */
    const val ALERT_DWELL = "alertDwellTenths"

    /**
     * Whether the bubbles wet the sides of the screen. On, a bubble standing near an edge grows a
     * neck into it and a grown one closes the corner it is held in; off, every bubble floats its
     * own margin clear of the glass. 1 or 0 — the store holds ints.
     */
    const val EDGE_MERGE = "edgeMerge"

    /**
     * Where the bubble has been carried to, in dp from the punch hole. Kept because a bubble
     * that went home every time the phone slept was never really put anywhere — persistence is
     * part of the feature rather than a polish pass on it.
     */
    /**
     * How far along the screen the lock screen's stack — the Now bubble and the notes above it,
     * one shared container now — stands, in dp from where it sits by default. It is a full-width
     * box held by an inset either side, so the shift is added to one inset and taken off the
     * other: the box moves and its width does not change.
     */
    const val LOCK_X = "lockOffsetDp"

    /**
     * Whether the lock screen carries its notification bubbles at all, and whether it carries the
     * padlock. 1 or 0 — the store holds ints. Both are the whole bubble rather than a detail of
     * it: off, nothing is drawn, nothing is skinned and the touch proxy is given no size, so the
     * lock screen is left exactly as One UI drew it.
     */
    const val NOTES_SHOWN = "notesShown"
    const val PADLOCK_SHOWN = "padlockShown"

    /**
     * How far above its own default the padlock stands, in dp — positive lifts it higher. The
     * default itself (PADLOCK_BOTTOM in padlock.js) is a measurement off the phone, same as it
     * always was; this is the fine adjustment on top of it, needed now that the notes stand
     * below it and the default has to clear a stack whose real height was never walked here.
     */
    const val PADLOCK_Y = "padlockOffsetDp"

    /**
     * Whether the shade swipe is locked out, 1 or 0 — the store holds ints. Hiding the real bar (StatusBarPolicy) is a reveal and leaves the swipe behind it; this is the other half, and there is no unprivileged way to ask SystemUI for it: the only thing that stops the gesture is a touchable window standing on the strip it starts from, which is exactly the window every other rule here exists to avoid. It is a mode rather than a default for that reason — on, the top of the screen belongs to us and the shade cannot be pulled at all.
     */
    const val BAR_LOCKED = "barLocked"

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
        // The neon green --section-color in dna.css was born as, mirrored here as an int.
        ACCENT to 0x5BFD5B,
        BLUR to 60,
        NOTIFICATION_IDENTITY to 2,
        GOO to 100,
        MOD_WIDTH to 56,
        NOW_PUSHES to 1,
        ALERT_DWELL to 50,
        EDGE_MERGE to 1,
        LOCK_X to 0,
        NOTES_SHOWN to 1,
        PADLOCK_SHOWN to 1,
        PADLOCK_Y to 0,
        BAR_LOCKED to 0
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

    /** The accent as CSS, which is the only form anything downstream of here wants it in. */
    fun accentCss(preferences: SharedPreferences): String =
        "#%06X".format(get(preferences, ACCENT) and 0xFFFFFF)
}
