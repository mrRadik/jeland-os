package ru.jeland.os.core

/** Every named position (130 slots, 126 distinct signals) in the installed SOP4 native lamp table.
 * NULL/reserved slots are not interpreted. No Android fault arrays or guessed bit assignments.
 * OEM resource aliases and missing-image text indications are recorded in native-indicators.json.
 */
enum class ClusterIndicatorArea { LIGHT, STATUS, AUXILIARY, SECONDARY, FOOTER }
data class ClusterIndicatorSpec(
    val bit:Int,val key:String,val asset:String?,val text:String?=null,
    val captionRu:String?=null,val captionEn:String?=null,val fixedTint:Int?=null,
    val priority:Int=3,val area:ClusterIndicatorArea=ClusterIndicatorArea.SECONDARY
)
object ClusterIndicatorCatalog {
    val definitions:List<ClusterIndicatorSpec> = listOf(
        ClusterIndicatorSpec(2,"LED_Position","cluster/oem/indicator/LED_Position.png",null,null,null,null,3,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(3,"LED_HighBeam","cluster/oem/indicator/LED_HighBeam.png",null,null,null,null,3,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(4,"LED_HMA-Grey","cluster/oem/indicator/LED_HMA-Grey.png",null,null,null,null,3,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(5,"LED_HMA-White","cluster/oem/indicator/LED_HMA-White.png",null,null,null,null,3,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(6,"LED_HMA-Yellow","cluster/oem/indicator/LED_HMA-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(7,"LED_RearFog-Yellow","cluster/oem/indicator/LED_RearFog-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(8,"LED_Daytime-Green","cluster/oem/indicator/LED_Daytime-Green.png",null,null,null,null,3,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(9,"LED_SecondLeftSeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"2Л","2L",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(10,"LED_RLSeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"ЗЛ","RL",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(11,"LED_RLSeatBelt-Green","cluster/oem/indicator/LED_SeatBelt-Green.png",null,"ЗЛ","RL",null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(12,"LED_RMSeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"ЗЦ","RM",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(13,"LED_RMSeatBelt-Green","cluster/oem/indicator/LED_SeatBelt-Green.png",null,"ЗЦ","RM",null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(14,"LED_RRSeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"ЗП","RR",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(15,"LED_RRSeatBelt-Green","cluster/oem/indicator/LED_SeatBelt-Green.png",null,"ЗП","RR",null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(16,"LED_EPC-Yellow","cluster/oem/indicator/LED_EPC-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(17,"LED_MILEmission-Yellow","cluster/oem/indicator/LED_MILEmission-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(18,"LED_EPB-Red","cluster/oem/indicator/LED_EPB-Red.png",null,null,null,null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(19,"LED_EPB-Yellow","cluster/oem/indicator/LED_EPB-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(20,"LED_AVH-Green","cluster/oem/indicator/LED_AVH-Green.png",null,null,null,null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(21,"LED_Charging-Red","cluster/oem/indicator/LED_Charging-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(22,"LED_BrakeFluidLevel-Red","cluster/oem/indicator/LED_BrakeFluidLevel-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(23,"LED_ABS-Yellow","cluster/oem/indicator/LED_ABS-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(24,"LED_ElecStability-Yellow","cluster/oem/indicator/LED_ElecStability-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(25,"LED_ElecStabilityOFF-Yellow","cluster/oem/indicator/LED_ElecStabilityOFF-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(26,"LED_AirBag-Red","cluster/oem/indicator/LED_AirBag-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(27,"LED_TPMS-Yellow","cluster/oem/indicator/LED_TPMS-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(28,"LED_GearBoxFault-Yellow","cluster/oem/indicator/LED_GearBoxFault-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(29,"LED_EPS-Yellow","cluster/oem/indicator/LED_EPS-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(30,"LED_CoolantTemp-Red","cluster/oem/indicator/LED_CoolantTemp-Red.png",null,null,null,null,0,ClusterIndicatorArea.AUXILIARY),
        ClusterIndicatorSpec(31,"LED_CoolantTemp-Blue","cluster/oem/indicator/LED_CoolantTemp-Blue.png",null,null,null,null,2,ClusterIndicatorArea.AUXILIARY),
        ClusterIndicatorSpec(32,"LED_OilPressure-Red","cluster/oem/indicator/LED_OilPressure-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(33,"LED_FuelLevelLow-Yellow","cluster/oem/indicator/LED_FuelLevelLow-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.AUXILIARY),
        ClusterIndicatorSpec(34,"LED_HDC-Green","cluster/oem/indicator/LED_HDC-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(35,"LED_HDC-Yellow","cluster/oem/indicator/LED_HDC-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(36,"LED_StartStop-Green","cluster/oem/indicator/LED_StartStop-Green.png",null,null,null,null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(37,"LED_StartStop-Yellow","cluster/oem/indicator/LED_StartStop-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(38,"LED_BSD-Green","cluster/oem/indicator/LED_BSD-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(39,"LED_BSD-Yellow","cluster/oem/indicator/LED_BSD-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(40,"LED_LDWRight-Green","cluster/oem/indicator/LED_LDWRight-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(41,"LED_LDWRight-Yellow","cluster/oem/indicator/LED_LDWRight-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(42,"LED_LDWRight-Grey","cluster/oem/indicator/LED_LDWRight-Grey.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(43,"LED_LaneKeeping-Green","cluster/oem/indicator/LED_LaneKeeping-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(44,"LED_LaneKeeping-Yellow","cluster/oem/indicator/LED_LaneKeeping-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(45,"LED_LaneKeeping-Grey","cluster/oem/indicator/LED_LaneKeeping-Grey.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(46,"LED_SpeedLimit-Fault","cluster/oem/indicator/LED_SpeedLimit-Fault.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(47,"LED_SpeedLimit","cluster/oem/indicator/LED_SpeedLimit.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(48,"LED_SpeedLimit-OFF","cluster/oem/indicator/LED_SpeedLimit-OFF.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(50,"LED_ACCNoObject-Grey","cluster/oem/indicator/LED_ACCNoObject-Grey.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(51,"LED_ACCTargetObject-Grey","cluster/oem/indicator/LED_ACCTargetObject-Grey.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(52,"LED_ACCNoObject-Green","cluster/oem/indicator/LED_ACCNoObject-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(53,"LED_ACCTargetObject-Green","cluster/oem/indicator/LED_ACCTargetObject-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(54,"LED_ACCTargetObject-Yellow","cluster/oem/indicator/LED_ACCTargetObject-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(55,"LED_AEB-Yellow","cluster/oem/indicator/LED_AEB-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(56,"LED_AEB-Red","cluster/oem/indicator/LED_AEB-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(57,"LED_FCW-Yellow","cluster/oem/indicator/LED_FCW-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(58,"LED_TJA-Grey","cluster/oem/indicator/LED_TJA-Grey.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(59,"LED_TJA-Green","cluster/oem/indicator/LED_TJA-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(60,"LED_TJA-Yellow","cluster/oem/indicator/LED_TJA-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(61,"LED_DrivingModeECO","cluster/oem/indicator/LED_DrivingModeECO.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(62,"LED_DrivingModeSport","cluster/oem/indicator/LED_DrivingModeSport.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(63,"LED_SpeedLimit","cluster/oem/indicator/LED_SpeedLimit.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(64,"LED_FatigueDriving","cluster/oem/indicator/LED_FatigueDriving.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(65,"LED_MaintenanceTip","cluster/oem/indicator/LED_MaintenanceTip.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(66,"LED_Warning-Yellow","cluster/oem/indicator/LED_Warning-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(70,"LED_EleSteerLock-Red","cluster/oem/indicator/LED_EleSteerLock-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(71,"LED_EleSteerLock-Yellow","cluster/oem/indicator/LED_EleSteerLock-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(72,"LED_EleSteerLock-White","cluster/oem/indicator/LED_EleSteerLock-White.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(73,"LED_ACCNoObject-Yellow","cluster/oem/indicator/LED_ACCNoObject-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(75,"LED_CoolantTempNormal","cluster/oem/indicator/LED_CoolantTempNormal.png",null,null,null,null,3,ClusterIndicatorArea.AUXILIARY),
        ClusterIndicatorSpec(76,"LED_FuelLevel-Normal","cluster/oem/indicator/LED_FuelLevel-Normal.png",null,null,null,null,3,ClusterIndicatorArea.AUXILIARY),
        ClusterIndicatorSpec(82,"LED_DrivingModeNormal","cluster/oem/indicator/LED_DrivingModeNormal-1.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(84,"LED_FrontFog-Green","cluster/oem/indicator/LED_FrontFog-Green.png",null,null,null,null,3,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(95,"LED_GPF-Green","cluster/oem/indicator/LED_GPF-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(103,"LED_GPF-Yellow","cluster/oem/indicator/LED_GPF-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(104,"LED_GearBoxFault-Red","cluster/oem/indicator/LED_GearBoxFault-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(105,"LED_GearBoxTemp-Red","cluster/oem/indicator/LED_GearBoxTemp-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(106,"LED_GearBoxTemp-Yellow","cluster/oem/indicator/LED_GearBoxTemp-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(107,"LED_SeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"ВОД","DRIVER",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(108,"LED_PassSeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"ПАС","PASS",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(123,"LED_NearBeam-Red","cluster/oem/indicator/LED_NearBeam-Red.png",null,null,null,null,0,ClusterIndicatorArea.LIGHT),
        ClusterIndicatorSpec(124,"LED_RLSeatBelt-Gray","cluster/oem/indicator/LED_SeatBelt-Grey.png",null,"ЗЛ","RL",null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(125,"LED_RMSeatBelt-Gray","cluster/oem/indicator/LED_SeatBelt-Grey.png",null,"ЗЦ","RM",null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(126,"LED_RRSeatBelt-Gray","cluster/oem/indicator/LED_SeatBelt-Grey.png",null,"ЗП","RR",null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(132,"LED_DrivingModeSNOW","cluster/oem/indicator/LED_DrivingModeSNOW.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(133,"LED_DrivingModeMUD","cluster/oem/indicator/LED_DrivingModeMUD.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(134,"LED_DrivingModeSAND","cluster/oem/indicator/LED_DrivingModeSAND.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(135,"LED_AWD-Yellow","cluster/oem/indicator/LED_AWD-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(136,"LED_AWD-Red","cluster/oem/indicator/LED_AWD-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(137,"LED_IceRoad-White","cluster/oem/indicator/LED_IceRoad-White.png",null,null,null,null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(140,"LED_GWL-Yellow","cluster/oem/indicator/LED_GWL-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(143,"LED_LaneKeeping-Red","cluster/oem/indicator/LED_LaneKeeping-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(145,"LED_EPS-Red","cluster/oem/indicator/LED_EPS-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(146,"LED_IntelligentNavi-Grey","cluster/oem/indicator/LED_IntelligentNavi-Grey.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(147,"LED_IntelligentNavi-Green","cluster/oem/indicator/LED_IntelligentNavi-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(148,"LED_TJA-Red","cluster/oem/indicator/LED_TJA-White.png",null,null,null,0xFFFF3B3B.toInt(),0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(149,"LED_IntelligentNavi-Yellow","cluster/oem/indicator/LED_IntelligentNavi-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(150,"LED_DrivingModeOFFROAD","cluster/oem/indicator/LED_DrivingModeOFFROAD.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(151,"LED_AVH-White","cluster/oem/indicator/LED_AVH-White.png",null,null,null,null,3,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(152,"LED_AVH-Yellow","cluster/oem/indicator/LED_AVH-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(156,"LED_DMS-Green","cluster/oem/indicator/LED_DMS-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(157,"LED_DMS-Yellow","cluster/oem/indicator/LED_DMS-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(158,"LED_DMSFault-Yellow","cluster/oem/indicator/LED_DMS-Yellow.png",null,"!","!",null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(159,"LED_FatigueDriving","cluster/oem/indicator/LED_FatigueDriving.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(160,"LED_Power_battery_yellow","cluster/oem/extra/PHEV/LED_Power_battery_yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(161,"LED_Power_battery_red","cluster/oem/extra/PHEV/LED_Power_battery_red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(162,"LED_Power_battery_white","cluster/oem/extra/PHEV/LED_Power_battery_white.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(164,"LED_READY-Green","cluster/oem/extra/TopBottom/PHEV/LED_ready.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(165,"LED_PowerModeEV","cluster/oem/extra/PHEV/img_ev.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(166,"LED_PowerModePEV","cluster/oem/extra/PHEV/img_hev.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(172,"LED_dqgr","cluster/oem/indicator/LED_dqgr.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(173,"LED_avas_yellow","cluster/oem/indicator/LED_avas_yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(174,"LED_hv_yelow","cluster/oem/indicator/LED_hv_yelow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(175,"LED_hv_rew","cluster/oem/indicator/LED_hv_rew.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(176,"LED_hv_rew","cluster/oem/indicator/LED_hv_rew.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(177,"LED_charge_red","cluster/oem/extra/PHEV/LED_charge_red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(178,"LED_charge_bule","cluster/oem/extra/PHEV/LED_charge_blue.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(179,"LED_charge_red","cluster/oem/extra/PHEV/LED_charge_red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(197,"LED_Power_failure_red","cluster/oem/indicator/LED_Power_failure_red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(199,"LED_PowerModeEV+","cluster/oem/extra/PHEV/img_ev+.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(200,"LED_CopilotSeatBelt-Red","cluster/oem/indicator/LED_SeatBelt-Red.png",null,"ПАС","PASS",null,0,ClusterIndicatorArea.STATUS),
        ClusterIndicatorSpec(203,"LED_LaneKeeping-OFF","cluster/oem/indicator/LED_LaneKeeping-Off.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(204,"LED_LDWRight-OFF","cluster/oem/indicator/LED_LDWRight-Off.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(205,"LED_DrivingModeECO+","cluster/oem/indicator/LED_DrivingModeECO.png",null,"+","+",null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(206,"LED_ISA-Bell",null,"ISA","ЗВУК","SOUND",null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(207,"LED_ISA-Horn",null,"ISA !","СИГНАЛ","ALERT",null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(210,"LED_Trailer_Green","cluster/oem/indicator/LED_trailer-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(216,"LED_LampFail-Yellow","cluster/oem/indicator/LED_lighting-Yellow.png",null,"!","!",null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(217,"LED_EDS-Green","cluster/oem/indicator/LED_EDS-Green.png",null,null,null,null,3,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(218,"LED_EDS-Yellow","cluster/oem/indicator/LED_EDS-Yellow.png",null,null,null,null,1,ClusterIndicatorArea.SECONDARY),
        ClusterIndicatorSpec(219,"LED_EDS-Red","cluster/oem/indicator/LED_EDS-Red.png",null,null,null,null,0,ClusterIndicatorArea.SECONDARY),
    )
    fun active(nativeBytes:List<Int>):List<ClusterIndicatorSpec> {
        require(nativeBytes.size==49&&nativeBytes.all {it in 0..255})
        return definitions.filter {nativeBytes[it.bit/8] and (1 shl(it.bit%8))!=0}.distinctBy {it.key}
    }
}

/** Shared header band in the native 2048 x 768 coordinate space. */
object ClusterHeaderLayout {
    const val TOP=8f
    const val BOTTOM=80f
    const val CENTER=(TOP+BOTTOM)/2
    const val ICON_SIZE=60f
    const val ICON_TOP=CENTER-ICON_SIZE/2
}

data class ClusterIndicatorSlot(val indicator:ClusterIndicatorSpec,val x:Float,val y:Float,val size:Float,val header:Boolean)
/** Stable OEM families: colour/state variants share one reserved position.
 * A message never participates in placement. Additional simultaneous states use the central rack.
 */
object ClusterIndicatorLayout {
    internal const val FOOTER_TOP=694f
    private data class Family(val bits:Set<Int>,val x:Float,val y:Float,val size:Float,val header:Boolean)
    private val fixed=buildList {
        listOf(setOf(2),setOf(123),setOf(3),setOf(4,5,6),setOf(84),setOf(7),setOf(8)).forEachIndexed {i,bits->
            add(Family(bits,84f+i*68,ClusterHeaderLayout.ICON_TOP,ClusterHeaderLayout.ICON_SIZE,true))
        }
        listOf(setOf(18,19),setOf(107,108,200),setOf(36,37),setOf(20,151,152),setOf(137),setOf(70,71,72),setOf(27)).forEachIndexed {i,bits->
            add(Family(bits,1536f+i*62,ClusterHeaderLayout.ICON_TOP,ClusterHeaderLayout.ICON_SIZE,true))
        }
        listOf(setOf(40,41,42,204),setOf(43,44,45,143,203),setOf(38,39),setOf(50,51,52,53,54,73,146),setOf(58,59,60,147,148,149)).forEachIndexed {i,bits->
            add(Family(bits,778f-i*62,FOOTER_TOP,54f,false))
        }
        listOf(setOf(22),setOf(32),setOf(21),setOf(17),setOf(23)).forEachIndexed {i,bits->
            add(Family(bits,1216f+i*62,FOOTER_TOP,54f,false))
        }
    }
    @Suppress("UNUSED_PARAMETER")
    fun place(active:List<ClusterIndicatorSpec>,message:Boolean,design:ClusterDesign=ClusterDesign.CLASSIC):List<ClusterIndicatorSlot> {
        val remaining=active.filter {it.area!=ClusterIndicatorArea.AUXILIARY}.distinctBy {it.key}.toMutableList()
        val out=mutableListOf<ClusterIndicatorSlot>()
        for(family in fixed) {
            val icon=remaining.filter {it.bit in family.bits}.minWithOrNull(compareBy({it.priority},{it.bit}))?:continue
            out+=ClusterIndicatorSlot(icon,family.x,family.y,family.size,family.header)
            remaining.remove(icon)
        }
        // Fixed Y and ordering: notifications and colour mode cannot move this rack either.
        val perRow=if(design==ClusterDesign.SPORT)14 else 30
        remaining.sortedWith(compareBy({it.priority},{it.bit})).chunked(perRow).forEachIndexed {row,icons->
            val start=(2048f-(icons.size*64-10))/2
            icons.forEachIndexed {i,icon->
                val x=if(design==ClusterDesign.SPORT){if(i<7)190f+i*64 else 1410f+(i-7)*64}else start+i*64
                out+=ClusterIndicatorSlot(icon,x,628f-row*64,54f,false)
            }
        }
        return out
    }
}
