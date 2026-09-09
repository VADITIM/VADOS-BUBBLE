package com.v.island

import android.app.Activity
import android.os.Bundle

/**
 * A launcher entry whose whole job is to open the Status bubble's quick settings, so that a
 * *gesture* can. One UI's own diagonal swipe from the right opens SystemUI's quick settings and
 * there is no way to point it at ours — nothing unprivileged can replace that panel, and hiding the
 * real bar (`StatusBarPolicy`) only takes the bar away, it does not hand the gesture over. What
 * every gesture host on this phone can do instead is *open an app*, so the panel is given an app to
 * be: this activity appears in the launcher, a gesture binding picks it like any other, and opening
 * it toggles the panel.
 *
 * It is a trampoline and nothing else. Translucent, so the app behind stays drawn and is never
 * stopped; `noHistory` and `excludeFromRecents` in the manifest, so it leaves nothing behind to
 * swipe away; finished inside `onCreate` with both window animations killed, because the panel's
 * own arrival is the animation and a window fading in under it is a second, wrong one.
 *
 * With the bubbles not running there is nothing to toggle and it does nothing, which is the honest
 * answer — the service is what draws every window, and an activity of ours cannot start it.
 */
class StatusPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BubbleService.toggleStatusPanel()
        finish()
        // Deprecated in 34 for `overrideActivityTransition`, which needs a version guard this
        // project does not want for a phone that is on 36 — the deprecated call still runs.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
