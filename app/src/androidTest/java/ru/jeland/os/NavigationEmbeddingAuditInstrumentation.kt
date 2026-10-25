package ru.jeland.os

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.navigation.NavigatorIntents
import java.io.File

/** Capability check only: no navigation launch, capture of other apps, root or grants. */
@RunWith(AndroidJUnit4::class)
class NavigationEmbeddingAuditInstrumentation {
    @Test fun ordinaryAndroid11AppCannotHostInstalledNavigatorsOnOwnDisplay() {
        assumeTrue("Android 11 audit",Build.VERSION.SDK_INT==30)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val lines=mutableListOf("SDK=${Build.VERSION.SDK_INT}","Host=${context.packageName}")
        for(permission in listOf("ACTIVITY_EMBEDDING","INTERNAL_SYSTEM_WINDOW","MANAGE_ACTIVITY_TASKS","INJECT_EVENTS")) {
            val granted=context.checkSelfPermission("android.permission.$permission")==PackageManager.PERMISSION_GRANTED
            lines.add("$permission=$granted");assertFalse(permission,granted)
        }
        val reader=ImageReader.newInstance(640,480,PixelFormat.RGBA_8888,2)
        val display=context.getSystemService(DisplayManager::class.java).createVirtualDisplay("Jeland-navigation-capability-audit",640,480,160,
            reader.surface,DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
        try {
            assertNotNull(display)
            val manager=context.getSystemService(ActivityManager::class.java)
            for(provider in NavigationProvider.entries) {
                val launch=context.packageManager.getLaunchIntentForPackage(provider.packageName)
                assertNotNull("Install official ${provider.title} locally for the audit",launch)
                val intents=listOf(launch!!,NavigatorIntents.route(provider,NavigationPlace("audit","Audit",55.16,61.4)))
                for((index,intent) in intents.withIndex()) {
                    val allowed=manager.isActivityStartAllowedOnDisplay(context,display.display.displayId,intent)
                    lines.add("${provider.name}.${if(index==0)"launch"else"route"}.allowedOnOwnDisplay=$allowed")
                    assertFalse("${provider.title} cannot be embedded by this ordinary app",allowed)
                }
            }
        } finally {
            display?.release();reader.close()
            val folder=File(context.getExternalFilesDir(null),"qa-navigation-embedding").apply {mkdirs()}
            File(folder,"capabilities.txt").writeText(lines.joinToString("\n"))
        }
    }
}
