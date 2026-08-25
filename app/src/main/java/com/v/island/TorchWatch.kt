package com.v.island

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import org.json.JSONObject

/**
 * The torch — Taschenlampe — as the fourth thing that is simply true: it is lit or
 * it is not, and while it is lit the bubble carries it. Nothing has to be announced
 * and nothing dwells.
 *
 * The state is read from the camera service rather than from this app's own idea of
 * it, so the mod is right whoever turned the light on: the quick settings tile, the
 * bubble, or an app. `registerTorchCallback` reports the current state immediately
 * on registration, which is also how the first reading arrives.
 */
object TorchWatch {

    /**
     * What the bubble offers, whatever the hardware calls its levels. The panel is
     * five steps because five is what a hand can aim at on a bubble; the camera's
     * own maximum is whatever it is and is mapped onto them here, so nothing above
     * this file ever has to know the difference.
     */
    const val STEPS = 5

    private var manager: CameraManager? = null
    private var callback: CameraManager.TorchCallback? = null

    /** The one camera with a flash, chosen once: the torch is not a per-lens thing. */
    private var cameraId: String? = null

    /** 1 on hardware with no strength control at all, which is most of it. */
    private var maxLevel = 1

    private var isOn = false
    private var step = STEPS

    /** Whether the light is lit, for a switch that has to be painted before it is touched. */
    val isLit: Boolean get() = isOn

    /** The step to go back to when the light is switched on rather than dialled. */
    val lastStep: Int get() = step

    fun start(context: Context, onChange: (JSONObject?) -> Unit) {
        if (callback != null) return
        val cameras = context.getSystemService(CameraManager::class.java) ?: return
        manager = cameras
        cameraId = cameras.cameraIdList.firstOrNull { id ->
            runCatching {
                cameras.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        } ?: return
        maxLevel = readMaxLevel(cameras, cameraId!!)

        val listener = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id != cameraId) return
                isOn = enabled
                onChange(state())
            }

            override fun onTorchModeUnavailable(id: String) {
                if (id != cameraId) return
                // The camera has been taken by something else, so there is no torch
                // to carry — and no way to turn one on either.
                isOn = false
                onChange(null)
            }

            override fun onTorchStrengthLevelChanged(id: String, newStrengthLevel: Int) {
                if (id != cameraId) return
                step = toStep(newStrengthLevel)
                if (isOn) onChange(state())
            }
        }
        callback = listener
        // The main looper is where the push into the WebView has to happen anyway.
        cameras.registerTorchCallback(listener, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    fun stop() {
        callback?.let { listener -> runCatching { manager?.unregisterTorchCallback(listener) } }
        callback = null
        manager = null
        cameraId = null
        isOn = false
    }

    /** Step 0 puts the light out; 1 to [STEPS] light it at that share of full. */
    fun set(requested: Int) {
        val cameras = manager ?: return
        val id = cameraId ?: return
        val wanted = requested.coerceIn(0, STEPS)
        runCatching {
            when {
                wanted == 0 -> cameras.setTorchMode(id, false)
                // Below Android 13 there is no strength to ask for, and on hardware
                // with a single level asking for it throws rather than rounding.
                maxLevel <= 1 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                    cameras.setTorchMode(id, true)
                else -> cameras.turnOnTorchWithStrengthLevel(id, toLevel(wanted))
            }
        }.onFailure { android.util.Log.w("IslandBubble", "torch step $wanted refused", it) }
        // The callback reports the mode, and on hardware that reports no strength
        // change the step would otherwise stay at whatever it was before.
        if (wanted > 0) step = wanted
    }

    /** Null while the light is out: the mod is only true while it is lit. */
    private fun state(): JSONObject? {
        if (!isOn) return null
        return JSONObject()
            // The one mod with no app behind it, so it wears the light itself.
            .put("accent", "#ffffff")
            .put("step", step)
            .put("steps", STEPS)
            // A panel of five steps on hardware with one real level would be four
            // switches that do nothing, so the page is told it may only draw one.
            .put("dimmable", maxLevel > 1)
    }

    private fun readMaxLevel(cameras: CameraManager, id: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return runCatching {
            cameras.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        }.getOrDefault(1)
    }

    /** Rounded up, so step 1 is never off and step [STEPS] is always the maximum. */
    private fun toLevel(step: Int): Int =
        ((step * maxLevel + STEPS - 1) / STEPS).coerceIn(1, maxLevel)

    private fun toStep(level: Int): Int =
        if (maxLevel <= 1) STEPS
        else ((level * STEPS + maxLevel - 1) / maxLevel).coerceIn(1, STEPS)
}
