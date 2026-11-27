package com.v.island

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.v.island.bubble.Island
import org.json.JSONArray
import org.json.JSONObject


















object DebugStage {

    private val handler = Handler(Looper.getMainLooper())

    
    private fun transfer(kind: String, name: String, done: Int, state: String = NowWatch.TRANSFER_ACTIVE) {
        BubbleService.deliverNowMods(
            JSONObject()
                .put("recording", JSONObject.NULL)
                .put(
                    "transfer",
                    JSONObject()
                        .put("mod", kind)
                        .put("state", state)
                        .put("key", "stage-transfer")
                        .put("package", "com.android.chrome")
                        .put("label", name)
                        .put("accent", "#4285f4")
                        .put("since", System.currentTimeMillis() - 30_000)
                        .put("done", done)
                        .put("total", 100)
                        .put("detail", "$done,4 von 118 MB · 12,1 MB/s")
                        .put("isPaused", false)
                        .put("actions", JSONArray().put(JSONObject().put("index", 0).put("title", "Pause")))
                )
        )
    }

    
    private fun runTransfer(kind: String, name: String) {
        var tick = 0
        while (tick * TRANSFER_STEP <= 100) {
            val done = tick * TRANSFER_STEP
            handler.postDelayed({ transfer(kind, name, done) }, tick * TRANSFER_TICK)
            tick++
        }
        handler.postDelayed({ transfer(kind, name, 100, NowWatch.TRANSFER_DONE) }, tick * TRANSFER_TICK)
        handler.postDelayed({ BubbleService.deliverNowMods(JSONObject().put("recording", JSONObject.NULL).put("transfer", JSONObject.NULL)) }, (tick + 1) * TRANSFER_TICK)
    }

    private const val TRANSFER_STEP = 10
    private const val TRANSFER_TICK = 280L

    fun run(name: String) {
        handler.removeCallbacksAndMessages(null)
        play(name)
    }

    private fun play(name: String) {
        when (name) {
            "idle" -> clearMods()
            "clear" -> clearAll()
            "reset" -> reset()

            "media" -> { clearMods(); BubbleService.deliverMedia(media(isPlaying = true)) }
            "timer" -> { clearMods(); BubbleService.deliverTimer(timer(isPaused = false)) }
            "call" -> { clearMods(); BubbleService.deliverCall(call()) }
            "recording" -> BubbleService.deliverNowMods(JSONObject().put("recording", recording()).put("transfer", JSONObject.NULL))
            "download" -> runTransfer("download", "Kaufvertrag.pdf")
            "upload" -> runTransfer("upload", "IMG_4471.heic")
            "add-mod" -> addMod()
            "remove-mod" -> removeMod()

            "alert" -> BubbleService.deliver(notification().put("imageBase64", picture()))
            "alert-stacking" -> {
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
        }
    }

    private fun clearMods() {
        BubbleService.deliverMedia(null)
        BubbleService.deliverTimer(null)
        BubbleService.deliverCall(null)
        BubbleService.deliverNowMods(JSONObject().put("recording", JSONObject.NULL).put("transfer", JSONObject.NULL))
    }

    /* Clear only dropped media, timer and call, so a staged transfer, recording, trip, torch, Alert, open menu or the unread count stayed standing; it empties every one of them. */
    private fun clearAll() {
        clearMods()
        BubbleService.clearStage()
    }

    private fun reset() {
        clearAll()
        handler.postDelayed({ BubbleService.republishReal() }, RESET_DELAY)
    }

    private const val RESET_DELAY = 400L

    private fun addMod() {
        when {
            Island.media == null -> BubbleService.deliverMedia(media(isPlaying = true))
            Island.timer == null -> BubbleService.deliverTimer(timer(isPaused = false))
            Island.call == null -> BubbleService.deliverCall(call())
            Island.recording == null -> BubbleService.deliverNowMods(JSONObject().put("recording", recording()).put("transfer", JSONObject.NULL))
        }
    }

    private fun removeMod() {
        when {
            Island.recording != null -> BubbleService.deliverNowMods(JSONObject().put("recording", JSONObject.NULL).put("transfer", JSONObject.NULL))
            Island.call != null -> BubbleService.deliverCall(null)
            Island.timer != null -> BubbleService.deliverTimer(null)
            Island.media != null -> BubbleService.deliverMedia(null)
        }
    }

    private fun recording() = JSONObject()
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

    private fun media(isPlaying: Boolean): JSONObject {
        val style = AppStyles.byKey("spotify") ?: AppStyles.generic
        return JSONObject()
            .put("app", style.key)
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

    private fun call(): JSONObject {
        val style = AppStyles.byKey("discord") ?: AppStyles.generic
        return JSONObject()
            .put("key", "stage:call")
            .put("app", style.key)
            .put("accent", style.accent)
            .put("package", style.packageName)
            .put("phone", false)
            .put("name", "staged-voice-channel")
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
