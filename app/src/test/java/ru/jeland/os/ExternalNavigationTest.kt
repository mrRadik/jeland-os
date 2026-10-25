package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ExternalNavigationTest {
    @Test fun coordinatesAreLatitudeThenLongitudeAndRejectInvalidInput() {
        assertEquals(55.16 to 61.4,parseNavigationCoordinates("55.16, 61.40"))
        assertEquals(-33.9 to 151.2,parseNavigationCoordinates("-33.9;151.2"))
        for(input in listOf("91, 0","0,181","NaN,0","Infinity,0","Москва","55,16,61,4","","55.1"))assertNull(input,parseNavigationCoordinates(input))
    }
    @Test fun onlyAllowedPackagesAndRouteNoticesAreAccepted() {
        val r=NavigationNoticeReducer()
        assertFalse(r.posted("bad","other.app","navigation",true,false,"private","100 м","",1))
        assertFalse(r.posted("summary",NavigationProvider.DGIS.packageName,"navigation",true,true,"100 м","","",1))
        assertFalse(r.posted("download",NavigationProvider.DGIS.packageName,"service",true,false,"Загрузка карты","45%","",2))
        assertTrue(r.posted("route",NavigationProvider.DGIS.packageName,"service",true,false,"Через 100 м","Поверните направо","",3))
        assertEquals("Через 100 м",r.current(NavigationProvider.DGIS)!!.title)
        assertNull(r.current(NavigationProvider.YANDEX))
        assertTrue(r.posted("distance",NavigationProvider.DGIS.packageName,"service",true,false,"Через 100 м","","",4))
        assertTrue(r.posted("case",NavigationProvider.DGIS.packageName,"service",true,false,"ПОВЕРНИТЕ НАПРАВО","","",5))
        assertFalse(r.posted("not-distance",NavigationProvider.DGIS.packageName,"service",true,false,"100 материалов","","",6))
    }
    @Test fun updatesRemovalProviderIsolationAndDisconnectClear() {
        val r=NavigationNoticeReducer()
        fun put(key:String,p:NavigationProvider,t:Long)=r.posted(key,p.packageName,"navigation",true,false,"$t м","Улица","",t)
        put("a",NavigationProvider.DGIS,1);put("b",NavigationProvider.DGIS,2);put("y",NavigationProvider.YANDEX,3)
        assertEquals("b",r.current(NavigationProvider.DGIS)!!.key)
        r.removed("a");assertEquals("b",r.current(NavigationProvider.DGIS)!!.key)
        r.removed("b");assertNull(r.current(NavigationProvider.DGIS));assertNotNull(r.current(NavigationProvider.YANDEX))
        r.clear();assertNull(r.current(NavigationProvider.YANDEX))
    }
    @Test fun revokedRouteNoticeCannotLeavePreviousGuidanceBehind() {
        val r=NavigationNoticeReducer()
        r.posted("a",NavigationProvider.DGIS.packageName,"navigation",true,false,"100 м","Направо","",1)
        assertFalse(r.posted("a",NavigationProvider.DGIS.packageName,"service",true,false,"Загрузка карты","10%","",2))
        assertNull(r.current(NavigationProvider.DGIS))
    }
    @Test fun textIsBoundedAndNoDistanceOrEtaIsInvented() {
        val r=NavigationNoticeReducer()
        r.posted("a",NavigationProvider.YANDEX.packageName,"navigation",true,false,"Съезд\nналево","x".repeat(500),"",1)
        val g=r.current(NavigationProvider.YANDEX)!!
        assertEquals("Съезд налево",g.title);assertEquals(240,g.text.length)
    }
}
