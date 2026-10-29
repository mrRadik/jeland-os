package ru.jeland.os.navigation.comaps

/** CoMaps exposes Distance in its selected display unit, not always meters. */
internal fun coMapsMeters(value:Double,unitOrdinal:Int):Double {
    if(!value.isFinite()||value<0)return 0.0
    return value*when(unitOrdinal){0->1.0;1->1000.0;2->.3048;3->1609.344;else->return 0.0}
}
