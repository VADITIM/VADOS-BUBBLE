package com.v.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject








object BatteryWatch {

    // Mirrors BATTERY_LOW in status.js, where the same mark turns the battery colour yellow.
    private const val LOW = 39

    // Mirrors BATTERY_CRITICAL in status.js, where the same mark turns the battery colour red.
    private const val CRITICAL = 19

    
    private var announced = Int.MAX_VALUE

    
    private var wasPlugged: Boolean? = null

    private var lastPercent = -1

    private var lastRemainingMinutes = -1

    // Samsung's charge counter moves in whole steps of 4015 µAh, one per 0.1% of the fuel gauge, so this is what the counter reads at 100%.
    private const val FULL_MICRO_AMP_HOURS = 4_015_000.0

    private const val COUNTER_STEP_MICRO_AMP_HOURS = 4_015.0

    // The shown value is the mean of this many seconds of estimates, so it trails the battery by about half of it.
    private const val SETTLE_MILLISECONDS = 4_000L

    // How long the estimate takes to lean most of the way back onto the counter when the current integration has drifted off it.
    private const val CORRECTION_MILLISECONDS = 20_000.0

    private var estimate = -1.0

    private var estimateMillis = 0L

    private val recentEstimates = ArrayDeque<Pair<Long, Double>>()

    private var shownMicroAmpHours = -1.0

    private var lastHundredths = -1

    private var receiver: BroadcastReceiver? = null


    private var onLevel: ((Int, Double, Boolean, Int) -> Unit)? = null

    fun start(context: Context, onEvent: (JSONObject) -> Unit, onCharge: (Int, Double, Boolean, Int) -> Unit) {
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
        lastHundredths = -1
        estimate = -1.0
        recentEstimates.clear()
        shownMicroAmpHours = -1.0
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
        val exact = exactPercent(percent, context.getSystemService(BatteryManager::class.java))
        val hundredths = Math.round(exact * 100).toInt()

        if (percent != lastPercent || hundredths != lastHundredths || isPlugged != wasPlugged || remainingMinutes != lastRemainingMinutes) {
            lastPercent = percent
            lastHundredths = hundredths
            lastRemainingMinutes = remainingMinutes
            onLevel?.invoke(percent, hundredths / 100.0, isPlugged, remainingMinutes)
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

    fun readExact(context: Context): Double =
        exactPercent(lastPercent, context.getSystemService(BatteryManager::class.java))

    /* The counter only moves in 0.1% steps and the instantaneous current swings by amps from one second to the next, so extrapolating the one with the other overshot and snapped back at every step and the hundredths jumped around. The estimate now integrates the averaged current, leans slowly onto the middle of the counter's step, is shown as the mean of the last few seconds, and never walks backwards against the direction the charge is going. */
    @Synchronized
    private fun exactPercent(percent: Int, manager: BatteryManager?): Double {
        val counter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
        if (counter <= 0) return percent.toDouble()
        val now = android.os.SystemClock.elapsedRealtime()
        val stepMiddle = counter + COUNTER_STEP_MICRO_AMP_HOURS / 2
        val microAmps = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
        val elapsed = now - estimateMillis
        if (estimate < 0 || elapsed > SETTLE_MILLISECONDS * 3) {
            estimate = stepMiddle
            recentEstimates.clear()
            shownMicroAmpHours = -1.0
        } else {
            estimate += microAmps * elapsed / 3_600_000.0
            estimate += (stepMiddle - estimate) * minOf(1.0, elapsed / CORRECTION_MILLISECONDS)
        }
        estimateMillis = now
        recentEstimates.addLast(now to estimate)
        while (now - recentEstimates.first().first > SETTLE_MILLISECONDS) recentEstimates.removeFirst()

        val settled = recentEstimates.sumOf { it.second } / recentEstimates.size
        shownMicroAmpHours = when {
            shownMicroAmpHours < 0 -> settled
            microAmps > 0 -> maxOf(shownMicroAmpHours, settled)
            microAmps < 0 -> minOf(shownMicroAmpHours, settled)
            else -> settled
        }
        return (shownMicroAmpHours * 100 / FULL_MICRO_AMP_HOURS).coerceIn(0.0, 100.0)
    }

    private fun event(state: String, percent: Int): JSONObject =
        JSONObject().put("state", state).put("level", percent)
}
