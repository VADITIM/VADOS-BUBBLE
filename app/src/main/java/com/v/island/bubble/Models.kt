package com.v.island.bubble

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.v.island.vas.Palette
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class Action(val index: Int, val title: String)

@Immutable
data class Message(val sender: String, val text: String, val time: Long)

@Immutable
data class Notice(
    val key: String,
    val app: String,
    val appName: String,
    val accent: Color,
    val packageName: String,
    val title: String,
    val text: String,
    val messages: List<Message>,
    val icon: ImageBitmap?,
    val appIcon: ImageBitmap?,
    val picture: ImageBitmap?,
    val postedAt: Long,
)

@Immutable
data class Media(
    val app: String,
    val packageName: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    val art: ImageBitmap?,
    val receivedAt: Long,
) {
    /* The media accent was the playing app's brand colour, so Spotify's green coloured the visualizer and the artist whatever accent was set; it follows the set accent now. */
    val accent: Color get() = Island.appearance.accent
}

@Immutable
data class Timer(
    val key: String,
    val appName: String,
    val accent: Color,
    val summary: String,
    val remaining: Long?,
    val endsAt: Long?,
    val actions: List<Action>,
    val isPaused: Boolean,
)

@Immutable
data class Call(
    val key: String,
    val accent: Color,
    val packageName: String,
    val isPhone: Boolean,
    val name: String,
    val since: Long,
    val avatar: ImageBitmap?,
)

@Immutable
data class Trip(
    val key: String,
    val appName: String,
    val accent: Color,
    val train: String?,
    val origin: String?,
    val destination: String?,
    val nextStop: String?,
    val exitStop: String?,
    val arrival: String?,
    val endsAt: Long?,
    val delay: String?,
    val platform: String?,
    val lines: List<String>,
    val actions: List<Action>,
)

@Immutable
data class Torch(val step: Int, val steps: Int, val isDimmable: Boolean)

@Immutable
data class Recording(val key: String, val label: String, val since: Long, val isPaused: Boolean, val actions: List<Action>)

@Immutable
data class Transfer(
    val key: String,
    val isUpload: Boolean,
    val label: String,
    val accent: Color,
    val done: Int,
    val total: Int,
    val detail: String,
    val isPaused: Boolean,
    val actions: List<Action>,
)

@Immutable
data class Paired(val names: List<String>, val charge: Int)

@Immutable
data class Connectivity(
    val link: String,
    val level: Int,
    val isOnline: Boolean,
    val generation: String,
    val ssid: String,
    val isUsb: Boolean,
    val isHotspot: Boolean,
    val isZen: Boolean,
    val zenName: String,
    val paired: Paired?,
)

@Immutable
data class Charge(val percent: Int, val exact: Double, val isPlugged: Boolean, val remainingMinutes: Int)

enum class BatteryEvent { Plugged, Low, Critical }

@Immutable
data class Toggles(val switches: Map<String, Boolean>, val levels: Map<String, Int>) {
    val isCall: Boolean get() = switches["call"] == true

    fun has(name: String) = name in switches

    companion object {
        val NONE = Toggles(emptyMap(), emptyMap())
    }
}

@Immutable
data class Vital(val usedGigabytes: Double, val totalGigabytes: Double, val percent: Int)

@Immutable
data class Vitals(val storage: Vital?, val ram: Vital?)

@Immutable
data class Weather(val celsius: Double, val sky: String)

@Immutable
data class Conversation(
    val id: String,
    val packageName: String,
    val accent: Color,
    val title: String,
    val isGroup: Boolean,
    val unread: Int,
    val isLive: Boolean,
    val updatedAt: Long,
    val messages: List<Message>,
)

object Payloads {
    /* The cache held 48 entries whatever their size, keyed by their whole base64 text, and decoded pictures at full resolution, so a few photos in a row ran the service out of memory; it is now bounded by bytes and pictures are decoded no larger than the screen needs. */
    private val decoded = object : android.util.LruCache<String, ImageBitmap>(IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 + key.length * 2
    }

    fun image(dataUri: String?): ImageBitmap? {
        val value = dataUri?.takeIf { it.startsWith("data:image") } ?: return null
        decoded.get(value)?.let { return it }
        val bitmap = runCatching {
            val bytes = Base64.decode(value.substringAfter(','), Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IMAGE_LONGEST) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull() ?: return null
        decoded.put(value, bitmap)
        return bitmap
    }

    private const val IMAGE_CACHE_BYTES = 24 * 1024 * 1024
    private const val IMAGE_LONGEST = 1080

    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.accent(): Color = Palette.parse(text("accent"), Color.White)

    private fun actions(array: JSONArray?): List<Action> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { Action(it.optInt("index"), it.optString("title")) }
        }

