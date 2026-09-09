package com.v.island

import android.app.Activity
import android.os.Bundle


















class StatusPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BubbleService.toggleStatusPanel()
        finish()
        
        
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
