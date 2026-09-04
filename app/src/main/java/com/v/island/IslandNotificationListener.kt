package com.v.island

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Captures every posted notification and hands the bubble a payload it can render.
 * The tap-through intent cannot travel as JSON, so it is parked in [NotificationLog]
 * under the notification's key and fired from the bridge when the bubble is tapped.
 */
class IslandNotificationListener : NotificationListenerService() {

    companion object {
        /** Icons are drawn at the size the compact bubble shows them, not at source size. */
        private const val ICON_PIXELS = 72

        /**
         * How long a notification killer gets to do its work before the bubble
         * announces anything. Long enough that BuzzKill has always finished, short
         * enough that the bubble still reads as instant.
         */
        private const val KILL_GRACE = 700L

        /** A photo only ever has to survive being shown 340dp wide. */
        private const val PICTURE_MAX_PIXELS = 1080

        /**
         * The platform's own packages. What they post is machine talk — charging,
         * USB mode, "system is running in the background" — and it re-appears every
         * time the phone is unlocked. None of it is news, so none of it is a
         * notification as far as the bubble is concerned.
         */
        private val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings"
        )

        private var instance: IslandNotificationListener? = null

        /**
         * Everything the notification centre is holding, newest last as the system
         * ranks it. The held-open list is the shade, not a private log of what the
         * bubble happened to announce: a notification the bubble never showed is
         * still sitting there, and one dismissed elsewhere is not.
         */
        fun shade(): JSONArray {
            val service = instance ?: return JSONArray()
            val active = runCatching { service.activeNotifications }.getOrNull() ?: return JSONArray()
            return JSONArray().apply {
                active.reversed()
                    .filter { service.belongsInList(it) }
                    .forEach { put(service.describe(it)) }
            }
        }

        /** How many notifications the bubble would list — the badge is that number. */
        fun count(): Int {
            val service = instance ?: return 0
            return service.activeNotifications?.count { service.belongsInList(it) } ?: 0
        }

        /**
         * Fires the notification's own intent, exactly as tapping it in the shade
         * would. The shade may do this from a privileged process; we may not, so the
         * launch has to be asked for explicitly — without the option, Android's
         * background-activity-launch rules drop the start on the floor and the tap
         * silently does nothing. If the app posted no intent at all, its launcher
         * entry is the honest second best.
         */
        fun open(key: String): Boolean {
            val service = instance ?: return false
            val posted = service.activeNotifications?.firstOrNull { it.key == key }
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()

            posted?.notification?.contentIntent?.let { intent ->
                // A cancelled intent is normal once the posting app has moved on, and
                // it must not take the bubble down with it.
                val sent = runCatching { intent.send(service, 0, null, null, null, null, options) }
                if (sent.isSuccess) return true
            }
            if (NotificationLog.open(key)) return true
            return service.launch(posted?.packageName)
        }

        /** Swiping a row away is the same act as swiping it away in the shade. */
        fun dismiss(key: String) {
            instance?.cancelNotification(key)
        }

        /** The running timer, or null when the clock app is not counting anything. */
        fun timer(): StatusBarNotification? =
            instance?.activeNotifications?.firstOrNull { TimerWatch.isTimer(it) }

        /** Pause, resume or cancel — the clock app's own buttons, fired from the bubble. */
        fun timerAction(index: Int) {
            timer()?.let { TimerWatch.act(it, index) }
        }

        /** Pushed on every change so the bubble can carry the timer while it runs. */
        fun publishTimer() {
            if (instance == null) return
            BubbleService.deliverTimer(timer()?.let { TimerWatch.describe(it) })
        }


        /** The recording that is running, or null when nothing is being recorded. */
        fun recording(): StatusBarNotification? =
            instance?.activeNotifications?.firstOrNull { NowWatch.isRecording(it) }

        /** The transfer in flight, or null. The oldest wins, so a second one waits its turn. */
        fun transfer(): StatusBarNotification? =
            instance?.activeNotifications
                ?.filter { NowWatch.isTransfer(it) }
                ?.minByOrNull { it.postTime }

