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
import kotlin.math.abs
import org.json.JSONObject









object MediaControl {

    
    private const val ART_PIXELS = 320

    private var manager: MediaSessionManager? = null
    private var listener: ComponentName? = null

    
    private var controllers: List<MediaController> = emptyList()

    
    private var controller: MediaController? = null

    




    private var hasSounded = false

    private var onChanged: (JSONObject?) -> Unit = {}

    
    private var artKey: String? = null
    private var artUri: String? = null

    









    private var artSent: String? = null

    
    private const val POSITION_SLACK = 2_000L

    private var publishedState: String? = null
    private var publishedPosition = 0L
    private var publishedPlaying = false
    private var publishedAt = 0L

    private val controllerCallback = object : MediaController.Callback() {
        
        
        
        override fun onMetadataChanged(metadata: MediaMetadata?) = select()
        override fun onPlaybackStateChanged(state: PlaybackState?) = select()
        
        
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

    





    private fun attach(next: List<MediaController>) {
        if (next.map { it.sessionToken } != controllers.map { it.sessionToken }) {
            controllers.forEach { it.unregisterCallback(controllerCallback) }
            controllers = next
            controllers.forEach { it.registerCallback(controllerCallback) }
        }
        select()
    }

    


























    private fun select() {
        
        
        
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

    






    private fun isOngoing(candidate: MediaController): Boolean = hasSounded &&
        when (candidate.playbackState?.state) {
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR, null -> false
            else -> true
        }

    
    val packageName: String? get() = controller?.packageName

    






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
            artSent = null
            publishedState = null
            onChanged(null)
            return
        }

        val metadata = current.metadata
        val state = current.playbackState
        
        
        if (state?.state == PlaybackState.STATE_PLAYING ||
            state?.state == PlaybackState.STATE_PAUSED
        ) hasSounded = true
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()

        val art = encodeArt(metadata, title + artist)
        val payload = JSONObject()
            .put("app", AppStyles.of(current.packageName).key)
            .put("accent", AppStyles.of(current.packageName).accent)
            .put("package", current.packageName)
            .put("title", title)
            .put("artist", artist)
            .put("isPlaying", state?.state == PlaybackState.STATE_PLAYING)
            .put("duration", metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
        if (art != artSent) {
            artSent = art
            payload.put("artBase64", art ?: JSONObject.NULL)
        }

        
        
        
        
        
        
        val position = state?.position ?: 0L
        val elapsed = SystemClock.elapsedRealtime() - publishedAt
        val expected = if (publishedPlaying) publishedPosition + elapsed else publishedPosition
        
        
        val fingerprint = payload.toString()
        if (fingerprint == publishedState && abs(position - expected) < POSITION_SLACK) return
        publishedState = fingerprint
        publishedPosition = position
        publishedPlaying = state?.state == PlaybackState.STATE_PLAYING
        publishedAt = SystemClock.elapsedRealtime()

        onChanged(payload.put("position", position))
    }

    










    private fun encodeArt(metadata: MediaMetadata?, key: String): String? {
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        
        
        val stamp = "$key#${bitmap.generationId}#${bitmap.width}x${bitmap.height}"
        if (stamp == artKey) return artUri
        artKey = stamp
        artUri = "data:image/webp;base64," + Base64.encodeToString(
            toWebp(scaleDown(bitmap)), Base64.NO_WRAP
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

    






    private fun toWebp(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)
        stream.toByteArray()
    }
}
