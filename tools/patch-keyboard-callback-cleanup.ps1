param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$files=@(Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'VoiceRecordService.smali')
if ($files.Count -ne 1) { throw 'Expected one VoiceRecordService class.' }
$text=[IO.File]::ReadAllText($files[0].FullName)
foreach($field in @('main:Landroid/os/Handler;','isRunning:Z','isProcessing:Z','requestedRecordingState:Ljava/lang/Boolean;')) {
    if (-not $text.Contains($field)) { throw "Expected dictation field is missing: $field" }
}
foreach($name in @('cleanup','onDestroy')) {
    $pattern='(?ms)^\.method [^\r\n]*\b'+$name+'\(\)V\r?\n    \.locals 2\r?\n.*?^\.end method'
    $match=[regex]::Match($text,$pattern)
    if (-not $match.Success) { throw "Unexpected dictation lifecycle layout: $name" }
    if ($match.Value.Contains('->removeCallbacksAndMessages(Ljava/lang/Object;)V')) { continue }
    # Restrict this patch to the known two-local layout; no parameter alias
    # registers or extra dependencies are introduced.
    if ($match.Value -match '\bv[2-9]\d*\b') { throw "Unexpected register alias in $name" }
    $instructions=@'

    # nemotron_cancel_pending_dictation_callbacks
    iget-object v0, p0, Lcom/nemotron/voiceime/record/VoiceRecordService;->main:Landroid/os/Handler;
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacksAndMessages(Ljava/lang/Object;)V
'@
    if ($name -eq 'onDestroy') {
        $instructions+=@'

    sput-boolean v1, Lcom/nemotron/voiceime/record/VoiceRecordService;->isRunning:Z
    sput-boolean v1, Lcom/nemotron/voiceime/record/VoiceRecordService;->isProcessing:Z
    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
    sput-object v0, Lcom/nemotron/voiceime/record/VoiceRecordService;->requestedRecordingState:Ljava/lang/Boolean;
'@
    }
    $replacement=[regex]::Replace($match.Value,'(?m)^(    \.locals 2)\r?$', {
        param($m) $m.Groups[1].Value+"`n"+$instructions+"`n"
    },1)
    $text=$text.Substring(0,$match.Index)+$replacement+$text.Substring($match.Index+$match.Length)
}
[IO.File]::WriteAllText($files[0].FullName,$text,[Text.UTF8Encoding]::new($false))
