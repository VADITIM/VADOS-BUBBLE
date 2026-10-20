package com.v.island

import android.app.Activity
import android.os.Bundle

class MessagingPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BubbleService.toggleMessaging()
        finish()

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
