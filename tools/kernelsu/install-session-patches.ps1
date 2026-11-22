param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$module = Join-Path $PSScriptRoot 'nemotron-session-patches'
$stage = '/data/local/tmp/nemotron-session-patches-update'
& $Adb get-state
if ($LASTEXITCODE -ne 0) { throw 'ADB device unavailable' }
& $Adb shell mkdir -p $stage
if ($LASTEXITCODE -ne 0) { throw 'Cannot create staging directory' }
foreach ($name in @('module.prop','boot-completed.sh','oneui-control.sh','honeyboard-control.sh','settings-app-filter-control.sh','dialer-control.sh','android-auto-rail-control.sh','waze-control.sh','install-update.sh')) {
    & $Adb push (Join-Path $module $name) "$stage/$name"
    if ($LASTEXITCODE -ne 0) { throw "Cannot push $name" }
}
& $Adb shell 'su -mm -c "sh /data/local/tmp/nemotron-session-patches-update/install-update.sh"'
if ($LASTEXITCODE -ne 0) { throw 'Installation failed' }
