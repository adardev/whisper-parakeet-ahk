param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('bedtime', 'morning')]
    [string]$Phase,
    [string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'NemotronBatteryAudit')
)

$ErrorActionPreference = 'Stop'
$adb = Get-Command adb -ErrorAction Stop | Select-Object -ExpandProperty Source
$connected = @(& $adb devices -l | Select-String '^\S+\s+device(\s|$)')
if ($connected.Count -ne 1) {
    throw "Se requiere exactamente un dispositivo ADB autorizado; detectados: $($connected.Count). No se cambió ningún ajuste."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $OutputDirectory "$stamp-$Phase.txt"
$writer = [System.IO.StreamWriter]::new($output, $false, [System.Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine("phase=$Phase")
    $writer.WriteLine("captured_local=$((Get-Date).ToString('o'))")
    $writer.WriteLine("adb_device=$($connected[0].Line)")

    $captures = @(
        @{ Name = 'battery'; Args = @('shell', 'dumpsys', 'battery') },
        @{ Name = 'batterystats'; Args = @('shell', 'dumpsys', 'batterystats') },
        @{ Name = 'power'; Args = @('shell', 'dumpsys', 'power') },
        @{ Name = 'deviceidle'; Args = @('shell', 'dumpsys', 'deviceidle') },
        @{ Name = 'alarms'; Args = @('shell', 'dumpsys', 'alarm') },
        @{ Name = 'sensors'; Args = @('shell', 'dumpsys', 'sensorservice') },
        @{ Name = 'connectivity'; Args = @('shell', 'dumpsys', 'connectivity') },
        @{ Name = 'wifi'; Args = @('shell', 'dumpsys', 'wifi') },
        @{ Name = 'bluetooth'; Args = @('shell', 'dumpsys', 'bluetooth_manager') }
    )

    foreach ($capture in $captures) {
        $writer.WriteLine("`n===== $($capture.Name) =====")
        $captureArgs = $capture.Args
        $text = & $adb @captureArgs 2>&1
        $exitCode = $LASTEXITCODE
        foreach ($line in $text) { $writer.WriteLine([string]$line) }
        if ($exitCode -ne 0) { $writer.WriteLine("capture_exit_code=$exitCode") }
    }
}
finally {
    $writer.Dispose()
}

Write-Output $output
