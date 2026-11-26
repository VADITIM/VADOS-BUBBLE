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
    private const val FINISHED_RATIO = 0.99f

    




    private val TRANSFER_APPS = setOf(
        "com.android.providers.downloads",
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.android.vending",
        "com.google.android.apps.docs",
        "com.google.android.apps.photos",
        "com.samsung.android.scloud",
        "com.samsung.android.app.sharelive",
        "com.microsoft.skydrive",
        "com.dropbox.android",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "com.whatsapp",
        "org.thoughtcrime.securesms",
        "com.google.android.gm",
        "com.samsung.android.email.provider",
    )

    private val UPLOAD_APPS = setOf("com.google.android.apps.photos", "com.samsung.android.scloud")

    private val UPLOADING = Regex("upload|hochlad|hochgeladen|sending|senden|gesendet|wird gesendet|backing up|back up|backup|sicher(n|ung)|syncing to|sharing", RegexOption.IGNORE_CASE)
    private val DOWNLOADING = Regex("download|herunterl|heruntergeladen|empfang|receiving|installing|wird installiert", RegexOption.IGNORE_CASE)
    private val FINISHED = Regex("""complete|finished|\bdone\b|downloaded|uploaded|\bsent\b|saved|backed up|abgeschlossen|fertig|heruntergeladen|hochgeladen|gesendet|gespeichert|gesichert|erfolgreich|installiert""",RegexOption.IGNORE_CASE)
    private val FAILED = Regex("fail|error|cancel|unsuccessful|couldn.t|could not|unable|fehlgeschlagen|fehler|abgebrochen|nicht möglich|konnte nicht", RegexOption.IGNORE_CASE)

    /* Mirrors TRANSFER_* in bubble/Models.kt: the state a transfer payload carries across to the bubble. */
    const val TRANSFER_ACTIVE = "active"
    const val TRANSFER_DONE = "done"
    const val TRANSFER_FAILED = "failed"

    fun isRecording(statusBarNotification: StatusBarNotification): Boolean {
        if (statusBarNotification.packageName !in RECORDERS) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        return notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }

    




    // Requiring FLAG_ONGOING_EVENT here is what kept transfers out of the Status bubble almost always: Telegram and WhatsApp post their download and upload progress as an ordinary notification and rewrite it per tick, so every tick fell past this test into the alert path and the file in flight arrived as a spam of alerts that swiping up brought straight back.
    // Only a determinate bar was a transfer, so a download still connecting, a Play Store install, a Photos backup or a Gmail send — all indeterminate or barless — never reached the bubble; an indeterminate bar, CATEGORY_PROGRESS or an ongoing notice now count when the app or the words say a file is moving, which keeps "Checking for updates" and system syncs out.
    fun isTransfer(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val isIndeterminate = extras.getBoolean(PROGRESS_INDETERMINATE, false)
        if (!isIndeterminate && extras.getInt(PROGRESS_MAX, 0) > 0) return true
        val words = wordsOf(statusBarNotification)
        val isMoving = statusBarNotification.packageName in TRANSFER_APPS || UPLOADING.containsMatchIn(words) || DOWNLOADING.containsMatchIn(words)
        if (!isMoving) return false
        if (isIndeterminate) return true
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        return (notification.category == Notification.CATEGORY_PROGRESS || isOngoing) && (UPLOADING.containsMatchIn(words) || DOWNLOADING.containsMatchIn(words))
    }

    fun ratioOf(statusBarNotification: StatusBarNotification): Float {
        val extras = statusBarNotification.notification.extras
        val total = extras.getInt(PROGRESS_MAX, 0)
        return if (total > 0) extras.getInt(PROGRESS, 0).toFloat() / total else 0f
    }

    fun outcomeOf(statusBarNotification: StatusBarNotification, lastRatio: Float, isRemoved: Boolean): String {
        val words = wordsOf(statusBarNotification)
        if (!isRemoved && FAILED.containsMatchIn(words)) return TRANSFER_FAILED
        if (!isRemoved && FINISHED.containsMatchIn(words)) return TRANSFER_DONE
        return if (lastRatio >= FINISHED_RATIO) TRANSFER_DONE else TRANSFER_FAILED
    }

    private fun isUpload(statusBarNotification: StatusBarNotification): Boolean {
        val words = wordsOf(statusBarNotification)
        if (UPLOADING.containsMatchIn(words)) return true
        if (DOWNLOADING.containsMatchIn(words)) return false
        return statusBarNotification.packageName in UPLOAD_APPS
    }

    private fun wordsOf(statusBarNotification: StatusBarNotification): String {
        val extras = statusBarNotification.notification.extras
        return listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
            .joinToString(" ") { extras.getCharSequence(it)?.toString().orEmpty() }
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
            .put("mod", if (isUpload(statusBarNotification)) "upload" else "download")
            .put("state", TRANSFER_ACTIVE)
            .put("key", statusBarNotification.key)
            .put("package", statusBarNotification.packageName)
            .put("indeterminate", extras.getBoolean(PROGRESS_INDETERMINATE, false) || extras.getInt(PROGRESS_MAX, 0) <= 0)
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
