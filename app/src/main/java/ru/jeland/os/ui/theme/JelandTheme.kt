package ru.jeland.os.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.DashboardStyle

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
    Color(0xFF355C74),Color(0xFFF5FAFF),Color(0xFFBCD3E3),Color(0xFF55D9FF),
    Color(0xFF136CB0),Color(0xFFFFAC66),Color(0xFF65E6BE),Color(0xBB000710),
)
private val Light = JelandColors(
    ThemeBases.lightBackground,Color(0xFFDFEAF1),Color(0xFFF8FBFD),Color(0xFFE1EDF6),
    Color(0xFF91AABC),Color(0xFF0B2335),Color(0xFF3D5669),Color(0xFF006487),
    Color(0xFF1171B6),Color(0xFFC26619),Color(0xFF167862),Color(0x660D2538),
    critical=Color(0xFFBD2735),unknown=Color(0xFF627482),
)
private val MinimalDark=JelandColors(
    background=Color(0xFF050E1B),rail=Color(0xFF081728),panel=Color(0xFF0A1B2E),raised=Color(0xFF16354B),
    outline=Color(0xFF47748B),text=Color(0xFFFFFCF5),muted=Color(0xFFC2D4DE),accent=Color(0xFF72DDE8),
    deep=Color(0xFF125570),warm=Color(0xFFEDC78F),success=Color(0xFF7ED9B0),scrim=Color(0xBB020812),
    unknown=Color(0xFF83939D),critical=Color(0xFFFF777A))
private val MinimalLight=JelandColors(
    background=Color(0xFFF8F3E9),rail=Color(0xFFECE7DB),panel=Color(0xFFFFFDFA),raised=Color(0xFFE4EBEA),
    outline=Color(0xFF93A49F),text=Color(0xFF0A2538),muted=Color(0xFF3E5565),accent=Color(0xFF005B6D),
    deep=Color(0xFF427B91),warm=Color(0xFF85561F),success=Color(0xFF28775F),scrim=Color(0x77102E46),
    unknown=Color(0xFF65777C),critical=Color(0xFFB4333F))
fun jelandPalette(dark:Boolean,style:DashboardStyle=DashboardStyle.CLASSIC):JelandColors=
    if(style==DashboardStyle.MINIMAL)if(dark)MinimalDark else MinimalLight else if(dark)Dark else Light
val LocalJelandColors = staticCompositionLocalOf { Dark }
val LocalDashboardStyle=staticCompositionLocalOf {DashboardStyle.CLASSIC}
object Jeland {
    val animation=MotionTokens
    val elevation=ElevationTokens
    val type=TypeScale
    val space=SpaceScale
    val colors: JelandColors @Composable get() = LocalJelandColors.current
    val style:DashboardStyle @Composable get()=LocalDashboardStyle.current
    val card = RoundedCornerShape(22.dp)
    val control = RoundedCornerShape(14.dp)
    val gap = 16.dp
    val inset = 22.dp
    val railWidth = 136.dp
    val climateHeight = 124.dp
    val headerHeight = 52.dp
    val heading = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=TypeScale.heading)
    val body = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.body)
    val caption = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.caption)
}
@Composable fun JelandTheme(dark: Boolean,style:DashboardStyle=DashboardStyle.CLASSIC, content: @Composable () -> Unit) {
    val c = jelandPalette(dark,style)
    val scheme = if(dark) darkColorScheme(primary=c.accent,background=c.background,surface=c.panel,onSurface=c.text,onBackground=c.text)
        else lightColorScheme(primary=c.accent,background=c.background,surface=c.panel,onSurface=c.text,onBackground=c.text)
    val typography=Typography(
        bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.body,fontWeight=FontWeight.Medium),
        bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.secondary,fontWeight=FontWeight.Medium),
        labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=TypeScale.control,fontWeight=FontWeight.SemiBold))
    CompositionLocalProvider(LocalJelandColors provides c,LocalDashboardStyle provides style) {
        MaterialTheme(colorScheme=scheme,typography=typography,content=content)
    }
}
