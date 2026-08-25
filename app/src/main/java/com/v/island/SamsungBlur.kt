package com.v.island

import android.view.View
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * The blur One UI draws behind its own pop-up menus.
 *
 * AOSP's own way of asking for it — `FLAG_BLUR_BEHIND` with `blurBehindRadius` — is
 * dead on this phone: `dumpsys window` reports `mBlurEnabled=false`, because the
 * build never set `ro.surface_flinger.supports_background_blur` and that property is
 * read-only. Samsung did not drop the effect, they route it through their own
 * framework class instead, and that class is on the device even though no SDK
 * compiles against it. So it is called by reflection, and a phone that does not have
 * it simply gets no blur rather than a crash.
 *
 * The lookups are held onto because the corner radius is re-applied on every frame of
 * the bubble's growth — reflection once per frame at 120Hz is worth avoiding, the
 * same call with the pieces already in hand is not.
 *
 * ponytail: reflection against a vendor API, which can be renamed by any One UI
 * update. It fails soft and logs; if it ever goes away the honest fix is to drop the
 * setting, not to reimplement a blur we cannot sample the wallpaper for.
 */
object SamsungBlur {

    private const val CLASS = "android.view.SemBlurInfo"

    private class Api(
        val builder: Constructor<*>,
        val setRadius: Method,
        val setCorner: Method?,
        val build: Method,
        val setInfo: Method,
        val mode: Int
    )

    /** Resolved once: a phone that does not have the class will not grow it later. */
    private var api: Api? = null
    private var resolved = false

    private fun api(): Api? {
        if (resolved) return api
        resolved = true
        api = runCatching {
            val infoClass = Class.forName(CLASS)
            val builderClass = Class.forName("$CLASS\$Builder")
            Api(
                builder = builderClass.getConstructor(Int::class.javaPrimitiveType),
                setRadius = builderClass.getMethod("setRadius", Int::class.javaPrimitiveType),
                // Not every One UI build carries the corner setter; a square blur is
                // still a blur, so its absence must not take the whole effect down.
                setCorner = runCatching {
                    builderClass.getMethod(
                        "setBackgroundCornerRadius", Float::class.javaPrimitiveType
                    )
                }.getOrNull(),
                build = builderClass.getMethod("build"),
                setInfo = View::class.java.getMethod("semSetBlurInfo", infoClass),
                mode = infoClass.getField("BLUR_MODE_WINDOW").getInt(null)
            )
        }.onFailure {
            android.util.Log.w("IslandBubble", "no Samsung blur on this build", it)
        }.getOrNull()
        return api
    }

    /**
     * @param radius blur radius in pixels.
     * @param cornerRadius rounds the blurred region, so it follows the bubble instead
     *   of showing a blurred rectangle behind it.
     */
    fun apply(view: View, radius: Int, cornerRadius: Float): Boolean {
        val api = api() ?: return false
        return runCatching {
            val builder = api.builder.newInstance(api.mode)
            api.setRadius.invoke(builder, radius)
            api.setCorner?.invoke(builder, cornerRadius)
            api.setInfo.invoke(view, api.build.invoke(builder))
        }.isSuccess
    }

    /** Takes the blur off again — the setting turned back down to zero. */
    fun clear(view: View) {
        val api = api() ?: return
        runCatching { api.setInfo.invoke(view, null) }
    }
}
