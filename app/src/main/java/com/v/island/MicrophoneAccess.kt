package com.v.island










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
