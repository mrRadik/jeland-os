package ru.jeland.os.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared day/night base surfaces. Hero copy uses the theme's primary text color. */
object ThemeBases {
    val darkBackground = Color(0xFF030B12)
    val lightBackground = Color(0xFFE9F0F5)
}
@Immutable
data class JelandColors(
    val background: Color, val rail: Color, val panel: Color, val raised: Color,
    val outline: Color, val text: Color, val muted: Color, val accent: Color,
    val deep: Color, val warm: Color, val success: Color, val scrim: Color,
    val artText: Color = Color(0xFFEAF6FF), val artMuted: Color = Color(0xFFA8CEE5),
    val map: Color = Color(0xFF071A2A), val road: Color = Color(0xFF29475D),
    val artScrim: Color = Color(0xFF020C17),
    val critical:Color=Color(0xFFFF666E), val unknown:Color=Color(0xFF80909C),
    val heroText:Color=text,
)
private val Dark = JelandColors(
    ThemeBases.darkBackground,Color(0xFF040F19),Color(0xFF081925),Color(0xFF102A3D),
    Color(0xFF28495D),Color(0xFFE7F3FC),Color(0xFF91B5CE),Color(0xFF55D9FF),
    Color(0xFF136CB0),Color(0xFFFFAC66),Color(0xFF65E6BE),Color(0xBB000710),
)
private val Light = JelandColors(
    ThemeBases.lightBackground,Color(0xFFDFEAF1),Color(0xFFF8FBFD),Color(0xFFE1EDF6),
    Color(0xFFAABFCE),Color(0xFF132C3D),Color(0xFF526E83),Color(0xFF007CA8),
    Color(0xFF1171B6),Color(0xFFC26619),Color(0xFF167862),Color(0x660D2538),
    critical=Color(0xFFBD2735),unknown=Color(0xFF627482),
)
fun jelandPalette(dark:Boolean):JelandColors=if(dark)Dark else Light
val LocalJelandColors = staticCompositionLocalOf { Dark }
object Jeland {
    val animation=MotionTokens
    val elevation=ElevationTokens
    val type=TypeScale
    val space=SpaceScale
    val colors: JelandColors @Composable get() = LocalJelandColors.current
    val card = RoundedCornerShape(22.dp)
    val control = RoundedCornerShape(14.dp)
    val gap = 16.dp
    val inset = 22.dp
    val railWidth = 124.dp
    val climateHeight = 104.dp
    val headerHeight = 46.dp
    val heading = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=TypeScale.heading)
    val body = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.body)
    val caption = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.caption)
}
@Composable fun JelandTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = jelandPalette(dark)
    val scheme = if(dark) darkColorScheme(primary=c.accent,background=c.background,surface=c.panel,onSurface=c.text,onBackground=c.text)
        else lightColorScheme(primary=c.accent,background=c.background,surface=c.panel,onSurface=c.text,onBackground=c.text)
    CompositionLocalProvider(LocalJelandColors provides c) {
        MaterialTheme(colorScheme=scheme,content=content)
    }
}
