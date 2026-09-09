package com.v.island














object HeadsUp {

    const val SUPPRESSED = "suppressed"
    const val SHOWING = "showing"
    const val UNAVAILABLE = "unavailable"

    private const val SETTING = "heads_up_notifications_enabled"
    private const val NOTILUS =
        "com.samsung.systemui.notilus/com.samsung.systemui.notilus.service.NotificationListener"

    fun read(): String {
        val headsUp = ShizukuShell.run("settings get global $SETTING")?.trim() ?: return UNAVAILABLE
        val listeners = ShizukuShell.run("settings get secure enabled_notification_listeners")
            ?: return UNAVAILABLE
        val isBriefOn = listeners.contains("com.samsung.systemui.notilus")
        return if (headsUp == "0" && !isBriefOn) SUPPRESSED else SHOWING
    }

    fun set(isSuppressed: Boolean) {
        ShizukuShell.run("settings put global $SETTING ${if (isSuppressed) 0 else 1}")
        ShizukuShell.run(
            if (isSuppressed) "cmd notification disallow_listener $NOTILUS"
            else "cmd notification allow_listener $NOTILUS"
        )
    }
}
