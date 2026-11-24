package ru.jeland.os.ui.cluster

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import ru.jeland.os.core.RoadAlertSymbol
import ru.jeland.os.ui.theme.JIcons

/** Original HU icons. A native warning is used when the HU has no matching road pictogram. */
internal class ClusterRoadAlertSymbols(private val assets:ClusterOemAssets) {
    private val tile=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(c:Canvas,symbol:RoadAlertSymbol,left:Float,centerY:Float,tint:Int,dark:Boolean) {
        val icon=when(symbol) {
            RoadAlertSymbol.CAMERA,RoadAlertSymbol.MOBILE,RoadAlertSymbol.CONTROL,RoadAlertSymbol.SECTION->JIcons.SpeedCamera
            RoadAlertSymbol.TURN->JIcons.TurnRight
            RoadAlertSymbol.FINISH->JIcons.Destination
            else->JIcons.Warning
        }
        tile.color=ColorUtils.setAlphaComponent(tint,if(dark)26 else 18)
        c.drawRoundRect(left,centerY-32f,left+64f,centerY+32f,13f,13f,tile)
        assets.drawIcon(c,icon,left+10f,centerY-22f,44f,tint)
    }
}
