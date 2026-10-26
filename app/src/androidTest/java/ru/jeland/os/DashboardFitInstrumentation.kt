package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

class DashboardFitInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun decimalTemperatureAndFavoriteLabelsFitWithLargeHeadUnitFont() {
        val names=listOf("Apple CarPlay","Музыка","Яндекс Навигатор","RuStore")
        val apps=names.mapIndexed {i,name->LauncherApp("qa-$i","qa.$i","qa.$i.Main",name)}
        val favorites=AppsState(apps,apps.map {it.id},loading=false)
        val scale=mutableStateOf(1.3f)
        ui.runOnIdle {
            val model=ViewModelProvider(ui.activity)[MainViewModel::class.java]
            ui.activity.setContent {
                val current=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(current.density,scale.value)) {
                    JelandTheme(false,DashboardStyle.MINIMAL) {
                        Column(Modifier.width(360.dp)) {
                            DashboardClock(Modifier.width(230.dp).height(112.dp),compact=true,outlined=false,
                                temperature=WeatherState(demo=false,outsideCelsius=13.5))
                            CompositionLocalProvider(LocalFramelessDashboard provides true) {
                                FavoriteAppsWidget(favorites,model,Modifier.fillMaxWidth().height(128.dp),compact=true){}
                            }
                        }
                    }
                }
            }
        }
        for(font in listOf(1.3f,1.6f)) {
            ui.runOnIdle {scale.value=font}
            for(tag in listOf("outside-temperature","hero-clock","hero-date")) {
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                assertTrue("Text layout for $tag",layouts.isNotEmpty())
                val layout=layouts.single()
                assertFalse("$tag at $font: size=${layout.size}, constraints=${layout.layoutInput.constraints}, font=${layout.layoutInput.style.fontSize}, lineHeight=${layout.layoutInput.style.lineHeight}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, line=${layout.getLineRight(0)}",layout.hasVisualOverflow)
                assertEquals("$tag is a single line",1,layouts.single().lineCount)
            }
            repeat(4) {index->
                val parent=ui.onNodeWithTag("favorite-home-card-$index").fetchSemanticsNode().boundsInRoot
                for(child in listOf("favorite-home-icon-$index","favorite-home-title-$index")) {
                    val b=ui.onNodeWithTag(child,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                    assertTrue("$child stays within card at $font",b.top>=parent.top&&b.bottom<=parent.bottom)
                }
            }
        }
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-hu-fixes").apply {mkdirs()}
        ui.onRoot().captureToImage().asAndroidBitmap().let {bitmap->File(folder,"large-font.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
}
