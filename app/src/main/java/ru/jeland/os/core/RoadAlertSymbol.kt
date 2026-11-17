package ru.jeland.os.core

import java.util.Locale

/** Presentation glyphs for exact labels in official Strelka 25.01.1s notifications.
 * Never classify the address/comment, guess a camera model, or infer a road limit from unrelated warnings.
 */
enum class RoadAlertSymbol {
    CAMERA, CONTROL, LANE, RAILWAY, INTERSECTION, MOBILE, BUMP, CROSSING, TURN, TUNNEL,
    DANGER, POLICE, TOWN, TOWN_END, NO_STOPPING, TRUCK, WEIGHT, SECTION, STOP_LINE, THRESHOLD, FINISH, OTHER;
    companion object {
        private val whitespace=Regex("\\s+")
        private val withSpeed=Regex("^(.*?) ([+-]?\\d+(?:[.,]\\d+)?) ?(?:КМ/Ч|KM/H)$")
        private fun normalize(title:String)=title.replace('\u00a0',' ').trim().uppercase(Locale.ROOT).replace(whitespace," ")
        private val cameraKinds=setOf(CAMERA,CONTROL,LANE,INTERSECTION,MOBILE,SECTION,STOP_LINE)
        /** Current camera's value reported by Strelka; not a map-derived or car-set speed limit. */
        fun cameraSpeedLimit(title:String):Int? {
            if(fromTitle(title) !in cameraKinds)return null
            val number=withSpeed.matchEntire(normalize(title))?.groupValues?.get(2)?:return null
            if(number.any {it !in '0'..'9'})return null
            return number.toIntOrNull()?.takeIf {it in 1..300}
        }
        fun fromTitle(title:String):RoadAlertSymbol {
            val normalized=normalize(title)
            val label=withSpeed.matchEntire(normalized)?.groupValues?.get(1)?:normalized
            return when(label) {
                "СТРЕЛКА","КАМЕРА","МУЛЯЖ"->CAMERA
                "КОНТРОЛЬ"->CONTROL
                "ПОЛОСА"->LANE
                "ПЕРЕЕЗД"->RAILWAY
                "ПЕРЕКРЁСТ"->INTERSECTION
                "ЗАСАДА"->MOBILE
                "НЕРОВН."->BUMP
                "ПЕРЕХОД"->CROSSING
                "ПОВОРОТ"->TURN
                "ТОННЕЛЬ"->TUNNEL
                "ОПАСНО"->DANGER
                "ПОСТ ДПС"->POLICE
                "НАС.ПУНКТ"->TOWN
                "КОНЕЦ Н.П."->TOWN_END
                "ОСТ.ЗАПР."->NO_STOPPING
                "ПЛАТОН"->TRUCK
                "ВЕС.КОНТ."->WEIGHT
                "УЧАСТОК"->SECTION
                "СТОП ЛИН"->STOP_LINE
                "ПОРОГ"->THRESHOLD
                "ФИНИШ"->FINISH
                else->OTHER
            }
        }
    }
}
