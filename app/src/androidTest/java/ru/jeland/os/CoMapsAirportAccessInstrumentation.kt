package ru.jeland.os.navigation.comaps

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.routing.RoutingController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import ru.jeland.os.JelandApplication
import ru.jeland.os.MainActivity
import ru.jeland.os.navigation.GeoPoint
import ru.jeland.os.navigation.NavigationPlace
import java.io.File

/** Real pinned CoMaps map, search and vehicle graph. A stationary debug GPS fix checks actual following. */
class CoMapsAirportAccessInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun chelyabinskAirportUsesReachableVehicleAccessAndCancellationStaysCancelled() {
        val app=(ui.activity.application as JelandApplication).container
        val repo=app.navigation.comaps
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-airport-access-133").apply {mkdirs()}
        try {
            ui.runOnIdle {app.firstRun.finish();repo.initialize()}
            ui.waitUntil(30000){repo.ready.value}
            ui.runOnIdle {repo.search("аэропорт Челябинск")}
            ui.waitUntil(20000){repo.results.value.isNotEmpty()&&!repo.searching.value}
            val results=repo.results.value
            File(folder,"search.json").writeText(JSONArray().apply {results.forEach {put(JSONObject().put("name",it.place.fullName).put("lat",it.place.point.latitude).put("lon",it.place.point.longitude))}}.toString(2))
            val airport=results.firstOrNull {it.place.name.contains("аэропорт",true)&&it.place.point.latitude in 55.25..55.36&&it.place.point.longitude in 61.4..61.6}?.place
            assertNotNull("Actual Chelyabinsk airport must be found in the offline region; results=$results",airport)
            val origin=GeoPoint(55.1608,61.4022)
            ui.runOnIdle {repo.select(airport!!,false);repo.build(origin)}
            ui.waitUntil(60000){!repo.state.value.calculating}
            val state=repo.state.value
            assertTrue("Airport route failed: ${repo.message.value}; airport=$airport",state.active)
            assertTrue(state.preview);assertTrue(state.geometry.size>2)
            assertEquals(airport!!.fullName,state.destination)
            var nativeBuilt=false
            var nativeState=""
            ui.runOnIdle {
                nativeBuilt=RoutingController.get().isBuilt
                nativeState=Framework.nativeGetRouteFollowingInfo()?.routingSessionState?.name.orEmpty()
            }
            assertTrue(nativeBuilt);assertTrue(CoMapsRouteUpdate.ready(nativeState))
            val access=repo.approach.value
            File(folder,"route.json").writeText(JSONObject().put("destination",state.destination).put("originalLat",airport.point.latitude).put("originalLon",airport.point.longitude)
                .put("sdkRouteBuilt",nativeBuilt).put("nativeState",nativeState).put("geometryPoints",state.geometry.size)
                .put("accessLat",access?.point?.latitude).put("accessLon",access?.point?.longitude).put("offsetMeters",access?.distanceMeters)
                .put("message",repo.message.value).toString(2))
            assertNotNull("Airport label should reproduce inaccessible-point recovery",access)
            assertTrue(access!!.distanceMeters in 1.0..5000.0)
            val last=RoutingController.get().routeGeometry.mJunctions.last()
            assertTrue(CoMapsDestinationRecovery.distance(access.point,GeoPoint(last.mLat,last.mLon))<20)
            ui.onNodeWithTag("nav-navigation").performScrollTo().performClick()
            ui.onNodeWithTag("navigation-access-point").assertIsDisplayed()
            ui.runOnIdle {repo.stop(false);repo.demoDriving.value=true;repo.resume();app.navigation.gps.demoLocation(origin,0.0,0f)}
            ui.waitUntil(10000){repo.locationHelper.savedLocation?.let {CoMapsDestinationRecovery.distance(origin,GeoPoint(it.latitude,it.longitude))<20}==true}
            ui.runOnIdle {repo.select(airport,false);repo.build()}
            ui.waitUntil(60000){!repo.state.value.calculating&&repo.state.value.active&&!repo.state.value.preview&&RoutingController.get().isNavigating}
            ui.runOnIdle {
                assertTrue(Framework.nativeIsRouteBuilt())
                val info=Framework.nativeGetRouteFollowingInfo()!!
                assertTrue(info.routingSessionState.name in setOf("OnRoute","OffRoute","RouteNotStarted"))
                assertNotNull(repo.approach.value)
                File(folder,"following.json").writeText(JSONObject().put("sdkNavigating",RoutingController.get().isNavigating)
                    .put("nativeState",info.routingSessionState.name).put("offsetMeters",repo.approach.value!!.distanceMeters).toString(2))
                repo.stop(false)
            }
            // An ordinary reachable target keeps its original endpoint and needs no recovery.
            val terminal=results.first {it.place.name.contains("Российский терминал",true)}.place
            ui.runOnIdle {repo.select(terminal,false);repo.build(origin)}
            ui.waitUntil(20000){!repo.state.value.calculating}
            ui.runOnIdle {
                assertTrue(repo.state.value.active);assertNull(repo.approach.value)
                assertTrue(RoutingController.get().isBuilt)
                repo.stop(false);repo.select(airport,false);repo.build(origin);repo.clearSelection()
            }
            Thread.sleep(1500)
            ui.runOnIdle {assertFalse(repo.state.value.active);assertFalse(repo.state.value.calculating);assertNull(repo.approach.value);assertTrue(repo.state.value.geometry.isEmpty())}
        }finally {ui.runOnIdle {repo.stop(false);app.navigation.gps.endDemo();app.navigation.gps.start()}}
    }
}
