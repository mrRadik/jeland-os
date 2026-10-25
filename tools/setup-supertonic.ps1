param([string]$Cache=(Join-Path $PSScriptRoot '../.local/supertonic'))
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$assets=Join-Path $project 'app/src/main/assets/voice/supertonic-3-int8'
$source=Get-Content -LiteralPath (Join-Path $assets 'SOURCE.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Path $Cache -Force | Out-Null
function Get-VerifiedFile([string]$Url,[string]$Target,[string]$Hash) {
    if((Test-Path -LiteralPath $Target) -and (Get-FileHash -LiteralPath $Target).Hash -eq $Hash){return}
    $temporary=Join-Path $Cache ([guid]::NewGuid().ToString()+'.download')
    & curl.exe -fL --retry 3 --connect-timeout 20 --max-time 600 --output $temporary $Url
    if($LASTEXITCODE -ne 0){throw "Download failed: $Url"}
    if((Get-FileHash -LiteralPath $temporary).Hash -ne $Hash){throw "Checksum mismatch: $Url"}
    Copy-Item -LiteralPath $temporary -Destination $Target
}
New-Item -ItemType Directory -Path (Join-Path $project 'app/libs') -Force | Out-Null
Get-VerifiedFile $source.aarUrl (Join-Path $project 'app/libs/sherpa-onnx-1.13.8.aar') $source.aarSha256
$archive=Join-Path $Cache 'supertonic-3-int8.tar.bz2'
Get-VerifiedFile $source.modelUrl $archive $source.modelArchiveSha256
$staging=Join-Path $Cache ([guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $staging | Out-Null
$prefix='sherpa-onnx-supertonic-3-tts-int8-2026-05-11'
# Extract only explicit package members, never arbitrary archive paths.
$names=@($source.assets.PSObject.Properties.Name)+@('LICENSE','README.md')
$members=$names | ForEach-Object {"$prefix/$_"}
& tar -xjf $archive -C $staging @members
if($LASTEXITCODE -ne 0){throw 'Model extraction failed'}
foreach($property in $source.assets.PSObject.Properties) {
    $file=Join-Path $staging "$prefix/$($property.Name)"
    if((Get-FileHash -LiteralPath $file).Hash -ne $property.Value){throw "Asset mismatch: $($property.Name)"}
}
foreach($name in $names){Copy-Item -LiteralPath (Join-Path $staging "$prefix/$name") -Destination (Join-Path $assets $name)}
Write-Host 'Supertonic model and AAR verified. Licenses and SOURCE.json remain in the project.'
