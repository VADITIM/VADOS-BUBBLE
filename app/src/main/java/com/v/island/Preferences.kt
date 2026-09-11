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

    






    






    


    const val BAR_LOCKED = "barLocked"

    const val QUICK_DIVIDERS = "quickDividers"

    const val FONT_CLOCK = "fontClock"
    const val FONT_MAIN = "fontMain"
    const val FONT_SATELLITE = "fontSatellite"
    const val FONT_STATUS = "fontStatus"
    const val FONT_OVERLAY = "fontOverlay"
    const val FONT_BATTERY = "fontBattery"
    const val FONT_STATS = "fontStats"
    const val FONT_CONNECTORS = "fontConnectors"
    const val FONT_NOTIFICATION_HEADING = "fontNotificationHeading"
    const val FONT_NOTIFICATION_CONTENT = "fontNotificationContent"

    const val FONT_SIZE_CLOCK = "fontSizeClock"
    const val FONT_SIZE_MAIN = "fontSizeMain"
    const val FONT_SIZE_SATELLITE = "fontSizeSatellite"
    const val FONT_SIZE_STATUS = "fontSizeStatus"
    const val FONT_SIZE_OVERLAY = "fontSizeOverlay"
    const val FONT_SIZE_BATTERY = "fontSizeBattery"
    const val FONT_SIZE_STATS = "fontSizeStats"
    const val FONT_SIZE_CONNECTORS = "fontSizeConnectors"
    const val FONT_SIZE_NOTIFICATION_HEADING = "fontSizeNotificationHeading"
    const val FONT_SIZE_NOTIFICATION_CONTENT = "fontSizeNotificationContent"

    private const val STORE = "island"

    








    val defaults = mapOf(
        WIDTH to 60,
        HEIGHT to 30,
        HORIZONTAL_OFFSET to 0,
        VERTICAL_OFFSET to 3,
        RED to 0,
        GREEN to 0,
        BLUE to 0,
        BACKGROUND_ALPHA to 70,

        ACCENT to 0x5BFD5B,
        BLUR to 50,
        SCRIM_BLUR to 35,
        NOTIFICATION_IDENTITY to 2,
        GOO to 100,
        MOD_WIDTH to 40,
        NOW_PUSHES to 0,
        ALERT_DWELL to 5,
        EDGE_MERGE to 0,
        LOCK_X to 0,
        BAR_LOCKED to 0,
        QUICK_DIVIDERS to 1,

        FONT_CLOCK to 0,
        FONT_MAIN to 0,
        FONT_SATELLITE to 0,
        FONT_STATUS to 0,
        FONT_OVERLAY to 0,
        FONT_BATTERY to 0,
        FONT_STATS to 0,
        FONT_CONNECTORS to 0,
        FONT_NOTIFICATION_HEADING to 0,
        FONT_NOTIFICATION_CONTENT to 0,

        FONT_SIZE_CLOCK to 100,
        FONT_SIZE_MAIN to 100,
        FONT_SIZE_SATELLITE to 100,
        FONT_SIZE_STATUS to 100,
        FONT_SIZE_OVERLAY to 100,
        FONT_SIZE_BATTERY to 100,
        FONT_SIZE_STATS to 100,
        FONT_SIZE_CONNECTORS to 100,
        FONT_SIZE_NOTIFICATION_HEADING to 100,
        FONT_SIZE_NOTIFICATION_CONTENT to 100
    )

    /* Long-valued and deliberately outside `defaults`: that map is Int-only and asJson walks all of it, so a byte count listed there would be read back with getInt and throw. */
    const val DATA_DAY = "dataDay"
    const val DATA_BASE = "dataBaseBytes"

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
