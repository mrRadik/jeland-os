package ru.jeland.os.core

data class ParkedOpeningNotice(val opening:VehicleOpening,val expiresAtMillis:Long)
data class VehicleNoticeState(
    val parked:List<ParkedOpeningNotice> = emptyList(),
    val warningOpenings:Set<VehicleOpening> = emptySet(),
) {
    val persistent:Boolean get()=warningOpenings.isNotEmpty()
    val visible:Boolean get()=persistent||parked.isNotEmpty()
}

/** Pure event reducer: independent expiry for each opening; no UI clock or Android dependency. */
class VehicleNoticeReducer {
    private var previous:Set<VehicleOpening> = emptySet()
    private var hazardLatched=false
    private val deadlines=mutableMapOf<VehicleOpening,Long>()

    fun update(openings:Set<VehicleOpening>,moving:Boolean,nowMillis:Long):VehicleNoticeState {
        deadlines.keys.retainAll(openings)
        deadlines.entries.removeAll {it.value<=nowMillis}
        hazardLatched=when {
            openings.isEmpty()->false
            moving->true
            else->hazardLatched
        }
        if(hazardLatched)deadlines.clear()
        else (openings-previous).forEach {deadlines[it]=nowMillis+PARKED_DURATION_MILLIS}
        previous=openings.toSet()
        return VehicleNoticeState(
            parked=VehicleOpening.entries.mapNotNull {opening->deadlines[opening]?.let {ParkedOpeningNotice(opening,it)}},
            warningOpenings=if(hazardLatched)openings.toSet()else emptySet(),
        )
    }
    companion object {const val PARKED_DURATION_MILLIS=4000L}
}

val VehicleOpening.displayName:String get()=when(this) {
    VehicleOpening.FRONT_LEFT->"Водительская дверь"
    VehicleOpening.FRONT_RIGHT->"Передняя правая дверь"
    VehicleOpening.REAR_LEFT->"Задняя левая дверь"
    VehicleOpening.REAR_RIGHT->"Задняя правая дверь"
    VehicleOpening.HOOD->"Капот"
    VehicleOpening.TRUNK->"Багажник"
}
val VehicleOpening.openEventTitle:String get()=when(this) {
    VehicleOpening.HOOD->"Открыт капот"
    VehicleOpening.TRUNK->"Открыт багажник"
    else->"Открыта ${displayName.lowercase()}"
}
