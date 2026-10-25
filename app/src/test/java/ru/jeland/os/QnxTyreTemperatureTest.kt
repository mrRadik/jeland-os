package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class QnxTyreTemperatureTest {
    private fun bytes(index:Int,raw:Int)=listOf(0,0,index,raw,180,180,180,180)
    @Test fun firmwareMultiplexOrderAndSignedScale() {
        val wheels=listOf(Wheel.FRONT_LEFT,Wheel.FRONT_RIGHT,Wheel.REAR_LEFT,Wheel.REAR_RIGHT)
        wheels.forEachIndexed {index,wheel->
            val f=QnxTyreTemperatureDecoder.decode(bytes(index,80))!!
            assertEquals(wheel,f.wheel);assertEquals(12,f.celsius)
        }
        assertEquals(-14,QnxTyreTemperatureDecoder.decode(bytes(0,40))!!.celsius)
        assertEquals(-40,QnxTyreTemperatureDecoder.decode(bytes(0,0))!!.celsius)
        assertEquals(7,QnxTyreTemperatureDecoder.decode(bytes(0,73))!!.celsius)
        assertEquals(125,QnxTyreTemperatureDecoder.decode(bytes(0,254))!!.celsius)
    }
    @Test fun exactPpsObjectAndSchemaOnly() {
        assertEquals(12,QnxTyreTemperatureDecoder.parse("@a005\ndata:json:[0,0,0,80,180,180,180,180]\n")!!.celsius)
        for(text in listOf("@a004\ndata:json:[0,0,0,80,180,180,180,180]","@a005\ndata::80","@a005\ndata:json:[80,80,80,80]",
            "@a005\ndata:json:[0,0,0,-1,180,180,180,180]","@a005\ndata:json:[0,0,0,256,180,180,180,180]",
            "@a005\ndata:json:[0,0,0,80,180,180,180,180]\ndata:json:[0]","@a005\u0000\ndata:json:[0,0,0,80,180,180,180,180]"))assertNull(text,QnxTyreTemperatureDecoder.parse(text))
        assertNull(QnxTyreTemperatureDecoder.parse("x".repeat(2049)))
        assertNull(QnxTyreTemperatureDecoder.decode(listOf(168,166,166,164))) // current Android pressure, NOT temperature
        assertNull(QnxTyreTemperatureDecoder.decode(List(8){0}))
        assertNull(QnxTyreTemperatureDecoder.decode(List(8){255}))
    }
    @Test fun perWheelInvalidFlagsAndRawSentinelAreWithheld() {
        for((index,flags) in listOf(0 to listOf(12,0),1 to listOf(1,128),2 to listOf(0,48),3 to listOf(0,6))) {
            val b=bytes(index,80).toMutableList();b[0]=flags[0];b[1]=flags[1]
            assertNull(QnxTyreTemperatureDecoder.decode(b)!!.celsius)
        }
        assertNull(QnxTyreTemperatureDecoder.decode(bytes(1,255))!!.celsius)
        for((index,flags) in listOf(0 to listOf(14,0),1 to listOf(1,192),2 to listOf(0,56),3 to listOf(0,7))) {
            val b=bytes(index,80).toMutableList();b[0]=flags[0];b[1]=flags[1]
            assertNull(QnxTyreTemperatureDecoder.decode(b)!!.celsius)
        }
    }
    @Test fun cacheAndReducerNeverInventUnseenWheelsOrKeepAcrossReconnect() {
        val r=LiveVehicleReducer();val key=VdsKey(VerifiedVehicleProtocol.INFO,140)
        r.accept(key,listOf(180,180,180,180),100)
        assertTrue(r.vehicle(100).tires.values.all {it.temperature==null})
        val f=QnxTyreTemperatureDecoder.decode(bytes(0,80))!!
        r.acceptTyreTemperature(f,100)
        assertEquals(12,r.vehicle(100).tires[Wheel.FRONT_LEFT]!!.temperature)
        assertNull(r.vehicle(100).tires[Wheel.FRONT_RIGHT]!!.temperature)
        assertNotNull(r.vehicle(100).tires[Wheel.FRONT_LEFT]!!.bar)
        assertEquals(12,r.vehicle(30_100).tires[Wheel.FRONT_LEFT]!!.temperature)
        assertTrue(r.vehicle(30_101).tires.isEmpty())
        r.acceptTyreTemperature(f,100);assertTrue(r.vehicle(99).tires.isEmpty())
        r.acceptTyreTemperature(f.copy(raw=255),101)
        assertTrue(r.vehicle(101).tires.values.all {it.temperature==null})
        assertNotNull(r.vehicle(101).tires[Wheel.FRONT_LEFT]!!.bar) // pressure must survive invalid temperature
        r.acceptTyreTemperature(f,200);r.clear();assertTrue(r.vehicle(200).tires.isEmpty())
    }
}
