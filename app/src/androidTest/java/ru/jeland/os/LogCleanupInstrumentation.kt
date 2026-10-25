package ru.jeland.os

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.ui.LogCleanupButton
import ru.jeland.os.ui.theme.JelandTheme
import ru.jeland.os.data.DiagnosticsCleaner

/** Pure confirmation UI / ownership predicates only; never deletes emulator or vehicle journals. */
class LogCleanupInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun cancellationNeverCallsCleanerAndConfirmationIsExplicitInBothThemes() {
        var confirmed=0;var busy by mutableStateOf(false)
        for(dark in listOf(true,false)) {
            ui.runOnIdle {busy=false}
            ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark){Column {LogCleanupButton(busy){confirmed++}}}}}
            val before=confirmed
            ui.onNodeWithTag("clear-os-logs").performClick()
            ui.onNodeWithText("Системные логи, логи Probe, карты и настройки не затрагиваются.",substring=true).assertExists()
            ui.onNodeWithTag("cancel-clear-os-logs").performClick()
            assertEquals(before,confirmed)
            ui.onNodeWithTag("clear-os-logs").performClick()
            ui.onNodeWithTag("confirm-clear-os-logs").performClick()
            assertEquals(before+1,confirmed)
            ui.runOnIdle {busy=true}
            ui.onNodeWithTag("clear-os-logs").assertIsNotEnabled()
        }
        assertEquals(2,confirmed)
    }
    @Test fun archiveRequiresExactFilenameFolderAndOurOwnMediaStoreOwner() {
        val name="JelandOSLogs_20261022_161000_1234abcd.zip"
        fun own(n:String?=name,path:String?="Download/JelandOS/",owner:String?="ru.jeland.os")=
            DiagnosticsCleaner.ownExport(n,path,owner,"ru.jeland.os")
        assertTrue(own())
        assertFalse(own(owner="ru.jeland.vdbprobe"));assertFalse(own(owner=null))
        assertFalse(own(path="Download/"));assertFalse(own(path="Download/JelandOS/nested/"))
        assertFalse(own(n="other.zip"));assertFalse(own(n="JelandOSLogs_important.zip"))
        assertFalse(own(n="../$name"));assertFalse(own(n=null))
    }
}
