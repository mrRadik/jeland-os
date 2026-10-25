package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClimatePanelDismissalTest {
    @Test fun onlyOwnRecentNoisyActionCanDismissOnce() {
        val policy=ClimatePanelDismissal()
        assertNull(policy.opened(100))
        val ticket=policy.arm(listOf(VerifiedVehicleProtocol.autoOn()),200)!!
        assertEquals(ticket,policy.opened(210));assertTrue(policy.canDismiss(ticket,210))
        assertNull(policy.opened(220));assertFalse(policy.canDismiss(ticket,1701))
    }
    @Test fun quietActionsNeverArmWindow() {
        for(write in listOf(VerifiedVehicleProtocol.temperature(true,7),VerifiedVehicleProtocol.temperature(false,8),
            VerifiedVehicleProtocol.fan(6),VerifiedVehicleProtocol.binary(18,false),VerifiedVehicleProtocol.binary(24,true),
            VerifiedVehicleProtocol.binary(65,true),VerifiedVehicleProtocol.toggle(80,0),VerifiedVehicleProtocol.shade(Shade.OPEN),VerifiedVehicleProtocol.openCameras())) {
            val policy=ClimatePanelDismissal()
            assertNull(write.toString(),policy.arm(listOf(write),100));assertNull(policy.opened(110))
        }
    }
    @Test fun noisyControlsAndSeatsAreCovered() {
        for(write in listOf(VerifiedVehicleProtocol.binary(1,true),VerifiedVehicleProtocol.autoOn(),VerifiedVehicleProtocol.sync(true),
            VerifiedVehicleProtocol.binary(8,true),VerifiedVehicleProtocol.airflow(0),VerifiedVehicleProtocol.binary(18,true),
            VerifiedVehicleProtocol.seat(95,2),VerifiedVehicleProtocol.rearSeat(RearSeatSide.LEFT,1),VerifiedVehicleProtocol.toggle(160,0))) {
            val policy=ClimatePanelDismissal();assertNotNull(write.toString(),policy.arm(listOf(write),100));assertNotNull(policy.opened(101))
        }
    }
    @Test fun failedOrStoppedSessionCannotCloseAnything() {
        val policy=ClimatePanelDismissal();val ticket=policy.arm(listOf(VerifiedVehicleProtocol.autoOn()),100)!!
        assertNotNull(policy.opened(110));policy.cancel(ticket)
        assertFalse(policy.canDismiss(ticket,120));assertNull(policy.opened(120))
    }
    @Test fun expiredOrSupersededCallbackCannotDismissLaterWindow() {
        val policy=ClimatePanelDismissal();val first=policy.arm(listOf(VerifiedVehicleProtocol.autoOn()),100)!!
        assertNull(policy.opened(1700))
        val second=policy.arm(listOf(VerifiedVehicleProtocol.sync(true)),1800)!!
        policy.cancel(first);assertEquals(second,policy.opened(1810))
        assertFalse(policy.canDismiss(first,1810));assertTrue(policy.canDismiss(second,1810))
    }
}
