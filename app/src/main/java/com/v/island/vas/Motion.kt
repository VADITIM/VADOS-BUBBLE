package com.v.island.vas

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object Curves {
    val enter: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val leave: Easing = CubicBezierEasing(0.55f, 0.06f, 0.68f, 0.19f)
    val sweep: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val grow: Easing = CubicBezierEasing(0.22f, 1.12f, 0.36f, 1f)
    val bubble: Easing = CubicBezierEasing(0.2f, 1.7f, 0.35f, 1f)
    val reach: Easing = CubicBezierEasing(0.2f, 1.24f, 0.35f, 1f)
    val settle: Easing = CubicBezierEasing(0.2f, 1.1f, 0.35f, 1f)
    val sling: Easing = CubicBezierEasing(0.1f, 1.45f, 0.3f, 1f)
    val split: Easing = CubicBezierEasing(0.2f, 1.7f, 0.35f, 1f)
    val powerTwoOut: Easing = CubicBezierEasing(0.5f, 1f, 0.89f, 1f)
    val powerTwoIn: Easing = CubicBezierEasing(0.11f, 0f, 0.5f, 0f)
}

object Durations {
    const val GROW = 340
    const val CONTENT_DROP = 200
    const val HOME = 520
    const val HOLD = 350
    const val HOLD_GRACE = 140
    const val POP_STAGGER = 34
    const val POP_OUT = 150
    const val POP_IN_STAGGER = 42
    const val POP_IN = 460
    const val MORPH = 420
    const val SWEEP_CYCLE = 12_000
    const val REVEAL_BAR = 620
    const val REVEAL_TEXT_AT = 283
    const val PRESS_DOWN = 80
    const val PRESS_UP = 220
    const val DASH = 300
    const val COLLAPSE = 340
}
