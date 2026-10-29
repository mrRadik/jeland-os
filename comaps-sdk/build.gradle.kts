plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val upstream = rootProject.file("third_party/comaps/android/sdk/src/main")
val prepared = layout.buildDirectory.dir("upstream")
val prepareCoMaps by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python", "tools/prepare-comaps.py", "--output", prepared.get().asFile.absolutePath)
    inputs.file(rootProject.file("tools/prepare-comaps.py"))
    inputs.dir(upstream)
    inputs.dir(rootProject.file("third_party/comaps/android/app/src/main/res"))
    inputs.property("sourceCommit", providers.exec {
        commandLine("git", "-C", rootProject.file("third_party/comaps").absolutePath, "rev-parse", "HEAD")
    }.standardOutput.asText)
    outputs.dir(prepared)
}

android {
    namespace = "app.organicmaps.sdk"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    buildFeatures { buildConfig = true }
    sourceSets["main"].apply {
        // Keep application storage/installation policy owned by Jeland OS.
        manifest.srcFile("src/main/AndroidManifest.xml")
        java.srcDir(upstream.resolve("java"))
        res.srcDir(prepared.map { it.dir("res") })
        assets.srcDir(prepared.map { it.dir("assets") })
        jniLibs.srcDir(prepared.map { it.dir("jniLibs") })
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("txt","bin","html","png","json","mwm","ttf","sdf","ui","config","csv","spv","obj") }
    lint { disable += "MissingTranslation" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
tasks.named("preBuild").configure { dependsOn(prepareCoMaps) }

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.customview:customview:1.2.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
}
