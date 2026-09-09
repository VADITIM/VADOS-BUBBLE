package com.v.island

import org.json.JSONObject

/**
 * Every switch the quick settings panel draws, read and written as shell.
 *
 * One object rather than one per switch: they are all the same two lines — a command that
 * answers with a number and a command that sets it — and eight files of that would be eight
 * places to look for the one that is spelled wrong. [MicrophoneAccess] stays where it is
 * because its state is a dump to be parsed rather than a setting to be read.
 *
 * Nothing here is polled. The panel reads the whole set when it opens, and each switch is
 * repainted from what it was told rather than from a re-read: a switch that goes back on its
 * own answer half a second after the finger left it is worse than one that is briefly wrong.
 */
object SystemToggles {

    /**
     * The whole set in one shell round-trip. Nine separate `run()` calls is nine binder hops
     * through the Shizuku runner while a panel is trying to open, and the panel opens in 420ms.
     */
    private const val READ_ALL =
        "settings get global wifi_on;" +
            "settings get global bluetooth_on;" +
            "svc usb getFunctions;" +
            "settings get global zen_mode;" +
            "settings get secure reduce_bright_colors_activated;" +
            "settings get system accelerometer_rotation;" +
            "settings get global low_power;" +
            "settings get global mobile_data;" +
            "settings get system screen_brightness;" +
            "settings get global airplane_mode_on;" +
            // A mode rather than a flag, the way zen is: 0 is off and 1, 2 and 3 are the three ways
            // the phone is allowed to work out where it is standing.
            "settings get secure location_mode;" +
            // Greped on the phone: this one command narrates what it is doing over several lines, and
            // every line of it would shift the index of everything read after it.
            "cmd media_session volume --stream 3 --get 2>&1 | grep -m1 'volume is'"

    /**
     * What the phone calls full brightness, which is not 255 on this device and is not a number
     * anything public will say. It lives in the framework's own resources, and `Resources.getSystem()`
     * is exactly the handle to those — no context, no permission, no guess. A phone that will not
     * answer leaves the slider at nothing rather than writing a value against a range we invented.
     */
    private val brightnessMax: Int by lazy {
        val resources = android.content.res.Resources.getSystem()
        val id = resources.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        if (id == 0) 0 else runCatching { resources.getInteger(id) }.getOrDefault(0)
    }

    /** The media stream's own scale, off the same line the level is read from: `volume is 3 in range [0..15]`. */
    private fun volumeOf(line: String): Int {
        val index = line.substringAfter("volume is ", "").substringBefore(' ').toIntOrNull() ?: return -1
        val top = line.substringAfter("..", "").substringBefore(']').toIntOrNull() ?: return -1
        if (top <= 0) return -1
        volumeTop = top
        return index * 100 / top
    }

    fun read(): JSONObject {
        val answer = JSONObject()
        // Unavailable rather than off: with no shell there is nothing true to say about any of
        // these, and a panel full of switches sitting at "off" is a lie the user can act on.
        val lines = ShizukuShell.run(READ_ALL)?.lines() ?: return answer
        fun line(index: Int) = lines.getOrNull(index)?.trim().orEmpty()
        answer.put("wifi", line(0) == "1")
        answer.put("bluetooth", line(1) == "1")
        answer.put("usb", line(2).contains("mtp"))
        // zen_mode is a mode rather than a flag — 0 is off and 1, 2 and 3 are the three kinds of
        // quiet — so anything that is not 0 is a Modus being worn.
        answer.put("modus", line(3).toIntOrNull()?.let { it != 0 } ?: false)
        answer.put("dim", line(4) == "1")
        answer.put("rotate", line(5) == "1")
        answer.put("saver", line(6) == "1")
        answer.put("mobile", line(7) == "1")
        answer.put("mic", MicrophoneAccess.read() == MicrophoneAccess.ALLOWED)
        answer.put("plane", line(9) == "1")
        // location_mode is a mode rather than a flag, the way zen_mode is: 0 is off and 1, 2 and 3
        // are the three ways the phone is allowed to work out where it is standing.
        answer.put("gps", line(10).toIntOrNull()?.let { it != 0 } ?: false)
        // Nothing about the hotspot is read here. Samsung keeps no settings key for it, and the
        // page is already told: ConnectivityWatch listens to the tether broadcast, so the switch is
        // lit off the connectivity payload rather than off a second answer that would disagree.
        // The two levels, as percentages: the ranges are the phone's business and the panel draws a
        // share of a bar. Missing rather than zero where the phone would not say — a slider standing
        // at the floor is a reading, and "we could not ask" is not one.
        val brightness = line(8).toIntOrNull() ?: -1
        if (brightness >= 0 && brightnessMax > 0) {
            answer.put("brightness", brightness * 100 / brightnessMax)
        }
        volumeOf(line(11)).takeIf { it >= 0 }?.let { answer.put("volume", it) }
        // Nothing about a recording is read here: the page is already told when one starts and
        // stops, because that is the Now bubble's own mod arriving. Asking the shell for it as
        // well would be a second answer to a question that already has one.
        return answer
    }

