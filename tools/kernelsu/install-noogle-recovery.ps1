param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference = 'Stop'
function Invoke-AdbChecked {
    param([string[]]$Arguments)
    $result = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}
$source = Join-Path $PSScriptRoot 'noogle-bootfix'
$stage = '/data/local/tmp/nemotron-noogle-recovery-install'
$module = '/data/adb/modules/nemotron-noogle-bootfix'
$identity = Invoke-AdbChecked -Arguments @('shell', 'su', '-c', 'id')
if ($identity -notmatch 'uid=0\(') { throw 'KernelSU root is not available.' }
Invoke-AdbChecked -Arguments @('shell', 'mkdir', '-p', $stage)
$files = @('repair-gservices.sh', 'boot-completed.sh', 'module.prop')
foreach ($file in $files) {
    $local = Join-Path $source $file
    Invoke-AdbChecked -Arguments @('push', $local, "$stage/$file")
    $expected = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()
    $actual = Invoke-AdbChecked -Arguments @('shell', 'sha256sum', "$stage/$file")
    if (($actual -split '\s+')[0] -ne $expected) { throw "Transfer verification failed: $file" }
}
# Prepare each file beside its destination, validate shell syntax, then rename.
# Helper is published first; hook last. Existing boot marker remains intact.
$command = "set -e; test -d $module; mkdir -p /data/adb/nemotron-backups; backup=/data/adb/nemotron-backups/noogle-hook-before-v11-`$(date +%s); mkdir `"`$backup`"; cp -p $module/boot-completed.sh $module/module.prop `"`$backup/`"; cp $stage/repair-gservices.sh $module/repair-gservices.sh.new; cp $stage/boot-completed.sh $module/boot-completed.sh.new; cp $stage/module.prop $module/module.prop.new; sh -n $module/repair-gservices.sh.new; sh -n $module/boot-completed.sh.new; chmod 755 $module/repair-gservices.sh.new $module/boot-completed.sh.new; chmod 644 $module/module.prop.new; mv $module/repair-gservices.sh.new $module/repair-gservices.sh; mv $module/module.prop.new $module/module.prop; mv $module/boot-completed.sh.new $module/boot-completed.sh"
Invoke-AdbChecked -Arguments @('shell', 'su', '-c', $command)
foreach ($file in $files) {
    $expected = (Get-FileHash -LiteralPath (Join-Path $source $file) -Algorithm SHA256).Hash.ToLowerInvariant()
    $actual = Invoke-AdbChecked -Arguments @('shell', 'su', '-c', "sha256sum $module/$file")
    if (($actual -split '\s+')[0] -ne $expected) { throw "Installed verification failed: $file" }
}
Write-Output 'Installed recovery v1.1; all three hashes verified. No restart performed.'
