param(
    [Parameter(Mandatory = $true)] [string] $DecodedApk
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath $DecodedApk -PathType Container)) {
    throw "Decoded APK directory was not found: $DecodedApk"
}

$matches = @(
    Get-ChildItem -LiteralPath $DecodedApk -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' } |
        ForEach-Object {
            $candidate = Join-Path $_.FullName 'cl\a.smali'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $candidateText = [IO.File]::ReadAllText($candidate)
                if ($candidateText.Contains('.class public final Lcl/a;') -or $candidateText.Contains('.class public Lcl/a;')) {
                    Get-Item -LiteralPath $candidate
                }
            }
        }
)
if ($matches.Count -ne 1) { throw "Expected exactly one HoneyBoard Lcl/a class, found $($matches.Count)." }

$file = $matches[0]
$text = [IO.File]::ReadAllText($file.FullName)
$old = '(?m)^\s*if-nez v3, :cond_2\s+if-eqz v5, :cond_5\s*$'
$new = @'
    # Nemotron: honor the keyboard's Auto caps preference even when EditorInfo
    # requests sentence capitalization. Manual Shift remains unaffected.
    if-eqz v3, :cond_5
'@

if ($text.Contains('Nemotron: honor the keyboard''s Auto caps preference')) { return }
if (-not [regex]::IsMatch($text, $old)) {
    throw "Could not find the expected autocapitalization branch in $($file.FullName)."
}
$text = [regex]::Replace($text, $old, "`n$new", 1)
[IO.File]::WriteAllText($file.FullName, $text, [Text.UTF8Encoding]::new($false))
Write-Host "Patched HoneyBoard autocapitalization preference: $($file.FullName)"
