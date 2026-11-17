package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.RoadAlertSymbol

class RoadAlertSymbolsTest {
    // Labels from the rs* string resources of official Strelka 25.01.1s, APK SHA-256:
    // 25908facb6b84a74e04c266115d0a68e0aaa71212550016f5a2687f6d0c9fcc7.
    @Test fun officialLabelsWithTheActualNotificationSpeedSuffixSelectTheExpectedGlyph() {
        val cases=mapOf("СТРЕЛКА" to "CAMERA","КАМЕРА" to "CAMERA","МУЛЯЖ" to "CAMERA",
            "КОНТРОЛЬ" to "CONTROL","ПОЛОСА" to "LANE","ПЕРЕЕЗД" to "RAILWAY",
            "ПЕРЕКРЁСТ" to "INTERSECTION","ЗАСАДА" to "MOBILE","НЕРОВН." to "BUMP",
            "ПЕРЕХОД" to "CROSSING","ПОВОРОТ" to "TURN","ТОННЕЛЬ" to "TUNNEL","ОПАСНО" to "DANGER",
            "ПОСТ ДПС" to "POLICE","НАС.ПУНКТ" to "TOWN","КОНЕЦ Н.П." to "TOWN_END",
            "ОСТ.ЗАПР." to "NO_STOPPING","ПЛАТОН" to "TRUCK","ВЕС.КОНТ." to "WEIGHT",
            "УЧАСТОК" to "SECTION","СТОП ЛИН" to "STOP_LINE","ПОРОГ" to "THRESHOLD","ФИНИШ" to "FINISH")
        for((label,kind) in cases)for(suffix in listOf(""," 80км/ч"," 0км/ч"))
            assertEquals(label+suffix,kind,RoadAlertSymbol.fromTitle(label+suffix).name)
    }
    @Test fun unknownTitlesAndAddressesAreNeverClassifiedBySubstring() {
        for(title in listOf("ОБЪЕКТ 80км/ч","ул. Переходная","КАМЕРА на улице Ленина","ПОЛОСА 60", "СТРЕЛКА80км/ч", "", "ГОРОД 0км/ч", "НАСТРОЙКИ 0км/ч"))
            assertEquals(title,RoadAlertSymbol.OTHER,RoadAlertSymbol.fromTitle(title))
    }
    @Test fun whitespaceAndCaseDoNotChangeTheOfficialObjectLabel() {
        assertEquals(RoadAlertSymbol.CAMERA,RoadAlertSymbol.fromTitle("  стрелка\u00a0 80км/ч "))
        assertEquals(RoadAlertSymbol.POLICE,RoadAlertSymbol.fromTitle(" пост    дпс 0 км/ч"))
        assertEquals(RoadAlertSymbol.CROSSING,RoadAlertSymbol.fromTitle("ПЕРЕХОД\n0км/ч"))
    }
    @Test fun cameraLimitUsesOnlyTheSpeedValueOfKnownCameraLabels() {
        assertEquals(80,RoadAlertSymbol.cameraSpeedLimit("СТРЕЛКА 80км/ч"))
        assertEquals(130,RoadAlertSymbol.cameraSpeedLimit("КАМЕРА 130км/ч"))
        assertEquals(60,RoadAlertSymbol.cameraSpeedLimit("КОНТРОЛЬ 60 км/ч"))
        assertEquals(90,RoadAlertSymbol.cameraSpeedLimit("УЧАСТОК 90км/ч"))
    }
    @Test fun noSignForMissingInvalidOrNonCameraSpeedValues() {
        for(title in listOf("КАМЕРА", "КАМЕРА 0км/ч", "КАМЕРА -80км/ч", "КАМЕРА +80км/ч", "КАМЕРА 80.5км/ч", "КАМЕРА 999км/ч",
            "ПЕРЕХОД 60км/ч", "ОПАСНО 80км/ч", "ПОРОГ 80км/ч", "ПЛАТОН 80км/ч", "ОБЪЕКТ 80км/ч", "КАМЕРА на улице Ленина 80км/ч"))
            assertNull(title,RoadAlertSymbol.cameraSpeedLimit(title))
    }
}
