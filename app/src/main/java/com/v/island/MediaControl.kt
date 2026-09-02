package com.v.island

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/**
 * The song that is playing, as state rather than as an event. Music is not a
 * notification: it does not arrive, dwell and leave, it is simply true for as long
 * as it is playing, so it owns the resting bubble instead of interrupting it.
 *
 * Sessions are read through [MediaSessionManager], which hands them out only to an
 * enabled notification listener — the same grant the bubble already needs.
 */
object MediaControl {

    /** Album art is never shown larger than the open player. */
    private const val ART_PIXELS = 320

    private var manager: MediaSessionManager? = null
    private var listener: ComponentName? = null

    /** Every session the phone currently has, all of them listened to. See [attach]. */
    private var controllers: List<MediaController> = emptyList()

    /** The one the bubble is about, which is not simply the first one that exists. */
    private var controller: MediaController? = null

    /**
     * Whether the session the bubble is showing has ever actually made a sound, reset
     * whenever the bubble changes session. It is the whole difference between a player
     * that is warming up and one that finished hours ago — see [select].
     */
    private var hasSounded = false

    private var onChanged: (JSONObject?) -> Unit = {}

    /** Re-encoding the same album art on every playback tick would be pure waste. */
    private var artKey: String? = null
    private var artUri: String? = null

    private val controllerCallback = object : MediaController.Callback() {
        // select() rather than publish(): a state change can mean this session is no
        // longer the one to show — it stopped, or another player started sounding —
        // and publishing straight from the callback showed a song that had ended.
        override fun onMetadataChanged(metadata: MediaMetadata?) = select()
        override fun onPlaybackStateChanged(state: PlaybackState?) = select()
        // One session dying says nothing about the others, so the list is asked for
        // again rather than thrown away.
        override fun onSessionDestroyed() = refresh()
    }

    fun start(context: Context, onChanged: (JSONObject?) -> Unit) {
        this.onChanged = onChanged
        val component = ComponentName(context, IslandNotificationListener::class.java)
        val sessions = context.getSystemService(MediaSessionManager::class.java) ?: return
        manager = sessions
        listener = component
        runCatching {
            sessions.addOnActiveSessionsChangedListener({ attach(it.orEmpty()) }, component)
        }.onFailure { android.util.Log.w("IslandBubble", "media listener refused", it) }
        refresh()
    }

    /**
     * Sessions are only handed to a notification listener the system has actually
     * bound, and after an install that binding can land later than the accessibility
     * service does — a song already playing then goes unseen, because a session that
     * never changes never fires the change listener. So the listener asks again the
     * moment it is connected.
     */
    fun refresh() {
        val sessions = manager ?: return
        val component = listener ?: return
        runCatching { attach(sessions.getActiveSessions(component)) }
            .onFailure { android.util.Log.w("IslandBubble", "no media sessions", it) }
    }

    fun stop() {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = emptyList()
        controller = null
        hasSounded = false
        onChanged = {}
    }

    /**
     * Every active session is listened to, not only the one being shown. A playback
     * state changing does not fire the session-list listener, so with a callback on
     * the chosen controller alone a second player starting to sound behind a session
     * that was already there was never heard at all.
     */
    private fun attach(next: List<MediaController>) {
        if (next.map { it.sessionToken } != controllers.map { it.sessionToken }) {
            controllers.forEach { it.unregisterCallback(controllerCallback) }
            controllers = next
            controllers.forEach { it.registerCallback(controllerCallback) }
        }
        select()
    }

    /**
     * Whichever session is actually making sound, otherwise whichever is paused,
     * otherwise the one already being shown for as long as it is still going.
     *
     * The third pass used to be `firstOrNull { it.metadata != null }` — any session
     * carrying a song, whatever state it was in. That was written for a player's first
     * seconds, which are STATE_NONE, BUFFERING or CONNECTING while it starts up, and
     * insisting on a clean playing state had meant the bubble did not see Spotify most
     * of the time it was asked. But the sessions an app leaves behind when it is
     * *finished* look identical: a browser tab that played a video this morning, a
     * game that made a sound, a podcast app that was closed. They sit in the active
     * list with their metadata intact for as long as the process lives, and the bubble
     * opened out into a media mod for them with nothing playing — which is what "it
     * randomly expands into a mod" was.
     *
     * So a session is no longer chosen on metadata alone. It is listened to from the
     * moment it appears and chosen the moment it plays or pauses, which for a real
     * player is the second or two it takes to connect. Nothing is lost in between: the
     * player's own notification still stands in through [offer] during exactly those
     * seconds, and it only exists while something really is playing.
     *
     * That alone was not enough, because the leftover a session list falls back to is
     * regularly a *paused* one: Spotify keeps its session alive long after the app has
     * been closed and swiped away, so closing YouTube or Netflix put a song from that
     * morning in the bubble. The second test is [IslandNotificationListener.isOfferingPlayer]
     * — whether the phone is still showing that player a notification at all.
     */
    private fun select() {
        // A session whose player has no notification is a session its app left behind:
        // the phone is not offering that player anywhere else either. Null is "could not
        // ask", not "no", so it passes.
        val offered = controllers.filter {
            IslandNotificationListener.isOfferingPlayer(it.packageName) != false
        }
        val next = offered.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: offered.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: offered.firstOrNull { it.sessionToken == controller?.sessionToken && isOngoing(it) }

        if (next?.sessionToken != controller?.sessionToken) hasSounded = false
        controller = next
        publish()
    }