    private fun messages(array: JSONArray?): List<Message> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { Message(it.optString("sender"), it.optString("text"), it.optLong("time")) }
        }

    fun notice(json: JSONObject) = Notice(
        key = json.optString("key"),
        app = json.optString("app"),
        appName = json.optString("appName"),
        accent = json.accent(),
        packageName = json.optString("package"),
        title = json.optString("title"),
        text = json.optString("text"),
        messages = messages(json.optJSONArray("lines")),
        icon = image(json.text("iconBase64")),
        appIcon = image(json.text("appIconBase64")),
        picture = image(json.text("imageBase64")),
        postedAt = json.optLong("postedAt", System.currentTimeMillis()),
    )

    fun notices(array: JSONArray): List<Notice> =
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::notice) }

    fun media(json: JSONObject?) = json?.let {
        Media(
            app = it.optString("app"),
            packageName = it.optString("package"),
            title = it.optString("title"),
            artist = it.optString("artist"),
            isPlaying = it.optBoolean("isPlaying"),
            duration = it.optLong("duration"),
            position = it.optLong("position"),
            art = image(it.text("artBase64")),
            receivedAt = android.os.SystemClock.elapsedRealtime(),
        )
    }

    fun timer(json: JSONObject?) = json?.let {
        Timer(
            key = it.optString("key"),
            appName = it.optString("appName"),
            accent = it.accent(),
            summary = it.optString("summary"),
            remaining = if (it.isNull("remaining") || !it.has("remaining")) null else it.optLong("remaining"),
            endsAt = if (it.isNull("endsAt") || !it.has("endsAt")) null else it.optLong("endsAt"),
            actions = actions(it.optJSONArray("actions")),
            isPaused = it.optBoolean("isPaused"),
        )
    }

    fun call(json: JSONObject?) = json?.let {
        Call(
            key = it.optString("key"),
            accent = it.accent(),
            packageName = it.optString("package"),
            isPhone = it.optBoolean("phone"),
            name = it.optString("name"),
            since = it.optLong("since", System.currentTimeMillis()),
            avatar = image(it.text("avatarBase64")),
        )
    }

    fun trip(json: JSONObject?) = json?.let {
        Trip(
            key = it.optString("key"),
            appName = it.optString("appName"),
            accent = it.accent(),
            train = it.text("train"),
            origin = it.text("origin"),
            destination = it.text("destination"),
            nextStop = it.text("nextStop"),
            exitStop = it.text("exitStop"),
            arrival = it.text("arrival"),
            endsAt = if (it.isNull("endsAt") || !it.has("endsAt")) null else it.optLong("endsAt"),
            delay = it.text("delay"),
            platform = it.text("platform"),
            lines = it.optJSONArray("lines")?.let { array -> (0 until array.length()).map(array::optString) }.orEmpty(),
            actions = actions(it.optJSONArray("actions")),
        )
    }

    fun torch(json: JSONObject?) = json?.let {
        Torch(it.optInt("step"), it.optInt("steps", 5), it.optBoolean("dimmable"))
    }

    fun recording(json: JSONObject?) = json?.let {
        Recording(it.optString("key"), it.optString("label"), it.optLong("since"), it.optBoolean("isPaused"), actions(it.optJSONArray("actions")))
    }

    fun transfer(json: JSONObject?) = json?.let {
        Transfer(
            key = it.optString("key"),
            isUpload = it.optString("mod") == "upload",
            label = it.optString("label"),
            accent = it.accent(),
            done = it.optInt("done"),
            total = it.optInt("total"),
            detail = it.optString("detail"),
            isPaused = it.optBoolean("isPaused"),
            actions = actions(it.optJSONArray("actions")),
        )
    }

    fun connectivity(json: JSONObject) = Connectivity(
        link = json.optString("link"),
        level = json.optInt("level"),
        isOnline = json.optBoolean("online"),
        generation = json.optString("generation"),
        ssid = json.optString("ssid"),
        isUsb = json.optBoolean("usb"),
        isHotspot = json.optBoolean("hotspot"),
        isZen = json.optBoolean("zen"),
        zenName = json.optString("zenName"),
        paired = json.optJSONObject("bluetooth")?.let { paired ->
            val names = paired.optJSONArray("names")?.let { array -> (0 until array.length()).map(array::optString).filter(String::isNotEmpty) }.orEmpty()
            Paired(names.ifEmpty { listOfNotNull(paired.text("name")) }, paired.optInt("charge", -1))
        },
    )

    fun toggles(json: JSONObject): Toggles {
        val switches = HashMap<String, Boolean>()
        val levels = HashMap<String, Int>()
        json.keys().forEach { name ->
            when (val value = json.opt(name)) {
                is Boolean -> switches[name] = value
                is Number -> levels[name] = value.toInt()
            }
        }
        return Toggles(switches, levels)
    }

    private fun vital(json: JSONObject?) = json?.let { Vital(it.optDouble("usedGigabytes"), it.optDouble("totalGigabytes"), it.optInt("percent")) }

    fun vitals(json: JSONObject) = Vitals(vital(json.optJSONObject("storage")), vital(json.optJSONObject("ram")))

    fun weather(json: JSONObject?) = json?.let { Weather(it.optDouble("celsius"), it.optString("sky", "cloud")) }

    fun batteryEvent(json: JSONObject): BatteryEvent? = when (json.optString("state")) {
        "charging", "plugged" -> BatteryEvent.Plugged
        "low" -> BatteryEvent.Low
        "critical" -> BatteryEvent.Critical
        else -> null
    }

    fun conversations(array: JSONArray): List<Conversation> =
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let {
                Conversation(
                    id = it.optString("id"),
                    packageName = it.optString("package"),
                    accent = it.accent(),
                    title = it.optString("title"),
                    isGroup = it.optBoolean("isGroup"),
                    unread = it.optInt("unread"),
                    isLive = it.optBoolean("isLive"),
                    updatedAt = it.optLong("updatedAt"),
                    messages = messages(it.optJSONArray("messages")),
                )
            }
        }
}
