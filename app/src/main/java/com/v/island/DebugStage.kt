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
        "idle", "media", "media-timer", "media-timer-call", "satellite-closing",
        "mod-closing", "mod-to-idle", "alert-over-mod",
        "clear", "alert", "alert-marked", "alert-stacking", "alert-image",
        "timer-paused", "call-phone",
        "now-flight", "torch", "recording", "download", "upload",
        "connectivity", "connectivity-bluetooth", "connectivity-hotspot",
        "battery-charging", "battery-low", "alarm", "locked", "clear"
    )

    /** A transfer at a share of the way through, wearing the line the app's own text would. */
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
                        .put("done", done)
                        .put("total", 100)
                        .put("detail", "$done,4 von 118 MB · 12,1 MB/s")
                        .put("actions", JSONArray())
                )
        )
    }

    /** What the phone is attached to, in the shape ConnectivityWatch publishes. */
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

            // A mod ending while another is still standing: the one that went closes, the
            // one left over is a circle out at the side, and it runs back in and makes the
            // bubble a mod bubble again. The merge, in other words — the transition with
            // the most moving parts and the one nothing else here reaches.
            "mod-closing" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.deliverTimer(timer(isPaused = false))
                handler.postDelayed({ BubbleService.deliverTimer(null) }, 1400L)
            }

            // Three mods down to two, so a dot has to become a circle. The row's widths, its
            // satellites and its dots are all worked out from the same list, and this is the
            // only stage where that list shortens with something still past the circles.
            "satellite-closing" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.deliverTimer(timer(isPaused = false))
                BubbleService.deliverCall(call(phone = false))
                handler.postDelayed({ BubbleService.deliverCall(null) }, 1400L)
            }

            // The last mod ending, which is a departure and not a repaint: the width comes
            // back first and the glyph leaves down into the hole. Staged because an app being
            // killed is the real cause and that is not something to arrange on purpose.
            "mod-to-idle" -> {
                clear()
                BubbleService.deliverMedia(media(isPlaying = true))
                handler.postDelayed({ BubbleService.deliverMedia(null) }, 1600L)
            }

            // The light out and back: born at the hole, flying to its spot by the clock, and
            // the whole row standing aside for it — then the same in reverse.
            // The Now bubble's other mods, each carrying the shape the real payload has. The
            // recorder's buttons are staged with their real titles, because what a tap does is
            // decided by matching those titles and a stage with different words would test
            // nothing that ships.
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

            // What the bubble at the right end of the bar wears, with and without a Modus.
            "connectivity" -> attached(link = "wifi", level = 3)
            "connectivity-bluetooth" -> attached(
                link = "wifi",
                level = 4,
                bluetooth = JSONObject().put("name", "Buds3 Pro").put("charge", 64)
            )
            "connectivity-hotspot" -> attached(link = "mobile", level = -1, hotspot = true)

            // An alarm ringing, which is the one state that takes the whole screen. Its buttons
            // carry the clock app's own words for the same reason the recorder's do.
            "alarm" -> BubbleService.deliverAlarm(
                JSONObject()
                    .put("key", "stage-alarm")
                    .put("label", "Wecker")
                    .put("detail", "")
                    .put(
                        "actions",
                        JSONArray()
                            .put(JSONObject().put("index", 0).put("title", "Schlummern"))
                            .put(JSONObject().put("index", 1).put("title", "Beenden"))
                    )
            )

            // The lock screen without locking the phone: the padlock, the notification bubbles
            // and the bottom Now bubble all stand up, and the unlock is the merge home.
            "locked" -> {
                BubbleService.deliverMedia(media(isPlaying = true))
                BubbleService.stageLock(true)
                handler.postDelayed({ BubbleService.stageLock(false) }, 2600L)
            }

            "now-flight" -> {
                BubbleService.deliverTorch(torch())
                handler.postDelayed({ BubbleService.deliverTorch(null) }, 2200L)
            }

            // Three from the same sender inside one dwell, arriving the way a messenger really
            // posts them: one notification rewritten, carrying every message so far. The first
            // is an alert; the two after it append beneath it without the bubble announcing
            // itself again, and by the third the oldest is being pushed off the top.
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

            // The marks in running text, on a message written the way this phone writes them:
            // a German date, a clock time and an amount with the symbol after it.
            "alert-marked" -> BubbleService.deliver(
                notification().put("text", "Termin 27.08.2026 um 14:30, Anzahlung 7,80€ fällig")
            )
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

    /**
     * One conversation as a messenger posts it: the same key rewritten each time, carrying
     * every message so far in `lines` with the newest also standing as `text`. That array
     * growing is the whole of what the alert stacks on.
     */
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
