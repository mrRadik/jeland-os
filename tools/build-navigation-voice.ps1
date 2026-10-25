param([string]$Output=(Join-Path $PSScriptRoot '../app/src/main/res/raw'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $Output -Force | Out-Null
$speaker=New-Object -ComObject SAPI.SpVoice
$russian=@($speaker.GetVoices() | Where-Object {$_.GetDescription() -match 'Irina.*Russian'})[0]
if(-not $russian){throw 'Install a local Russian Windows SAPI voice first.'}
$speaker.Voice=$russian
$speaker.Rate=0
$phrases=@{
  left_ahead='Впереди поверните налево';left_now='Поверните налево'
  right_ahead='Впереди поверните направо';right_now='Поверните направо'
  straight_ahead='Продолжайте движение прямо';straight_now='Продолжайте движение прямо'
  uturn_ahead='Впереди развернитесь';uturn_now='Развернитесь'
  roundabout_ahead='Впереди круговое движение';roundabout_now='Въезжайте на круговое движение'
  arrive_ahead='Вы приближаетесь к месту назначения';arrive_now='Вы прибыли к месту назначения'
}
# Shared distance prefixes keep the APK small; Android joins prefix + maneuver as one WAV.
$tens=@{30='тридцать';40='сорок';50='пятьдесят';60='шестьдесят';70='семьдесят';80='восемьдесят';90='девяносто'}
$hundreds=@{100='сто';200='двести';300='триста';400='четыреста';500='пятьсот';600='шестьсот';700='семьсот';800='восемьсот';900='девятьсот'}
foreach($distance in 30..1000 | Where-Object {$_ % 10 -eq 0}) {
  if($distance -eq 1000){$words='один километр'}else {
    $parts=@()
    $hundred=[int]([math]::Floor($distance/100)*100)
    $ten=$distance % 100
    if($hundred){$parts+=$hundreds[$hundred]}
    if($ten -eq 10){$parts+='десять'}elseif($ten -eq 20){$parts+='двадцать'}elseif($ten){$parts+=$tens[$ten]}
    $words=($parts -join ' ')+' метров'
  }
  $phrases['distance_'+$distance]='Через '+$words
}
foreach($entry in $phrases.GetEnumerator()) {
  $stream=New-Object -ComObject SAPI.SpFileStream
  try {
    $stream.Format.Type=22 # 22.05 kHz 16-bit mono PCM, no network synthesis.
    $stream.Open((Join-Path $Output ('nav_'+$entry.Key+'.wav')),3,$false)
    $speaker.AudioOutputStream=$stream
    [void]$speaker.Speak($entry.Value)
  }finally{$stream.Close()}
}
