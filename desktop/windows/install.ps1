$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tmp = Join-Path $env:TEMP 'handy-separate-downloads'
New-Item -ItemType Directory -Force $tmp, (Join-Path $root 'native'), (Join-Path $root '..\models'), (Join-Path $root 'packages') | Out-Null

Write-Host 'Descargando Python portable...'
$pyZip = Join-Path $tmp 'python-3.12.10-embed-amd64.zip'
if (!(Test-Path $pyZip)) { Invoke-WebRequest 'https://www.python.org/ftp/python/3.12.10/python-3.12.10-embed-amd64.zip' -OutFile $pyZip }
if (!(Test-Path (Join-Path $root 'python\python.exe'))) {
  New-Item -ItemType Directory -Force (Join-Path $root 'python') | Out-Null
  Expand-Archive $pyZip (Join-Path $root 'python') -Force
}
$pth = Get-ChildItem (Join-Path $root 'python') -Filter 'python*._pth' | Select-Object -First 1
(Get-Content $pth.FullName) -replace '#import site','import site' | Set-Content $pth.FullName
Add-Content $pth.FullName "../packages"
$getPip = Join-Path $tmp 'get-pip.py'
if (!(Test-Path $getPip)) { Invoke-WebRequest 'https://bootstrap.pypa.io/get-pip.py' -OutFile $getPip }
& (Join-Path $root 'python\python.exe') $getPip --no-warn-script-location

Write-Host 'Instalando dependencias Python...'
& (Join-Path $root 'python\python.exe') -m pip install --target (Join-Path $root 'packages') --only-binary=:all: -r (Join-Path $root 'requirements.txt')

Write-Host 'Descargando motor transcribe.cpp 0.2.3...'
$tar = Join-Path $tmp 'transcribe-native.tar.gz'
if (!(Test-Path $tar)) { Invoke-WebRequest 'https://github.com/handy-computer/transcribe.cpp/releases/download/v0.2.3/transcribe-native-0.2.3-windows-x86_64-cpu-vulkan.tar.gz' -OutFile $tar }
$nativeTmp = Join-Path $tmp 'native-extract'
if (Test-Path $nativeTmp) { Remove-Item $nativeTmp -Recurse -Force }
New-Item -ItemType Directory -Force $nativeTmp | Out-Null
tar -xzf $tar -C $nativeTmp
Copy-Item (Join-Path $nativeTmp 'transcribe-native-windows-x86_64-cpu-vulkan\*') (Join-Path $root 'native') -Recurse -Force

Write-Host 'Descargando modelo multilingual streaming (~473 MB)...'
$model = Join-Path $root '..\models\nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf'
if (!(Test-Path $model)) {
  Invoke-WebRequest 'https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf?download=true' -OutFile $model
}
Write-Host 'Instalación completa. Ejecuta start-server.ps1 y después HandySeparate.ahk.'
