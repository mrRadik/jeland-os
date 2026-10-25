package ru.jeland.os.ui

import androidx.compose.ui.graphics.Color
import ru.jeland.os.R

/** Matched scene, relit for day/night; other consumers keep their original artwork. */
fun heroBackgroundResource(dark:Boolean):Int = if(dark) R.drawable.hero_jeland_night_v2 else R.drawable.hero_jeland_day_v2
fun radioIndicatorColor(foreground:Color,active:Boolean):Color = if(active) foreground else foreground.copy(alpha=.35f)
