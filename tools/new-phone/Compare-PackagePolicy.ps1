param(
    [Parameter(Mandatory=$true)][string]$InventoryJson,
    [string]$Policy = (Join-Path $PSScriptRoot 'package-policy.json')
)
$ErrorActionPreference='Stop'
$inv=Get-Content -Raw $InventoryJson | ConvertFrom-Json
$policy=Get-Content -Raw $Policy | ConvertFrom-Json
$installed=@($inv.packages | ForEach-Object { ($_ -split '=')[-1] } | ForEach-Object { $_ -replace '^package:', '' } | Sort-Object -Unique)
$remove=@($policy.remove)
$protected=@($policy.protected)
$blocked=@($remove | Where-Object { $protected -contains $_ })
if ($blocked.Count) { throw "Policy attempts to remove protected packages: $($blocked -join ', ')" }
$present=@($remove | Where-Object { $installed -contains $_ })
$absent=@($remove | Where-Object { $installed -notcontains $_ })
[ordered]@{model=$inv.model; inventoryAt=$inv.capturedAt; planned=$remove; presentOnDevice=$present; alreadyAbsent=$absent; protected=$protected} | ConvertTo-Json -Depth 5
Write-Host 'Report only. This script does not uninstall or disable packages.'
