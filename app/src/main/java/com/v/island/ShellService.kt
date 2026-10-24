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

    // One UI's own ISemWifiManager call keeps the Wi-Fi station up, but it answered shell with a refusal nobody could see, so the button did nothing; the AOSP tethering service, which shell holds TETHER_PRIVILEGED for, is the fallback, and whatever both refused is handed back for the Debug screen.
    override fun setHotspot(isOn: Boolean): String {
        val refusals = mutableListOf<String>()
        if (setSemHotspot(isOn, refusals)) return ""
        if (setTethering(isOn, refusals)) return ""
        refusals.forEach { Log.w(TAG, it) }
        return refusals.joinToString("\n")
    }

    private fun setSemHotspot(isOn: Boolean, refusals: MutableList<String>): Boolean {
        val candidates = runCatching {
            semWifiManager.javaClass.methods.filter { it.name == "setWifiApEnabled" }
        }.getOrElse {
            refusals += "sem_wifi: ${it.reason()}"
            return false
        }
        if (candidates.isEmpty()) refusals += "sem_wifi has no setWifiApEnabled"
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
            if (answer.isFailure) {
                refusals += "sem setWifiApEnabled($signature): ${answer.exceptionOrNull()!!.reason()}"
                continue
            }
            if (answer.getOrNull() != false) return true
            refusals += "sem setWifiApEnabled($signature) answered false"
        }
        return false
    }

    private fun setTethering(isOn: Boolean, refusals: MutableList<String>): Boolean {
        val answer = java.util.concurrent.LinkedBlockingQueue<Int>()
        val listener = object : android.os.Binder() {
            override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                if (code != android.os.IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(RESULT_LISTENER)
                answer.offer(data.readInt())
                return true
            }
        }
        val result = runCatching {
            val connector = Class.forName("android.net.ITetheringConnector\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, SystemServiceHelper.getSystemService(TETHERING_SERVICE))!!
            val proxy = Class.forName("$RESULT_LISTENER\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, listener)
            val name = if (isOn) "startTethering" else "stopTethering"
            val method = connector.javaClass.methods.first { it.name == name }
            var strings = 0
            val arguments = method.parameterTypes.map { type ->
                when {
                    type == Int::class.javaPrimitiveType -> TETHERING_WIFI
                    type == String::class.java -> if (strings++ == 0) SHELL_PACKAGE else null
                    type.name.endsWith("IIntResultListener") -> proxy
                    else -> tetheringRequest(type)
                }
            }
            method.invoke(connector, *arguments.toTypedArray())
            answer.poll(TETHERING_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        }
        val code = result.getOrElse {
            refusals += "tethering: ${it.reason()}"
            return false
        }
        if (code == 0) return true
        refusals += if (code == null) "tethering never answered" else "tethering answered error $code"
        return false
    }

    // Android 16 takes the TetheringRequest itself where older releases took its parcel, so the parameter's own type says which one is wanted.
    private fun tetheringRequest(type: Class<*>): Any {
        val request = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            .getConstructor(Int::class.javaPrimitiveType)
            .newInstance(TETHERING_WIFI)
            .let { it.javaClass.getMethod("build").invoke(it)!! }
        if (type.isInstance(request)) return request
        return request.javaClass.getMethod("getParcel").invoke(request)!!
    }

    private fun Throwable.reason(): String {
        val cause = (this as? java.lang.reflect.InvocationTargetException)?.targetException ?: this
        return cause.javaClass.simpleName + (cause.message?.let { ": $it" } ?: "")
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
        const val TETHERING_SERVICE = "tethering"
        const val TETHERING_WIFI = 0
        const val TETHERING_WAIT_SECONDS = 5L
        const val RESULT_LISTENER = "android.net.IIntResultListener"
        const val TAG = "IslandBubble"
    }
}
