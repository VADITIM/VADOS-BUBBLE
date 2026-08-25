package com.v.island

/**
 * The system-wide "Mikrofonzugriff" toggle — the same switch One UI puts in
 * Steuerung und Warnungen. It is sensor-privacy state, not a secure setting, so
 * it is only reachable with a shell UID.
 *
 * In `dumpsys sensor_privacy`, sensor 1 is the microphone and toggle type 1 is the
 * software toggle; state 1 means privacy is on, so access is blocked. Verified on
 * this device by toggling it from adb and diffing the dump.
 */
object MicrophoneAccess {

    const val ALLOWED = "allowed"
    const val BLOCKED = "blocked"
    const val UNAVAILABLE = "unavailable"

    private val microphoneState = Regex(
        """sensor=1\s+toggles=\{\s+toggle_type=1\s+state_type=(\d)"""
    )

    fun read(): String {
        val dump = ShizukuShell.run("dumpsys sensor_privacy") ?: return UNAVAILABLE
        return when (microphoneState.find(dump)?.groupValues?.get(1)) {
            "1" -> BLOCKED
            "2" -> ALLOWED
            else -> UNAVAILABLE
        }
    }

    fun set(isAllowed: Boolean): Boolean {
        val verb = if (isAllowed) "disable" else "enable"
        return ShizukuShell.run("cmd sensor_privacy $verb 0 microphone") != null
    }
}
