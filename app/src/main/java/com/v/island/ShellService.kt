package com.v.island

import android.util.Log
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader






class ShellService : IShellService.Stub() {

    override fun execute(command: String): String {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        process.waitFor()
        return output
    }

    // The AOSP tethering binder tore the Wi-Fi connection down on this radio, so One UI's own ISemWifiManager path is taken; it was looked up by one exact signature with every failure swallowed, which left the button doing nothing at all — each overload is tried now and each refusal is logged.
    override fun setHotspot(isOn: Boolean): Boolean {
        val candidates = runCatching {
            semWifiManager.javaClass.methods.filter { it.name == "setWifiApEnabled" }
        }.onFailure { Log.w(TAG, "no sem_wifi binder", it) }.getOrDefault(emptyList())
        if (candidates.isEmpty()) {
            val related = runCatching {
                semWifiManager.javaClass.methods.filter { it.name.contains("Ap") || it.name.contains("otspot") || it.name.contains("ether") }
                    .joinToString { it.name + "(" + it.parameterTypes.joinToString { type -> type.simpleName } + ")" }
            }.getOrDefault("unreadable")
            Log.w(TAG, "sem_wifi has no setWifiApEnabled; related: $related")
        }
        for (method in candidates) {
            val arguments = method.parameterTypes.map { type ->
                when (type) {
                    Boolean::class.javaPrimitiveType -> isOn
                    String::class.java -> SHELL_PACKAGE
                    else -> null
                }
            }
            val signature = method.parameterTypes.joinToString { it.simpleName }
            val answer = runCatching { method.invoke(semWifiManager, *arguments.toTypedArray()) }
                .onFailure { Log.w(TAG, "setWifiApEnabled($signature) refused", it) }
            if (answer.isFailure) continue
            if (answer.getOrNull() != false) return true
            Log.w(TAG, "setWifiApEnabled($signature) answered false")
        }
        return false
    }

    private val semWifiManager: Any by lazy {
        Class.forName("com.samsung.android.wifi.ISemWifiManager\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java)
            .invoke(null, SystemServiceHelper.getSystemService(SEM_WIFI_SERVICE))
    }

    /* A brightness written to settings is not applied, it is glided to: One UI ramps the panel towards it at `rampSpeed` 0.7 of the range a second, so a drag across the bar trailed the finger by a second however fast the writes landed. A temporary brightness is what SystemUI's own slider sends while it is being dragged, and the display applies it with no ramp; the final one is written once on the lift, which is what persists it and clears the temporary. Both need CONTROL_DISPLAY_BRIGHTNESS, which shell holds and this app cannot. */
    override fun setBrightness(brightness: Float, isFinal: Boolean): Boolean = runCatching {
        val method = if (isFinal) persistBrightness else temporaryBrightness
        method.invoke(displayManager, DEFAULT_DISPLAY, brightness)
        true
    }.getOrDefault(false)

    // Looked up once: the drag calls this at the display's rate, and a class lookup and two method lookups per frame is reflection standing between the finger and the panel.
    private val displayManager: Any by lazy {
        Class.forName("android.hardware.display.IDisplayManager\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java)
            .invoke(null, SystemServiceHelper.getSystemService(DISPLAY_SERVICE))
    }
    private val temporaryBrightness by lazy { brightnessMethod("setTemporaryBrightness") }
    private val persistBrightness by lazy { brightnessMethod("setBrightness") }

    private fun brightnessMethod(name: String) = displayManager.javaClass
        .getMethod(name, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)

    private companion object {
        const val DISPLAY_SERVICE = "display"
        const val DEFAULT_DISPLAY = 0
        const val SEM_WIFI_SERVICE = "sem_wifi"
        const val SHELL_PACKAGE = "com.android.shell"
        const val TAG = "IslandBubble"
    }
}
