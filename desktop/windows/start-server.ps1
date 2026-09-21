$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = Join-Path $root 'python\python.exe'
$env:TRANSCRIBE_LIBRARY = Join-Path $root 'native\transcribe.dll'
$env:PYTHONPATH = Join-Path $root 'packages'
& $python (Join-Path $root 'transcriber.py')
