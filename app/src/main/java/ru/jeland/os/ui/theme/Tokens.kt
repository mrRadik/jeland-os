package ru.jeland.os.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared type scale. Display text, labels and tracking live here, not in screen palettes. */
object TypeScale {
    val micro=9.sp; val badge=10.sp; val footnote=11.sp; val navigation=13.sp
    val caption=14.sp; val secondary=16.sp; val control=17.sp; val body=18.sp
    val clockSmall=21.sp; val title=22.sp; val labelLine=23.sp; val heading=25.sp
    val temperature=28.sp; val weather=40.sp; val clock=64.sp
    val trackTight=1.sp; val trackNormal=2.sp; val trackWide=4.sp
}
/** Shared inset/gap scale. Component sizes are kept separate from spacing. */
object SpaceScale {
    val hairline=1.dp; val tiny=2.dp; val xs=5.dp; val small=6.dp; val compact=7.dp
    val iconGap=8.dp; val controlGap=10.dp; val pair=12.dp; val inset=14.dp
    val regular=16.dp; val roomy=18.dp; val card=20.dp; val page=22.dp
    val large=24.dp; val railVertical=26.dp; val hero=28.dp; val stack=32.dp
}
object MotionTokens {const val quick=180;const val panel=260;const val door=320}
object ElevationTokens {val panel=8.dp;val control=2.dp;val border=1.dp;val iconStroke=1.55f}
