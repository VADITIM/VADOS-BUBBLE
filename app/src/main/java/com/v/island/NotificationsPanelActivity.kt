package com.v.island

import android.app.Activity
import android.os.Bundle

class NotificationsPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BubbleService.toggleNotifications()
        finish()

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
