#requires -version 5.1
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'windows-setup.ps1') -LibraryOnly
function Check($Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Reset-Fake {$script:children=@();$script:verifyCode=0;$script:actionCode=0}
function Invoke-WindowsSetupChild([string]$Root,[string]$Script,[string[]]$Arguments) {
    $script:children+=,[pscustomobject]@{Root=$Root;Script=$Script;Arguments=$Arguments}
    if($Arguments -contains '-VerifyOnly'){return $script:verifyCode}
    return $script:actionCode
}
$cases=@(
    { $result=Invoke-WindowsSetupAction -Root 'C:\kit' -Choice 1 -Serial DEVICE;
      Check ($result -eq 0 -and $script:children.Count -eq 2) 'OS dispatch failed';
      Check ($script:children[0].Arguments -contains '-VerifyOnly') 'Integrity not checked first';
      Check ($script:children[1].Script -eq 'install.ps1') 'Wrong OS installer';
      Check (($script:children[1].Arguments -join ' ') -eq '-Serial DEVICE') 'Device lost' },
    { $result=Invoke-WindowsSetupAction -Root 'C:\kit' -Choice 2;
      Check ($result -eq 0 -and $script:children[1].Script -eq 'install-strelka.ps1') 'Strelka dispatch failed';
      Check ($script:children[1].Arguments.Count -eq 0) 'Unwanted device arguments' },
    { $result=Invoke-WindowsSetupAction -Root 'C:\kit' -Choice 3 -Serial DEVICE;
      Check ($result -eq 0 -and $script:children[1].Script -eq 'configure-updates.ps1') 'Updates dispatch failed';
      Check (($script:children[1].Arguments -join ' ') -eq '-Serial DEVICE -Mode allow -Yes') 'Wrong update action' },
    { $script:verifyCode=1;$result=Invoke-WindowsSetupAction -Root 'C:\kit' -Choice 2;
      Check ($result -eq 1 -and $script:children.Count -eq 1) 'Executed action after corrupt bundle' },
    { $script:actionCode=3;$result=Invoke-WindowsSetupAction -Root 'C:\kit' -Choice 2;
      Check ($result -eq 3 -and $script:children.Count -eq 2) 'Unknown result retried or hidden' },
    { $caught=$false;try {Invoke-WindowsSetupAction -Root 'C:\kit' -Choice 'anything'}catch{$caught=$true};
      Check ($caught -and $script:children.Count -eq 0) 'Invalid menu action executed' }
)
foreach($case in $cases){Reset-Fake;& $case}
Write-Host ('PASS: '+$cases.Count+' Windows setup dispatch scenarios; no device writes')
