param([Parameter(ValueFromRemainingArguments=$true)][string[]]$BuilderArgs)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$environmentPath = Join-Path $projectRoot '.local/map-builder'
$builderPython = Join-Path $environmentPath 'Scripts/python.exe'
try {
    $builderTemp = Join-Path $projectRoot '.local/map-builder-temp'
    [IO.Directory]::CreateDirectory($builderTemp) | Out-Null
    $env:TEMP = $builderTemp
    $env:TMP = $builderTemp
    $env:PIP_CACHE_DIR = Join-Path $projectRoot '.local/map-builder-pip-cache'
    if (-not (Test-Path -LiteralPath $builderPython)) {
        Write-Host 'Preparing Jeland Map Builder (Python 3.12 or newer required)...'
        $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
        $usablePython = $false
        if ($pythonCommand) {
            & python -c "import sys; assert sys.version_info >= (3,12), 'Python 3.12+ required'"
            $usablePython = $LASTEXITCODE -eq 0
        }
        if ($usablePython) {
            & python -m venv $environmentPath
        } elseif (Get-Command py -ErrorAction SilentlyContinue) {
            & py -3.12 -m venv $environmentPath
        } else {
            throw 'Install Python 3.12 x64 from python.org, then retry.'
        }
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create Python environment. Install Python 3.12 x64 from python.org.' }
    }
    $requirements = Join-Path $PSScriptRoot 'requirements.txt'
    $requirementsHash = (Get-FileHash -LiteralPath $requirements -Algorithm SHA256).Hash
    $marker = Join-Path $environmentPath '.requirements.sha256'
    if (-not (Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw).Trim() -ne $requirementsHash) {
        Write-Host 'Installing map tools into the project (first launch needs internet)...'
        & $builderPython -m ensurepip --upgrade --default-pip
        if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare pip in the project environment.' }
        & $builderPython -m pip install --disable-pip-version-check --only-binary=:all: -r $requirements
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Check internet and Python architecture (x64).' }
        [IO.File]::WriteAllText($marker,$requirementsHash)
    }
    $env:PYTHONIOENCODING = 'utf-8'
    $env:PYTHONUTF8 = '1'
    if (-not $BuilderArgs) { $BuilderArgs = @('gui') }
    & $builderPython (Join-Path $PSScriptRoot 'map_builder.py') @BuilderArgs
    exit $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
