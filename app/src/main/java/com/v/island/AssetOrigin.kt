package com.v.island

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient











object AssetOrigin {

    const val ROOT = "https://appassets.androidplatform.net/"

    private const val HOST = "appassets.androidplatform.net"

    private val MIME = mapOf(
        "html" to "text/html",
        "css" to "text/css",
        "js" to "text/javascript",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "otf" to "font/otf",
        "ttf" to "font/ttf",
    )

    fun client(context: Context): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (request.url.host != HOST) return null
            val path = request.url.path?.trimStart('/').orEmpty()
            return try {
                val stream = context.assets.open(path)
                WebResourceResponse(MIME[path.substringAfterLast('.', "")], "utf-8", stream)
            } catch (missing: java.io.IOException) {
                
                
                Log.w("IslandBubble", "asset not found: $path")
                null
            }
        }
    }
}
