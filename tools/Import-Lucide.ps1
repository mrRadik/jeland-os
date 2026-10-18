param([Parameter(Mandatory=$true)][string]$PackagePath)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$mapping=[ordered]@{
 House='house';Workflow='workflow';Pencil='pencil';Trash='trash-2';Music='music-2';Navigation='navigation';Car='car-front';Apps='layout-grid';Settings='settings'
 Plus='plus';Minus='minus';Close='x';Right='chevron-right';Left='chevron-left';Down='chevron-down';Fan='fan';Power='power';Snowflake='snowflake'
 Camera='camera';Fuel='fuel';Cloud='cloud';Heart='heart';Star='star';Play='play';Pause='pause';Next='skip-forward';Previous='skip-back'
 TurnRight='corner-up-right';TurnLeft='corner-up-left';Straight='arrow-up';Search='search';Refresh='rotate-cw';Wifi='wifi';Bluetooth='bluetooth';WifiOff='wifi-off'
 Warning='triangle-alert';Door='door-open';Moon='moon';Sun='sun'
}
function Number($value){[double]::Parse([string]$value,[Globalization.CultureInfo]::InvariantCulture)}
function CirclePath($x,$y,$rx,$ry){"M $($x+$rx),$y A $rx,$ry 0 1 0 $($x-$rx),$y A $rx,$ry 0 1 0 $($x+$rx),$y Z"}
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('// Generated from lucide-static 0.468.0 SVGs by tools/Import-Lucide.ps1. See assets/licenses/lucide.txt.')
$lines.Add('package ru.jeland.os.ui.theme')
$lines.Add('import androidx.compose.ui.graphics.*')
$lines.Add('import androidx.compose.ui.graphics.vector.*')
$lines.Add('import androidx.compose.ui.unit.dp')
$lines.Add('internal object LucideVectors {')
$lines.Add('    private fun icon(name:String,vararg data:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {data.forEach {addPath(PathParser().parsePathString(it).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=2f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)}}.build()')
foreach($item in $mapping.GetEnumerator()) {
    $svgPath=Join-Path $PackagePath "icons/$($item.Value).svg"
    [xml]$svg=Get-Content -LiteralPath $svgPath -Raw
    $paths=[Collections.Generic.List[string]]::new()
    foreach($node in $svg.DocumentElement.ChildNodes) {
        $p=switch($node.LocalName) {
            'path' {$node.GetAttribute('d')}
            'circle' {CirclePath (Number $node.cx) (Number $node.cy) (Number $node.r) (Number $node.r)}
            'ellipse' {CirclePath (Number $node.cx) (Number $node.cy) (Number $node.rx) (Number $node.ry)}
            'line' {"M $($node.x1),$($node.y1) L $($node.x2),$($node.y2)"}
            'polyline' {"M $($node.points)"}
            'polygon' {"M $($node.points) Z"}
            'rect' {
                $x=if($node.HasAttribute('x')){Number $node.x}else{0};$y=if($node.HasAttribute('y')){Number $node.y}else{0}
                $w=Number $node.width;$h=Number $node.height;$r=if($node.HasAttribute('rx')){Number $node.rx}else{0}
                if($r -eq 0){"M $x,$y h $w v $h h $(-$w) Z"}
                else {"M $($x+$r),$y H $($x+$w-$r) Q $($x+$w),$y $($x+$w),$($y+$r) V $($y+$h-$r) Q $($x+$w),$($y+$h) $($x+$w-$r),$($y+$h) H $($x+$r) Q $x,$($y+$h) $x,$($y+$h-$r) V $($y+$r) Q $x,$y $($x+$r),$y Z"}
            }
            '#comment' {''}
            default {throw "Unsupported SVG element $($node.LocalName) in $svgPath"}
        }
        if($p){$paths.Add($p)}
    }
    $data=($paths | ForEach-Object {'"'+($_ -replace '\s+',' ')+'"'}) -join ','
    $lines.Add("    val $($item.Key)=icon(`"Lucide-$($item.Value)`",$data)")
}
$lines.Add('}')
$encoding=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllLines((Join-Path $projectRoot 'app/src/main/java/ru/jeland/os/ui/theme/LucideVectors.kt'),$lines,$encoding)
$licenseDir=Join-Path $projectRoot 'app/src/main/assets/licenses'
New-Item -ItemType Directory -Path $licenseDir -Force > $null
Copy-Item -LiteralPath (Join-Path $PackagePath 'LICENSE') -Destination (Join-Path $licenseDir 'lucide.txt')
Write-Output "Imported $($mapping.Count) pinned Lucide icons; license included in APK."
