package ru.jeland.os

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit recovery of our own interrupted QA run, never an automatic preference reset. */
class QaPreferenceRestoreInstrumentation {
    @Test fun restoreInterruptedRunOnlyWhenExplicitlyRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restoreQaSnapshot")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"qa-0.4.5/preferences-before-tests.json")
        val snapshot=JSONObject(file.readText())
        val preferences=context.getSharedPreferences("jelandos_mock_only",0)
        val editor=preferences.edit().clear()
        snapshot.keys().forEach {key->when(val value=snapshot.get(key)) {
            is String->editor.putString(key,value)
            is Boolean->editor.putBoolean(key,value)
            is Int->editor.putInt(key,value)
            is Long->editor.putLong(key,value)
            is Number->editor.putFloat(key,value.toFloat())
            is JSONArray->editor.putStringSet(key,(0 until value.length()).map {value.getString(it)}.toSet())
            else->error("Unsupported preference in QA snapshot: $key")
        }}
        assertTrue(editor.commit())
        assertEquals(snapshot.length(),preferences.all.size)
    }
}
