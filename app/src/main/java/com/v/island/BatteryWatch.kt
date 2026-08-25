package com.v.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject

/**
 * The battery, announced rather than carried. Everything else the bubble knows about
 * is state — a song is playing, a call is connected — but a battery is always there,
 * and a bubble that showed it permanently would be a status bar. What matters is the
 * moment it changes: it has just fallen past a mark, or it has just been plugged in.
 * Both are events, so both are shown once and then the bubble goes back to itself.
 */
object BatteryWatch {

    /** Yellow: worth knowing. */
    private const val LOW = 15

    /** Red: do something about it. */
    private const val CRITICAL = 5

    /** The lowest mark already announced, so a slow drain says it once and not per percent. */
    private var announced = Int.MAX_VALUE

    /** Null until the first reading, which is the sticky one and announces nothing. */
    private var wasPlugged: Boolean? = null

    private var receiver: BroadcastReceiver? = null

    fun start(context: Context, onEvent: (JSONObject) -> Unit) {
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = read(intent, onEvent)
        }
        receiver = listener
        // ACTION_BATTERY_CHANGED is sticky and cannot be declared in the manifest, so
        // registering it is also how the current state is read: the first broadcast
        // arrives immediately and is only used to prime.
        context.registerReceiver(listener, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        wasPlugged = null
        announced = Int.MAX_VALUE
    }

    private fun read(intent: Intent, onEvent: (JSONObject) -> Unit) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        val first = wasPlugged == null
        val justPlugged = !first && isPlugged && wasPlugged == false
        wasPlugged = isPlugged

        // Charging clears the marks: the next fall past them is news again.
        if (isPlugged) announced = Int.MAX_VALUE
        if (percent > LOW) announced = Int.MAX_VALUE

        // The sticky broadcast is the state as it already was, not a change, and
        // announcing it would mean a low battery pops up every time the service is
        // restarted — including on every install.
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
        // Lower than anything said so far: 15 announces, then 5 announces again.
        if (mark >= announced) return
        announced = mark
        onEvent(event(if (mark == CRITICAL) "critical" else "low", percent))
    }

    private fun event(state: String, percent: Int): JSONObject =
        JSONObject().put("state", state).put("level", percent)
}
