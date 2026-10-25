package ru.jeland.os

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.RunListener
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Optional runner listener: preserve the local user's DEMO preferences around a whole QA run. */
class QaPreferenceGuard: RunListener() {
    private var original: Map<String,*>?=null
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("jelandos_mock_only",0)
    private val folder get()=File(context.getExternalFilesDir(null),"qa-0.4.5").apply {mkdirs()}
    private fun json(values: Map<String,*>): String=JSONObject().apply {
        values.toSortedMap().forEach {(key,value)->put(key,if(value is Set<*>)JSONArray(value.filterIsInstance<String>().sorted())else value)}
    }.toString(2)
    override fun testRunStarted(description: Description) {
        original=prefs.all.mapValues {(_,value)->if(value is Set<*>)value.toSet()else value}
        File(folder,"preferences-before-tests.json").writeText(json(original!!))
    }
    override fun testRunFinished(result: Result) {
        val before=original?:return
        val editor=prefs.edit().clear()
        before.forEach {(key,value)->when(value){
            is String->editor.putString(key,value);is Boolean->editor.putBoolean(key,value)
            is Int->editor.putInt(key,value);is Long->editor.putLong(key,value);is Float->editor.putFloat(key,value)
            is Set<*>->editor.putStringSet(key,value.filterIsInstance<String>().toSet())
        }}
        check(editor.commit()){ "Failed to restore local preferences after QA" }
        File(folder,"preferences-after-tests.json").writeText(json(prefs.all))
        File(folder,"test-result.txt").writeText("Tests=${result.runCount}; Failures=${result.failureCount}; PreferencesRestored=true")
    }
}
