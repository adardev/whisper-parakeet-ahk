$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$server = Start-Process powershell.exe -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $root 'start-server.ps1')) -WorkingDirectory $root -PassThru
Start-Sleep -Seconds 3
$ahk = Get-ChildItem "$env:USERPROFILE\Documents" -Recurse -File -Filter 'AutoHotkey.exe' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match '\\runtime\\x64\\AutoHotkey\.exe$' } | Select-Object -First 1
if (-not $ahk) { $ahk = Get-Command AutoHotkey.exe -ErrorAction SilentlyContinue }
if (-not $ahk) { throw 'No encontré AutoHotkey v2. Instálalo o ejecuta HandySeparate.ahk con AutoHotkey v2.' }
$ahkPath = if ($ahk.FullName) { $ahk.FullName } else { $ahk.Source }
Start-Process $ahkPath -ArgumentList (Join-Path $root 'HandySeparate.ahk') -WorkingDirectory $root
Write-Host "Handy separado iniciado. Ctrl+Space inicia/detiene; servidor PID $($server.Id)."
