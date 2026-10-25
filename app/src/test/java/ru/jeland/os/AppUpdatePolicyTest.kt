package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class AppUpdatePolicyTest {
    private val root="https://github.com/mrRadik/jeland-os/releases/download/v0.9.30/"
    private val manifest=AppUpdateManifest(1,"ru.jeland.os",71,"0.9.30",30,"JelandOS-0.9.30.apk",218_000_000,"a".repeat(64))
    private fun candidate(m:AppUpdateManifest=manifest,assets:List<UpdateAsset> = listOf(UpdateAsset(m.apkName,root+m.apkName,m.size)),url:String=root+AppUpdatePolicy.MANIFEST)=
        AppUpdatePolicy.candidate(m,assets,"v0.9.30",url)
    private fun rejected(block:()->Unit) {try {block();fail("Must reject unsafe metadata")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun validManifestSelectsExactlyTheDeclaredApk() {
        val expected=candidate();assertEquals(manifest,expected.manifest);assertEquals(root+manifest.apkName,expected.apk.url)
    }
    @Test fun unrelatedAndAmbiguousApksAreNeverSelected() {
        rejected {candidate(assets=emptyList())}
        rejected {candidate(assets=listOf(UpdateAsset("Probe.apk",root+"Probe.apk",manifest.size)))}
        val same=UpdateAsset(manifest.apkName,root+manifest.apkName,manifest.size)
        rejected {candidate(assets=listOf(same,same))}
    }
    @Test fun otherApplicationSchemaAndMalformedVersionsAreRejected() {
        for(m in listOf(manifest.copy(applicationId="ru.other"),manifest.copy(schema=2),manifest.copy(versionCode=0),
            manifest.copy(versionName=" "),manifest.copy(minSdk=1)))rejected {candidate(m)}
    }
    @Test fun missingOrInvalidDigestIsRejected() {
        for(hash in listOf("","a".repeat(63),"g".repeat(64)))rejected {candidate(manifest.copy(sha256=hash))}
        candidate(manifest.copy(sha256="A".repeat(64)))
    }
    @Test fun OversizeZeroAndMismatchAreRejected() {
        for(size in listOf(0L,-1L,AppUpdatePolicy.MAX_APK_BYTES+1))rejected {candidate(manifest.copy(size=size))}
        rejected {candidate(assets=listOf(UpdateAsset(manifest.apkName,root+manifest.apkName,manifest.size-1)))}
    }
    @Test fun pathTraversalAndInvalidNamesAreRejected() {
        for(name in listOf("../JelandOS-test.apk","JelandOS-../evil.apk","Probe.apk","JelandOS-test.apk.exe"))rejected {candidate(manifest.copy(apkName=name))}
    }
    @Test fun onlyHttpsExactRepoAssetsWithoutCredentialsAreAccepted() {
        for(url in listOf(root.replace("https:","http:")+manifest.apkName,
            root.replace("github.com","github.com.evil.test")+manifest.apkName,
            root.replace("mrRadik/jeland-os","other/jeland-os")+manifest.apkName,
            root.replace("github.com","user@github.com")+manifest.apkName,
            root+"../"+manifest.apkName,root+manifest.apkName+"?token=secret",root+manifest.apkName+"#fragment")) {
            assertFalse(url,AppUpdatePolicy.releaseUrl(url,manifest.apkName))
        }
    }
    @Test fun apkAndManifestMustBelongToSameRelease() {
        rejected {candidate(assets=listOf(UpdateAsset(manifest.apkName,root.replace("v0.9.30","v0.9.29")+manifest.apkName,manifest.size)))}
        rejected {candidate(url=root.replace("github.com","example.com")+AppUpdatePolicy.MANIFEST)}
    }
    @Test fun installRequiresNewerVersionMatchingMetadataAndCurrentSigner() {
        val installed=UpdateApkIdentity("ru.jeland.os",70,"0.9.29",30,setOf("current-certificate"))
        val apk=installed.copy(versionCode=71,versionName="0.9.30")
        AppUpdatePolicy.requireInstallable(manifest,installed,apk,30)
        for(bad in listOf(apk.copy(versionCode=70),apk.copy(versionCode=72),apk.copy(versionName="other"),apk.copy(minSdk=31),
            apk.copy(applicationId="other"),apk.copy(signers=emptySet()),apk.copy(signers=setOf("other-certificate"))))
            rejected {AppUpdatePolicy.requireInstallable(manifest,installed,bad,30)}
        rejected {AppUpdatePolicy.requireInstallable(manifest,installed,apk,29)}
    }
    @Test fun versionCodeNotVersionNameControlsDowngradeProtection() {
        val installed=UpdateApkIdentity("ru.jeland.os",71,"0.1",30,setOf("key"))
        val apk=installed.copy(versionCode=70,versionName="99.99")
        rejected {AppUpdatePolicy.requireInstallable(manifest.copy(versionCode=70,versionName="99.99"),installed,apk,30)}
    }
    @Test fun automaticChecksAreThrottledAndClockRollbackDoesNotBlockForever() {
        val now=10_000_000L
        assertTrue(AppUpdatePolicy.due(0,now));assertFalse(AppUpdatePolicy.due(now,now))
        assertFalse(AppUpdatePolicy.due(now,now+AppUpdatePolicy.CHECK_INTERVAL-1))
        assertTrue(AppUpdatePolicy.due(now,now+AppUpdatePolicy.CHECK_INTERVAL))
        assertTrue(AppUpdatePolicy.due(now,now-1))
    }
}
