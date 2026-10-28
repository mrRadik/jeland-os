function Invoke-JelandAdb {
    param([string]$Root,[string]$Serial,[string[]]$Arguments,[int]$TimeoutSeconds=30)
    $adb=Join-Path $Root 'tools\adb.exe'
    if(-not (Test-Path -LiteralPath $adb -PathType Leaf)){throw "Не найден ADB: $adb"}
    $native=@(); if($Serial){$native+=@('-s',$Serial)}; $native+=$Arguments
    $quoted=@($native | ForEach-Object {
        $value=[regex]::Replace($_,'(\\*)"','$1$1\"')
        $value=[regex]::Replace($value,'(\\+)$','$1$1')
        '"'+$value+'"'
    })
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$adb; $info.Arguments=$quoted -join ' '
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.Encoding]::UTF8; $info.StandardErrorEncoding=[Text.Encoding]::UTF8
    $process=New-Object Diagnostics.Process; $process.StartInfo=$info
    try {
        [void]$process.Start()
        $output=$process.StandardOutput.ReadToEndAsync(); $errorOutput=$process.StandardError.ReadToEndAsync()
        $timeout=-not $process.WaitForExit($TimeoutSeconds*1000)
        if($timeout){try{$process.Kill()}catch{}; [void]$process.WaitForExit(3000)}
        if(-not $output.Wait(3000) -or -not $errorOutput.Wait(3000)){throw 'Не удалось завершить чтение вывода ADB.'}
        $code=if($timeout){124}else{$process.ExitCode}
        [pscustomobject]@{Code=$code;TimedOut=$timeout;Text=($output.Result+"`n"+$errorOutput.Result).Trim()}
    }finally{$process.Dispose()}
}

function Select-JelandDevice {
    param([string]$Root,[string]$Serial)
    $list=Invoke-JelandAdb -Root $Root -Arguments @('devices','-l')
    if($list.Code -ne 0){throw "Не удалось получить устройства: $($list.Text)"}
    Write-Host $list.Text
    $ready=@($list.Text -split '\r?\n' | ForEach-Object {if($_ -match '^(\S+)\s+device\b'){$Matches[1]}})
    if($Serial){if($ready -notcontains $Serial){throw "Устройство '$Serial' не авторизовано или отключено."}; return $Serial}
    if($ready.Count -eq 0){throw 'Нет подключённых устройств. Проверьте USB-отладку, кабель и RSA-запрос на экране.'}
    if($ready.Count -eq 1){return $ready[0]}
    for($i=0;$i -lt $ready.Count;$i++){Write-Host "$($i+1). $($ready[$i])"}
    $number=0
    if(-not [int]::TryParse((Read-Host 'Номер устройства'),[ref]$number) -or $number -lt 1 -or $number -gt $ready.Count){throw 'Выбор отменён или неверен.'}
    return $ready[$number-1]
}