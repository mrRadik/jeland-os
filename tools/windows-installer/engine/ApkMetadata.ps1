function Read-StrelkaApkInfo([string]$Path) {
    $file=Get-Item -LiteralPath $Path
    if($file.PSIsContainer -or $file.Extension -ine '.apk'){throw 'Нужен обычный APK-файл.'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $entry=$zip.GetEntry('AndroidManifest.xml')
        if($null -eq $entry -or $entry.Length -gt 4MB){throw 'Некорректный AndroidManifest.xml.'}
        $stream=$entry.Open();$buffer=New-Object IO.MemoryStream
        try {$stream.CopyTo($buffer);$data=$buffer.ToArray()}finally {$stream.Dispose();$buffer.Dispose()}
    }finally {$zip.Dispose()}
    function U16([int]$At){if($At -lt 0 -or $At+2 -gt $data.Length){throw 'Обрезанный манифест.'};[BitConverter]::ToUInt16($data,$At)}
    function U32([int]$At){if($At -lt 0 -or $At+4 -gt $data.Length){throw 'Обрезанный манифест.'};[BitConverter]::ToUInt32($data,$At)}
    function Length8([ref]$At) {
        $value=[int]$data[$At.Value];$At.Value++
        if($value -band 128){$value=(($value -band 127) -shl 8) -bor $data[$At.Value];$At.Value++}
        return $value
    }
    function Length16([ref]$At) {
        $value=[int](U16 $At.Value);$At.Value+=2
        if($value -band 32768){$value=(($value -band 32767) -shl 16) -bor (U16 $At.Value);$At.Value+=2}
        return $value
    }
    $strings=@()
    function S([long]$Index){if($Index -eq 4294967295){return $null};if($Index -lt 0 -or $Index -ge $strings.Count){throw 'Некорректная строка AXML.'};return $strings[$Index]}
    if($data.Length -lt 8 -or (U16 0) -ne 3){throw 'APK не содержит бинарный манифест Android.'}
    $total=[long](U32 4)
    if($total -gt $data.Length -or $total -lt 8){throw 'Некорректная длина манифеста.'}
    $package=$null;$version=0L;$major=0L;$versionName='';$offset=[int](U16 2);$manifestCount=0
    while($offset+8 -le $total) {
        $type=U16 $offset;$header=U16 ($offset+2);$size=[long](U32 ($offset+4));$end=$offset+$size
        if($header -lt 8 -or $size -lt $header -or $end -gt $total){throw 'Повреждённый блок AXML.'}
        if($type -eq 1) {
            $count=[long](U32 ($offset+8));$flags=U32 ($offset+16);$start=U32 ($offset+20)
            if($header -lt 28 -or $count -gt 100000 -or $header+$count*4 -gt $size){throw 'Повреждённый пул строк.'}
            $strings=[string[]]::new([int]$count)
            for($i=0;$i -lt $count;$i++) {
                $cursor=[int]($offset+$start+(U32 ($offset+$header+$i*4)))
                if($cursor -lt $offset -or $cursor -ge $end){throw 'Повреждённое смещение строки.'}
                if($flags -band 256) {
                    $null=Length8 ([ref]$cursor);$bytes=Length8 ([ref]$cursor);$encoding=[Text.Encoding]::UTF8
                }else {$bytes=2*(Length16 ([ref]$cursor));$encoding=[Text.Encoding]::Unicode}
                if($bytes -lt 0 -or $cursor+$bytes -gt $end){throw 'Повреждённая строка.'}
                $strings[$i]=$encoding.GetString($data,$cursor,$bytes)
            }
        }elseif($type -eq 258) {
            if($header -lt 16 -or $size -lt 36){throw 'Повреждённый элемент AXML.'}
            $element=S (U32 ($offset+20))
            if($element -eq 'manifest') {
                $manifestCount++
                $attributeStart=U16 ($offset+24);$attributeSize=U16 ($offset+26);$attributeCount=U16 ($offset+28)
                if($attributeSize -lt 20 -or $offset+16+$attributeStart+$attributeSize*$attributeCount -gt $end){throw 'Повреждённые атрибуты манифеста.'}
                for($i=0;$i -lt $attributeCount;$i++) {
                    $at=$offset+16+$attributeStart+$i*$attributeSize
                    $name=S (U32 ($at+4));$kind=$data[$at+15];$value=U32 ($at+16)
                    $namespace=S (U32 $at)
                    if($name -eq 'package' -and $null -eq $namespace -and $kind -eq 3){$package=S $value}
                    elseif($namespace -eq 'http://schemas.android.com/apk/res/android') {
                        if($name -eq 'versionCode' -and $kind -in @(16,17)){$version=[long]$value}
                        elseif($name -eq 'versionCodeMajor' -and $kind -in @(16,17)){$major=[long]$value}
                        elseif($name -eq 'versionName' -and $kind -eq 3){$versionName=S $value}
                    }
                }
            }
        }
        $offset=[int]$end
    }
    if($manifestCount -ne 1 -or [string]::IsNullOrWhiteSpace($package) -or $version -le 0){throw 'Не удалось определить пакет и версию APK.'}
    [pscustomobject]@{Path=$file.FullName;Package=$package;VersionCode=($major*4294967296L+$version);VersionName=$versionName;Sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash}
}
