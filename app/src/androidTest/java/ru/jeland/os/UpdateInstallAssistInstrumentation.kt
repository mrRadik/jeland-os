package ru.jeland.os

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.AndroidSettingsStore
import ru.jeland.os.data.updates.GitHubUpdateCodec
import ru.jeland.os.data.updates.UpdateInstallAssistService
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

class UpdateInstallAssistInstrumentation {
    @Test fun localQaPreferencesBackupOrRestore() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowSelfUpdateAssistTest")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(runCatching {context.packageManager.getPackageInfo("com.desaysv.ivi.vds.carinfo",0)}.isFailure)
        val prefs=context.getSharedPreferences("jelandos_mock_only",android.content.Context.MODE_PRIVATE)
        val file=File(context.getExternalFilesDir(null),"assist-test-preferences.json")
        if(args.getString("restorePreferences")=="true") {
            val saved=JSONObject(file.readText())
            val edit=prefs.edit().clear()
            saved.keys().forEach {key->val entry=saved.getJSONObject(key)
                when(entry.getString("type")) {
                    "string"->edit.putString(key,entry.getString("value"))
                    "boolean"->edit.putBoolean(key,entry.getBoolean("value"))
                    "int"->edit.putInt(key,entry.getInt("value"))
                    "long"->edit.putLong(key,entry.getLong("value"))
                    "float"->edit.putFloat(key,entry.getDouble("value").toFloat())
                    "set"->{val array=entry.getJSONArray("value");edit.putStringSet(key,(0 until array.length()).map {array.getString(it)}.toSet())}
                    else->error("Unknown preference type")
                }
            }
            check(edit.commit());file.delete()
        }else {
            val saved=JSONObject()
            prefs.all.forEach {(key,value)->val type=when(value) {
                is String->"string";is Boolean->"boolean";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"set"
                else->error("Unknown preference type")}
                saved.put(key,JSONObject().put("type",type).put("value",if(value is Set<*>)org.json.JSONArray(value.toList())else value))
            }
            file.writeText(saved.toString())
            if(args.getString("targetVersionCode")!=null)prepareFixture()
        }
    }
    @Test fun accessibilityServiceIsBoundByAndroidAndLimitedToInstallerPackages() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val info=context.packageManager.getServiceInfo(ComponentName(context,UpdateInstallAssistService::class.java),PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission)
        val xml=info.loadXmlMetaData(context.packageManager,"android.accessibilityservice")!!
        xml.use {
            while(it.eventType!=org.xmlpull.v1.XmlPullParser.START_TAG)it.next()
            val android="http://schemas.android.com/apk/res/android"
            assertEquals(UpdateInstallClickGate.INSTALLERS,it.getAttributeValue(android,"packageNames").split(',').toSet())
            assertNull(it.getAttributeValue(android,"canPerformGestures"))
        }
    }
    /** Prepares an opt-in local fixture before the normal app and accessibility service start. */
    private fun prepareFixture() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowSelfUpdateAssistTest")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(runCatching {context.packageManager.getPackageInfo("com.desaysv.ivi.vds.carinfo",0)}.isFailure)
        val source=File(context.getExternalFilesDir(null),"assist-test-update.apk")
        check(source.isFile)
        val sha=source.inputStream().use {input->val d=MessageDigest.getInstance("SHA-256");val bytes=ByteArray(65536)
            while(true){val n=input.read(bytes);if(n<0)break;d.update(bytes,0,n)};d.digest().joinToString(""){"%02x".format(it)}}
        val version=args.getString("targetVersionCode")!!.toLong()
        val name=args.getString("targetVersionName")!!
        val apkName="JelandOS-$name.apk"
        val manifest=AppUpdateManifest(1,context.packageName,version,name,30,apkName,source.length(),sha)
        val candidate=AppUpdateCandidate(manifest,UpdateAsset(apkName,
            "https://github.com/mrRadik/jeland-os/releases/download/v$name/$apkName",source.length()),"v$name")
        val ready=File(context.filesDir,"updates/ready/JelandOS-$version.apk");ready.parentFile!!.mkdirs();source.copyTo(ready,true)
        val store=AndroidSettingsStore(context)
        store.putDurable("updates.pending",GitHubUpdateCodec.pending(candidate))
        store.putDurable("updates.auto_check","false")
        store.putDurable("updates.install_click_assist","true")
    }
}
