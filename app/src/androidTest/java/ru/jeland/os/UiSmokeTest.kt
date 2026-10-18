package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.ThemeMode
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    private fun internalRoute(route:String) {
        ui.onNodeWithTag("nav-${if(route=="car")"settings"else route}").performClick()
        if(route=="car")ui.onNodeWithTag("open-demo-car").performScrollTo().performClick()
    }
    @Before fun stablePortraitDemo() {
        ui.runOnIdle {container().car.useVehicle(false);if(!container().climate.state.value.power)container().climate.togglePower()}
        ui.activityRule.scenario.onActivity {activity->
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
        }
        // BlueStacks is landscape; wait for Android's requested portrait recreation before navigating.
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        ui.waitForIdle()
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Compose idle does not imply SurfaceFlinger/Activity transitions have finished.
        android.os.SystemClock.sleep(650)
        val folder=File(instrumentation.targetContext.getExternalFilesDir(null),"qa-0.3.1").apply {mkdirs()}
        // Capture only this app's Compose root: avoid BlueStacks' native UiAutomation screenshot crash.
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun permanentClimateAcrossAllScreens() {
        listOf("home","music","navigation","car","apps","settings","home").forEach {route->
            internalRoute(route)
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
            ui.onNodeWithTag("driver-plus").assertIsDisplayed()
        }
    }
    @Test fun homeFitsWithoutScrolling() {
        ui.onNodeWithTag("nav-home").performClick()
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {container().theme.setMode(theme)}
            ui.waitForIdle()
            val range=ui.onNodeWithTag("home-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            org.junit.Assert.assertEquals("Home must fit the 1440x1920 viewport",0f,range.maxValue(),0f)
            ui.onNodeWithTag("quick-steering").assertIsDisplayed()
            ui.onNodeWithTag("quick-camera").assertIsDisplayed()
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
            capture("home-fit-${theme.name.lowercase()}")
        }
        ui.runOnIdle {container().theme.setMode(ThemeMode.DARK)}
    }
    @Test fun temperatureAndSeatControlsChangeMockState() {
        val start=container().climate.state.value.driver
        val direction=if(start>=32.0)-.5 else .5
        ui.onNodeWithTag(if(direction>0)"driver-plus"else"driver-minus").performClick()
        ui.waitUntil(5000){container().climate.state.value.driver==start+direction}
        ui.onNodeWithTag("driver-seat").performClick()
        ui.onNodeWithTag("popup-driver").assertIsDisplayed()
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        ui.onNodeWithTag("seat-vent").performClick()
        ui.onNodeWithTag("seat-level-2").performClick()
        ui.onNodeWithTag("seat-value").assertTextContains("2 / 3",substring=true)
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("popup-driver").assertDoesNotExist()
        ui.runOnIdle {container().climate.adjustTemperature(true,-direction)}
    }
    @Test fun themeAutoUsesMockProvider() {
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithTag("theme-auto").performScrollTo().performClick()
        val night=container().dayNight.isNight.value
        ui.onNodeWithTag("toggle-night").performClick()
        ui.waitUntil(5000){container().theme.mode.value==ThemeMode.AUTO && container().dayNight.isNight.value!=night}
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
    }
    @Test fun visualGalleryAndOnlyReviewedVehiclePermission() {
        ui.runOnIdle {
            container().theme.setMode(ThemeMode.DARK)
            container().seats.setLevel(ru.jeland.os.core.SeatSide.DRIVER,ru.jeland.os.core.SeatMode.VENTILATE,0)
            container().seats.setLevel(ru.jeland.os.core.SeatSide.DRIVER,ru.jeland.os.core.SeatMode.HEAT,0)
        }
        ui.onNodeWithTag("nav-home").performClick();capture("01-home-dark")
        ui.onNodeWithTag("driver-seat").performClick();ui.onNodeWithTag("seat-level-2").performClick();capture("02-driver-heat")
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("passenger-seat").performClick();capture("03-passenger-seat")
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("expand-climate").performClick();capture("04-climate")
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("nav-navigation").performClick();capture("05-navigation")
        ui.onNodeWithTag("nav-settings").performClick();ui.onNodeWithTag("theme-light").performScrollTo().performClick()
        ui.onNodeWithTag("nav-home").performClick();capture("06-home-light")
        ui.onNodeWithTag("nav-settings").performClick();ui.onNodeWithTag("theme-dark").performScrollTo().performClick()
        ui.onNodeWithTag("nav-home").performClick()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val permissions=context.packageManager.getPackageInfo(context.packageName,4096).requestedPermissions.orEmpty()
        check("ivi.vds.permission.SERVICE" in permissions)
        check(permissions.none {it.startsWith("android.car.") || it.contains("desaysv",true) || it=="android.permission.INTERNET" || it=="android.permission.SYSTEM_ALERT_WINDOW"})
    }
    @Test fun dashboardLayoutAndBothThemePopups() {
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("fuel-widget").assertIsDisplayed()
        ui.onNodeWithTag("vehicle-status-card").assertIsDisplayed()
        ui.onNodeWithTag("favorite-apps-widget").assertIsDisplayed()
        ui.onNodeWithText("Камеры\n360°").assertIsDisplayed()
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {container().theme.setMode(theme);container().vehicle.closeAllOpenings();container().vehicle.simulateMoving(false)}
            capture("home-${theme.name.lowercase()}")
            for(tag in listOf("driver-seat","passenger-seat","expand-climate","configure-favorites","fuel-widget","weather-widget","open-debug","quick-shade","quick-camera")) {
                ui.onNodeWithTag(tag).performClick()
                ui.onNodeWithTag("climate-bar").assertIsDisplayed()
                capture("${theme.name.lowercase()}-$tag")
                if(tag=="driver-seat") {
                    ui.onNodeWithTag("seat-heat").performClick();ui.onNodeWithTag("seat-level-2").performClick();capture("${theme.name.lowercase()}-seat-heat")
                    ui.onNodeWithTag("seat-vent").performClick();ui.onNodeWithTag("seat-level-3").performClick();capture("${theme.name.lowercase()}-seat-vent")
                    ui.onNodeWithTag("seat-level-0").performClick()
                }
                ui.onNodeWithTag("close-popup").performClick()
            }
        }
        ui.runOnIdle {container().theme.setMode(ThemeMode.DARK)}
    }
    @Test fun doorsTiresAndFavoritesAreReactive() {
        ui.onNodeWithTag("nav-home").performClick()
        ui.runOnIdle {
            container().vehicle.simulateOpening(ru.jeland.os.core.VehicleOpening.FRONT_LEFT)
            container().vehicle.simulateOpening(ru.jeland.os.core.VehicleOpening.REAR_RIGHT)
            container().vehicle.simulateMoving(true)
        }
        ui.onNodeWithTag("vehicle-motion-warning").assertIsDisplayed()
        capture("open-doors-motion-demo")
        ui.onNodeWithText("Движение с открытым кузовом",substring=true).assertIsDisplayed()
        ui.runOnIdle {
            container().vehicle.closeAllOpenings();container().vehicle.simulateMoving(false)
            container().vehicle.simulateTire(ru.jeland.os.core.Wheel.FRONT_LEFT,ru.jeland.os.core.TireReading())
        }
        ui.waitUntil(5000){ui.onAllNodesWithTag("vehicle-motion-warning").fetchSemanticsNodes().isEmpty()}
        ui.onNodeWithTag("tire-front_left").assertTextContains("Нет данных",substring=true)
        ui.runOnIdle {container().vehicle.simulateTire(ru.jeland.os.core.Wheel.FRONT_LEFT,ru.jeland.os.core.TireReading(2.4,18))}
        ui.onNodeWithTag("configure-favorites").performClick()
        ui.waitUntil(10000){!container().apps.state.value.loading}
        val installed=container().apps.state.value.installed
        check(installed.isNotEmpty()) {"Local test device has no launcher apps"}
        val wasFavorite=installed[0].id in container().apps.state.value.favorites
        ui.runOnIdle {if(!wasFavorite)container().apps.toggleFavorite(installed[0].id)}
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithText(installed[0].label).assertIsDisplayed()
        ui.runOnIdle {if(!wasFavorite)container().apps.toggleFavorite(installed[0].id)}
    }
    @Test fun appsSearchAndFavoriteButtonsWork() {
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("configure-favorites").performClick()
        ui.waitUntil(10000){!container().apps.state.value.loading}
        val app=container().apps.state.value.installed.first()
        val original=app.id in container().apps.state.value.favorites
        ui.onNodeWithTag("app-search").performClick().performTextInput(app.label)
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        ui.onNodeWithTag("favorite-${app.packageName}").performClick()
        ui.waitUntil(5000){(app.id in container().apps.state.value.favorites)!=original}
        capture("apps-search-keyboard")
        ui.onNodeWithTag("favorite-${app.packageName}").performClick()
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
    }
    @Test fun sixScreensAndVehicleTabsInBothThemes() {
        ui.runOnIdle {container().vehicle.closeAllOpenings();container().vehicle.simulateMoving(false)}
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {container().theme.setMode(theme)}
            for(route in listOf("home","music","navigation","car","apps","settings")) {
                internalRoute(route)
                ui.onNodeWithTag("jeland-header").assertIsDisplayed()
                ui.onNodeWithTag("climate-bar").assertIsDisplayed()
                if(route=="apps") {
                    ui.onNodeWithTag("screen-apps").assertIsDisplayed()
                    ui.onNodeWithTag("popup-apps").assertDoesNotExist()
                }
                if(route=="settings")ui.onNodeWithTag("theme-dark").performScrollTo()
                capture("${theme.name.lowercase()}-screen-$route")
                if(route=="car") {
                    for(tab in ru.jeland.os.core.VehicleSection.entries) {
                        ui.onNodeWithTag("car-tab-${tab.name.lowercase()}").performScrollTo().performClick()
                        capture("${theme.name.lowercase()}-car-${tab.name.lowercase()}")
                    }
                    ui.onNodeWithTag("car-tab-quick").performScrollTo().performClick()
                }
            }
        }
        ui.runOnIdle {container().theme.setMode(ThemeMode.DARK)}
    }
    @Test fun vehicleTabsAndSettingsAreInteractiveAndPersist() {
        val preferences=container().preferences.state.value
        val original=container().vehicle.state.value.options.getValue(ru.jeland.os.core.VehicleOption.AUTO_LIGHT)
        try {
            internalRoute("car")
            ui.onNodeWithTag("car-tab-lights").performScrollTo().performClick()
            ui.onNodeWithTag("car-option-auto_light").performScrollTo().performClick()
            ui.onNodeWithTag("car-option-auto_light").assertIsToggleable()
            ui.waitUntil(5000){container().vehicle.state.value.options[ru.jeland.os.core.VehicleOption.AUTO_LIGHT]!=original}
            ui.onNodeWithTag("nav-settings").performClick()
            ui.onNodeWithTag("setting-fuel").performScrollTo().performClick()
            ui.waitUntil(5000){container().preferences.state.value.showFuel!=preferences.showFuel}
            ui.onNodeWithTag("nav-home").performClick()
            if(preferences.showFuel)ui.onNodeWithTag("fuel-widget").assertDoesNotExist()else ui.onNodeWithTag("fuel-widget").assertIsDisplayed()
            ui.activityRule.scenario.recreate()
            ui.waitForIdle()
            org.junit.Assert.assertEquals(!preferences.showFuel,container().preferences.state.value.showFuel)
            val store=ru.jeland.os.data.AndroidSettingsStore(ui.activity)
            org.junit.Assert.assertEquals(!preferences.showFuel,ru.jeland.os.data.PersistentInterfaceRepository(store).state.value.showFuel)
            org.junit.Assert.assertEquals(!original,ru.jeland.os.data.MockVehicleRepository(store).state.value.options[ru.jeland.os.core.VehicleOption.AUTO_LIGHT])
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        } finally {
            ui.runOnIdle {
                container().preferences.change(preferences)
                if(container().vehicle.state.value.options[ru.jeland.os.core.VehicleOption.AUTO_LIGHT]!=original)container().vehicle.toggleOption(ru.jeland.os.core.VehicleOption.AUTO_LIGHT)
            }
        }
    }
    @Test fun fullAppsSearchEmptyStateAndOrder() {
        ui.onNodeWithTag("nav-apps").performClick()
        ui.waitUntil(10000){!container().apps.state.value.loading}
        val installed=container().apps.state.value.installed
        check(installed.size>=2){"Local QA requires two launcher apps"}
        val original=container().apps.state.value.favorites
        val first=installed[0];val second=installed[1]
        try {
            ui.onNodeWithTag("app-search").performTextInput("__no_jeland_app__")
            ui.onNodeWithTag("apps-empty").assertIsDisplayed()
            capture("apps-no-results")
            ui.onNodeWithTag("clear-app-search").performClick()
            ui.onNodeWithTag("apps-filter-favorites").performClick()
            capture("apps-favorites-filter")
            ui.runOnIdle {
                original.forEach {container().apps.toggleFavorite(it)}
                container().apps.toggleFavorite(first.id);container().apps.toggleFavorite(second.id)
            }
            ui.waitUntil(5000){container().apps.state.value.favorites==listOf(first.id,second.id)}
            ui.onNodeWithTag("favorite-later-${first.packageName}").performClick()
            ui.waitUntil(5000){container().apps.state.value.favorites==listOf(second.id,first.id)}
            capture("apps-reordered")
            org.junit.Assert.assertNotNull(container().apps.launch("nonexistent/package"))
        } finally {
            ui.runOnIdle {
                container().apps.state.value.favorites.toList().forEach {container().apps.toggleFavorite(it)}
                original.forEach {container().apps.toggleFavorite(it)}
            }
        }
    }
    @Test fun musicControlsAndBackgroundPause() {
        ui.onNodeWithTag("nav-music").performClick()
        val original=container().media.state.value
        try {
            ui.runOnIdle {if(container().media.state.value.playing)container().media.togglePlay()}
            val index=container().media.state.value.index
            ui.onNodeWithTag("media-next").performClick()
            ui.waitUntil(5000){container().media.state.value.index==(index+1)%ru.jeland.os.core.demoTracks.size}
            ui.onNodeWithTag("media-previous").performClick()
            ui.waitUntil(5000){container().media.state.value.index==index}
            ui.onNodeWithTag("media-seek").performSemanticsAction(SemanticsActions.SetProgress){it(60f)}
            ui.waitUntil(5000){container().media.state.value.position==60}
            ui.onNodeWithTag("media-source-USB").performScrollTo().performClick()
            ui.waitUntil(5000){container().media.state.value.source=="USB"}
            ui.onNodeWithTag("media-play").performScrollTo().performClick()
            ui.waitUntil(5000){container().media.state.value.playing}
            ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val pausedAt=container().media.state.value.position
            android.os.SystemClock.sleep(1200)
            org.junit.Assert.assertEquals(pausedAt,container().media.state.value.position)
            ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        } finally {
            ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            ui.runOnIdle {
                container().media.selectTrack(original.index);container().media.seek(original.position);container().media.setSource(original.source)
                if(container().media.state.value.playing!=original.playing)container().media.togglePlay()
            }
        }
    }
    @Test fun optionalHomeCardsStillFitWithoutScrolling() {
        val original=container().preferences.state.value
        ui.onNodeWithTag("nav-home").performClick()
        try {
            for(tires in listOf(true,false))for(favorites in listOf(true,false)) {
                ui.runOnIdle {container().preferences.change(original.copy(showFuel=true,showTires=tires,showFavorites=favorites))}
                ui.waitUntil(5000){ui.onAllNodesWithTag("vehicle-status-card").fetchSemanticsNodes().isNotEmpty()==tires && ui.onAllNodesWithTag("favorite-apps-widget").fetchSemanticsNodes().isNotEmpty()==favorites}
                val range=ui.onNodeWithTag("home-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                org.junit.Assert.assertEquals(0f,range.maxValue(),0f)
                ui.onNodeWithTag("quick-camera").assertIsDisplayed()
            }
        } finally {ui.runOnIdle {container().preferences.change(original)}}
    }
    @Test fun warningUnknownAndMultipleOpeningsInBothThemes() {
        val original=container().vehicle.state.value
        try {
            ui.runOnIdle {
                container().vehicle.closeAllOpenings()
                for(opening in listOf(ru.jeland.os.core.VehicleOpening.FRONT_LEFT,ru.jeland.os.core.VehicleOpening.REAR_RIGHT,ru.jeland.os.core.VehicleOpening.HOOD,ru.jeland.os.core.VehicleOpening.TRUNK))container().vehicle.simulateOpening(opening)
                container().vehicle.simulateMoving(true)
                container().vehicle.simulateTire(ru.jeland.os.core.Wheel.FRONT_LEFT,ru.jeland.os.core.TireReading(1.5,18))
                container().vehicle.simulateTire(ru.jeland.os.core.Wheel.FRONT_RIGHT,ru.jeland.os.core.TireReading(1.9,18))
                container().vehicle.simulateTire(ru.jeland.os.core.Wheel.REAR_LEFT,ru.jeland.os.core.TireReading())
            }
            for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
                ui.runOnIdle {container().theme.setMode(theme)}
                ui.onNodeWithTag("nav-home").performClick()
                ui.onNodeWithTag("vehicle-motion-warning").assertIsDisplayed()
                ui.onNodeWithTag("climate-bar").assertIsDisplayed()
                capture("${theme.name.lowercase()}-tire-door-states")
                ui.onNodeWithTag("nav-music").performClick()
                ui.onNodeWithTag("vehicle-motion-warning").assertIsDisplayed()
                capture("${theme.name.lowercase()}-global-door-warning")
            }
        } finally {
            ui.runOnIdle {
                container().vehicle.closeAllOpenings()
                original.openings.forEach {container().vehicle.simulateOpening(it)}
                container().vehicle.simulateMoving(original.mockMoving)
                original.tires.forEach {(wheel,reading)->container().vehicle.simulateTire(wheel,reading)}
                container().theme.setMode(ThemeMode.DARK)
            }
        }
    }
    @Test fun parkedOpeningExpiresAndClosingHidesNoticeEarly() {
        ui.runOnIdle {container().vehicle.closeAllOpenings();container().vehicle.simulateMoving(false)}
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("vehicle-body").performClick()
        ui.waitUntil(5000){ui.onAllNodesWithTag("vehicle-details").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("status-door-front_left").performScrollTo().performClick()
        ui.onNodeWithTag("vehicle-event-notice").assertIsDisplayed()
        ui.onNodeWithText("Открыта водительская дверь · DEMO").assertIsDisplayed()
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("vehicle-status-overlay").assertDoesNotExist()
        capture("parked-opening-notice")
        val noticeBounds=ui.onNodeWithTag("vehicle-event-notice").fetchSemanticsNode().boundsInRoot
        for(tag in listOf("hero-date","hero-clock","weather-widget","fuel-widget")) {
            val bounds=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertFalse("Notice overlaps $tag",noticeBounds.overlaps(bounds))
        }
        ui.waitUntil(6500){ui.onAllNodesWithTag("vehicle-event-notice").fetchSemanticsNodes().isEmpty()}
        org.junit.Assert.assertTrue(ru.jeland.os.core.VehicleOpening.FRONT_LEFT in container().vehicle.state.value.openings)
        capture("parked-notice-expired-car-still-open")
        ui.onNodeWithTag("vehicle-body").performClick()
        ui.onNodeWithTag("status-door-hood").performScrollTo().performClick()
        ui.onNodeWithText("Открыт капот · DEMO").assertIsDisplayed()
        ui.onNodeWithTag("status-door-hood").performClick()
        ui.waitUntil(2000){ui.onAllNodesWithTag("vehicle-event-notice").fetchSemanticsNodes().isEmpty()}
        ui.runOnIdle {container().vehicle.closeAllOpenings()}
        ui.onNodeWithTag("close-popup").performClick()
    }
    @Test fun wheelTouchesOpenCorrectReactivePressureAndTemperature() {
        val original=container().vehicle.state.value
        ui.onNodeWithTag("nav-home").performClick()
        try {
            ui.runOnIdle {
                container().vehicle.closeAllOpenings();container().vehicle.simulateMoving(false)
                ru.jeland.os.core.Wheel.entries.forEachIndexed {i,wheel->container().vehicle.simulateTire(wheel,ru.jeland.os.core.TireReading(2.2+i*.1,11+i))}
            }
            for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
                ui.runOnIdle {container().theme.setMode(theme)}
                ru.jeland.os.core.Wheel.entries.forEachIndexed {i,wheel->
                    ui.onNodeWithTag("wheel-${wheel.name.lowercase()}").performClick()
                    ui.onNodeWithTag("popup-tire_${wheel.name.lowercase()}").assertIsDisplayed()
                    ui.onNodeWithTag("tire-detail-pressure").assertTextEquals(String.format(java.util.Locale.US,"%.1f бар",2.2+i*.1))
                    ui.onNodeWithTag("tire-detail-temperature").assertTextEquals("${11+i} °C")
                    capture("${theme.name.lowercase()}-tire-${wheel.name.lowercase()}")
                    ui.onNodeWithTag("close-popup").performClick()
                }
            }
            ui.onNodeWithTag("tire-front_left").performClick()
            ui.runOnIdle {container().vehicle.simulateTire(ru.jeland.os.core.Wheel.FRONT_LEFT,ru.jeland.os.core.TireReading())}
            ui.onNodeWithTag("tire-detail-pressure").assertTextEquals("Нет данных")
            ui.onNodeWithTag("tire-detail-temperature").assertTextEquals("Нет данных")
            ui.onNodeWithTag("close-popup").performClick()
        } finally {ui.runOnIdle {original.tires.forEach {(wheel,reading)->container().vehicle.simulateTire(wheel,reading)};container().theme.setMode(ThemeMode.DARK)}}
    }
    @Test fun allSixDemoControlsDriveSchemeAndPersistentWarning() {
        ui.runOnIdle {container().vehicle.closeAllOpenings();container().vehicle.simulateMoving(false)}
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("vehicle-body").performClick()
        ui.onNodeWithTag("vehicle-details").assertIsDisplayed()
        ru.jeland.os.core.VehicleOpening.entries.forEachIndexed {i,opening->
            ui.onNodeWithTag("status-door-${opening.name.lowercase()}").performScrollTo().performClick()
            ui.waitUntil(5000){container().vehicle.state.value.openings.size==i+1}
        }
        capture("expanded-six-openings")
        ui.onNodeWithTag("status-moving").performScrollTo().performClick()
        ui.onNodeWithTag("vehicle-motion-warning").assertIsDisplayed()
        ui.onNodeWithTag("close-popup").performClick()
        android.os.SystemClock.sleep(4300)
        ui.onNodeWithTag("vehicle-motion-warning").assertIsDisplayed()
        ui.runOnIdle {container().vehicle.simulateMoving(false)}
        ui.onNodeWithTag("vehicle-motion-warning").assertIsDisplayed()
        capture("persistent-warning-until-everything-closed")
        ui.onNodeWithTag("vehicle-body").performClick()
        ui.onNodeWithTag("status-close-all").performScrollTo().performClick()
        ui.waitUntil(2000){ui.onAllNodesWithTag("vehicle-motion-warning").fetchSemanticsNodes().isEmpty()}
        org.junit.Assert.assertTrue(container().vehicle.state.value.openings.isEmpty())
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("vehicle-event-notice").assertDoesNotExist()
        ui.onNodeWithTag("vehicle-status-card").assertIsDisplayed()
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        capture("all-closed-no-notifications")
    }
}
