package com.v.island

import android.net.wifi.SoftApConfiguration
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

    // The hotspot went through the AOSP tethering binder, and this phone's radio does not declare station-and-AP concurrency to AOSP — so starting it tore the Wi-Fi connection down, and the request died with it. One UI's own tile goes through ISemWifiManager instead, which is the path that honours Wi-Fi sharing and keeps the station up.
    override fun setHotspot(isOn: Boolean): Boolean = runCatching {
        semWifiManager.javaClass
            .getMethod("setWifiApEnabled", SoftApConfiguration::class.java, Boolean::class.javaPrimitiveType)
            .invoke(semWifiManager, null, isOn) as Boolean
    }.getOrDefault(false)

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
    }
}
