param(
    [Parameter(Mandatory = $true)][string]$CandidateApk,
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$EnableNow
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$adb = 'adb'
$module = Join-Path $repo 'tools\kernelsu\nemotron-session-patches'
$controller = Join-Path $module 'waze-control.sh'
$moduleProp = Join-Path $module 'module.prop'
$deviceModule = '/data/adb/modules/nemotron-session-patches'
$store = '/data/adb/nemotron-session-patches'
$stage = '/data/local/tmp/nemotron-waze-aa-stage'
$remoteCandidate = "$stage/nemotron-waze-aa-candidate-v1.apk"
$remoteHash = "$stage/waze-aa.sha256"
$remoteController = "$stage/waze-control.sh"
$remoteProp = "$stage/module.prop"
$candidateHash = (Get-FileHash -LiteralPath $CandidateApk -Algorithm SHA256).Hash.ToLowerInvariant()

& $adb -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "ADB device unavailable: $Serial" }
$remotePaths = & $adb -s $Serial shell su -c 'pm path com.waze' 2>&1
$stockLine = $remotePaths | Where-Object { $_ -match '^package:' } | Select-Object -First 1
$stockApk = ($stockLine -replace '^package:', '').Trim()
if ($LASTEXITCODE -ne 0 -or $stockApk -notmatch '^/data/app/[^ ]+/com\.waze-[^ ]+/base\.apk$') {
    throw "Could not resolve the installed Waze base APK: $($remotePaths -join ' ')"
}
$remoteStock = & $adb -s $Serial shell su -c "sha256sum '$stockApk'" 2>&1
if ($LASTEXITCODE -ne 0 -or ($remoteStock -join ' ') -notmatch '3ea14a2669439b240bb59aaa28b7a8557829d125cf103ebde196bc5606c0aefc') {
    throw "Installed Waze base APK does not match the build source. Device response: $($remoteStock -join ' ')"
}

$backup = "$store/backup-before-waze-aa-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$prepare = "mkdir -p $stage $store $backup; cp -p $deviceModule/boot-completed.sh $backup/boot-completed.sh; cp -p $deviceModule/module.prop $backup/module.prop"
& $adb -s $Serial shell "su -c '$prepare'" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not back up the existing KernelSU hook.' }


$hashFile = Join-Path $env:TEMP ('waze-aa-hash-' + [guid]::NewGuid().ToString('N') + '.txt')
[System.IO.File]::WriteAllText($hashFile, $candidateHash + "`n", [System.Text.UTF8Encoding]::new($false))
try {
    foreach ($entry in @(
        @($CandidateApk, $remoteCandidate),
        @($hashFile, $remoteHash),
        @($controller, $remoteController),
        @($moduleProp, $remoteProp)
    )) {
        & $adb -s $Serial push $entry[0] $entry[1] | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Failed to stage $($entry[0])" }
    }
} finally {
    Remove-Item -LiteralPath $hashFile -Force -ErrorAction SilentlyContinue
}

 $installScript = @"
#!/system/bin/sh
set -e
mkdir -p $store $deviceModule
cp $remoteCandidate $store/nemotron-waze-aa-candidate-v1.apk.new
cp $remoteHash $store/waze-aa.sha256.new
cp $remoteController $deviceModule/waze-control.sh.new
cp $remoteProp $deviceModule/module.prop.new
test "`$(sha256sum $store/nemotron-waze-aa-candidate-v1.apk.new | cut -d ' ' -f1)" = $candidateHash
chmod 0644 $store/nemotron-waze-aa-candidate-v1.apk.new $store/waze-aa.sha256.new $deviceModule/module.prop.new
chmod 0755 $deviceModule/waze-control.sh.new
mv -f $store/nemotron-waze-aa-candidate-v1.apk.new $store/nemotron-waze-aa-candidate-v1.apk
mv -f $store/waze-aa.sha256.new $store/waze-aa.sha256
mv -f $deviceModule/waze-control.sh.new $deviceModule/waze-control.sh
mv -f $deviceModule/module.prop.new $deviceModule/module.prop
sh -n $deviceModule/waze-control.sh
echo staged_no_reboot
"@
$tempInstall = Join-Path $env:TEMP ('waze-aa-install-' + [guid]::NewGuid().ToString('N') + '.sh')
[System.IO.File]::WriteAllText($tempInstall, $installScript, [System.Text.UTF8Encoding]::new($false))
try {
    & $adb -s $Serial push $tempInstall "$stage/install.sh" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to stage the root installer.' }
} finally {
    Remove-Item -LiteralPath $tempInstall -Force -ErrorAction SilentlyContinue
}
& $adb -s $Serial shell su -c "sh $stage/install.sh"
if ($LASTEXITCODE -ne 0) { throw 'Could not stage the Waze candidate and controller.' }

if ($EnableNow) {
    & $adb -s $Serial shell "su -c 'sh $deviceModule/waze-control.sh enable'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not activate the Waze mount.' }
} else {
    Write-Output 'Staged the manual Waze candidate without enabling it at boot. Use -EnableNow only while the car is parked.'
}
Write-Output "Candidate SHA256: $candidateHash"