    /**
     * Whether a session that is neither playing nor paused at this instant is still
     * the one the bubble is in the middle of. One that has sounded keeps the bubble
     * through the states a player passes through mid-song — buffering, or the
     * STATE_NONE some apps report while they re-connect — but STOPPED and ERROR are
     * the player saying it is done, and one that has never sounded keeps nothing.
     */
    private fun isOngoing(candidate: MediaController): Boolean = hasSounded &&
        when (candidate.playbackState?.state) {
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR, null -> false
            else -> true
        }

    /** Who is playing, so a caller can tell whether this session is the way in. */
    val packageName: String? get() = controller?.packageName

    /**
     * The player's own notification, offered as a stand-in. A media notification is
     * proof that something is playing even when the session behind it is not readable
     * yet — the transport controls will not work off this, but the bubble shows the
     * song the moment the phone knows about it, and the session takes over as soon as
     * it answers.
     */
    fun offer(fallback: JSONObject) {
        if (controller != null) return
        onChanged(fallback)
    }

    fun command(action: String) {
        val transport = controller?.transportControls ?: return
        when (action) {
            "play" -> transport.play()
            "pause" -> transport.pause()
            "next" -> transport.skipToNext()
            "previous" -> transport.skipToPrevious()
        }
    }

    /**
     * Opens the player. The session's own `sessionActivity` is the way in rather than
     * a launcher intent: it is the player's PendingIntent, so it carries the player's
     * right to start an activity — a service starting one itself is a background
     * activity launch, which Android blocks without a word. It also lands on what is
     * playing instead of on whatever screen the app was last left on.
     */
    fun open(context: Context): Boolean {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
        controller?.sessionActivity?.let { intent ->
            if (runCatching { intent.send(context, 0, null, null, null, null, options) }
                    .isSuccess
            ) return true
        }
        val fallback = controller?.packageName
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return false
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /**
     * Where the song is *now*. The session reports a position only when something
     * changes, so a player that has been running untouched for a minute still says
     * what it said a minute ago — the elapsed time since that report has to be added
     * back on, or the open player draws a timeline that is a minute behind.
     */
    fun position(): Long {
        val state = controller?.playbackState ?: return 0L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        val since = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (since * state.playbackSpeed).toLong()
    }

    /**
     * How fast it plays, which for a voice note is the difference between listening to it and
     * waiting for it. It is a transport control like any other — the session either honours it
     * or ignores it, and there is no way to ask which beforehand, so the bubble offers it where
     * it is worth having and lets the player answer.
     */
    fun setSpeed(rate: Float) {
        controller?.transportControls?.setPlaybackSpeed(rate)
    }

    fun seek(milliseconds: Long) {
        controller?.transportControls?.seekTo(milliseconds)
    }

    private fun publish() {
        val current = controller
        if (current == null) {
            artKey = null
            artUri = null
            onChanged(null)
            return
        }

        val metadata = current.metadata
        val state = current.playbackState
        // The one place the fact is established: a session that has played or paused
        // is a real player, and [select] lets it keep the bubble on that basis.
        if (state?.state == PlaybackState.STATE_PLAYING ||
            state?.state == PlaybackState.STATE_PAUSED
        ) hasSounded = true
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()

        onChanged(
            JSONObject()
                .put("app", AppStyles.of(current.packageName).key)
                .put("accent", AppStyles.of(current.packageName).accent)
                .put("package", current.packageName)
                .put("title", title)
                .put("artist", artist)
                .put("artBase64", encodeArt(metadata, title + artist) ?: JSONObject.NULL)
                .put("isPlaying", state?.state == PlaybackState.STATE_PLAYING)
                .put("position", state?.position ?: 0L)
                .put("duration", metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
        )
    }

    /**
     * The cover, encoded once per picture rather than once per song.
     *
     * Spotify publishes a song before it has the artwork for it: the first metadata
     * carries its grey placeholder disc, or nothing at all, and the real cover arrives
     * a second later under the same title and artist. Keyed on the song alone, that
     * second arrival looked like the same art and the placeholder was kept for the
     * whole track — which is what "sometimes it doesn't load the image" was. The
     * bitmap itself is part of the key, so a new picture is a new key even when the
     * song has not changed.
     */
    private fun encodeArt(metadata: MediaMetadata?, key: String): String? {
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        // Unchanged metadata hands back the same bitmap, so this still encodes only
        // when the picture is actually a different one.
        val stamp = "$key#${bitmap.generationId}#${bitmap.width}x${bitmap.height}"
        if (stamp == artKey) return artUri
        artKey = stamp
        artUri = "data:image/png;base64," + Base64.encodeToString(
            toPng(scaleDown(bitmap)), Base64.NO_WRAP
        )
        return artUri
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= ART_PIXELS) return bitmap
        val scale = ART_PIXELS.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
        )
    }

    private fun toPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
}
