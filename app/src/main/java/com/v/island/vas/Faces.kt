package com.v.island.vas

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.v.island.R

object Faces {
    val mono = FontFamily(Font(R.font.space_mono))
    val audiowide = FontFamily(Font(R.font.audiowide))
    val wosker = FontFamily(Font(R.font.wosker))
    val striker = FontFamily(Font(R.font.striker))
    val tanker = FontFamily(Font(R.font.tanker))

    /* Mirrors FONT_STACKS in panel.html: the index a font preference holds has to mean the same face on both sides. */
    private val stacks: List<FontFamily> = listOf(
        mono,
        FontFamily.SansSerif,
        audiowide,
        wosker,
        striker,
        FontFamily(Font(R.font.clash_extralight)),
        FontFamily(Font(R.font.clash_light)),
        FontFamily(Font(R.font.clash_regular)),
        FontFamily(Font(R.font.clash_medium)),
        FontFamily(Font(R.font.clash_semibold)),
        FontFamily(Font(R.font.clash_bold)),
        FontFamily(Font(R.font.general_extralight)),
        FontFamily(Font(R.font.general_extralight_italic)),
        FontFamily(Font(R.font.general_light)),
        FontFamily(Font(R.font.general_light_italic)),
        FontFamily(Font(R.font.general_regular)),
        FontFamily(Font(R.font.general_italic)),
        FontFamily(Font(R.font.general_medium)),
        FontFamily(Font(R.font.general_medium_italic)),
        FontFamily(Font(R.font.general_semibold)),
        FontFamily(Font(R.font.general_semibold_italic)),
        FontFamily(Font(R.font.general_bold)),
        FontFamily(Font(R.font.general_bold_italic)),
        tanker,
    )

    fun stack(index: Int): FontFamily = stacks.getOrElse(index) { mono }
}
