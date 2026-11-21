package com.v.island.vas

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

enum class Haptic { Tap, Expand, Dismiss, Release, Toggle }

object Haptics {
    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun play(haptic: Haptic) {
        val vibrator = context?.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        val effect = when (haptic) {
            Haptic.Toggle -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.45f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 40)
                .compose()
            Haptic.Tap -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Haptic.Expand -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            Haptic.Dismiss -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            Haptic.Release -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
        vibrator.vibrate(effect)
    }
}
