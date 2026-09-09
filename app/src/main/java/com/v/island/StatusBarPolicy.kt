package com.v.island

/**
 * The real status bar, hidden — or given back.
 *
 * `Settings.Global.policy_control` is a hidden AOSP flag SystemUI reads on every window layout:
 * `immersive.status=*` hides the status bar for every app, `null*` puts it back. It needs
 * `WRITE_SECURE_SETTINGS`, which a normal app may not grant itself, so it goes through Shizuku
 * like the heads-up switch does.
 *
 * It is a *reveal*, not a removal. The bar still exists and a swipe from the top edge brings it
 * back momentarily, the way it does in a fullscreen video player — which is the one property
 * this project needs kept, because the shade swipe must never be blocked.
 *
 * **Unverified on this phone, and it may simply not work.** Samsung's SystemUI fork does not
 * always take the AOSP path, and the flag is reported as honoured on some One UI builds and
 * silently ignored on others. So `read()` asks the setting what it says rather than assuming the
 * write took, and the panel shows that answer: if it reads back as hidden while the bar is
 * plainly still there, the mechanism is the thing that failed, and there is no second one —
 * Good Lock's NavStar was checked and rejected, it reports as icons-only rather than a hide.
 */
object StatusBarPolicy {

    const val HIDDEN = "hidden"
    const val SHOWING = "showing"
    const val UNAVAILABLE = "unavailable"

    private const val SETTING = "policy_control"

    /** The status bar alone. The navigation bar is the gesture area and stays where it is. */
    private const val IMMERSIVE = "immersive.status=*"

    fun read(): String {
        val value = ShizukuShell.run("settings get global $SETTING")?.trim() ?: return UNAVAILABLE
        return if (value.contains("immersive.status") || value.contains("immersive.full")) HIDDEN
        else SHOWING
    }

    fun set(isHidden: Boolean) {
        // "null*" rather than deleting the key: that is the documented way to say "no policy",
        // and a deleted key has been seen leaving SystemUI holding the last one it read.
        ShizukuShell.run("settings put global $SETTING ${if (isHidden) IMMERSIVE else "null*"}")
    }
}
