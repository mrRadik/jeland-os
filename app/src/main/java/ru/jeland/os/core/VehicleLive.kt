package ru.jeland.os.core

/** Owner-provided hypotheses, not OEM calibration. Change coefficients only here. */
object ExperimentalVehicleConversions {
    const val PRESSURE_DIVISOR = 70.0
    const val FUEL_DIVISOR = 10.0
    const val TANK_LITERS = 55.0
    const val RPM_MULTIPLIER = 4
    // Photo/log calibration 18 Oct: subtract 0.1 bar from the owner's initial raw/70.
    // A one-point estimate, not OEM calibration. Zero must remain visible as zero, not "normal".
    fun pressureBar(raw: Int?): Double? = raw?.takeIf { it in 0..255 }?.let {(it/PRESSURE_DIVISOR-.1).coerceAtLeast(0.0)}
    fun fuel(raw: Int?): FuelState {
        val percent = raw?.takeIf { it in 0..1000 }?.div(FUEL_DIVISOR)
        return FuelState(percent?.let { TANK_LITERS * it / 100.0 }, estimated=true, raw=raw, percent=percent, experimental=true)
    }
    fun engineRpm(raw: Int?): Int? = raw?.takeIf { it in 0..(Int.MAX_VALUE / RPM_MULTIPLIER) }?.times(RPM_MULTIPLIER)
}

data class VdsKey(val module: Int, val cmd: Int) { override fun toString() = "0x${module.toString(16)}/$cmd" }
data class RawVehicleSample(val values: List<Int>?, val receivedMillis: Long)
data class VehicleLinkState(
    val demo: Boolean = true, val connected: Boolean = false, val writesEnabled: Boolean = false,
    val busy: Boolean = false, val detail: String = "DEMO · без подключения к авто",
    val lastCommand: String? = null, val raw: Map<VdsKey, RawVehicleSample> = emptyMap(),
    val userMessage: String? = null,
)
data class VdsWrite(val key: VdsKey, val value: Int, val feedback: VdsKey = key, val expected: Int? = null)
val VehicleState.shadeSummary: String get()=if(!live)shade.title else when(shadeRaw?.singleOrNull()) {
    1->"Закрыта";2->"Открывается";3->"Частично открыта";4->"Закрывается";5->"Открыта"
    7->"Недоступна";else->"Нет данных"
}
val VehicleState.reportedShade: Shade? get()=if(!live)shade else when(shadeRaw?.singleOrNull()){1->Shade.CLOSED;5->Shade.OPEN;else->null}
// Presentation follows an accepted local command, without rewriting physical feedback or persisting a command.
val VehicleState.shadeUiPosition:Shade? get()=if(!live)shade else requestedShade?:reportedShade?:Shade.HALF.takeIf {shadeRaw==listOf(3)}
val VehicleState.shadeUiSummary:String get()=if(live && requestedShade!=null)requestedShade.title else shadeSummary
val VehicleState.steeringSummary: String get()=when {live && !steeringKnown->"Нет данных";steeringHeat->if(live)"Включён · ГУ"else"Включён · DEMO";else->"Выключен"}

