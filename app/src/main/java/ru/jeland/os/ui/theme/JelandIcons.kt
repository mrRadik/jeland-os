package ru.jeland.os.ui.theme

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/** Standard Lucide pictograms + purpose-drawn automotive symbols, all on a 24-unit / 2-unit grid. */
object JIcons {
    private fun auto(name:String,data:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString(data).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()
    val Home=LucideVectors.House
    val Automations=LucideVectors.Workflow
    val Edit=LucideVectors.Pencil
    val Delete=LucideVectors.Trash
    val MusicNote=LucideVectors.Music
    val Navigation=LucideVectors.Navigation
    val DirectionsCar=LucideVectors.Car
    val Apps=LucideVectors.Apps
    val Settings=LucideVectors.Settings
    val Add=LucideVectors.Plus
    val Remove=LucideVectors.Minus
    val Close=LucideVectors.Close
    val ChevronRight=LucideVectors.Right
    val ChevronLeft=LucideVectors.Left
    val Expand=LucideVectors.Down
    val Fan=LucideVectors.Fan
    val Air=Fan
    val Power=LucideVectors.Power
    val Snowflake=LucideVectors.Snowflake
    val Thermometer=auto("Thermometer","M14,14.76 V4 A2,2 0,0 0,10,4 V14.76 A4,4 0,1 0,14,14.76 M12,9 V18")
    val Fuel=LucideVectors.Fuel
    val Hazard=auto("Hazard","M12,2 L23,22 H1 Z M12,7 L18.5,19 H5.5 Z")
    val Battery=auto("Battery","M5,6 V3 H9 V6 M15,6 V3 H19 V6 M3,6 H21 V21 H3 Z M6,12 H10 M8,10 V14 M14,12 H18")
    val Cloud=LucideVectors.Cloud
    val FavoriteBorder=LucideVectors.Heart
    val Favorite=FavoriteBorder
    val Star=LucideVectors.Star
    val PlayArrow=LucideVectors.Play
    val Pause=LucideVectors.Pause
    val SkipNext=LucideVectors.Next
    val SkipPrevious=LucideVectors.Previous
    val TurnRight=LucideVectors.TurnRight
    val TurnLeft=LucideVectors.TurnLeft
    val Straight=LucideVectors.Straight
    val UTurn=auto("UTurn","M18,21 V8 A6,6 0,0 0,6,8 V15 M2,11 L6,15 L10,11")
    val Roundabout=auto("Roundabout","M15,5 A7,7 0,1 0,19,12 M15,1 V5 H19 M5,18 L2,21 M17,18 L20,21 M12,12 V22")
    val Destination=auto("Destination","M5,22 V3 M5,3 H20 L16,8 L20,13 H5")
    val Search=LucideVectors.Search
    val Refresh=LucideVectors.Refresh
    val Wifi=LucideVectors.Wifi
    val Bluetooth=LucideVectors.Bluetooth
    val WifiOff=LucideVectors.WifiOff
    val Warning=LucideVectors.Warning
    val DarkMode=LucideVectors.Moon
    val LightMode=LucideVectors.Sun
    // Clear automotive silhouettes: separate seat, face/feet airflow, glass heating and defogging.
    private const val SEAT_PROFILE="M4.7,2.5 H6.5 Q7.5,2.5 7.5,3.5 V5 Q7.5,6 6.5,6 H4.7 Q3.7,6 3.7,5 V3.5 Q3.7,2.5 4.7,2.5 Z M4.4,8 H6 Q7,8 7.2,9 L9,16 H19.5 Q21,16 20.5,17.5 L19.3,20.5 H7.5 Q6,20.5 5.7,19 L3.2,9.5 Q2.9,8 4.4,8 Z"
    val Seat=auto("SeatProfile",SEAT_PROFILE)
    val SeatHeating=auto("HeatedSeatProfile",SEAT_PROFILE+" M11,12.5 Q9.7,10.8 11,9 Q12.3,7.2 11,5.5 M15,12.5 Q13.7,10.8 15,9 Q16.3,7.2 15,5.5 M19,12.5 Q17.7,10.8 19,9 Q20.3,7.2 19,5.5")
    val SeatVentilation=auto("VentilatedSeatProfile",SEAT_PROFILE+" M11,5.5 V12 M9.7,10.7 L11,12 L12.3,10.7 M15,5.5 V12 M13.7,10.7 L15,12 L16.3,10.7 M19,5.5 V12 M17.7,10.7 L19,12 L20.3,10.7")
    val AirlineSeatReclineNormal=SeatHeating
    val Heat=auto("Heat","M6,21 Q3,17 6,13 Q9,9 6,5 M12,21 Q9,17 12,13 Q15,9 12,5 M18,21 Q15,17 18,13 Q21,9 18,5 M4,7 L6,5 L8,7 M10,7 L12,5 L14,7 M16,7 L18,5 L20,7")
    val AirFace=auto("AirFace","M20,4 A2,2 0,1 0,16,4 A2,2 0,1 0,20,4 M16,9 L13,16 H8 L5,21 M19,10 L16,21 H11 M2,6 Q5,4 8,6 Q10,7.3 12,6 M2,9 Q5,7 8,9 Q10,10.3 12,9")
    val AirFeet=auto("AirFeet","M20,4 A2,2 0,1 0,16,4 A2,2 0,1 0,20,4 M16,9 L13,16 H8 L5,21 M19,10 L16,21 H11 M2,11 Q1,14 3,16 M1,15 Q1,18 3,19")
    val Windshield=auto("WindshieldOutline","M3,7 Q12,2 21,7 L18,20 H6 Z")
    val Waves=auto("FrontDefog","M3,7 Q12,2 21,7 L18,20 H6 Z M8,17 V10 M6,12 L8,10 L10,12 M12,16 V9 M10,11 L12,9 L14,11 M16,17 V10 M14,12 L16,10 L18,12")
    val RearDefrost=auto("RearGlassHeat","M3,5 H21 V18 H3 Z M7,21 Q4,18 7,14 Q10,10 7,8 M12,21 Q9,18 12,14 Q15,10 12,8 M17,21 Q14,18 17,14 Q20,10 17,8")
    val WindshieldHeat=auto("FrontGlassHeat","M3,6 Q12,1 21,6 L18,17 H6 Z M7,21 Q4,18 7,14 Q10,10 7,8 M12,21 Q9,18 12,14 Q15,10 12,8 M17,21 Q14,18 17,14 Q20,10 17,8")
    // Owner-approved car + return-arrow form; retained, aligned to the new optical stroke weight.
    val Loop=auto("Recirculation","M2.2,11.7 Q2.2,10.3 3.7,10 L6.6,9.5 L9.7,7.1 Q10.4,6.5 11.4,6.5 H15.5 Q16.5,6.5 17.2,7.5 L19.2,10 L20.7,10.3 Q21.8,10.6 21.8,11.8 V14.6 Q21.8,15.3 20.9,15.3 H19.6 Q19.3,17.5 17.3,17.5 Q15.3,17.5 15,15.3 H8.1 Q7.8,17.5 5.8,17.5 Q3.8,17.5 3.5,15.3 H3 Q2.2,15.3 2.2,14.5 Z M8.3,13.1 H12.4 Q15.1,13.1 15.1,11.4 Q15.1,9.8 12.5,9.8 H9 M10.8,8.3 L9,9.8 L10.8,11.3")
    val Steering=auto("HeatedSteering","M19.5,14 A7.5,7.5 0,1 0,4.5,14 A7.5,7.5 0,1 0,19.5,14 M5,12.5 Q12,10 19,12.5 M6,13 L10,16 H14 L18,13 M12,16 V21 M8,1 Q6.5,2.5 8,4 M12,1 Q10.5,2.5 12,4 M16,1 Q14.5,2.5 16,4")
    val Shade=auto("PanoramicShade","M6,3 H18 Q19,3 19.3,5 L21,17 H3 L4.7,5 Q5,3 6,3 Z M4.5,8 H19.5 M4,12 H20 M9,21 H19 M16,18 L19,21 L16,23")
    val Cameraswitch=auto("Camera360","M4,7 H8 L10,4 H14 L16,7 H20 V18 H4 Z M15.5,12.5 A3.5,3.5 0,1 0,8.5,12.5 A3.5,3.5 0,1 0,15.5,12.5 M3,2 L1,4 L3,6 M21,18 L23,20 L21,22")
    val SwapHoriz=auto("FoldingMirrors","M3,7 Q7,5 9,8 V16 H4 Q2,13 3,7 Z M21,7 Q17,5 15,8 V16 H20 Q22,13 21,7 Z M9,14 H15 M9,3 L12,6 L15,3 M12,6 V10")
    val Door=auto("CarDoor","M4,21 V9 L10,3 H18 Q20,3 20,5 V21 Z M7,10 L11,6 H17 V10 Z M15,15 H18")
    val Hood=auto("OpenHood","M4,20 V13 L7,9 H17 L20,13 V20 M4,13 H20 M7,17 H9 M15,17 H17 M7,9 L4,4 H15 L17,9")
    val Trunk=auto("OpenTrunk","M4,20 V13 L7,9 H17 L20,13 V20 M4,13 H20 M7,17 H9 M15,17 H17 M7,9 L9,3 H20 L17,9")
}
