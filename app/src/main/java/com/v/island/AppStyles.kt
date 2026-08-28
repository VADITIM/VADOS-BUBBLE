package com.v.island

/**
 * Per-app presentation for the apps this phone actually gets notifications from.
 * The colour lives here and travels to the bubble as data, so the markup never
 * names an app's brand colour.
 *
 * Each app will grow its own layout later: what matters in a train notification is
 * not what matters in a chat one. The key is what the bubble switches its layout on.
 */
object AppStyles {

    data class Style(
        val key: String,
        val label: String,
        val packageName: String,
        val accent: String
    )

    /**
     * Anything with no colour of its own is white. It used to borrow the phone's own
     * accent, which read as a colour that meant something — a green that said Spotify
     * on a notification that had nothing to do with it.
     */
    val generic = Style("generic", "Generic", "", "#ffffff")

    /** Order is the order the debug screen lists them in. */
    private val styles = listOf(
        Style("telegram", "Telegram", "org.telegram.messenger", "#2ea6ff"),
        Style("whatsapp", "WhatsApp", "com.whatsapp", "#25d366"),
        Style("discord", "Discord", "com.discord", "#5865f2"),
        Style("instagram", "Instagram", "com.instagram.android", "#e1306c"),
        Style("gmail", "Gmail", "com.google.android.gm", "#fbbc04"),
        // Google Blue, because a Google app wears a Google colour — and this one wears the
        // primary rather than a share of the four. The accent is a single value read into
        // border-color, color and background at nine places in pill.css, so a four-colour
        // Google treatment is not a colour change here, it is teaching every one of those
        // sites to take a gradient. Gmail keeps its yellow and is the stated exception.
        Style("wallet", "Google Wallet", "com.google.android.apps.walletnfcrel", "#4285f4"),
        Style("db", "DB Navigator", "de.hafas.android.db", "#ec0016"),
        Style("comdirect", "Comdirect", "de.comdirect.app", "#ffd200"),
        Style("spotify", "Spotify", "com.spotify.music", "#1db954"),
        Style("clock", "Uhr", "com.sec.android.app.clockpackage", "#ff8a00")
    )

    /** Second packages of the same app: the business build, the web build, the token app. */
    private val aliases = mapOf(
        "org.telegram.messenger.web" to "telegram",
        "com.whatsapp.w4b" to "whatsapp",
        "com.instagram.barcelona" to "instagram",
        "com.comdirect.phototan" to "comdirect"
    )

    private val byPackage = styles.associateBy { it.packageName }

    fun of(packageName: String): Style =
        byPackage[packageName]
            ?: aliases[packageName]?.let { key -> styles.first { it.key == key } }
            ?: generic

    fun byKey(key: String): Style? = styles.firstOrNull { it.key == key }

    fun all(): List<Style> = styles
}
