package com.v.island

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every state the bubble can be in, reachable from a button instead of from a real
 * song, a real timer or a real call. There are no tests here and the check is the
 * phone — so this is the check. A state nothing on the phone happens to be in right
 * now is otherwise only found broken weeks later, by accident, which is exactly how
 * a small change keeps breaking something unrelated.
 *
 * A stage sets the *environment* up and only then fires the event, because most of
 * these states are not events at all: they are things that are simply true, and the
 * bubble cannot be shown carrying a song unless something is playing. Everything
 * goes through the same companion doors the real watchers knock on, so a stage
 * exercises the real path and not a second one written for testing.
 *
 * The payloads are the shapes the watchers build, kept in step with them by hand:
 * `MediaControl.publish`, `TimerWatch.describe`, `CallWatch.describe`,
 * `BatteryWatch` and `TorchWatch.describe`. A field added there is missing here.
 */
object DebugStage {

    private val handler = Handler(Looper.getMainLooper())

    /** What the sequence walks, in the order it walks them. */
    private val SEQUENCE = listOf(
        "idle", "media", "media-timer", "media-timer-call", "alert-over-mod",
        "clear", "alert", "alert-image", "timer-paused", "call-phone",
        "torch", "battery-charging", "battery-low", "clear"
    )

    /** How long each staged state is left standing before the next one replaces it. */
    private const val STEP_MILLIS = 3500L

    fun run(name: String) {
        handler.removeCallbacksAndMessages(null)
        play(name)
    }

    /**
     * The whole system in one pass, so a single screen recording shows every state
     * and every transition between them in a fixed order — which is what makes two
     * recordings, before and after a change, comparable.
     */
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

            // Two live mods: the second one splits off as a satellite. Three: one a
            // side. This is the whole of what the row has to survive, and the swipe
            // that rotates it is a gesture, so it is left to the finger.
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
            // The one that has to hand the bubble back to the song rather than to the
            // bare bubble when its dwell runs out. The song is given a moment to take
            // the bubble first, or the alert lands during the merge instead of over it
            // and a different transition is what gets checked.
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
        }
    }

    /** Every mod off. The torch is left alone: it is a real light and may be on. */
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

    /**
     * A paused timer is exactly the one that kept its remaining half and lost its
     * target, so the staged pause drops `endsAt` rather than setting a flag on top
     * of a still-running clock — the page reads the absence, not the flag.
     */
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
            // The buttons are drawn from this and do nothing when pressed: they fire a
            // real notification's own actions, and a staged timer has no notification
            // behind it. The layout is what is being checked here, not the wiring.
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

    private fun battery(state: String, percent: Int) =
        JSONObject().put("state", state).put("level", percent)

    private fun torch() = JSONObject()
        .put("accent", "#ffffff")
        .put("step", 3)
        .put("steps", 5)
        .put("dimmable", true)

    /**
     * Stand-in artwork and stand-in photo, drawn as SVG rather than as a bitmap: the
     * page sets these straight into an `img` src, so a data URI is the whole of what
     * is needed and there is no encoder to reach for.
     */
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