    /**
     * A level put where the finger left it, as a percentage of whatever the phone's own range is.
     *
     * Nothing is read back afterwards, unlike a switch: this is written per frame while a thumb is
     * moving, and a shell round-trip returning the whole panel's state on each of those would be a
     * command queue the finger outruns. The page owns the value while it is being dragged.
     */
    fun setLevel(name: String, percent: Int): Boolean {
        val share = percent.coerceIn(0, 100)
        val command = when (name) {
            // Auto brightness put back what the finger asked for a moment later, which reads as a
            // slider that will not stay where it is put, so a drag is also the switch off it.
            "brightness" -> if (brightnessMax <= 0) return false else
                "settings put system screen_brightness_mode 0;" +
                    "settings put system screen_brightness " +
                    (share * brightnessMax / 100).coerceAtLeast(1)
            "volume" -> "cmd media_session volume --stream 3 --set " + volumeIndex(share)
            else -> return false
        }
        return ShizukuShell.run(command) != null
    }

    /** The media stream's steps are few and coarse, so the share is put back onto its own scale. */
    private fun volumeIndex(share: Int): Int {
        val top = volumeTop.takeIf { it > 0 } ?: return 0
        return share * top / 100
    }

    /** How many steps the media stream has, learned from the same line its level is read off. */
    private var volumeTop = 0

    /** True when the command was actually run, which is not the same as the switch having moved. */
    fun set(name: String, isOn: Boolean): Boolean {
        val one = if (isOn) "1" else "0"
        val command = when (name) {
            "wifi" -> "svc wifi " + if (isOn) "enable" else "disable"
            "bluetooth" -> "svc bluetooth " + if (isOn) "enable" else "disable"
            // An empty function list is the charging-only state, which is what USB off means here.
            "usb" -> "svc usb setFunctions " + if (isOn) "mtp" else ""
            "mobile" -> "svc data " + if (isOn) "enable" else "disable"
            "modus" -> "cmd notification set_dnd " + if (isOn) "on" else "off"
            "dim" -> "settings put secure reduce_bright_colors_activated $one"
            "rotate" -> "settings put system accelerometer_rotation $one"
            "saver" -> "settings put global low_power $one"
            // The setting alone changes nothing — writing airplane_mode_on without the broadcast
            // leaves every radio up under a phone that says it is in aeroplane mode — and the
            // broadcast needs a permission no sideloaded app gets. The connectivity shell command
            // does both halves in one call, which is why it is not a `settings put` like its
            // neighbours here.
            "plane" -> "cmd connectivity airplane-mode " + if (isOn) "enable" else "disable"
            "gps" -> "cmd location set-location-enabled " + if (isOn) "true" else "false"
            // No setting behind it either, and for a different reason from the recorder's: starting
            // a hotspot from the shell means handing `cmd wifi start-softap` an SSID, a band and a
            // password, which is this app inventing a network rather than switching the phone's own
            // one on. The tile carries the configuration the person already saved.
            "hotspot" -> "cmd statusbar click-tile " +
                "com.android.systemui/com.android.systemui.qs.tiles.HotspotTile"
            // The recorder has no setting behind it — it is a quick settings tile and pressing it
            // is the only way in. One UI's own recorder is Smart Capture's tile service.
            "recording" -> "cmd statusbar click-tile " +
                "com.samsung.android.app.smartcapture/" +
                "com.samsung.android.app.smartcapture.screenrecorder.ScreenRecorderTileService"
            "mic" -> return MicrophoneAccess.set(isOn)
            else -> return false
        }
        return ShizukuShell.run(command) != null
    }
}
