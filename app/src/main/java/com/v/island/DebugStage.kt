package com.v.island

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject


















object DebugStage {

    private val handler = Handler(Looper.getMainLooper())

    
    private val SEQUENCE = listOf(
        "idle", "media", "media-timer", "media-timer-call", "satellite-closing",
        "mod-closing", "mod-to-idle", "alert-over-mod",
        "clear", "alert", "alert-marked", "alert-stacking", "alert-image",
        "timer-paused", "call-phone",
        "now-flight", "torch", "recording", "download", "upload",
        "connectivity", "connectivity-bluetooth", "connectivity-hotspot",
        "battery-charging", "battery-low", "locked", "clear"
    )

    
    private fun transfer(kind: String, name: String, done: Int) {
        BubbleService.deliverNowMods(
            JSONObject()
                .put("recording", JSONObject.NULL)
                .put(
                    "transfer",
                    JSONObject()
                        .put("mod", kind)
                        .put("key", "stage-transfer")
                        .put("label", name)
                        .put("accent", "#4285f4")
                        .put("since", System.currentTimeMillis() - 30_000)
                        .put("done", done)
                        .put("total", 100)
                        .put("detail", "$done,4 von 118 MB · 12,1 MB/s")
                        .put("actions", JSONArray())
                )
        )
    }

    
    private fun attached(
        link: String,
        level: Int,
        bluetooth: JSONObject? = null,
        hotspot: Boolean = false,
        usb: Boolean = false
    ) {
        BubbleService.deliverConnectivity(
            JSONObject()
                .put("link", link)
                .put("level", level)
                .put("usb", usb)
                .put("hotspot", hotspot)
                .put("bluetooth", bluetooth ?: JSONObject.NULL)
        )
    }

    
    private const val STEP_MILLIS = 3500L

    fun run(name: String) {
        handler.removeCallbacksAndMessages(null)
        play(name)
    }

    




    fun sequence() {
        handler.removeCallbacksAndMessages(null)
        SEQUENCE.forEachIndexed { index, name ->
            handler.postDelayed({ play(name) }, index * STEP_MILLIS)
        }
    }

    private fun play(name: String) {
        when (name) {
            "idle" -> clear()

            "media" -> { clear(); BubbleService.deliverMedia(media(isPlaying = true)) }
            "media-paused" -> { clear(); BubbleService.deliverMedia(media(isPlaying = false)) }

            "timer" -> { clear(); BubbleService.deliverTimer(timer(isPaused = false)) }
            "timer-paused" -> { clear(); BubbleService.deliverTimer(timer(isPaused = true)) }

            "call" -> { clear(); BubbleService.deliverCall(call(phone = false)) }
            "call-phone" -> { clear(); BubbleService.deliverCall(call(phone = true)) }

            
            
            
            "media-timer" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.deliverTimer(timer(isPaused = false))
            }
            "media-timer-call" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.deliverTimer(timer(isPaused = false))
                BubbleService.deliverCall(call(phone = false))
            }

