package ru.jeland.os.ui.cluster

import android.graphics.*
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import ru.jeland.os.core.RoadAlertSymbol

/** Lucide 0.468.0 (ISC; assets/licenses/lucide.txt) and road pictograms on the same 24-unit grid. */
internal class ClusterRoadAlertSymbols {
    companion object {
    private fun paths(vararg data:String)=data.map {PathParser.createPathFromPathData(it)}
    private val glyphs=mapOf(
        RoadAlertSymbol.CAMERA to paths("M16.75 12h3.632a1 1 0 0 1 .894 1.447l-2.034 4.069a1 1 0 0 1-1.708.134l-2.124-2.97","M17.106 9.053a1 1 0 0 1 .447 1.341l-3.106 6.211a1 1 0 0 1-1.342.447L3.61 12.3a2.92 2.92 0 0 1-1.3-3.91L3.69 5.6a2.92 2.92 0 0 1 3.92-1.3z","M2 19h3.76a2 2 0 0 0 1.8-1.1L9 15","M2 21v-4","M7 9h.01"),
        RoadAlertSymbol.CONTROL to paths("M3 7V5a2 2 0 0 1 2-2h2","M17 3h2a2 2 0 0 1 2 2v2","M21 17v2a2 2 0 0 1-2 2h-2","M7 21H5a2 2 0 0 1-2-2v-2","M7 12h10"),
        RoadAlertSymbol.RAILWAY to paths("M8 3.1V7a4 4 0 0 0 8 0V3.1","m9 15-1-1","m15 15 1-1","M9 19c-2.8 0-5-2.2-5-5v-4a8 8 0 0 1 16 0v4c0 2.8-2.2 5-5 5Z","m8 19-2 3","m16 19 2 3"),
        RoadAlertSymbol.MOBILE to paths("M16.75 12h3.632a1 1 0 0 1 .894 1.447l-2.034 4.069a1 1 0 0 1-1.708.134l-2.124-2.97","M17.106 9.053a1 1 0 0 1 .447 1.341l-3.106 6.211a1 1 0 0 1-1.342.447L3.61 12.3a2.92 2.92 0 0 1-1.3-3.91L3.69 5.6a2.92 2.92 0 0 1 3.92-1.3z","M2 19h3.76a2 2 0 0 0 1.8-1.1L9 15","M2 21v-4","M7 9h.01"),
        RoadAlertSymbol.TURN to paths("M15 14 20 9 15 4","M4 20v-7a4 4 0 0 1 4-4h12"),
        RoadAlertSymbol.TUNNEL to paths("M3 21V12a9 9 0 0 1 18 0v9 M6 21l3-8 M18 21l-3-8 M12 21v-3 M12 15v-2"),
        RoadAlertSymbol.DANGER to paths("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3","M12 9v4","M12 17h.01"),
        RoadAlertSymbol.POLICE to paths("M7 18v-6a5 5 0 1 1 10 0v6","M5 21a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-1a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2z","M21 12h1","M18.5 4.5 18 5","M2 12h1","M12 2v1","m4.929 4.929.707.707","M12 12v6"),
        RoadAlertSymbol.TOWN to paths("M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z","M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2","M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2","M10 6h4","M10 10h4","M10 14h4","M10 18h4"),
        RoadAlertSymbol.TRUCK to paths("M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2","M15 18H9","M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14","M19 18A2 2 0 1 0 15 18A2 2 0 1 0 19 18Z","M9 18A2 2 0 1 0 5 18A2 2 0 1 0 9 18Z"),
        RoadAlertSymbol.WEIGHT to paths("M15 5A3 3 0 1 0 9 5A3 3 0 1 0 15 5Z","M6.5 8a2 2 0 0 0-1.905 1.46L2.1 18.5A2 2 0 0 0 4 21h16a2 2 0 0 0 1.925-2.54L19.4 9.5A2 2 0 0 0 17.48 8Z"),
        RoadAlertSymbol.SECTION to paths("M9 19A3 3 0 1 0 3 19A3 3 0 1 0 9 19Z","M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15","M21 5A3 3 0 1 0 15 5A3 3 0 1 0 21 5Z"),
        RoadAlertSymbol.THRESHOLD to paths("m12 14 4-4","M3.34 19a10 10 0 1 1 17.32 0"),
        RoadAlertSymbol.FINISH to paths("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z","M4 22L4 15"),
        RoadAlertSymbol.OTHER to paths("M22 12A10 10 0 1 0 2 12A10 10 0 1 0 22 12Z","M12 8L12 12","M12 16L12.01 16"),
        RoadAlertSymbol.LANE to paths("M4 3v18 M20 3v18 M12 21v-4 M12 13V4 M8 8l4-4 4 4"),
        RoadAlertSymbol.INTERSECTION to paths("M9 3v6H3 M15 3v6h6 M9 21v-6H3 M15 21v-6h6"),
        RoadAlertSymbol.BUMP to paths("M3 19h18 M3 14h3c2 0 2-5 6-5s4 5 6 5h3"),
        RoadAlertSymbol.CROSSING to paths("M13.8 4a1.8 1.8 0 1 0-3.6 0a1.8 1.8 0 1 0 3.6 0","m12 7-2 5 4 3-1 6 M10 12l-4 5 M12 8l4 4h3 M3 21h4 M17 21h4"),
        RoadAlertSymbol.NO_STOPPING to paths("M22 12a10 10 0 1 0-20 0a10 10 0 1 0 20 0 M5 5l14 14 M5 19 19 5"),
        RoadAlertSymbol.STOP_LINE to paths("M3 5h18 M7 20v-5l2-5h6l2 5v5 M7 15h10 M8 20h2 M14 20h2"),
        RoadAlertSymbol.TOWN_END to paths("M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z","M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2","M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2","M10 6h4","M10 10h4","M10 14h4","M10 18h4","M3 21 21 3"),
    )
    }
    private val tile=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
    }
    fun draw(c:Canvas,symbol:RoadAlertSymbol,left:Float,centerY:Float,tint:Int,dark:Boolean) {
        tile.color=ColorUtils.setAlphaComponent(tint,if(dark)26 else 18)
        c.drawRoundRect(left,centerY-32f,left+64f,centerY+32f,13f,13f,tile)
        c.save();c.translate(left+10f,centerY-22f);c.scale(44f/24f,44f/24f)
        stroke.color=tint
        glyphs.getValue(symbol).forEach {c.drawPath(it,stroke)}
        // Mobile-camera context is explicit in the title; add two small motion marks.
        if(symbol==RoadAlertSymbol.MOBILE) {c.drawLine(15f,22f,21f,22f,stroke);c.drawLine(18f,19f,22f,19f,stroke)}
        c.restore()
    }
}
