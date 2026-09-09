package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/**
 * A running timer or stopwatch, as state rather than as an event — the same reading
 * of the world that puts a playing song in the resting bubble. The clock app posts an
 * ongoing notification for as long as it runs and takes it away when it stops, so
 * that notification *is* the state.
 *
 * One UI does not put the remaining time in a field: the shade renders it from a
 * private RemoteViews chronometer. What it does leave behind is a human-readable
 * summary — "40 min, 4 s / 18:45" — whose second half is the wall-clock time the
 * timer ends at. That is worth more than the first half, because a target time can be
 * counted down against locally, while a snapshot goes stale the moment it arrives.
 */
object TimerWatch {

    /** Samsung's own key for the line the shade shows under the title. */
    private const val SUMMARY = "android.ongoingActivityNoti.secondaryInfo"

    /** Present on the clock app's ongoing notifications, and on nothing else. */
    private const val CHRONOMETER = "android.ongoingActivityNoti.chronometerRemoteViewTag"

    private val endsAtPattern = Regex("""(\d{1,2}):(\d{2})\s*$""")

    fun isTimer(statusBarNotification: StatusBarNotification): Boolean {
        if (AppStyles.of(statusBarNotification.packageName).key != "clock") return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        // The group summary carries no chronometer and no actions: it is the child
        // beside it said twice.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return notification.extras.containsKey(CHRONOMETER) ||
            notification.extras.containsKey(SUMMARY)
    }

    fun describe(statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val style = AppStyles.of(statusBarNotification.packageName)
        val summary = extras.getString(SUMMARY).orEmpty()

        return JSONObject()
            .put("key", statusBarNotification.key)
            .put("app", style.key)
            .put("appName", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: style.label)
            .put("accent", style.accent)
            .put("summary", summary)
            .put("remaining", remaining(summary) ?: JSONObject.NULL)
            .put(
                "endsAt",
                (endsAt(summary, statusBarNotification.postTime) ?: endsAtByChronometer(notification))
                    ?: JSONObject.NULL
            )
            // A paused timer is exactly the one that kept its remaining half and lost
            // its target: there is no wall clock to count down to any more, which is
            // the same test endsAt() already makes to decide whether it is running.
            // The page needs it said outright, because "no target" and "no timer at
            // all" look identical from there.
            .put(
                "isPaused",
                endsAt(summary, statusBarNotification.postTime) == null &&
                    remaining(summary) != null
            )
            .put("actions", actions(notification))
    }

    /** Fires one of the notification's own buttons: pause, resume, cancel. */
    fun act(statusBarNotification: StatusBarNotification, index: Int) {
        val action = statusBarNotification.notification.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
    }

    private fun actions(notification: Notification): JSONArray = JSONArray().apply {
        notification.actions?.forEachIndexed { index, action ->
            put(JSONObject().put("index", index).put("title", action.title?.toString().orEmpty()))
        }
    }

    /**
     * "40 min, 4 s" — the half of the summary that carries seconds. It is what a
     * paused timer is left with, and the bubble reads its own clock off it so a
     * pause changes nothing but the counting.
     */
    private val remainingPattern =
        Regex("""(\d+)\s*(std|h|min|sek|s)\b""", RegexOption.IGNORE_CASE)

    /**
     * When the timer ends, as epoch millis.
     *
     * The wall-clock half of the summary is only accurate to the minute, so counting
     * down against it finished up to a minute early — which is what "the bubble says
     * less than the app does" was. The first half carries the seconds, and it is
     * exact at the moment the notification was posted, so the end is measured from
     * `postTime` instead and the wall clock is only the fallback.
     */
    private fun endsAt(summary: String, postTime: Long): Long? {
        // The wall clock is still what says a timer is *running*: a paused one keeps
        // its remaining half and loses the target, and must not be counted down.
        val byClock = endsAtByClock(summary) ?: return null
        return remaining(summary)?.let { postTime + it } ?: byClock
    }

    private fun remaining(summary: String): Long? {
        val head = summary.substringBefore('/')
        val parts = remainingPattern.findAll(head).toList()
        if (parts.isEmpty()) return null
        return parts.sumOf { part ->
            val value = part.groupValues[1].toLongOrNull() ?: 0L
            value * when (part.groupValues[2].lowercase()) {
                "std", "h" -> 3_600_000L
                "min" -> 60_000L
                // "Sek" and "s" are the same unit written two ways — One UI abbreviates German seconds either way depending on how much room the shade line has.
                else -> 1_000L
            }
        }
    }

    /**
     * The target the notification's own chronometer is counting down to, which is where the reading comes from when the summary is missing or written in words this parser does not know — the summary is a localised sentence and the only thing keeping the bubble's digits alive, so a build or a locale that phrases it differently leaves the bubble blank, and this is the machine-readable half of the same fact that One UI has to set for its own shade chronometer to run at all.
     */
    private fun endsAtByChronometer(notification: Notification): Long? {
        val extras = notification.extras
        // The key by its own name rather than by the constant: EXTRA_SHOWS_CHRONOMETER is @hide on
        // this platform and does not compile, while the string it holds is what is actually in the
        // bundle and has been stable since the field existed.
        if (!extras.getBoolean("android.showChronometer", false) &&
            !extras.containsKey(CHRONOMETER)
        ) return null
        if (!extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)) return null
        return notification.`when`.takeIf { it > System.currentTimeMillis() }
    }

    /**
     * The wall-clock time the timer ends at, as epoch millis. A target that has
     * already passed today belongs to tomorrow — a timer set at 23:50 for twenty
     * minutes ends at 00:10.
     */
    private fun endsAtByClock(summary: String): Long? {
        val match = endsAtPattern.find(summary.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis < System.currentTimeMillis() - 60_000L) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
