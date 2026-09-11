package com.v.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject








object BatteryWatch {

    // Mirrors BATTERY_LOW in status.js, where the same mark turns the battery colour yellow.
    private const val LOW = 40

    // Mirrors BATTERY_CRITICAL in status.js, where the same mark turns the battery colour red.
    private const val CRITICAL = 15

    
    private var announced = Int.MAX_VALUE

    
    private var wasPlugged: Boolean? = null

    private var lastPercent = -1

    private var lastRemainingMinutes = -1

    private var receiver: BroadcastReceiver? = null


    private var onLevel: ((Int, Boolean, Int) -> Unit)? = null

    fun start(context: Context, onEvent: (JSONObject) -> Unit, onCharge: (Int, Boolean, Int) -> Unit) {
        onLevel = onCharge
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = read(intent, context, onEvent)
        }
        receiver = listener
        
        
        
        context.registerReceiver(listener, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        onLevel = null
        wasPlugged = null
        lastPercent = -1
        lastRemainingMinutes = -1
        announced = Int.MAX_VALUE
    }

    private fun read(intent: Intent, context: Context, onEvent: (JSONObject) -> Unit) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        val remainingMinutes = if (isPlugged) {
            val millis = context.getSystemService(BatteryManager::class.java)
                ?.computeChargeTimeRemaining() ?: -1L
            if (millis > 0) (millis / 60000).toInt() else -1
        } else -1

        /* ACTION_BATTERY_CHANGED is broadcast on temperature and voltage as well as on charge, so it lands every few seconds on a phone doing nothing — and each one was an evaluateJavascript into the page and a repaint of a reading that had not moved. The level, the cable and the estimate of when the cable stops mattering are the only things anyone here is watching. */
        if (percent != lastPercent || isPlugged != wasPlugged || remainingMinutes != lastRemainingMinutes) {
            lastPercent = percent
            lastRemainingMinutes = remainingMinutes
            onLevel?.invoke(percent, isPlugged, remainingMinutes)
        }

        val first = wasPlugged == null
        val justPlugged = !first && isPlugged && wasPlugged == false
        wasPlugged = isPlugged

        
        if (isPlugged) announced = Int.MAX_VALUE
        if (percent > LOW) announced = Int.MAX_VALUE

        
        
        
        if (first) return

        if (justPlugged) {
            onEvent(event("charging", percent))
            return
        }
        if (isPlugged) return

        val mark = when {
            percent <= CRITICAL -> CRITICAL
            percent <= LOW -> LOW
            else -> return
        }
        
        if (mark >= announced) return
        announced = mark
        onEvent(event(if (mark == CRITICAL) "critical" else "low", percent))
    }

    private fun event(state: String, percent: Int): JSONObject =
        JSONObject().put("state", state).put("level", percent)
}
