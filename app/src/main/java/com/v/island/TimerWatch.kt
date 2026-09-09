package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject













object TimerWatch {

    
    private const val SUMMARY = "android.ongoingActivityNoti.secondaryInfo"

    
    private const val CHRONOMETER = "android.ongoingActivityNoti.chronometerRemoteViewTag"

    private val endsAtPattern = Regex("""(\d{1,2}):(\d{2})\s*$""")

    fun isTimer(statusBarNotification: StatusBarNotification): Boolean {
        if (AppStyles.of(statusBarNotification.packageName).key != "clock") return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        
        
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
            
            
            
            
            
            .put(
                "isPaused",
                endsAt(summary, statusBarNotification.postTime) == null &&
                    remaining(summary) != null
            )
            .put("actions", actions(notification))
    }

    
    fun act(statusBarNotification: StatusBarNotification, index: Int) {
        val action = statusBarNotification.notification.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
    }

    private fun actions(notification: Notification): JSONArray = JSONArray().apply {
        notification.actions?.forEachIndexed { index, action ->
            put(JSONObject().put("index", index).put("title", action.title?.toString().orEmpty()))
        }
    }

    




    private val remainingPattern =
        Regex("""(\d+)\s*(std|h|min|sek|s)\b""", RegexOption.IGNORE_CASE)

    








    private fun endsAt(summary: String, postTime: Long): Long? {
        
        
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
                
                else -> 1_000L
            }
        }
    }

    


    private fun endsAtByChronometer(notification: Notification): Long? {
        val extras = notification.extras
        
        
        
        if (!extras.getBoolean("android.showChronometer", false) &&
            !extras.containsKey(CHRONOMETER)
        ) return null
        if (!extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)) return null
        return notification.`when`.takeIf { it > System.currentTimeMillis() }
    }

    




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
