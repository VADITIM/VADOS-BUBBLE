package com.v.island.vas

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

object Palette {
    val ground = Color(0xFF181818)
    val panel = Color(0xD9121212)
    val panelSolid = Color(0xFF121212)
    val sunkenDeep = Color(0xFF0E0E0E)
    val sunken = Color(0xFF1C1C1C)
    val raised = Color(0xFF232323)

    val border = Color(0xFF262626)
    val borderStrong = Color(0xFF2C2C2C)
    val borderControl = Color(0xFF3A3A3A)

    val textPrimary = Color(0xDEFFFFFF)
    val textBright = Color(0xFFF0F0F0)
    val textBody = Color(0xFFD8D8D8)
    val textMuted = Color(0xFF9A9A9A)
    val textLabel = Color(0xFF8A8A8A)
    val textIcon = Color(0xFF6A6A6A)
    val textFaint = Color(0xFF4A4A4A)

    val danger = Color(0xFFFF6B6B)
    val dangerHard = Color(0xFFDC143C)

    val camera = Color(0xFFF09B3A)
    val link = Color(0xFF6FB3FF)
    val markTime = Color(0x525BC4FD)
    val markMoney = Color(0x4DFFDD1B)

    fun parse(css: String?, fallback: Color): Color {
        val value = css?.trim().orEmpty()
        if (!value.startsWith("#")) return fallback
        return runCatching {
            val digits = value.substring(1)
            val expanded = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
            when (expanded.length) {
                6 -> Color(0xFF000000 or expanded.toLong(16))
                8 -> Color(expanded.substring(6, 8).toLong(16).shl(24) or expanded.substring(0, 6).toLong(16))
                else -> fallback
            }
        }.getOrDefault(fallback)
    }
}

val LocalAccent = compositionLocalOf { Color.White }