/** Field-tested or OEM-source-backed setters. A successful Binder transaction is NOT actuator confirmation. */
object VerifiedVehicleProtocol {
    const val FAN_MAX = 10
    const val HVAC = 0x5000A
    const val CAR = 0x50001
    const val INFO = 0x50004
    const val AVM = 0x50008
    // Stock Launcher CarLanManager.openAVM: ID_AVM_DISPLAY=8, VALUE=[1].
    // Opens the native surround-view UI; does not select a camera or change calibration.
    fun openCameras()=VdsWrite(VdsKey(AVM,8),1)
    fun requiresClimatePower(write:VdsWrite)=write.key.module==HVAC && write.key.cmd!=18
    const val VR = 0x50010
    val openings = mapOf(15 to VehicleOpening.FRONT_LEFT,16 to VehicleOpening.FRONT_RIGHT,
        17 to VehicleOpening.REAR_LEFT,18 to VehicleOpening.REAR_RIGHT,19 to VehicleOpening.TRUNK)
    val subscriptions = mapOf(
        HVAC to listOf(18,1,3,4,8,12,6,10,13,24,80,65,9,19),
        CAR to listOf(93,94,95,96,160,139,141,10),
        INFO to (listOf(15,16,17,18,19,68,140,62,79,74,13)+BatteryDecoder.commands),
    )
    fun seat(cmd: Int, level: Int): VdsWrite {
        require(cmd in listOf(93,94,95,96,139,141) && level in 0..3)
        return VdsWrite(VdsKey(CAR,cmd),level+1,expected=level)
    }
    fun rearSeat(side:RearSeatSide,level:Int)=seat(side.commandId,level)
    fun binary(cmd: Int, enabled: Boolean): VdsWrite {
        require(cmd in listOf(18,1,24,65,8))
        return VdsWrite(VdsKey(HVAC,cmd),if(enabled)1 else 2,expected=if(cmd==8){if(enabled)0 else 1}else if(enabled)1 else 0)
    }
    fun toggle(cmd: Int, before: Int): VdsWrite {
        require(cmd in listOf(80,160) && before in 0..1)
        return VdsWrite(VdsKey(if(cmd==160)CAR else HVAC,cmd),1,expected=1-before)
    }
    fun temperature(driver: Boolean, raw: Int): VdsWrite {
        require(raw in 0..16); return VdsWrite(VdsKey(HVAC,if(driver)76 else 78),raw,VdsKey(HVAC,if(driver)10 else 13),expected=raw)
    }
    fun fan(raw: Int): VdsWrite { require(raw in 1..FAN_MAX); return VdsWrite(VdsKey(HVAC,77),raw,VdsKey(HVAC,12),expected=raw) }
    fun airflow(raw: Int): VdsWrite {
        val mode=requireNotNull(Airflow.fromCommand(raw)); return VdsWrite(VdsKey(HVAC,6),raw,expected=mode.feedbackRaw)
    }
    fun autoOn()=VdsWrite(VdsKey(HVAC,4),1,expected=1)
    // OEM DUAL feedback: 0=SYNC on, 1=independent zones. SET 1 enables SYNC, 2 disables.
    fun sync(enabled:Boolean)=VdsWrite(VdsKey(HVAC,3),if(enabled)1 else 2,expected=if(enabled)0 else 1)
    fun absoluteTemperature(driver:Boolean,celsius:Double):VdsWrite {
        require(celsius.isFinite()&&celsius in 15.0..31.0&&celsius==kotlin.math.round(celsius))
        return temperature(driver,celsius.toInt()-15) // endpoints mean LO/HI, not literal 15/31 degrees
    }
    fun shade(value: Shade)=VdsWrite(VdsKey(VR,124),when(value){Shade.OPEN->1;Shade.CLOSED->2;Shade.HALF->3},VdsKey(CAR,10),when(value){Shade.CLOSED->1;Shade.OPEN->5;Shade.HALF->null})
    fun allowed(write: VdsWrite): Boolean = runCatching {
        when(write.key.module) {
            AVM -> write==openCameras()
            VR -> Shade.entries.any { shade(it)==write }
            CAR -> when(write.key.cmd){160->(0..1).any {toggle(160,it)==write};else->(0..3).any {seat(write.key.cmd,it)==write}}
            HVAC -> when(write.key.cmd){18,1,24,65,8->listOf(false,true).any {binary(write.key.cmd,it)==write}
                80->(0..1).any {toggle(80,it)==write};4->write==autoOn();3->listOf(false,true).any {sync(it)==write}
                76,78->(0..16).any {temperature(write.key.cmd==76,it)==write}
                77->(1..FAN_MAX).any {fan(it)==write};6->Airflow.entries.any {airflow(it.commandRaw)==write};else->false}
            else->false
        }
    }.getOrDefault(false)
    fun unsigned(values: List<Int>?): Int? = when {
        values?.size==1 -> values[0].takeIf {it in 0..65535}
        values?.size==2 && values.all {it in 0..255} -> (values[0] shl 8) or values[1]
        else->null
    }
}

