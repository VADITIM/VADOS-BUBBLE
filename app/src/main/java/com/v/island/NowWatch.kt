package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject










object NowWatch {

    
    private val RECORDERS = setOf(
        "com.samsung.android.app.screenrecorder",
        "com.sec.android.app.voicenote",
    )

    
    private const val PROGRESS = Notification.EXTRA_PROGRESS
    private const val PROGRESS_MAX = Notification.EXTRA_PROGRESS_MAX
    private const val PROGRESS_INDETERMINATE = Notification.EXTRA_PROGRESS_INDETERMINATE

    




    private val UPLOADING = Regex("upload|hochlad|senden|sending", RegexOption.IGNORE_CASE)

    fun isRecording(statusBarNotification: StatusBarNotification): Boolean {
        if (statusBarNotification.packageName !in RECORDERS) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        return notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }

    




    // Requiring FLAG_ONGOING_EVENT here is what kept transfers out of the Status bubble almost always: Telegram and WhatsApp post their download and upload progress as an ordinary notification and rewrite it per tick, so every tick fell past this test into the alert path and the file in flight arrived as a spam of alerts that swiping up brought straight back. A determinate progress bar is the platform's own statement that something is in flight, and it is the whole test now.
    fun isTransfer(statusBarNotification: StatusBarNotification): Boolean {
        val extras = statusBarNotification.notification.extras
        if (extras.getBoolean(PROGRESS_INDETERMINATE, false)) return false
        return extras.getInt(PROGRESS_MAX, 0) > 0
    }

    fun describeRecording(statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        return JSONObject()
            .put("mod", "recording")
            .put("key", statusBarNotification.key)
            .put("label", title)
            
            
            
            .put("since", statusBarNotification.postTime)
            .put("isPaused", isPaused(notification))
            .put("actions", actions(notification))
    }

    fun describeTransfer(statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val style = AppStyles.of(statusBarNotification.packageName)
        return JSONObject()
            .put("mod", if (UPLOADING.containsMatchIn(title + ' ' + text)) "upload" else "download")
            .put("key", statusBarNotification.key)
            .put("label", title.ifEmpty { style.label })
            .put("accent", style.accent)
            
            .put("since", statusBarNotification.postTime)
            .put("done", extras.getInt(PROGRESS, 0))
            .put("total", extras.getInt(PROGRESS_MAX, 0))
            
            
            
            .put("detail", text)
            .put("isPaused", isPaused(notification))
            .put("actions", actions(notification))
    }

    
    fun act(statusBarNotification: StatusBarNotification, index: Int) {
        val action = statusBarNotification.notification.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
    }

    




    private fun isPaused(notification: Notification): Boolean =
        notification.actions?.any {
            Regex("resume|fortsetzen|weiter", RegexOption.IGNORE_CASE)
                .containsMatchIn(it.title?.toString().orEmpty())
        } == true

    private fun actions(notification: Notification): JSONArray = JSONArray().apply {
        notification.actions?.forEachIndexed { index, action ->
            put(JSONObject().put("index", index).put("title", action.title?.toString().orEmpty()))
        }
    }
}
