param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference = 'Stop'
$service = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'HoneyBoardService.smali' | Select-Object -First 1
if (-not $service) { throw 'HoneyBoardService was not found.' }
$text = [IO.File]::ReadAllText($service.FullName)
foreach ($hook in @(
    @{Method='onWindowHidden';Call='invoke-static {p0}, Lcom/nemotron/voiceime/record/KeyboardMemoryController;->onHidden(Landroid/inputmethodservice/InputMethodService;)V'},
    @{Method='onWindowShown';Call='invoke-static {}, Lcom/nemotron/voiceime/record/KeyboardMemoryController;->onShown()V'},
    @{Method='onDestroy';Call='invoke-static {}, Lcom/nemotron/voiceime/record/KeyboardMemoryController;->onShown()V'}
)) {
    $pattern = '(?ms)(^\.method public ' + $hook.Method + '\(\)V\r?\n\s*\.locals \d+\r?\n)(.*?)(^\.end method)'
    $match = [regex]::Match($text,$pattern)
    if (-not $match.Success) { throw "Keyboard hook target missing: $($hook.Method)" }
    if (-not $match.Value.Contains($hook.Call)) {
        $replacement = $match.Groups[1].Value + "`n    " + $hook.Call + "`n" + $match.Groups[2].Value + $match.Groups[3].Value
        $text = $text.Substring(0,$match.Index) + $replacement + $text.Substring($match.Index+$match.Length)
    }
}
[IO.File]::WriteAllText($service.FullName,$text,[Text.UTF8Encoding]::new($false))
