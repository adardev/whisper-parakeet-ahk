param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [string]$Adb = 'adb',
    [string]$Serial = ''
)
$ErrorActionPreference = 'Stop'
$deviceArgs = if ($Serial) { @('-s', $Serial) } else { @() }
function Invoke-Device([string[]]$CommandArgs) {
    & $Adb @deviceArgs @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($CommandArgs -join ' ')" }
}
Invoke-Device @('get-state')
Invoke-Device @('install', '--no-incremental', '-r', (Resolve-Path -LiteralPath $Apk).Path)
# Samsung's keyboard picker and Recents retain dependencies on these packages.
# Disabling the original IME package crashed system_server on the A54.
Invoke-Device @('shell','pm','enable','--user','0','com.samsung.android.honeyboard')
Invoke-Device @('shell','pm','enable','--user','0','com.sec.android.app.launcher')
Invoke-Device @('shell','ime','enable','com.samsung.android.honeyboard/.service.HoneyBoardService')
Invoke-Device @('shell','ime','enable','com.nemotron.voiceime2/com.samsung.android.honeyboard.service.HoneyBoardService')
Invoke-Device @('shell','ime','set','com.nemotron.voiceime2/com.samsung.android.honeyboard.service.HoneyBoardService')
Invoke-Device @('shell','pm','grant','com.nemotron.voiceime2','android.permission.PACKAGE_USAGE_STATS')
Invoke-Device @('shell','appops','set','com.nemotron.voiceime2','GET_USAGE_STATS','allow')
Invoke-Device @('shell','appwidget','grantbind','--package','com.nemotron.voiceime2','--user','0')
Invoke-Device @('shell','cmd','role','add-role-holder','--user','0','android.app.role.HOME','com.nemotron.voiceime2')
Invoke-Device @('shell','settings','get','secure','default_input_method')
Invoke-Device @('shell','cmd','role','get-role-holders','--user','0','android.app.role.HOME')
