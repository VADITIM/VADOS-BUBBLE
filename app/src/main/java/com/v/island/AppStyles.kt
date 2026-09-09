package com.v.island

import android.content.Context
import android.content.pm.PackageManager

/**
 * Per-app presentation for the apps this phone actually gets notifications from.
 * The colour lives here and travels to the bubble as data, so the markup never
 * names an app's brand colour.
 *
 * Each app will grow its own layout later: what matters in a train notification is
 * not what matters in a chat one. The key is what the bubble switches its layout on.
 */
object AppStyles {

    /**
     * @param accent the one colour the interface threads through borders, glyphs and marks.
     * @param gradient the CSS gradient for an identity that is genuinely several colours —
     *   Google's four. Optional, and only the two places that can take one use it: the mark
     *   beside a notification row and the app's name. Everything else reads [accent], because
     *   a border-color and an SVG fill cannot take a gradient without being rewritten.
     */
    data class Style(
        val key: String,
        val label: String,
        val packageName: String,
        val accent: String,
        val gradient: String? = null
    )

    /**
     * Anything with no colour of its own is white. It used to borrow the phone's own
     * accent, which read as a colour that meant something — a green that said Spotify
     * on a notification that had nothing to do with it.
     */
    val generic = Style("generic", "Generic", "", "#ffffff")

    /**
     * Google's four, left to right as Google writes them. Shared, because every Google app but
     * Gmail wears it — Gmail is the stated exception and keeps its own colour.
     */
    private const val GOOGLE =
        "linear-gradient(100deg, #4285f4 0%, #ea4335 34%, #fbbc04 67%, #34a853 100%)"

    /** Order is the order the debug screen lists them in. */
    private val styles = listOf(
        Style("telegram", "Telegram", "org.telegram.messenger", "#2ea6ff"),
        Style("whatsapp", "WhatsApp", "com.whatsapp", "#25d366"),
        Style("discord", "Discord", "com.discord", "#5865f2"),
        Style("instagram", "Instagram", "com.instagram.android", "#e1306c"),
        // Gmail is the stated exception and wears its own product red rather than the four.
        // It used to be #fbbc04, which is Google yellow — so of all the Google apps it was the
        // only one already wearing a Google colour, and the one that was asked not to.
        Style("gmail", "Gmail", "com.google.android.gm", "#ea4335"),
        // The four Google colours, in their own order — blue, red, yellow, green. A Google app
        // wears Google's identity and that identity is not one colour, so the flat blue it had
        // was the compromise rather than the answer. The accent stays blue for the places that
        // can only take one colour; the gradient is what the name and the mark wear.
        Style(
            "wallet", "Google Wallet", "com.google.android.apps.walletnfcrel", "#4285f4",
            GOOGLE
        ),
        // Every Google app that actually notifies, wearing the same treatment Wallet does: a
        // flat accent for the nine places in the CSS that can only take one colour, and the
        // four-colour gradient for the two that can take more. A Google app wears Google's
        // identity, and that identity is not one colour — the flat accent is each app's own
        // primary so the two readings agree where only one of them fits.
        Style("calendar", "Google Calendar", "com.google.android.calendar", "#4285f4", GOOGLE),
        Style("drive", "Google Drive", "com.google.android.apps.docs", "#34a853", GOOGLE),
        Style("photos", "Google Photos", "com.google.android.apps.photos", "#4285f4", GOOGLE),
        Style("keep", "Google Keep", "com.google.android.keep", "#fbbc04", GOOGLE),
        Style("maps", "Google Maps", "com.google.android.apps.maps", "#34a853", GOOGLE),
        Style("db", "DB Navigator", "de.hafas.android.db", "#ec0016"),
        // Green on white is the app's own identity, and a shared calendar is the one thing on
        // this phone whose notifications are somebody else writing in your day.
        Style("timetree", "TimeTree", "works.jubilee.timetree", "#2ecc71"),
        Style("comdirect", "Comdirect", "de.comdirect.app", "#ffd200"),
        Style("spotify", "Spotify", "com.spotify.music", "#1db954"),
        Style("clock", "Clock", "com.sec.android.app.clockpackage", "#ff8a00"),
        // Gemini is the second identity here that is genuinely several colours rather than one, so
        // it is written the way Google's four are: its own ramp for the two places that can take a
        // gradient, and the blue it starts from for the nine that cannot. It is not given GOOGLE —
        // Gemini wears its own mark everywhere Google draws it, which is the whole point of it.
        Style(
            "gemini", "Gemini", "com.google.android.apps.bard", "#4285f4",
            "linear-gradient(100deg, #4285f4 0%, #9b72cb 52%, #d96570 100%)"
        ),
        Style("claude", "Claude", "com.anthropic.claude", "#d97757"),
        Style("chatgpt", "ChatGPT", "com.openai.chatgpt", "#10a37f"),
        Style("reddit", "Reddit", "com.reddit.frontpage", "#ff4500"),
        Style("vinted", "Vinted", "fr.vinted", "#09b1ba"),
        Style("kleinanzeigen", "Kleinanzeigen", "com.ebay.kleinanzeigen", "#86bd3a")
    )

    /** Second packages of the same app: the business build, the web build, the token app. */
    private val aliases = mapOf(
        "org.telegram.messenger.web" to "telegram",
        "com.whatsapp.w4b" to "whatsapp",
        "com.instagram.barcelona" to "instagram",
        "com.comdirect.phototan" to "comdirect"
    )

    private val byPackage = styles.associateBy { it.packageName }

    /**
     * The phone's own package list, so an app nobody named here can still be asked what colour it
     * is. It is handed over once rather than passed to every `of()` — the watchers call this from
     * eight places and none of them have a Context to spare, and this object is already the one
     * place presentation lives.
     */
    private var packages: PackageManager? = null

    fun learnFrom(context: Context) {
        if (packages == null) packages = context.applicationContext.packageManager
    }

    /**
     * The style an app wears. Named first, then whatever its own icon says, and white only for an
     * app with no icon or an icon with no colour in it at all — a phone where every unnamed app is
     * the same white is a bubble that says an app arrived and never which one.
     */
    fun of(packageName: String): Style =
        byPackage[packageName]
            ?: aliases[packageName]?.let { key -> styles.first { it.key == key } }
            ?: fromIcon(packageName)
            ?: generic

    private fun fromIcon(packageName: String): Style? {
        val colour = packages?.let { IconColour.of(it, packageName) } ?: return null
        // Keyed by package rather than by a name of ours: the key is what the page switches a
        // layout on, and an app that was never given one has no layout to switch to.
        return Style(generic.key, packageName, packageName, colour)
    }

    fun byKey(key: String): Style? = styles.firstOrNull { it.key == key }

    fun all(): List<Style> = styles
}
