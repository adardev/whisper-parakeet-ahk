param(
    [Parameter(Mandatory = $true)] [string] $CoreApk,
    [Parameter(Mandatory = $true)] [string] $KeyboardApk,
    [Parameter(Mandatory = $true)] [string] $UiApk,
    [Parameter(Mandatory = $true)] [string] $RollbackApk,
    [string] $Adb = "$env:USERPROFILE\adb\adb.exe",
    [string] $Serial = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$uiPackage = 'com.nemotron.voiceime2'
$corePackage = 'com.nemotron.core'
$keyboardPackage = 'com.nemotron.keyboard'
$uiIme = "$uiPackage/com.samsung.android.honeyboard.service.HoneyBoardService"
$keyboardIme = "$keyboardPackage/com.samsung.android.honeyboard.service.HoneyBoardService"
$homeActivity = "$uiPackage/com.sec.android.app.launcher.activities.LauncherActivity"

foreach ($file in @($CoreApk, $KeyboardApk, $UiApk, $RollbackApk, $Adb)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Required file not found: $file" }
}
$CoreApk = (Resolve-Path -LiteralPath $CoreApk).Path
$KeyboardApk = (Resolve-Path -LiteralPath $KeyboardApk).Path
$UiApk = (Resolve-Path -LiteralPath $UiApk).Path
$RollbackApk = (Resolve-Path -LiteralPath $RollbackApk).Path

if (-not $Serial) {
    $deviceRows = @(& $Adb devices -l 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Could not query ADB devices: $($deviceRows -join ' ')" }
    $online = @($deviceRows | ForEach-Object {
        if ($_ -match '^([^\s]+)\s+device(?:\s|$)') { $Matches[1] }
    })
    if ($online.Count -eq 0) { throw 'No online ADB device. No package was changed.' }
    if ($online.Count -eq 1) {
        $Serial = $online[0]
    } else {
        # Wireless debugging can expose the same phone through both its IP and
        # TLS/mDNS transports. Resolve those aliases using Android's hardware
        # serial, then prefer the canonical TLS transport when it is unique.
        $physical = @{}
        foreach ($candidate in $online) {
            $id = @(& $Adb -s $candidate shell getprop ro.serialno 2>&1)
            if ($LASTEXITCODE -eq 0) {
                $key = ($id -join '').Trim()
                if ($key) { $physical[$candidate] = $key }
            }
        }
        $physicalIds = @($physical.Values | Sort-Object -Unique)
        if ($physicalIds.Count -ne 1) {
            throw "ADB reports multiple physical devices ($($online -join ', ')); pass -Serial explicitly. No package was changed."
        }
        $canonicalTls = @($physical.Keys | Where-Object {
            $physical[$_] -eq $physicalIds[0] -and $_ -match '_adb-tls-connect\._tcp$' -and
                $_ -notmatch '\s+\(\d+\)\._adb-tls-connect\._tcp$'
        })
        if ($canonicalTls.Count -eq 1) { $Serial = $canonicalTls[0] }
        else { $Serial = @($physical.Keys | Where-Object { $physical[$_] -eq $physicalIds[0] } | Select-Object -First 1)[0] }
    }
    Write-Host "Using ADB transport $Serial"
}

function Invoke-Device([string[]] $Arguments) {
    $result = @(& $Adb -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($($Arguments -join ' ')): $($result -join ' ')" }
    return $result
}

if ((Invoke-Device @('get-state') | Select-Object -First 1) -ne 'device') {
    throw "A54 $Serial is not online. No package was changed."
}

$existingCore = Invoke-Device @('shell', 'pm', 'path', $corePackage)
if (@($existingCore | Where-Object { $_ -match '^package:' }).Count -gt 0) {
    throw "Preflight mismatch: $corePackage is already installed. No package was changed; inspect it before reusing this installer."
}
$existingKeyboard = Invoke-Device @('shell', 'pm', 'path', $keyboardPackage)
if (@($existingKeyboard | Where-Object { $_ -match '^package:' }).Count -gt 0) {
    throw "Preflight mismatch: $keyboardPackage is already installed. No package was changed; inspect it before reusing this installer."
}

$homeBefore = (Invoke-Device @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')) -join "`n"
$holdersBefore = (Invoke-Device @('shell', 'cmd', 'role', 'get-role-holders', '--user', '0', 'android.app.role.HOME')) -join "`n"
$imeBefore = (Invoke-Device @('shell', 'settings', 'get', 'secure', 'default_input_method') | Select-Object -Last 1).Trim()
if ($homeBefore -notmatch [regex]::Escape($homeActivity) -or
    $holdersBefore -notmatch [regex]::Escape($uiPackage) -or
    $imeBefore -ne $uiIme) {
    throw "Preflight mismatch: expected Nemotron Home role and its current keyboard. Home='$homeBefore'; role='$holdersBefore'; IME='$imeBefore'. No package was changed."
}

$changed = $false
$coreInstalled = $false
$keyboardInstalled = $false
try {
    Invoke-Device @('install', '--no-incremental', '-r', $CoreApk) | Out-Host
    $changed = $true
    $coreInstalled = $true
    Invoke-Device @('install', '--no-incremental', '-r', $KeyboardApk) | Out-Host
    $keyboardInstalled = $true
    Invoke-Device @('install', '--no-incremental', '-r', $UiApk) | Out-Host

    Invoke-Device @('shell', 'ime', 'enable', $keyboardIme) | Out-Host
    Invoke-Device @('shell', 'ime', 'set', $keyboardIme) | Out-Host
    $homeAfter = (Invoke-Device @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')) -join "`n"
    $imeAfter = (Invoke-Device @('shell', 'settings', 'get', 'secure', 'default_input_method') | Select-Object -Last 1).Trim()
    if ($homeAfter -notmatch [regex]::Escape($homeActivity)) { throw "Nemotron Home did not remain the HOME activity: $homeAfter" }
    if ($imeAfter -ne $keyboardIme) { throw "Nemotron Keyboard was not selected: $imeAfter" }
    $holders = (Invoke-Device @('shell', 'cmd', 'role', 'get-role-holders', '--user', '0', 'android.app.role.HOME')) -join "`n"
    if ($holders -notmatch [regex]::Escape($uiPackage)) { throw "Nemotron UI lost the HOME role: $holders" }
    Invoke-Device @('shell', 'am', 'start', '-W', '-n', $homeActivity) | Out-Host

    $keyboardPath = Invoke-Device @('shell', 'pm', 'path', $keyboardPackage)
    $corePath = Invoke-Device @('shell', 'pm', 'path', $corePackage)
    if (@($keyboardPath | Where-Object { $_ -match '^package:' }).Count -eq 0 -or
        @($corePath | Where-Object { $_ -match '^package:' }).Count -eq 0) {
        throw 'Core or Keyboard package is missing after install.'
    }
    Write-Host "Installed safely. HOME=$homeActivity IME=$keyboardIme"
    Write-Host 'Nemotron, Nemotron Keyboard, and Nemotron UI use separate packages. UI package/data stayed in place; no Home role change or reboot.'
} catch {
    if ($changed) {
        Write-Warning 'Split install verification failed. Restoring the integrated APK in place and the previous keyboard.'
        try {
            Invoke-Device @('install', '--no-incremental', '-r', $RollbackApk) | Out-Host
            Invoke-Device @('shell', 'ime', 'enable', $uiIme) | Out-Host
            Invoke-Device @('shell', 'ime', 'set', $uiIme) | Out-Host
            if ($keyboardInstalled) { Invoke-Device @('uninstall', $keyboardPackage) | Out-Host }
            if ($coreInstalled) { Invoke-Device @('uninstall', $corePackage) | Out-Host }
            $restoredIme = (Invoke-Device @('shell', 'settings', 'get', 'secure', 'default_input_method') | Select-Object -Last 1).Trim()
            if ($restoredIme -ne $uiIme) { throw "Rollback did not restore the original keyboard: $restoredIme" }
            Write-Warning 'Previous Home package data and keyboard selection restored; Home role was not changed.'
        } catch {
            Write-Error "Automatic restore failed; do not reboot. Reinstall the rollback APK manually: $RollbackApk`n$($_.Exception.Message)"
        }
    }
    throw
}
