package ru.jeland.os.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGuidanceTest {
    @Test fun rightTurnCarriesStreet(){assertEquals("Поверните направо · Ленина",guidanceLabel("TURN","RIGHT","Ленина"))}
    @Test fun noMetadataDoesNotInventStraight(){assertEquals("Двигайтесь к выезду",guidanceLabel(null,null,"Двигайтесь к выезду"))}
    @Test fun roundaboutIsNotReportedAsRightTurn(){assertEquals("Круговое движение · Площадь",guidanceLabel("ROUNDABOUT","RIGHT","Площадь"))}
    @Test fun arrivalDoesNotUsePriorDirection(){assertEquals("Вы прибыли",guidanceLabel("ARRIVE","LEFT",""))}
    @Test fun uTurnIsExplicit(){assertEquals("Развернитесь",guidanceLabel("TURN","U_TURN",""))}
    @Test fun completeInstructionIsNotDuplicated(){assertEquals("Поверните налево",guidanceLabel("TURN","LEFT","Поверните налево."))}
    @Test fun spacedDirectionAndLegacyPrefixAreCleaned(){assertEquals("Поверните налево",guidanceLabel("TURN","LEFT","Налево · Поверните на лево."))}
    @Test fun widgetContainsDistanceAndOneAction(){assertEquals("Через 100 м поверните налево",distanceGuidance(100,guidanceLabel("TURN","LEFT","Поверните налево.")))}
    @Test fun immediateTurnDoesNotSayZeroMeters(){assertEquals("Поверните направо",distanceGuidance(0,"Поверните направо"))}
    @Test fun existingDistanceIsNotRepeated(){assertEquals("Через 100 м поверните налево",distanceGuidance(100,"Через 100 м поверните налево"))}
    @Test fun arrivalDoesNotAskForAnotherTurn(){assertEquals("Вы прибыли",distanceGuidance(100,"Вы прибыли"))}
    @Test fun widgetDistanceIsReadableNotMeterByMeter(){assertEquals("Через 130 м поверните направо",distanceGuidance(129,"Поверните направо"))}
}
