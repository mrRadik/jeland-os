#requires -version 5.1
# Import only. Captures installer state without sending input, changing permissions or installing APKs.
function Save-JelandInstallerDiagnostics {
    param([Parameter(Mandatory=$true)][string]$Root,[Parameter(Mandatory=$true)][string]$Serial,
        [Parameter(Mandatory=$true)][string]$Out,[switch]$IncludeApk)
    $destination=Join-Path $Out 'update-installer'
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $checks=New-Object Collections.Generic.List[object]
    $save={param([string]$name,[string[]]$arguments,[int]$seconds=12)
        $answer=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments $arguments -TimeoutSeconds $seconds
        @("EXIT=$($answer.Code)","TIMED_OUT=$($answer.TimedOut)",$answer.Text) |
            Out-File -LiteralPath (Join-Path $destination $name) -Encoding utf8
        $checks.Add([pscustomobject]@{File=$name;Exit=$answer.Code;TimedOut=$answer.TimedOut})
        return $answer
    }
    # Snapshot focus before the UI inspector: inspection itself can change timing.
    $null=& $save 'window-windows.txt' @('shell','dumpsys','window','windows')
    $null=& $save 'activity-activities.txt' @('shell','dumpsys','activity','activities')
    $null=& $save 'input.txt' @('shell','dumpsys','input')
    $null=& $save 'os-install-permission.txt' @('shell','cmd','appops','get','--user','0','ru.jeland.os','REQUEST_INSTALL_PACKAGES')
    $null=& $save 'installer-logcat.txt' @('logcat','-d','-b','main','-b','system','-t','2500','-v','threadtime',
        'PackageInstaller:*','PackageInstallerActivity:*','InstallStart:*','InstallStaging:*','PackageManager:*',
        'ActivityTaskManager:*','WindowManager:*','InputDispatcher:*','InputManager:*','JelandOSVehicle:*','AndroidRuntime:E','*:S')
    $remoteUi='/data/local/tmp/jeland-installer-'+[Guid]::NewGuid().ToString('N')+'.xml'
    try {
        $dump=& $save 'ui-dump.txt' @('shell','uiautomator','dump',$remoteUi) 20
        if($dump.Code -eq 0 -and -not $dump.TimedOut){
            $null=& $save 'ui-pull.txt' @('pull',$remoteUi,(Join-Path $destination 'installer-ui.xml'))
        }
    }finally {$null=& $save 'ui-temp-cleanup.txt' @('shell','rm','-f',$remoteUi)}
    $remaining=32L*1024*1024
    foreach($package in @('com.android.packageinstaller','com.google.android.packageinstaller')) {
        $null=& $save ($package+'-package.txt') @('shell','dumpsys','package',$package)
        $paths=& $save ($package+'-paths.txt') @('shell','pm','path',$package)
        if(-not $IncludeApk -or $paths.Code -ne 0 -or $paths.TimedOut){continue}
        $index=0
        foreach($line in ($paths.Text -split '\r?\n')) {
            # Only paths returned for these two fixed installer packages; no app-data export.
            if($line -notmatch '^package:(/[A-Za-z0-9_+@=./~-]+\.apk)\s*$'){continue}
            $remoteApk=$Matches[1]
            if($remoteApk -match '/\.\./' -or $index -ge 8){continue}
            $size=& $save ($package+"-$index-size.txt") @('shell','stat','-c','%s',$remoteApk)
            $bytes=0L
            if($size.Code -eq 0 -and -not $size.TimedOut -and [long]::TryParse($size.Text.Trim(),[ref]$bytes) -and $bytes -gt 0 -and $bytes -le $remaining) {
                $target=Join-Path $destination ($package+"-$index.apk")
                $pull=& $save ($package+"-$index-pull.txt") @('pull',$remoteApk,$target) 30
                $remaining-=$bytes
                if($pull.Code -eq 0 -and -not $pull.TimedOut -and (Test-Path -LiteralPath $target -PathType Leaf)) {
                    Get-FileHash -LiteralPath $target -Algorithm SHA256 | Select-Object Hash |
                        ConvertTo-Json | Out-File -LiteralPath ($target+'.sha256.json') -Encoding utf8
                }
            }
            $index++
        }
    }
    $checks | Export-Csv -LiteralPath (Join-Path $destination 'checks.csv') -NoTypeInformation -Encoding utf8
    return $destination
}
