package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * An alarm going off, which is the one thing this interface has to be able to take the whole
 * screen for: it is not information, it is a demand, and a bubble that says it quietly has
 * failed at the only job the alarm has.
 *
 * It is read the way the timer is — off the notification, because a ringing alarm is a state
 * the clock app declares and takes back. What separates it from every other notification that
 * app posts is the full-screen intent: an app asking to take over the screen is the platform's
 * own definition of "this cannot wait", and it is exactly the alarms and the incoming calls
 * that use it.
 */
object AlarmWatch {

    fun isAlarm(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        // A call is a full-screen intent too, and it has a bubble of its own already.
        if (CallWatch.isCall(statusBarNotification)) return false
        if (notification.category == Notification.CATEGORY_ALARM) return true
        return notification.fullScreenIntent != null &&
            AppStyles.of(statusBarNotification.packageName).key == "clock"
    }

    fun describe(statusBarNotification: StatusBarNotification): JSONObject {
        val extras = statusBarNotification.notification.extras
        return JSONObject()
            .put("key", statusBarNotification.key)
            .put("label", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            .put("detail", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            .put("actions", actions(statusBarNotification.notification))
    }

    /** Snooze and dismiss are the clock app's own buttons, pressed from the bubble. */
    fun act(statusBarNotification: StatusBarNotification, index: Int) {
        val action = statusBarNotification.notification.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
    }

    private fun actions(notification: Notification): JSONArray = JSONArray().apply {
        notification.actions?.forEachIndexed { index, action ->
            put(JSONObject().put("index", index).put("title", action.title?.toString().orEmpty()))
        }
    }
}
