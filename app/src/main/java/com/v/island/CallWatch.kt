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












object CallWatch {

    
    private const val AVATAR_PIXELS = 96

    
    private const val PHONE_ACCENT = "#30d158"

    







    fun isCall(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (AppStyles.of(statusBarNotification.packageName).key == "discord") return true
        
        
        
        
        return notification.category == Notification.CATEGORY_CALL
    }

    
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
            
            
            .put("accent", if (phone) PHONE_ACCENT else style.accent)
            .put("package", statusBarNotification.packageName)
            .put("phone", phone)
            
            
            
            .put("name", if (phone) title.ifBlank { text } else text.ifBlank { title })
            
            
            .put("since", notification.`when`.takeIf { it > 0L } ?: System.currentTimeMillis())
            .put("avatarBase64", avatar(context, notification) ?: JSONObject.NULL)
    }

    






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
