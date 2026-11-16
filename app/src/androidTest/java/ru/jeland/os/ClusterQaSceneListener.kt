package ru.jeland.os

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.RunListener
import ru.jeland.os.core.*

/** Isolate cluster UI tests from the emulator map GL driver. Preserve user preferences. */
class ClusterQaSceneListener:RunListener() {
    private val guard=QaPreferenceGuard()
    private var before:InterfacePreferences?=null
    private val app get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JelandApplication).container
    override fun testRunStarted(description:Description) {
        guard.testRunStarted(description)
        before=app.preferences.state.value
        app.preferences.change(before!!.copy(style=DashboardStyle.FREE,
            freeLayout=FreeDashboardLayout.default().remove(DashboardWidgetKind.NAVIGATION)))
    }
    override fun testRunFinished(result:Result) {
        before?.let(app.preferences::change)
        guard.testRunFinished(result)
    }
}
