param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference = 'Stop'
$file = Join-Path $DecodedRoot 'smali_classes3/com/android/systemui/shade/SecNotificationShadeWindowControllerHelperImpl.smali'
$text = [IO.File]::ReadAllText($file)
$method = $text.IndexOf('.method public final apply(')
$call = $text.IndexOf('invoke-virtual {v1}, Lcom/android/systemui/util/SettingsHelper;->isUltraPowerSavingMode()Z', $method)
$branch = $text.IndexOf('if-nez v1, :cond_c', $call)
if ($method -lt 0 -or $call -lt 0 -or $branch -lt 0 -or ($branch - $call) -gt 300) { throw 'Unexpected SystemUI layout; patch refused.' }
$needle = 'if-nez v1, :cond_c'
$text = $text.Remove($branch, $needle.Length).Insert($branch, 'nop')
[IO.File]::WriteAllText($file, $text, [Text.UTF8Encoding]::new($false))
