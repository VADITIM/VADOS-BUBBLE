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






class IslandNotificationListener : NotificationListenerService() {

    companion object {
        
        private const val ICON_PIXELS = 72

        




        private const val KILL_GRACE = 700L

        
        private const val PICTURE_MAX_PIXELS = 1080

        





        private val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings"
        )

        private var instance: IslandNotificationListener? = null

        





        fun shade(): JSONArray {
            val service = instance ?: return JSONArray()
            val active = runCatching { service.activeNotifications }.getOrNull() ?: return JSONArray()
            return JSONArray().apply {
                active.reversed()
                    .filter { service.belongsInList(it) }
                    .forEach { put(service.describe(it)) }
            }
        }

        
        fun count(): Int {
            val service = instance ?: return 0
            return service.activeNotifications?.count { service.belongsInList(it) } ?: 0
        }

        







        fun open(key: String): Boolean {
            val service = instance ?: return false
            val posted = service.activeNotifications?.firstOrNull { it.key == key }
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()

            posted?.notification?.contentIntent?.let { intent ->
                
                
                val sent = runCatching { intent.send(service, 0, null, null, null, null, options) }
                if (sent.isSuccess) return true
            }
            if (NotificationLog.open(key)) return true
            return service.launch(posted?.packageName)
        }

        
        fun dismiss(key: String) {
            instance?.cancelNotification(key)
        }

        
        fun timer(): StatusBarNotification? =
            instance?.activeNotifications?.firstOrNull { TimerWatch.isTimer(it) }

        
        fun timerAction(index: Int) {
            timer()?.let { TimerWatch.act(it, index) }
        }

        
        fun publishTimer() {
            if (instance == null) return
            BubbleService.deliverTimer(timer()?.let { TimerWatch.describe(it) })
        }


        
        fun recording(): StatusBarNotification? =
            instance?.activeNotifications?.firstOrNull { NowWatch.isRecording(it) }

        
        fun transfer(): StatusBarNotification? =
            instance?.activeNotifications
                ?.filter { NowWatch.isTransfer(it) }
                ?.minByOrNull { it.postTime }

        
        fun recordingAction(index: Int) {
            recording()?.let { NowWatch.act(it, index) }
        }

        




        fun publishNowMods() {
            if (instance == null) return
            BubbleService.deliverNowMods(
                JSONObject()
                    .put("recording", recording()?.let { NowWatch.describeRecording(it) } ?: JSONObject.NULL)
                    .put("transfer", transfer()?.let { NowWatch.describeTransfer(it) } ?: JSONObject.NULL)
            )
        }

        
        fun call(): StatusBarNotification? =
            instance?.activeNotifications?.firstOrNull { CallWatch.isCall(it) }

        
        fun publishCall() {
            val service = instance ?: return
            BubbleService.deliverCall(call()?.let { CallWatch.describe(service, it) })
        }

        
        fun isPlayer(statusBarNotification: StatusBarNotification): Boolean =
            statusBarNotification.notification.extras
                .containsKey(Notification.EXTRA_MEDIA_SESSION)

        












