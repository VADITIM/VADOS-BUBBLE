package com.v.island

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Serves `assets/` over a virtual https origin so the page can be ES modules.
 *
 * Chromium gives every `file://` document its own opaque origin and then refuses the module
 * fetches an import graph is made of, so a page loaded from `file:///android_asset/` can carry
 * inline script and nothing else — which is why `pill.html` was one 6,000-line file for as long
 * as it was loaded that way. `appassets.androidplatform.net` is the domain reserved for exactly
 * this and resolves nowhere on the internet, so a request that misses the interceptor fails
 * rather than reaching a network, and every asset is same-origin with the page that asks for it.
 */
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
                // A mistyped import is otherwise a silently dead page, and this is the log every
                // other console line from the interface already goes to.
                Log.w("IslandBubble", "asset not found: $path")
                null
            }
        }
    }
}
