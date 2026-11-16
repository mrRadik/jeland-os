package ru.jeland.os.ui.cluster

import android.graphics.*
import ru.jeland.os.core.*

/** Common warning rack for every native named signal; messages never hide lamp icons. */
internal class ClusterWarningStrip(private val assets:ClusterOemAssets) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun drawIndicators(c:Canvas,state:ClusterPanelState,text:Int,labels:ClusterLabels,design:ClusterDesign) {
        val native=state.indicators?:ClusterJ7Indicators.fallback(state.lamps)
        ClusterIndicatorLayout.place(ClusterJ7Indicators.presentation(native),false,design).filterNot {it.header}.forEach {slot->
            assets.drawNative(c,slot.indicator,slot.x,slot.y,slot.size,labels.km=="km",text)
        }
    }
}
