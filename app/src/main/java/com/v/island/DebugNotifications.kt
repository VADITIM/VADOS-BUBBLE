package com.v.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import kotlin.random.Random

object DebugNotifications {

    const val CHANNEL = "debug"

    private val SENDERS = listOf("Mara", "Jonas", "Lea", "Dad", "Office", "Kiosk", "Tim", "Anna")
    private val MESSAGES = listOf(
        "Are you around this evening?",
        "I found the place we were talking about last week",
        "Termin 27.08.2026 um 14:30, Anzahlung 7,80€ fällig",
        "Sent you a photo",
        "ok",
        "Can you call me back when you have a minute",
        "https://example.com/tickets is where the booking went through",
        "Package arrives tomorrow between 10:00 and 12:00",
        "Did you see the reel I sent?",
        "The meeting moved to Thursday",
    )

    fun send(context: Context, count: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Debug", NotificationManager.IMPORTANCE_DEFAULT)
        )
        repeat(count) {
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(SENDERS.random())
                .setContentText(MESSAGES.random())
                .setAutoCancel(false)
                .setWhen(System.currentTimeMillis() - Random.nextLong(0, 3_600_000))
                .setShowWhen(true)
                .build()
            manager.notify(Random.nextInt(), notification)
        }
    }
}
