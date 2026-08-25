package com.v.island

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/**
 * A Discord voice call, read the same way a song or a timer is: not as an event that
 * arrives and leaves, but as something that is simply true for as long as it is true.
 * Discord keeps a foreground service running while the call is connected, and the
 * notification that service must post is therefore the call itself — it exists
 * exactly while the call does.
 *
 * What the bubble wants from it is the face on the other end: the large icon is the
 * avatar of whoever or whatever is being called into, which is the one thing that
 * says which call this is without a word of text.
 */
object CallWatch {

    /** The avatar is never drawn larger than the closed bubble's glyph. */
    private const val AVATAR_PIXELS = 96

    /** The colour a live line has worn on every phone there has ever been. */
    private const val PHONE_ACCENT = "#30d158"

    /**
     * Discord's ongoing notification. The call service is the only thing that keeps
     * one posted for any length of time, so the flag is the test.
     *
     * ponytail: every ongoing Discord notification counts as a call. If a second
     * kind ever shows up, the log line in [describe] names its channel and category
     * and this can be narrowed to that one.
     */
    fun isCall(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        // The summary is the child beside it said twice.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (AppStyles.of(statusBarNotification.packageName).key == "discord") return true
        // A telephony call is the same kind of truth read a different way: the dialer
        // keeps an ongoing notification up for exactly as long as the line is open,
        // and declares what it is rather than leaving it to be guessed from a package
        // name — whether that is the Samsung dialer, Google's, or a SIP app.
        return notification.category == Notification.CATEGORY_CALL
    }

    /** A call on the line rather than in an app: its own colour, its own glyph. */
    private fun isPhoneCall(
        statusBarNotification: StatusBarNotification,
        notification: Notification
    ): Boolean = AppStyles.of(statusBarNotification.packageName).key != "discord" &&
        notification.category == Notification.CATEGORY_CALL

    fun describe(context: Context, statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val style = AppStyles.of(statusBarNotification.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        android.util.Log.i(
            "IslandBubble",
            "discord ongoing: channel=${notification.channelId} " +
                "category=${notification.category} title=$title text=$text"
        )

        val phone = isPhoneCall(statusBarNotification, notification)

        return JSONObject()
            .put("key", statusBarNotification.key)
            .put("app", style.key)
            // A call on the line is green wherever the dialer's own brand sits: green
            // is what a live call has meant on a phone for longer than this app.
            .put("accent", if (phone) PHONE_ACCENT else style.accent)
            .put("package", statusBarNotification.packageName)
            .put("phone", phone)
            // Who the call is with is the *text* for Discord: its title is boilerplate
            // — "Voice Connected — Tap to return to call" — the same on every call
            // there is. The dialer is the other way round: the title is the contact.
            .put("name", if (phone) title.ifBlank { text } else text.ifBlank { title })
            // When the line opened, counted up locally from here. The dialer posts
            // this as the chronometer base and does not re-post it every second.
            .put("since", notification.`when`.takeIf { it > 0L } ?: System.currentTimeMillis())
            .put("avatarBase64", avatar(context, notification) ?: JSONObject.NULL)
    }

    /**
     * The face on the other end, when there is one. Discord's voice notification
     * carries `android.largeIcon=null` — the avatar is simply not in it — so this is
     * usually nothing, and the bubble draws a monogram instead. The small icon is
     * not a stand-in: it is Discord's crossed-out microphone, which says something
     * quite different from who is on the call.
     */
    private fun avatar(context: Context, notification: Notification): String? {
        val drawable = notification.getLargeIcon()?.loadDrawable(context) ?: return null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }
            ?: render(drawable)
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun render(drawable: Drawable): Bitmap =
        Bitmap.createBitmap(AVATAR_PIXELS, AVATAR_PIXELS, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
}
