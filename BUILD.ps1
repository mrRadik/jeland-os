param([switch]$Tests,[switch]$Release)
$ErrorActionPreference='Stop'
# Uses the caller's JDK/Android SDK, or Android Studio's local.properties.
# Release output is unsigned. Signing credentials are intentionally not stored here.
$gradle=Join-Path $PSScriptRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) { throw 'Gradle Wrapper is missing.' }
$tasks=@($(if ($Release) { 'assembleRelease' } else { 'assembleDebug' }))
if ($Tests) { $tasks+=@('testDebugUnitTest','compileDebugAndroidTestKotlin') }
Push-Location $PSScriptRoot
try {
    & $gradle @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
