package ru.jeland.os

import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.DiagnosticsExporter

/** Cached synthetic debug state only. No network, QNX, permission changes or real file export. */
class TyreDiagnosticsInstrumentation {
    private fun failure()=TyreTemperatureDiagnostics(active=true,phase=TyreReadPhase.GREETING,attempts=3,
        interfaceName="eth0",lastError="SocketTimeoutException: Read timed out",failedPhase=TyreReadPhase.GREETING,
        errorAtMillis=SystemClock.elapsedRealtime(),retryAtMillis=SystemClock.elapsedRealtime()+30_000,
        trace=TyreProtocolTrace(TyreReadPhase.GREETING,"<CRLF>",13,2,"QCONN\\r\\n<qconn-","51 43 4f 4e 4e"))
    @Test fun exportContainsBoundedTraceAndRawSampleWithoutInventingTemperature() {
        val state=failure().copy(payload="@a005\ndata:json:[0,0,0,255,0,0,0,0]",
            samples=mapOf(Wheel.FRONT_LEFT to TyreDebugSample(255,6,null,100)))
        val json=DiagnosticsExporter.tyreDiagnosticsJson(state)
        assertEquals("GREETING",json.getString("failedPhase"))
        assertEquals(13,json.getJSONObject("trace").getLong("rxBytes"))
        assertTrue(json.getJSONArray("samples").getJSONObject(0).isNull("celsius"))
        assertEquals(255,json.getJSONArray("samples").getJSONObject(0).getInt("raw"))
        assertEquals("elapsedRealtimeMillis",json.getString("clock"))
        assertEquals(2048,DiagnosticsExporter.tyreDiagnosticsJson(state.copy(payload="x".repeat(4096))).getString("lastPayload").length)
    }
}
