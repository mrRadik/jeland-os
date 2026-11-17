package ru.jeland.os.core

/** J7 has one shared front-seat belt telltale (owner-confirmed 2026-11-11).
 * OEM native table: 107=driver, 108=passenger, 200=copilot variant.
 * Generic multi-row seat pictograms are not separate telltales on this vehicle.
 * Raw decoding retains all original slots; adaptation happens only for presentation.
 */
object ClusterJ7Indicators {
    private val frontBeltBits=setOf(107,108,200)
    // OEM driving-assistance status families. Critical powertrain/brake lamps keep the warning rack.
    private val footerBits=setOf(34,35,38,39,40,41,42,43,44,45,50,51,52,53,54,58,59,60,73,143,146,147,148,149,203,204)
    fun passengerWarning(native:List<ClusterIndicatorSpec>)=native.any {it.bit==108||it.bit==200}
    fun presentation(native:List<ClusterIndicatorSpec>):List<ClusterIndicatorSpec> {
        val frontBelt=native.any {it.bit in frontBeltBits}
        val shared=ClusterIndicatorCatalog.definitions.first {it.bit==107}.copy(captionRu=null,captionEn=null)
        return native.filterNot {it.key.contains("SeatBelt")||it.key.startsWith("LED_DrivingMode")}
            .map {if(it.bit in footerBits)it.copy(area=ClusterIndicatorArea.FOOTER)else it}+if(frontBelt)listOf(shared)else emptyList()
    }
    /** Existing lamp fallback uses the same OEM slots and assets as native packets. */
    fun fallback(lamps:Set<ClusterLamp>):List<ClusterIndicatorSpec> {
        val bits=mapOf(ClusterLamp.POSITION to 2,ClusterLamp.LOW_BEAM to 123,ClusterLamp.HIGH_BEAM to 3,
            ClusterLamp.FRONT_FOG to 84,ClusterLamp.REAR_FOG to 7,ClusterLamp.BELT to 107,
            ClusterLamp.EPB to 18,ClusterLamp.EPB_FAULT to 19,ClusterLamp.AUTO_HOLD to 20,
            ClusterLamp.AUTO_HOLD_READY to 151,ClusterLamp.AUTO_HOLD_FAULT to 152,
            ClusterLamp.START_STOP_READY to 36,ClusterLamp.START_STOP_UNAVAILABLE to 37,ClusterLamp.ICE to 137,
            ClusterLamp.BRAKE to 22,ClusterLamp.OIL_PRESSURE to 32,ClusterLamp.CHARGING to 21,
            ClusterLamp.AIRBAG to 26,ClusterLamp.ABS to 23,ClusterLamp.ESP to 24,ClusterLamp.ESP_OFF to 25,
            ClusterLamp.EPS to 29,ClusterLamp.EPS_RED to 145,ClusterLamp.CHECK_ENGINE to 17,
            ClusterLamp.TPMS to 27,ClusterLamp.EPC to 16,ClusterLamp.GEARBOX to 28,ClusterLamp.GEARBOX_RED to 104,
            ClusterLamp.WASHER to 140,ClusterLamp.HDC to 35,ClusterLamp.HDC_ACTIVE to 34,
            ClusterLamp.LANE_STANDBY to 45,ClusterLamp.LANE_ACTIVE to 43,ClusterLamp.LANE_WARNING to 44,ClusterLamp.LANE_RED to 143,
            ClusterLamp.LDW_STANDBY to 42,ClusterLamp.LDW_ACTIVE to 40,ClusterLamp.LDW_WARNING to 41,
            ClusterLamp.BSD_ACTIVE to 38,ClusterLamp.BSD_WARNING to 39,ClusterLamp.GENERAL_WARNING to 66)
        val active=lamps.mapNotNull {bits[it]}.toSet()
        return ClusterIndicatorCatalog.definitions.filter {it.bit in active}
    }

}
