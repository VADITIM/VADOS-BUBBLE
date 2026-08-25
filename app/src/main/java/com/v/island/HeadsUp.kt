package com.v.island

/**
 * SystemUI's own pop-ups, handed over to the bubble. A notification still lands in
 * the shade and still reaches the listener — only the banner is gone.
 *
 * One UI pops up twice over: the AOSP heads-up, switched by a global setting, and
 * Samsung's own "brief" pop-up, drawn by Notilus off its own notification listener.
 * Turning off only the first leaves the second on screen, which is exactly what it
 * looked like when this seemed not to work at all.
 *
 * `NotificationAssistantService` would be the surgical way to do this per
 * notification, but it is a @SystemApi and does not exist in the public SDK. Both
 * switches here need a shell UID, so both go through Shizuku.
 */
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
