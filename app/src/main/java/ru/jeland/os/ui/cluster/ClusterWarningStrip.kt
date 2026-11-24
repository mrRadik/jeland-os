package ru.jeland.os.ui.cluster

import android.graphics.*
import ru.jeland.os.core.*

/** Common warning rack for every native named signal; messages never hide lamp icons. */
internal class ClusterWarningStrip(private val assets:ClusterOemAssets) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun drawIndicators(c:Canvas,state:ClusterPanelState,text:Int,labels:ClusterLabels,design:ClusterDesign,backdrop:Boolean=false,dark:Boolean=true) {
        val native=state.indicators?:ClusterJ7Indicators.fallback(state.lamps)
        ClusterIndicatorLayout.place(ClusterJ7Indicators.presentation(native),false,design).filterNot {it.header}.forEach {slot->
            // Additional lamps may sit over content. Fixed footer slots already have a solid fascia.
            if(backdrop&&slot.y<ClusterIndicatorLayout.FOOTER_TOP) {
                paint.color=if(dark)Color.argb(240,7,19,30)else Color.argb(240,237,245,250)
                c.drawRoundRect(slot.x-5,slot.y-4,slot.x+slot.size+5,slot.y+slot.size+4,8f,8f,paint)
            }
            assets.drawNative(c,slot.indicator,slot.x,slot.y,slot.size,labels.km=="km",text)
        }
    }
}