            "alert" -> BubbleService.deliver(notification())
            "alert-image" -> BubbleService.deliver(notification().put("imageBase64", picture()))
            
            
            
            
            "alert-over-mod" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                handler.postDelayed({ BubbleService.deliver(notification()) }, 900L)
            }

            "torch" -> BubbleService.deliverTorch(torch())
            "torch-off" -> BubbleService.deliverTorch(null)

            "battery-charging" -> BubbleService.deliverBattery(battery("charging", 62))
            "battery-low" -> BubbleService.deliverBattery(battery("low", 14))
            "battery-critical" -> BubbleService.deliverBattery(battery("critical", 4))

            "clear" -> { clear(); BubbleService.deliverTorch(null) }

            
            
            
            
            "mod-closing" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.deliverTimer(timer(isPaused = false))
                handler.postDelayed({ BubbleService.deliverTimer(null) }, 1400L)
            }

            
            
            
            "satellite-closing" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.deliverTimer(timer(isPaused = false))
                BubbleService.deliverCall(call(phone = false))
                handler.postDelayed({ BubbleService.deliverCall(null) }, 1400L)
            }

            
            
            
            "mod-to-idle" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                handler.postDelayed({ BubbleService.deliverMedia(null) }, 1600L)
            }

            
            
            
            
            
            
            "recording" -> BubbleService.deliverNowMods(
                JSONObject()
                    .put(
                        "recording",
                        JSONObject()
                            .put("mod", "recording")
                            .put("key", "stage-recording")
                            .put("label", "Screen recorder")
                            .put("since", System.currentTimeMillis() - 74_000L)
                            .put("isPaused", false)
                            .put(
                                "actions",
                                JSONArray()
                                    .put(JSONObject().put("index", 0).put("title", "Pause"))
                                    .put(JSONObject().put("index", 1).put("title", "Stop"))
                            )
                    )
                    .put("transfer", JSONObject.NULL)
            )

            "download" -> transfer("download", "Kaufvertrag.pdf", 62)
            "upload" -> transfer("upload", "IMG_4471.heic", 88)

            
            "connectivity" -> attached(link = "wifi", level = 3)
            "connectivity-bluetooth" -> attached(
                link = "wifi",
                level = 4,
                bluetooth = JSONObject().put("name", "Buds3 Pro").put("charge", 64)
            )
            "connectivity-hotspot" -> attached(link = "mobile", level = -1, hotspot = true)

            
            
            "locked" -> {
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.stageLock(true)
                handler.postDelayed({ BubbleService.stageLock(false) }, 2600L)
            }

            "now-flight" -> {
                BubbleService.deliverTorch(torch())
                handler.postDelayed({ BubbleService.deliverTorch(null) }, 2200L)
            }

            
            
            
            
            "alert-stacking" -> {
                clear()
                BubbleService.deliver(conversation("Are you around this evening?"))
                handler.postDelayed({
                    BubbleService.deliver(
                        conversation(
                            "Are you around this evening?",
                            "I found the place we were talking about last week"
                        )
                    )
                }, 900L)
                handler.postDelayed({
                    BubbleService.deliver(
                        conversation(
                            "Are you around this evening?",
                            "I found the place we were talking about last week",
                            "It is a five minute walk from you, we could go at eight"
                        )
                    )
                }, 1800L)
            }

            
            
            "alert-marked" -> BubbleService.deliver(
                notification().put("text", "Termin 27.08.2026 um 14:30, Anzahlung 7,80€ fällig")
            )
        }
    }

    
    private fun clear() {
        BubbleService.deliverMedia(null)
        BubbleService.deliverTimer(null)
        BubbleService.deliverCall(null)
    }

    private fun media(isPlaying: Boolean): JSONObject {
        val style = AppStyles.byKey("spotify") ?: AppStyles.generic
        return JSONObject()
            .put("app", style.key)
            .put("accent", style.accent)
            .put("package", style.packageName)
            .put("title", "Staged Song")
            .put("artist", "Debug Stage")
            .put("artBase64", cover())
            .put("isPlaying", isPlaying)
            .put("position", 61_000L)
            .put("duration", 214_000L)
    }

    




    private fun timer(isPaused: Boolean): JSONObject {
        val remaining = 754_000L
        val style = AppStyles.byKey("clock") ?: AppStyles.generic
        return JSONObject()
            .put("key", "stage:timer")
            .put("app", style.key)
            .put("appName", "Debug Timer")
            .put("accent", style.accent)
            .put("summary", "12 min, 34 s / staged")
            .put("remaining", remaining)
            .put("endsAt", if (isPaused) JSONObject.NULL else System.currentTimeMillis() + remaining)
            .put("isPaused", isPaused)
            
            
            
            .put(
                "actions",
                JSONArray()
                    .put(JSONObject().put("index", 0).put("title", "Pause"))
                    .put(JSONObject().put("index", 1).put("title", "Abbrechen"))
            )
    }

    private fun call(phone: Boolean): JSONObject {
        val style = if (phone) AppStyles.generic else AppStyles.byKey("discord") ?: AppStyles.generic
        return JSONObject()
            .put("key", "stage:call")
            .put("app", style.key)
            .put("accent", if (phone) "#25d366" else style.accent)
            .put("package", style.packageName)
            .put("phone", phone)
            .put("name", if (phone) "Staged Caller" else "staged-voice-channel")
            .put("since", System.currentTimeMillis() - 95_000L)
            .put("avatarBase64", JSONObject.NULL)
    }

    private fun notification(): JSONObject {
        val style = AppStyles.byKey("telegram") ?: AppStyles.generic
        return JSONObject()
            .put("key", "stage:alert:" + System.currentTimeMillis())
            .put("app", style.key)
            .put("appName", style.label)
            .put("accent", style.accent)
            .put("package", style.packageName)
            .put("title", "Staged alert")
            .put("text", "A notification long enough to run out of room and have to fade at the end")
            .put("iconBase64", JSONObject.NULL)
            .put("imageBase64", JSONObject.NULL)
            .put("mediaState", JSONObject.NULL)
    }

    




    private fun conversation(vararg messages: String): JSONObject = notification()
        .put("key", "stage:conversation")
        .put("title", "Mara")
        .put("text", messages.last())
        .put("lines", JSONArray().apply {
            messages.forEach { put(JSONObject().put("text", it)) }
        })

    private fun battery(state: String, percent: Int) =
        JSONObject().put("state", state).put("level", percent)

    private fun torch() = JSONObject()
        .put("accent", "#ffffff")
        .put("step", 3)
        .put("steps", 5)
        .put("dimmable", true)

    




    private fun cover() = svg(
        """<rect width="240" height="240" fill="#1db954"/>""" +
            """<circle cx="120" cy="120" r="46" fill="#0d0d0d"/>""" +
            """<circle cx="120" cy="120" r="10" fill="#1db954"/>"""
    )

    private fun picture() = svg(
        """<rect width="240" height="240" fill="#2ea6ff"/>""" +
            """<path d="M0 190 L70 110 L130 170 L180 130 L240 190 L240 240 L0 240 Z" fill="#0d0d0d"/>""" +
            """<circle cx="180" cy="60" r="26" fill="#0d0d0d"/>"""
    )

    private fun svg(body: String): String {
        val document =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240">$body</svg>"""
        val encoded = Base64.encodeToString(document.toByteArray(), Base64.NO_WRAP)
        return "data:image/svg+xml;base64,$encoded"
    }
}
