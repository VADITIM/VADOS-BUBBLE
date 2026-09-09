package com.v.island

import android.app.Notification
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * What is *happening*, which is the Now bubble's subject: a recording running, a file on its
 * way somewhere. Both are read off ongoing notifications, because an ongoing notification is
 * exactly what the platform means by "this is still going on" — the same reading of the world
 * that puts a running timer in the row.
 *
 * The line against the Status bubble at the other end of the bar is what decides where a thing
 * belongs: this file only ever answers with something that started and will stop.
 */
object NowWatch {

    /** The recorders on this phone. A recording is the app doing it, not a category. */
    private val RECORDERS = setOf(
        "com.samsung.android.app.screenrecorder",
        "com.sec.android.app.voicenote",
    )

    /** What the shade calls the two halves of a progress bar. */
    private const val PROGRESS = Notification.EXTRA_PROGRESS
    private const val PROGRESS_MAX = Notification.EXTRA_PROGRESS_MAX
    private const val PROGRESS_INDETERMINATE = Notification.EXTRA_PROGRESS_INDETERMINATE

    /**
     * Words that make a transfer an upload rather than a download, in both languages this
     * phone speaks. There is no field for the direction — a progress notification is a bar and
     * a title — so the title is the only place it is ever said.
     */
    private val UPLOADING = Regex("upload|hochlad|senden|sending", RegexOption.IGNORE_CASE)

    fun isRecording(statusBarNotification: StatusBarNotification): Boolean {
        if (statusBarNotification.packageName !in RECORDERS) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        return notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }

    /**
     * A transfer is a progress bar that knows how far along it is. An indeterminate one is an
     * app saying it has no idea, and a bubble that draws a timeline for it is drawing a
     * guess — those are left in the shade where they belong.
     */
    fun isTransfer(statusBarNotification: StatusBarNotification): Boolean {
        val extras = statusBarNotification.notification.extras
        if (extras.getBoolean(PROGRESS_INDETERMINATE, false)) return false
        if (extras.getInt(PROGRESS_MAX, 0) <= 0) return false
        return statusBarNotification.notification.flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    fun describeRecording(statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        return JSONObject()
            .put("mod", "recording")
            .put("key", statusBarNotification.key)
            .put("label", title)
            // Where the recording started, so the page counts its own seconds rather than
            // being told them: the elapsed time in the shade is a chronometer the notification
            // renders itself, and there is no field carrying the number.
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
            // When the transfer was posted, so the page can average a rate and say how long is left: no field carries a remaining time, and the app's own detail line says a speed in words rather than a number.
            .put("since", statusBarNotification.postTime)
            .put("done", extras.getInt(PROGRESS, 0))
            .put("total", extras.getInt(PROGRESS_MAX, 0))
            // The app's own line under the title, which is where "12,4 MB/s" and "3 von 8"
            // actually live. Parsed nowhere: the page draws what the app wrote, because every
            // app writes it differently and a wrong parse is worse than the app's own words.
            .put("detail", text)
            .put("actions", actions(notification))
    }

    /** Fires one of the notification's own buttons — pause, resume, stop. */
    fun act(statusBarNotification: StatusBarNotification, index: Int) {
        val action = statusBarNotification.notification.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
    }

    /**
     * Paused is read off the buttons, because no recorder posts a state field: a recording
     * that is running offers "pause", and a paused one offers "resume". Neither word is ours,
     * so both languages are matched — this phone is German and the recorder follows it.
     */
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
