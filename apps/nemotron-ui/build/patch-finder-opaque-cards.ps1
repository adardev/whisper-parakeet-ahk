param(
    [Parameter(Mandatory = $true)] [string] $DecodedRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Finder cards use four drawable variants depending on card type and appearance.
# Give all of them a fully opaque charcoal fill while preserving Samsung's
# rounded corners, dimensions, and touch effects.
$color = '#ff242629'
$drawables = @(
    'search_card_background.xml',
    'search_card_background_dark.xml',
    'search_card_background_light.xml',
    'search_card_background_medium.xml'
)

foreach ($name in $drawables) {
    $path = Join-Path $DecodedRoot "res/drawable/$name"
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing Finder card drawable: $path" }
    $xml = [IO.File]::ReadAllText($path)
    $updated = [regex]::Replace(
        $xml,
        '(<solid\s+android:color=")[^"]+("\s*/>)',
        ('$1' + $color + '$2'),
        [Text.RegularExpressions.RegexOptions]::Singleline
    )
    if ($updated -ceq $xml) { throw "Could not patch Finder card fill: $name" }
    if ([regex]::Matches($updated, 'android:color="' + [regex]::Escape($color) + '"').Count -ne 1) {
        throw "Unexpected solid fill count in Finder card drawable: $name"
    }
    [IO.File]::WriteAllText($path, $updated, [Text.UTF8Encoding]::new($false))
}

Write-Output 'Finder card backgrounds are opaque (#ff242629); layout and corner radius remain stock.'
