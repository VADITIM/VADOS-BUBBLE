package com.v.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject








object BatteryWatch {

    
    private const val LOW = 30

    
    private const val CRITICAL = 10

    
    private var announced = Int.MAX_VALUE

    
    private var wasPlugged: Boolean? = null

    private var receiver: BroadcastReceiver? = null

    
    private var onLevel: ((Int, Boolean) -> Unit)? = null

    fun start(context: Context, onEvent: (JSONObject) -> Unit, onCharge: (Int, Boolean) -> Unit) {
        onLevel = onCharge
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = read(intent, onEvent)
        }
        receiver = listener
        
        
        
        context.registerReceiver(listener, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        onLevel = null
        wasPlugged = null
        announced = Int.MAX_VALUE
    }

    private fun read(intent: Intent, onEvent: (JSONObject) -> Unit) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        
        
        
        onLevel?.invoke(percent, isPlugged)

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
