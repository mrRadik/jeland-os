package ru.jeland.os.ui.cluster

import ru.jeland.os.core.ClusterDesign

/** Shared by the cached housing and live scales; all coordinates use the same 2048×768 viewport. */
internal data class ClusterDial(val x:Float,val y:Float,val rx:Float,val ry:Float)

/** Map body only; the manoeuvre strip sits immediately above it in the native artwork. */
internal data class ClusterMapBounds(val left:Float,val top:Float,val width:Float,val height:Float)
internal fun clusterMapBounds(design:ClusterDesign,guidance:Boolean=true):ClusterMapBounds?=(when(design) {
    ClusterDesign.CLASSIC->ClusterMapBounds(752f,322f,544f,276f)
    ClusterDesign.SPORT->ClusterMapBounds(1368f,212f,498f,398f)
    else->null
})?.let {if(guidance)it else it.copy(top=it.top-46f,height=it.height+46f)}

internal object ClusterGeometry {
    const val INSTRUMENT_TOP=77f
    const val INSTRUMENT_BOTTOM=750f
    private const val CENTER_Y=(INSTRUMENT_TOP+INSTRUMENT_BOTTOM)/2f
    private val classic=listOf(ClusterDial(424f,CENTER_Y,282f,282f),ClusterDial(1624f,CENTER_Y,282f,282f))
    private val sport=listOf(ClusterDial(1024f,393f,300f,300f))
    private val navigation=listOf(ClusterDial(224f,511f,148f,148f),ClusterDial(1824f,511f,148f,148f))
    fun dials(design:ClusterDesign):List<ClusterDial> = when(design) {
        ClusterDesign.CLASSIC->classic
        ClusterDesign.SPORT->sport
        ClusterDesign.NAVIGATION->navigation
        ClusterDesign.ORIGINAL->emptyList()
    }
}


/** All radii are measured from the same dial centre, in fractions of its outer ring. */
internal object ClusterDialMetrics {
    const val FACE=.881f
    const val HUB_RIM=.587f
    const val SCALE_INNER=.605f
    const val SCALE_OUTER=.858f
    const val MAJOR_TICK_INNER=.808f
    const val MINOR_TICK_INNER=.830f
    const val NUMBER_RADIUS=.695f
    const val NUMBER_SIZE=.100f
    const val START=150f
    const val SWEEP=240f
    const val POCKET=.855f
    const val SECONDARY_RADIUS=.790f
    const val SECONDARY_THICKNESS=.038f
    const val SECONDARY_GAP=.008f
    const val SECONDARY_START=120f
    const val SECONDARY_SWEEP=-60f
}

/** Camera limit placement is independent of the warning capsule and numerical readouts. */
internal data class ClusterSpeedLimitCircle(val x:Float,val y:Float,val radius:Float)
internal fun clusterSpeedLimitCircle(design:ClusterDesign):ClusterSpeedLimitCircle?=when(design) {
    ClusterDesign.CLASSIC,ClusterDesign.NAVIGATION->ClusterGeometry.dials(design).first().let {
        ClusterSpeedLimitCircle(it.x,it.y-it.ry*.34f,it.ry*if(design==ClusterDesign.NAVIGATION).16f else .13f)
    }
    ClusterDesign.SPORT->ClusterGeometry.dials(design).single().let {
        ClusterSpeedLimitCircle(it.x,it.y-it.ry*.36f,it.ry*.115f)
    }
    ClusterDesign.ORIGINAL->null
}
