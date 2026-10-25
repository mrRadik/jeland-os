package ru.jeland.os.data.automation

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.jeland.os.core.VehicleLinkState
import ru.jeland.os.core.automation.*

/** Preserves the OEM sample's original age; never makes cached data artificially fresh. */
class LiveAutomationTemperatureRepository(link:StateFlow<VehicleLinkState>,scope:CoroutineScope):AutomationTemperatureRepository {
    override val state=link.map {value->
        val elapsed=SystemClock.elapsedRealtime()
        ru.jeland.os.core.OutsideTemperature.celsius(value,elapsed)?.let {celsius->
            val sample=value.raw.getValue(ru.jeland.os.core.OutsideTemperature.key)
            OutsideTemperature(celsius,System.currentTimeMillis()-(elapsed-sample.receivedMillis),TemperatureSource.DESAY_VDS)
        }
    }.stateIn(scope,SharingStarted.Eagerly,null)
}
