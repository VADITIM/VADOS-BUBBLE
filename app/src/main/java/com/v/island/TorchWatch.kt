package com.v.island

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import org.json.JSONObject











object TorchWatch {

    





    const val STEPS = 5

    private var manager: CameraManager? = null
    private var callback: CameraManager.TorchCallback? = null

    
    private var cameraId: String? = null

    
    private var maxLevel = 1

    private var isOn = false
    private var step = STEPS

    
    val isLit: Boolean get() = isOn

    
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
        
        cameras.registerTorchCallback(listener, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    fun stop() {
        callback?.let { listener -> runCatching { manager?.unregisterTorchCallback(listener) } }
        callback = null
        manager = null
        cameraId = null
        isOn = false
    }

    
    fun set(requested: Int) {
        val cameras = manager ?: return
        val id = cameraId ?: return
        val wanted = requested.coerceIn(0, STEPS)
        runCatching {
            when {
                wanted == 0 -> cameras.setTorchMode(id, false)
                
                
                maxLevel <= 1 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                    cameras.setTorchMode(id, true)
                else -> cameras.turnOnTorchWithStrengthLevel(id, toLevel(wanted))
            }
        }.onFailure { android.util.Log.w("IslandBubble", "torch step $wanted refused", it) }
        
        
        if (wanted > 0) step = wanted
    }

    
    private fun state(): JSONObject? {
        if (!isOn) return null
        return JSONObject()
            
            .put("accent", "#ffffff")
            .put("step", step)
            .put("steps", STEPS)
            
            
            .put("dimmable", maxLevel > 1)
    }

    private fun readMaxLevel(cameras: CameraManager, id: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return runCatching {
            cameras.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        }.getOrDefault(1)
    }

    
    private fun toLevel(step: Int): Int =
        ((step * maxLevel + STEPS - 1) / STEPS).coerceIn(1, maxLevel)

    private fun toStep(level: Int): Int =
        if (maxLevel <= 1) STEPS
        else ((level * STEPS + maxLevel - 1) / maxLevel).coerceIn(1, STEPS)
}
