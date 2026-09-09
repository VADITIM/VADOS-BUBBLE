package com.v.island

import android.view.View
import java.lang.reflect.Constructor
import java.lang.reflect.Method




















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

    




    fun apply(view: View, radius: Int, cornerRadius: Float): Boolean {
        val api = api() ?: return false
        return runCatching {
            val builder = api.builder.newInstance(api.mode)
            api.setRadius.invoke(builder, radius)
            api.setCorner?.invoke(builder, cornerRadius)
            api.setInfo.invoke(view, api.build.invoke(builder))
        }.isSuccess
    }

    
    fun clear(view: View) {
        val api = api() ?: return
        runCatching { api.setInfo.invoke(view, null) }
    }
}
