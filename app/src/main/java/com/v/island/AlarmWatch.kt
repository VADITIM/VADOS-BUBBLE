package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * An alarm going off, recognised only so that nothing happens because of it.
 *
 * The bubble used to take the whole screen for one. It does not any more: the phone is already
 * ringing and the clock app already owns the screen while it does, so a state of ours over the
 * top of that is a second thing to dismiss at six in the morning. What is left is the test,
 * because a ringing alarm still has to be told apart from the ordinary notifications the clock
 * app posts — it is announced by nobody rather than announced by us.
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
}
