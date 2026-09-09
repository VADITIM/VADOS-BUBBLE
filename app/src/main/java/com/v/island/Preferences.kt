package com.v.island

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject


object Preferences {

    const val WIDTH = "widthDp"
    const val HEIGHT = "heightDp"
    const val HORIZONTAL_OFFSET = "horizontalOffsetDp"
    const val VERTICAL_OFFSET = "verticalOffsetDp"
    const val RED = "red"
    const val GREEN = "green"
    const val BLUE = "blue"
    const val BACKGROUND_ALPHA = "backgroundAlpha"

    





    const val ACCENT = "accentColor"

    
    const val BLUR = "blurRadius"

    





    const val SCRIM_BLUR = "scrimBlurRadius"

    
    const val NOTIFICATION_IDENTITY = "notificationIdentity"

    




    const val GOO = "gooStrength"

    
    const val MOD_WIDTH = "modWidthDp"

    





    const val NOW_PUSHES = "nowPushesRow"

    





    const val ALERT_DWELL = "alertDwellTenths"

    




    const val EDGE_MERGE = "edgeMerge"

    




    





    const val LOCK_X = "lockOffsetDp"

    





    const val NOTES_SHOWN = "notesShown"
    const val PADLOCK_SHOWN = "padlockShown"

    





    const val PADLOCK_Y = "padlockOffsetDp"

    


    const val BAR_LOCKED = "barLocked"

    private const val STORE = "island"

    








    val defaults = mapOf(
        WIDTH to 85,
        HEIGHT to 30,
        HORIZONTAL_OFFSET to 0,
        VERTICAL_OFFSET to 3,
        RED to 0,
        GREEN to 0,
        BLUE to 0,
        BACKGROUND_ALPHA to 80,
        
        ACCENT to 0x5BFD5B,
        BLUR to 60,
        SCRIM_BLUR to 63,
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

    
    fun backgroundCss(preferences: SharedPreferences): String {
        val red = get(preferences, RED)
        val green = get(preferences, GREEN)
        val blue = get(preferences, BLUE)
        val alpha = get(preferences, BACKGROUND_ALPHA) / 100f
        return "rgba($red,$green,$blue,$alpha)"
    }

    
    fun accentCss(preferences: SharedPreferences): String =
        "#%06X".format(get(preferences, ACCENT) and 0xFFFFFF)
}
