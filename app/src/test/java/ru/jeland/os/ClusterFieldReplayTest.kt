package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

/** Hardware captures and OEM enum extraction are separate, traceable inputs. */
class ClusterFieldReplayTest {
    private fun fixture(name:String)=javaClass.getResource("/cluster-pps/$name")!!.readText()
    private fun panel(text:String):ClusterPanelState {
        val sample=ClusterPpsBatch.parse(text).telemetry(19_900)
        assertNull(sample.error)
        return ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=sample)),null,null,20_000)
    }
    @Test fun exactCarJsonEnvelopeReachesThePanelAndDoesNotTurnServiceBytesIntoEvWarnings() {
        for(name in listOf("field-cold.txt","field-cold-second.txt","field-warm.txt")) {
            val p=panel(fixture(name))
            assertEquals(if(name=="field-warm.txt")800.0 else 900.0,p.rpm!!,.001)
            assertEquals(0.0,p.speed!!,.001)
            assertEquals(if(name=="field-warm.txt")ClusterCoolantCondition.NORMAL else ClusterCoolantCondition.COLD,p.coolantCondition)
            assertEquals(65535,QnxClusterTelemetryDecoder.warnings(ClusterPpsBatch.parse(fixture(name)).warnings.bytes)!!.messageId)
            assertNull(p.message)
            assertTrue(p.lamps.containsAll(setOf(ClusterLamp.POSITION,ClusterLamp.EPB,ClusterLamp.BELT,ClusterLamp.START_STOP_UNAVAILABLE)))
            assertFalse(ClusterLamp.START_STOP_READY in p.lamps)
            assertEquals(setOf(2,9,18,if(name=="field-warm.txt")75 else 31,37,38,42,45,76,82,107),p.indicators!!.map {it.bit}.toSet())
        }
        assertNull(panel(fixture("field-warm.txt")).coolant)
    }
    @Test fun everyNativeGearEnumUsesExpectedLabelsExtractedFromOemIntegerStateRecords() {
        val rows=fixture("oem-gear.csv").lineSequence().drop(1).filter {it.isNotBlank()}.toList()
        assertEquals(72,rows.size)
        for(row in rows) {
            val fields=row.split(',');val raw=fields[0].toInt();val expected=fields[2].ifEmpty {null}
            val wire=List(54){if(it==0)raw else 0}
            val p=panel(fixture("field-warm.txt")+"__JOS_COMPUTER__\n[n]@a006\ndata:json:{\"len\":54,\"data\":$wire}\n__JOS_COMPUTER_EXIT__:0\n")
            assertEquals("OEM raw=$raw, Kanzi state=${fields[1]}",expected,p.gear)
        }
        assertNull(ClusterGear.native(255));assertNull(ClusterGear.native(-1))
    }
    @Test fun androidGearEnumDoesNotBorrowQnxExtendedValues() {
        assertEquals("P",ClusterGear.carInfo(1));assertEquals("R",ClusterGear.carInfo(2))
        assertEquals("N",ClusterGear.carInfo(3));assertEquals("D",ClusterGear.carInfo(4))
        assertNull(ClusterGear.carInfo(5));assertNull(ClusterGear.carInfo(255))
    }
    @Test fun wireMessageIsAt20AndCountAt22NotAt39WhichIsFlashState() {
        val text=fixture("field-warm.txt")
        // Source-backed mutation: OEM warning ID 178 (press brake), NOT claimed as a captured event.
        val message=text.replace("255,255,0,1,1,0,255,255,255","178,0,1,1,1,0,255,255,255")
        val p=panel(message)
        assertEquals(178,p.message!!.id)
        assertEquals("Пожалуйста, нажмите на педаль тормоза",p.message!!.russian)
        assertEquals(panel(text).indicators,p.indicators) // message metadata must never become lamps
        assertNull(panel(text).message)
    }
    @Test fun envelopeLengthDuplicateFieldsAndTruncationAreRejected() {
        val text=fixture("field-warm.txt")
        for(bad in listOf(text.replace("\"len\":54","\"len\":53"),
            text.replace("\"len\":54","\"len\":54,\"len\":54"),text.replace(",62,62]",",62]"))) {
            assertNull(ClusterPpsBatch.parse(bad).gauge.bytes)
        }
    }
    @Test fun everyOemNamedIndicatorSurvivesTheWireLayoutIncludingAutoBeamStates() {
        // RcDataSet(5) source offsets. This is the source ABI fixture, not a new CAN hypothesis.
        val offsets=(0..19).toList()+(29..38).toList()+(43..51).toList()
        for(def in ClusterIndicatorCatalog.definitions) {
            val wire=MutableList(54){0};wire[20]=255;wire[21]=255
            wire[offsets[def.bit/8]]=1 shl (def.bit%8)
            val p=panel(fixture("field-warm.txt").substringBefore("__JOS_WARNINGS__")+
                "__JOS_WARNINGS__\n[n]@a00d\ndata:json:{\"len\":54,\"data\":$wire}\n__JOS_WARNINGS_EXIT__:0\n")
            assertEquals("OEM bit ${def.bit}",listOf(def),p.indicators)
            assertNull(p.message)
        }
        assertEquals(listOf("LED_HMA-Grey","LED_HMA-White","LED_HMA-Yellow"),
            ClusterIndicatorCatalog.definitions.filter {it.bit in 4..6}.map {it.key})
    }

}