        fun isOfferingPlayer(packageName: String): Boolean? {
            val service = instance ?: return null
            return runCatching {
                service.activeNotifications.any { it.packageName == packageName && isPlayer(it) }
            }.getOrNull()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    
    override fun onListenerConnected() {
        AppStyles.learnFrom(this)
        instance = this
        MediaControl.refresh()
        publishTimer()
        publishCall()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        
        
        if (TimerWatch.isTimer(statusBarNotification)) {
            publishTimer()
            return
        }
        
        
        if (CallWatch.isCall(statusBarNotification)) {
            publishCall()
            return
        }
        
        if (AlarmWatch.isAlarm(statusBarNotification)) return
        
        
        
        
        if (NowWatch.isRecording(statusBarNotification) || NowWatch.isTransfer(statusBarNotification)) {
            publishNowMods()
            return
        }
        
        
        
        if (isPlayer(statusBarNotification)) {
            MediaControl.refresh()
            MediaControl.offer(standInFor(statusBarNotification))
            return
        }
        BubbleService.deliverCount(count())
        if (!isWorthShowing(statusBarNotification)) return

        val notification = statusBarNotification.notification
        
        val payload = describe(statusBarNotification)
            .put("imageBase64", encodePicture(notification))
            .put("mediaState", JSONObject.NULL)

        NotificationLog.add(statusBarNotification.key, payload, notification.contentIntent)

        
        
        
        
        
        
        
        val key = statusBarNotification.key
        handler.postDelayed({ if (isStillPosted(key)) pushToIsland(payload) }, KILL_GRACE)
    }

    
    private fun isStillPosted(key: String): Boolean =
        runCatching { activeNotifications }.getOrNull()?.any { it.key == key } == true

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        NotificationLog.forget(statusBarNotification.key)
        
        
        BubbleService.deliverGone(statusBarNotification.key)
        if (TimerWatch.isTimer(statusBarNotification)) publishTimer()
        if (CallWatch.isCall(statusBarNotification)) publishCall()
        if (NowWatch.isRecording(statusBarNotification) || NowWatch.isTransfer(statusBarNotification)) {
            publishNowMods()
        }
        
        if (isPlayer(statusBarNotification)) MediaControl.refresh()
        
        
        BubbleService.deliverCount(count())
    }


    




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

    





    private fun describe(statusBarNotification: StatusBarNotification): JSONObject {
        val extras = statusBarNotification.notification.extras
        val style = AppStyles.of(statusBarNotification.packageName)
        return JSONObject()
            .put("key", statusBarNotification.key)
            .put("app", style.key)
            .put("appName", labelOf(statusBarNotification.packageName))
            .put("accent", style.accent)
            
            .put("gradient", style.gradient ?: JSONObject.NULL)
            .put("package", statusBarNotification.packageName)
            .put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            .put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            .put("lines", messages(statusBarNotification.notification))
            .put("iconBase64", encodeIcon(statusBarNotification.notification))
            .put("appIconBase64", encodeAppIcon(statusBarNotification.packageName))
            
            
            
            
            .put("postedAt", statusBarNotification.postTime)
    }

    








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

    





    private fun belongsInList(statusBarNotification: StatusBarNotification): Boolean {
        if (statusBarNotification.packageName == packageName) return false
        if (statusBarNotification.packageName in systemPackages) return false
        
        
        if (isPlayer(statusBarNotification)) return false
        
        
        if (CallWatch.isCall(statusBarNotification)) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val extras = notification.extras
        return !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
            !extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()
    }

    
    private fun launch(target: String?): Boolean {
        val intent = target?.let { packageManager.getLaunchIntentForPackage(it) } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { startActivity(intent) }.isSuccess
    }

    



    private fun isWorthShowing(statusBarNotification: StatusBarNotification): Boolean {
        if (!belongsInList(statusBarNotification)) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        return isAllowedToInterrupt(statusBarNotification)
    }

    





    private fun isAllowedToInterrupt(statusBarNotification: StatusBarNotification): Boolean {
        val ranking = Ranking()
        if (!currentRanking.getRanking(statusBarNotification.key, ranking)) return true
        if (ranking.importance <= NotificationManager.IMPORTANCE_MIN) return false
        return ranking.matchesInterruptionFilter()
    }

    
    private fun labelOf(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    

    private val appIcons = HashMap<String, Any>()


    private fun encodeAppIcon(packageName: String): Any = appIcons.getOrPut(packageName) {
        runCatching { dataUri(toBitmap(packageManager.getApplicationIcon(packageName))) }
            .getOrDefault(JSONObject.NULL)
    }

    private fun encodeIcon(notification: Notification): Any {
        val drawable = notification.getLargeIcon()?.loadDrawable(this)
            ?: notification.smallIcon?.loadDrawable(this)
            ?: return JSONObject.NULL
        return dataUri(toBitmap(drawable))
    }

    



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