        /** The recorder's own buttons — pause, resume, stop — fired from the bubble. */
        fun recordingAction(index: Int) {
            recording()?.let { NowWatch.act(it, index) }
        }

        /**
         * Both of the Now bubble's notification-borne mods in one push, because the bubble
         * shows one thing at a time and deciding which is the page's job: it is the side that
         * knows what it is already carrying and what that would cost to swap.
         */
        fun publishNowMods() {
            if (instance == null) return
            BubbleService.deliverNowMods(
                JSONObject()
                    .put("recording", recording()?.let { NowWatch.describeRecording(it) } ?: JSONObject.NULL)
                    .put("transfer", transfer()?.let { NowWatch.describeTransfer(it) } ?: JSONObject.NULL)
            )
        }

        /** The Discord call that is connected, or null when none is. */
        fun call(): StatusBarNotification? =
            instance?.activeNotifications?.firstOrNull { CallWatch.isCall(it) }

        /** Pushed on every change so the bubble carries the call while it lasts. */
        fun publishCall() {
            val service = instance ?: return
            BubbleService.deliverCall(call()?.let { CallWatch.describe(service, it) })
        }

        /** A notification carrying a media session is a player's, whoever posted it. */
        fun isPlayer(statusBarNotification: StatusBarNotification): Boolean =
            statusBarNotification.notification.extras
                .containsKey(Notification.EXTRA_MEDIA_SESSION)

        /**
         * Whether this player is one the phone is actually offering right now, or a
         * session left lying around by an app that is gone. Null means the shade could
         * not be read at all, which is a different answer from "no" and must not be
         * treated as one.
         *
         * A media session is not proof that a player exists. Spotify keeps a paused one
         * alive long after it has been closed and swiped away, so closing YouTube handed
         * the bubble a song nobody had touched since that morning — the session list
         * simply fell back to whatever else was in it. The notification is the proof: a
         * player the phone is really offering has a row in the shade and a card in the
         * media panel, and one that has been closed has neither.
         */
        fun isOfferingPlayer(packageName: String): Boolean? {
            val service = instance ?: return null
            return runCatching {
                service.activeNotifications.any { it.packageName == packageName && isPlayer(it) }
            }.getOrNull()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Media sessions become readable only once this binding exists. */
    override fun onListenerConnected() {
        instance = this
        MediaControl.refresh()
        publishTimer()
        publishCall()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        // A timer is not an announcement, it is a state the bubble carries: the clock
        // app re-posts this notification every time it pauses, resumes or ticks over.
        if (TimerWatch.isTimer(statusBarNotification)) {
            publishTimer()
            return
        }
        // A call is the same kind of thing: Discord's service notification exists
        // exactly as long as the call does, so it is state and never an alert.
        if (CallWatch.isCall(statusBarNotification)) {
            publishCall()
            return
        }
        // A ringing alarm is left alone entirely — no bubble, no alert, nothing. The phone is already ringing, the clock app already owns the screen while it does, and anything of ours arriving on top of that is a second thing to dismiss at six in the morning. It is asked before the recording, because the clock app posts an ongoing notification for a ringing alarm as well and that one would otherwise be read as a state worth carrying.
        if (AlarmWatch.isAlarm(statusBarNotification)) return
        // A recording and a transfer are states in the same sense, and they belong to the
        // bubble out at the clock rather than to the row: what is happening, not what is
        // connected. A progress notification re-posts on every tick, so this is also how the
        // timeline moves.
        if (NowWatch.isRecording(statusBarNotification) || NowWatch.isTransfer(statusBarNotification)) {
            publishNowMods()
            return
        }
        // Same for a player: its notification is the song, and the song is a closed
        // mod. Ask the session again first — it is the richer source and it carries
        // the transport — and hand over the notification only if it cannot answer.
        if (isPlayer(statusBarNotification)) {
            MediaControl.refresh()
            MediaControl.offer(standInFor(statusBarNotification))
            return
        }
        BubbleService.deliverCount(count())
        if (!isWorthShowing(statusBarNotification)) return

        val notification = statusBarNotification.notification
        // describe() already puts iconBase64; this adds only what the alert needs on top of it.
        val payload = describe(statusBarNotification)
            .put("imageBase64", encodePicture(notification))
            .put("mediaState", JSONObject.NULL)

        NotificationLog.add(statusBarNotification.key, payload, notification.contentIntent)

        // Announced a moment late, on purpose. A notification killer — BuzzKill here —
        // is another listener on the same broadcast, so it is racing this one: it sees
        // the notification at the same instant and cancels it a fraction of a second
        // later. Showing immediately meant the bubble announced exactly the things
        // that were switched off, and then sat there for its full dwell after they
        // were already gone. So the announcement waits out the race and asks the
        // shade whether the notification is still there before saying anything.
        val key = statusBarNotification.key
        handler.postDelayed({ if (isStillPosted(key)) pushToIsland(payload) }, KILL_GRACE)
    }

    /** Whether the shade is still holding it, or something has since taken it away. */
    private fun isStillPosted(key: String): Boolean =
        runCatching { activeNotifications }.getOrNull()?.any { it.key == key } == true

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        NotificationLog.forget(statusBarNotification.key)
        // A kill that lands after the grace has run out still takes the bubble back:
        // whatever is showing is only true while the shade agrees it is.
        BubbleService.deliverGone(statusBarNotification.key)
        if (TimerWatch.isTimer(statusBarNotification)) publishTimer()
        if (CallWatch.isCall(statusBarNotification)) publishCall()
        if (NowWatch.isRecording(statusBarNotification) || NowWatch.isTransfer(statusBarNotification)) {
            publishNowMods()
        }
        // The song may have ended with its notification, or only changed players.
        if (isPlayer(statusBarNotification)) MediaControl.refresh()
        // The badge is the shade, so it moves whenever the shade does — including
        // when something is dismissed from the shade itself, or read on the watch.
        BubbleService.deliverCount(count())
    }


