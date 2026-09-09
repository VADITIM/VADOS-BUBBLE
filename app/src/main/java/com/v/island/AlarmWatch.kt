package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification
















object AlarmWatch {

    fun isAlarm(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        
        if (CallWatch.isCall(statusBarNotification)) return false
        if (notification.category == Notification.CATEGORY_ALARM) return true
        return notification.fullScreenIntent != null &&
            AppStyles.of(statusBarNotification.packageName).key == "clock"
    }
}
