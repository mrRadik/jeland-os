package ru.jeland.os.core

fun validCoordinates(latitude:Double,longitude:Double)=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0