    /**
     * The player's notification wearing the shape [MediaControl] publishes, so the
     * bubble can render it through the same path. It has no position, no duration and
     * no transport: what it has is the song, immediately.
     */
    private fun standInFor(statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val style = AppStyles.of(statusBarNotification.packageName)
        return JSONObject()
            .put("app", style.key)
            .put("accent", style.accent)
            .put("package", statusBarNotification.packageName)
            .put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            .put("artist", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            .put("artBase64", encodeIcon(notification))
            .put("isPlaying", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            .put("position", 0)
            .put("duration", 0)
            .put("canAdd", false)
    }

    /**
     * Who sent it, what it says and which colour it wears. `iconBase64` is the large icon when
     * the app supplied one — which for a messaging app *is* the sender's own avatar, exactly
     * the picture its native notification wears — falling back to the app's small monochrome
     * icon for everything else. See encodeIcon().
     */
    private fun describe(statusBarNotification: StatusBarNotification): JSONObject {
        val extras = statusBarNotification.notification.extras
        val style = AppStyles.of(statusBarNotification.packageName)
        return JSONObject()
            .put("key", statusBarNotification.key)
            .put("app", style.key)
            .put("appName", labelOf(statusBarNotification.packageName))
            .put("accent", style.accent)
            // Absent for every app whose identity is one colour, which is all but Google's.
            .put("gradient", style.gradient ?: JSONObject.NULL)
            .put("package", statusBarNotification.packageName)
            .put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            .put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            .put("lines", messages(statusBarNotification.notification))
            .put("iconBase64", encodeIcon(statusBarNotification.notification))
            // When it arrived, so the list can say how long ago rather than only what. The
            // notification's own postTime, not the moment this ran: a conversation is rewritten
            // in place as each line lands, so re-reading it must not make an hour-old thread
            // look new. Epoch milliseconds, and the page turns it into an age when it draws.
            .put("postedAt", statusBarNotification.postTime)
    }

    /**
     * Every message a conversation is currently holding, oldest first.
     *
     * A messenger does not post one notification per message: it posts one per
     * conversation and rewrites it as each new line arrives, which is why reading
     * only EXTRA_TEXT showed the newest line and lost every one before it — the list
     * looked like older messages were being erased, and they were, by us. The whole
     * run is in EXTRA_MESSAGES, and it is the only place it is.
     */
    private fun messages(notification: Notification): JSONArray {
        val raw = notification.extras
            .getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?: return JSONArray()
        return JSONArray().apply {
            raw.forEach { message ->
                val text = message.getCharSequence("text")?.toString().orEmpty()
                if (text.isBlank()) return@forEach
                put(
                    JSONObject()
                        .put("text", text)
                        .put("sender", message.getCharSequence("sender")?.toString().orEmpty())
                        .put("time", message.getLong("time"))
                )
            }
        }
    }

    /**
     * The list shows the shade as the user thinks of it. Group summaries are the
     * children said twice, the platform's own packages are machine talk rather than
     * news, and a notification with neither a title nor a line of text has nothing
     * to show in a row.
     */
    private fun belongsInList(statusBarNotification: StatusBarNotification): Boolean {
        if (statusBarNotification.packageName == packageName) return false
        if (statusBarNotification.packageName in systemPackages) return false
        // A song is a state the bubble already carries. Counting it as unread would
        // put a number on the badge for something nobody has to read.
        if (isPlayer(statusBarNotification)) return false
        // Same for a call: the bubble is already carrying it, and nobody has to
        // read a badge for something they are currently talking into.
        if (CallWatch.isCall(statusBarNotification)) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val extras = notification.extras
        return !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
            !extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()
    }

    /** The app's own launcher entry, for a notification that carries no intent. */
    private fun launch(target: String?): Boolean {
        val intent = target?.let { packageManager.getLaunchIntentForPackage(it) } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { startActivity(intent) }.isSuccess
    }

    /**
     * Ongoing notifications are status, not events, and a group summary duplicates the
     * child that arrives beside it — either one would make the bubble fire for nothing.
     */
    private fun isWorthShowing(statusBarNotification: StatusBarNotification): Boolean {
        if (!belongsInList(statusBarNotification)) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        return isAllowedToInterrupt(statusBarNotification)
    }

    /**
     * What the phone itself has already decided about this notification. The bubble
     * stands in for the heads-up pop-up, so it owes the same answers: a channel
     * turned down to minimum never pops up, and neither does anything Do Not Disturb
     * is currently filtering. Both are settings somebody made on purpose.
     */
    private fun isAllowedToInterrupt(statusBarNotification: StatusBarNotification): Boolean {
        val ranking = Ranking()
        if (!currentRanking.getRanking(statusBarNotification.key, ranking)) return true
        if (ranking.importance <= NotificationManager.IMPORTANCE_MIN) return false
        return ranking.matchesInterruptionFilter()
    }

    /** The name the launcher shows, so the bubble names the app the way the phone does. */
    private fun labelOf(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** The large icon when the app supplied one, otherwise its small monochrome icon. */
    private fun encodeIcon(notification: Notification): Any {
        val drawable = notification.getLargeIcon()?.loadDrawable(this)
            ?: notification.smallIcon?.loadDrawable(this)
            ?: return JSONObject.NULL
        return dataUri(toBitmap(drawable))
    }

    /**
     * The photo in a photo message. Messaging apps post it as BigPictureStyle, either
     * as a bitmap or — on newer builds — as an Icon the shade resolves for itself.
     */
    private fun encodePicture(notification: Notification): Any {
        val extras = notification.extras
        val bitmap = extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
            ?: extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                ?.let { icon -> runCatching { icon.loadDrawable(this) }.getOrNull() }
                ?.let { drawable -> (drawable as? BitmapDrawable)?.bitmap }
            ?: return JSONObject.NULL
        return dataUri(scaleDown(bitmap))
    }

    private fun toBitmap(drawable: Drawable): Bitmap =
        (drawable as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }
            ?: Bitmap.createBitmap(ICON_PIXELS, ICON_PIXELS, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }

    /** A full-resolution photo as a data URI would be megabytes of JSON per notification. */
    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= PICTURE_MAX_PIXELS) return bitmap
        val scale = PICTURE_MAX_PIXELS.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
        )
    }

    private fun dataUri(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
