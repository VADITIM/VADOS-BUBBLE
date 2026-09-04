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
            "settings get global low_power"

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
        answer.put("mic", MicrophoneAccess.read() == MicrophoneAccess.ALLOWED)
        // Nothing about a recording is read here: the page is already told when one starts and
        // stops, because that is the Now bubble's own mod arriving. Asking the shell for it as
        // well would be a second answer to a question that already has one.
        return answer
    }

    /** True when the command was actually run, which is not the same as the switch having moved. */
    fun set(name: String, isOn: Boolean): Boolean {
        val one = if (isOn) "1" else "0"
        val command = when (name) {
            "wifi" -> "svc wifi " + if (isOn) "enable" else "disable"
            "bluetooth" -> "svc bluetooth " + if (isOn) "enable" else "disable"
            // An empty function list is the charging-only state, which is what USB off means here.
            "usb" -> "svc usb setFunctions " + if (isOn) "mtp" else ""
            "modus" -> "cmd notification set_dnd " + if (isOn) "on" else "off"
            "dim" -> "settings put secure reduce_bright_colors_activated $one"
            "rotate" -> "settings put system accelerometer_rotation $one"
            "saver" -> "settings put global low_power $one"
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