/** Reducer is Android-free and never carries DEMO values into a vehicle snapshot. */
class LiveVehicleReducer {
    private val tyreTemperature=QnxTyreTemperatureCache()
    private val raw=linkedMapOf<VdsKey,RawVehicleSample>()
    @Synchronized fun clear() {raw.clear();tyreTemperature.clear()}
    @Synchronized fun acceptTyreTemperature(frame:QnxTyreTemperatureFrame,now:Long){tyreTemperature.accept(frame,now)}
    @Synchronized fun accept(key: VdsKey, values: List<Int>?, now: Long) {
        if(key.cmd in VerifiedVehicleProtocol.subscriptions[key.module].orEmpty())raw[key]=RawVehicleSample(values?.toList(),now)
    }
    @Synchronized fun snapshot()=raw.toMap()
    @Synchronized fun values(key: VdsKey,now: Long): List<Int>? = raw[key]?.takeIf {now-it.receivedMillis in 0..15_000}?.values
    @Synchronized fun scalar(module: Int,cmd: Int,now: Long)=values(VdsKey(module,cmd),now)?.singleOrNull()
    @Synchronized fun climate(now: Long): ClimateState {
        val m=VerifiedVehicleProtocol.HVAC
        fun v(cmd: Int)=scalar(m,cmd,now)
        val available=VerifiedVehicleProtocol.subscriptions.getValue(m).filter {cmd->
            cmd !in listOf(9,19) && v(cmd)?.let {when(cmd){10,13->it in 0..16;12->it in 0..VerifiedVehicleProtocol.FAN_MAX;6->it in 0..7;else->it in 0..1}}==true
        }.toSet()
        val d=v(10)?.takeIf {10 in available}; val p=v(13)?.takeIf {13 in available}
        return ClimateState(driver=d?.plus(15)?.toDouble()?:22.0,passenger=p?.plus(15)?.toDouble()?:22.0,
            fan=v(12)?:0,auto=v(4)==1,ac=v(1)==1,recirculation=v(8)==0,defrost=v(24)==1,sync=3 in available&&v(3)==0,
            available=available,driverRaw=d,passengerRaw=p,power=v(18)==1,rearDefrost=v(80)==1,
            windshieldHeat=v(65)==1,airflowRaw=v(6)?.takeIf {6 in available})
    }
    @Synchronized fun seats(now: Long): SeatState {
        val m=VerifiedVehicleProtocol.CAR
        fun v(cmd: Int)=scalar(m,cmd,now)?.takeIf {it in 0..3}
        val available=listOf(93,94,95,96,139,141).filter {v(it)!=null}.toSet()
        val l=v(139); val r=v(141)
        return SeatState(SeatChannel(v(95)?:0,v(93)?:0),SeatChannel(v(96)?:0,v(94)?:0),
            RearHeat.fromLevels(l,r),available,l,r)
    }
    @Synchronized fun vehicle(now: Long, requested: Shade?=null): VehicleState {
        val m=VerifiedVehicleProtocol.INFO
        val known=VerifiedVehicleProtocol.openings.filterKeys {scalar(m,it,now) in 0..1}
        val fuel=VerifiedVehicleProtocol.unsigned(values(VdsKey(m,68),now))
        val pressure=values(VdsKey(m,140),now)?.takeIf {it.size==4 && it.all {p->p in 0..255}}
        val steering=scalar(VerifiedVehicleProtocol.CAR,160,now)?.takeIf {it in 0..1}
        // Provisional LF/RF/LR/RR order. The two right tyres cannot be distinguished by this photo.
        val temperatures=tyreTemperature.values(now)
        val tireReadings=if(pressure!=null||temperatures.isNotEmpty())Wheel.entries.associateWith {wheel->
            TireReading(ExperimentalVehicleConversions.pressureBar(pressure?.get(wheel.ordinal)),temperatures[wheel])
        }else emptyMap()
        return VehicleState(live=true,steeringHeat=steering==1,steeringKnown=steering!=null,
            openings=known.filterKeys {scalar(m,it,now)==1}.values.toSet(),knownOpenings=known.values.toSet(),
            // Speed conversion is not field-verified; persistent warning is conservative, not a claim of motion.
            mockMoving=true,fuel=ExperimentalVehicleConversions.fuel(fuel),tires=tireReadings,options=emptyMap(),
            shadeRaw=values(VdsKey(VerifiedVehicleProtocol.CAR,10),now),requestedShade=requested,
            // Field data contradicts raw*4 away from idle. Keep RAW, do not publish false RPM.
            engineRpm=null,battery=BatteryDecoder.decode {values(VdsKey(m,it),now)},
            tireChannels=pressure?.map {ExperimentalVehicleConversions.pressureBar(it)}?:emptyList())
    }
}
