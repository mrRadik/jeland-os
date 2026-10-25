package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class NativeCameraRouteTest {
    @Test fun normalArchitectureUsesConfig55And112BitsAsStockLauncher() {
        val zero="00".repeat(8)
        val flag55="00".repeat(4)+"04"+"00".repeat(3)
        val flag112="00".repeat(5)+"80"+"00".repeat(2)
        assertEquals(NativeCameraRoute.RVC,nativeCameraConfiguration("4.0",zero,zero).route)
        assertEquals(1,nativeCameraConfiguration("4.0",flag55,zero).flag55)
        assertEquals(1,nativeCameraConfiguration("4.0",zero,flag112).flag112)
        assertEquals(NativeCameraRoute.CARINFO,nativeCameraConfiguration("4.0",flag55,zero).route)
        assertEquals(NativeCameraRoute.CARINFO,nativeCameraConfiguration("4.0",zero,flag112).route)
    }
    @Test fun eea51UsesDifferentByteAndDoesNotInventUnsupported112() {
        val flag55="00".repeat(10)+"80"+"00".repeat(52)
        val config=nativeCameraConfiguration("5.1",flag55,"FF".repeat(8))
        assertEquals(1,config.flag55);assertNull(config.flag112);assertEquals(NativeCameraRoute.CARINFO,config.route)
        assertNull(nativeCameraConfiguration("5.1",flag55.take(24),null).flag55)
    }
    @Test fun missingMalformedOrTruncatedConfigUsesNativeRvcDefaultNotDoubleSend() {
        for(value in listOf(null,"","0","not hex","GG".repeat(8),"00".repeat(7),"01".repeat(300))) {
            val config=nativeCameraConfiguration("4.0",value,value)
            assertNull(config.flag55);assertNull(config.flag112);assertEquals(NativeCameraRoute.RVC,config.route)
        }
    }
    @Test fun otherBitsDoNotSelectCarInfoRoute() {
        val unrelated="FF".repeat(4)+"FB7F"+"FF".repeat(2)
        val config=nativeCameraConfiguration("4.0",unrelated,unrelated)
        assertEquals(0,config.flag55);assertEquals(0,config.flag112);assertEquals(NativeCameraRoute.RVC,config.route)
    }
}
