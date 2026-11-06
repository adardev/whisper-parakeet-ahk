param(
    [string]$Adb = "$env:USERPROFILE\adb\adb.exe",
    [string]$Serial = '',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'runs')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) { $Adb = (Get-Command adb -ErrorAction Stop).Source }
$devices = @(& $Adb devices)
$online = @($devices | Select-String '\tdevice$')
if (-not $Serial) {
    if ($online.Count -ne 1) { throw "Connect exactly one authorized device or pass -Serial. Found $($online.Count)." }
    $Serial = ($online[0].Line -split '\s+')[0]
}
if (-not ($online | Where-Object { $_.Line -match "^$([regex]::Escape($Serial))\s+device$" })) { throw "Device $Serial is not online and authorized." }
function ADB([string[]]$CommandArgs) {
    $result = @(& $Adb -s $Serial @CommandArgs 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($($CommandArgs -join ' ')): $($result -join ' ')" }
    return $result
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $run | Out-Null
$model = (ADB @('shell','getprop','ro.product.model') | Select-Object -First 1).Trim()
$build = (ADB @('shell','getprop','ro.build.display.id') | Select-Object -First 1).Trim()
$packages = ADB @('shell','pm','list','packages','-f','-u') | ForEach-Object { $_.Trim() } | Sort-Object
$packages | Set-Content -LiteralPath (Join-Path $run 'packages-all.txt') -Encoding utf8
(ADB @('shell','pm','list','packages','-3','-f')) | Set-Content (Join-Path $run 'packages-user.txt') -Encoding utf8
(ADB @('shell','getprop')) | Set-Content (Join-Path $run 'getprop.txt') -Encoding utf8
$root = ADB @('shell','su','-M','-c','id')
$rootOk = ($root -join ' ') -match 'uid=0'
$summary = [ordered]@{
    capturedAt = (Get-Date).ToString('o'); serial = $Serial; model = $model; build = $build
    rootAvailable = $rootOk; packages = @($packages | ForEach-Object { $_ -replace '^package:', '' })
}
$summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $run 'inventory.json') -Encoding utf8
Write-Host "Read-only inventory saved to $run"
Write-Host "Device: $model / $build; root available: $rootOk; packages: $($packages.Count)"
Write-Host 'This script does not uninstall, disable, flash, or install anything.'
