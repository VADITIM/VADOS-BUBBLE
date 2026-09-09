package com.v.island

import android.content.Context
import android.content.pm.PackageManager









object AppStyles {

    






    data class Style(
        val key: String,
        val label: String,
        val packageName: String,
        val accent: String,
        val gradient: String? = null
    )

    




    val generic = Style("generic", "Generic", "", "#ffffff")

    



    private const val GOOGLE =
        "linear-gradient(100deg, #4285f4 0%, #ea4335 34%, #fbbc04 67%, #34a853 100%)"

    
    private val styles = listOf(
        Style("telegram", "Telegram", "org.telegram.messenger", "#2ea6ff"),
        Style("whatsapp", "WhatsApp", "com.whatsapp", "#25d366"),
        Style("discord", "Discord", "com.discord", "#5865f2"),
        Style("instagram", "Instagram", "com.instagram.android", "#e1306c"),
        
        
        
        Style("gmail", "Gmail", "com.google.android.gm", "#ea4335"),
        
        
        
        
        Style(
            "wallet", "Google Wallet", "com.google.android.apps.walletnfcrel", "#4285f4",
            GOOGLE
        ),
        
        
        
        
        
        Style("calendar", "Google Calendar", "com.google.android.calendar", "#4285f4", GOOGLE),
        Style("drive", "Google Drive", "com.google.android.apps.docs", "#34a853", GOOGLE),
        Style("photos", "Google Photos", "com.google.android.apps.photos", "#4285f4", GOOGLE),
        Style("keep", "Google Keep", "com.google.android.keep", "#fbbc04", GOOGLE),
        Style("maps", "Google Maps", "com.google.android.apps.maps", "#34a853", GOOGLE),
        Style("db", "DB Navigator", "de.hafas.android.db", "#ec0016"),
        
        
        Style("timetree", "TimeTree", "works.jubilee.timetree", "#2ecc71"),
        Style("comdirect", "Comdirect", "de.comdirect.app", "#ffd200"),
        Style("spotify", "Spotify", "com.spotify.music", "#1db954"),
        Style("clock", "Clock", "com.sec.android.app.clockpackage", "#ff8a00"),
        
        
        
        
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

    
    private val aliases = mapOf(
        "org.telegram.messenger.web" to "telegram",
        "com.whatsapp.w4b" to "whatsapp",
        "com.instagram.barcelona" to "instagram",
        "com.comdirect.phototan" to "comdirect"
    )

    private val byPackage = styles.associateBy { it.packageName }

    





    private var packages: PackageManager? = null

    fun learnFrom(context: Context) {
        if (packages == null) packages = context.applicationContext.packageManager
    }

    




    fun of(packageName: String): Style =
        byPackage[packageName]
            ?: aliases[packageName]?.let { key -> styles.first { it.key == key } }
            ?: fromIcon(packageName)
            ?: generic

    private fun fromIcon(packageName: String): Style? {
        val colour = packages?.let { IconColour.of(it, packageName) } ?: return null
        
        
        return Style(generic.key, packageName, packageName, colour)
    }

    fun byKey(key: String): Style? = styles.firstOrNull { it.key == key }

    fun all(): List<Style> = styles
}
