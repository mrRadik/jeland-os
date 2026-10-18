param([switch]$Tests)
$ErrorActionPreference='Stop'
# Portable local build setup for this workstation. Android Studio can use its own JDK/SDK.
$buildRoot='C:\Users\nonam\Documents\Codex\2026-10-15\referenced-chatgpt-conversation-this-is-an\work\jelandos-build'
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME='C:\Users\nonam\Documents\Codex\2026-10-15\referenced-chatgpt-conversation-this-is-an\work\android-build\jdk17\jdk-17.0.20.1+1'
}
$env:GRADLE_USER_HOME=Join-Path $buildRoot 'gradle-cache'
$env:ANDROID_USER_HOME=Join-Path $buildRoot 'android-user'
$env:ANDROID_SDK_ROOT=Join-Path $buildRoot 'sdk'
$gradle=Join-Path $PSScriptRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) { $gradle=Join-Path $buildRoot 'gradle-8.9\bin\gradle.bat' }
$tasks=@('assembleDebug')
if ($Tests) { $tasks+=@('testDebugUnitTest','assembleDebugAndroidTest') }
Push-Location $PSScriptRoot
try {
    & $gradle @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
