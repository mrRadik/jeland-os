package ru.jeland.os.ui.cluster

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.jeland.os.core.*

/** The timer invalidates stale readings, but is not the timestamp of new callback data.
 * Read uptime inside remember's calculation on EVERY new snapshot, on either display.
 */
@Composable internal fun rememberClusterPanelSnapshot(state:UiState,trip:TripSummary?,speed:VehicleSpeedSample?,refreshTick:Long,tripPeriod:ClusterTripPeriod=state.preferences.clusterTripPeriod):ClusterPanelState =
    remember(state,trip,speed,refreshTick,tripPeriod) {ClusterPanelState.from(state,trip,speed,SystemClock.elapsedRealtime(),tripPeriod)}
