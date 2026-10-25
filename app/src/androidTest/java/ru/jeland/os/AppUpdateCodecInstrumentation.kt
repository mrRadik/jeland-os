package ru.jeland.os

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.updates.GitHubUpdateCodec
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Codec fixtures only: no network requests, APK downloads or installer starts. */
@RunWith(AndroidJUnit4::class)
class AppUpdateCodecInstrumentation {
    @Test fun installerPermissionAndProviderBoundaryAreDeclared() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val authority="${context.packageName}.updates"
        val provider=context.packageManager.resolveContentProvider(authority,0)!!
        assertFalse(provider.exported);assertTrue(provider.grantUriPermissions)
        @Suppress("DEPRECATION")
        val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
        val ready=FileProvider.getUriForFile(context,authority,File(context.filesDir,"updates/ready/qa-test.apk"))
        assertEquals("content",ready.scheme);assertTrue(ready.path!!.startsWith("/verified_update/"))
        for(path in listOf("updates/staging/qa-test.apk","logs/qa-test.log","updates/ready/../staging/qa-test.apk")) {
            try {FileProvider.getUriForFile(context,authority,File(context.filesDir,path));fail("Must not expose $path")}
            catch(_:IllegalArgumentException){}
        }
    }
    @Test fun pendingMetadataRoundtripsExactlyAndRejectsOtherHosts() {
        val m=AppUpdateManifest(1,"ru.jeland.os",71,"0.9.30",30,"JelandOS-0.9.30.apk",100,"a".repeat(64))
        val c=AppUpdateCandidate(m,UpdateAsset(m.apkName,"https://github.com/mrRadik/jeland-os/releases/download/v0.9.30/${m.apkName}",100),"v0.9.30")
        assertEquals(c,GitHubUpdateCodec.restore(GitHubUpdateCodec.pending(c)))
        try {GitHubUpdateCodec.restore(GitHubUpdateCodec.pending(c).replace("github.com","example.com"));fail("Other host")}
        catch(_:IllegalArgumentException){}
        val invalid=GitHubUpdateCodec.manifestJson(m).put("sha256","")
        try {GitHubUpdateCodec.manifest(invalid);fail("Missing digest")}
        catch(_:IllegalArgumentException){}
    }
    @Test fun metadataWithoutRequiredFieldsIsNotAnEmptySuccessfulUpdate() {
        try {GitHubUpdateCodec.manifest(JSONObject("{}"));fail("Missing metadata")}
        catch(_:org.json.JSONException){}
    }
}
