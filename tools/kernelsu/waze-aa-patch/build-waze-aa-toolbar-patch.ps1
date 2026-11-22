param(
    [string]$InputApk = "$env:TEMP\waze-base.apk",
    [string]$OutputApk = "$env:TEMP\nemotron-waze-aa-toolbar-v1.apk"
)

$ErrorActionPreference = 'Stop'
$apktool = 'C:\Users\adaredu\develop\apktool\apktool_2.12.1.jar'
$java = 'C:\Users\adaredu\develop\temurin17-task\jdk-17.0.21+9\bin\java.exe'
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
$buildTools = 'C:\Users\adaredu\develop\android-sdk\build-tools\36.0.0'
$keyStore = "$env:USERPROFILE\.android\debug.keystore"
$work = Join-Path $env:TEMP ('nemotron-waze-toolbar-build-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$decoded = Join-Path $work 'decoded'
$rebuilt = Join-Path $work 'rebuilt.apk'
$aligned = Join-Path $work 'aligned.apk'

foreach ($path in @($InputApk, $apktool, $java, $keyStore, "$buildTools\zipalign.exe", "$buildTools\apksigner.bat")) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required build input missing: $path" }
}

$inputHash = (Get-FileHash -LiteralPath $InputApk -Algorithm SHA256).Hash.ToLowerInvariant()
$expectedInputHash = '3ea14a2669439b240bb59aaa28b7a8557829d125cf103ebde196bc5606c0aefc'
if ($inputHash -ne $expectedInputHash) {
    throw "Unexpected Waze base APK hash $inputHash; expected $expectedInputHash. Review a Waze update before rebuilding."
}

New-Item -ItemType Directory -Path $work | Out-Null
& $java -jar $apktool d -f -o $decoded $InputApk
if ($LASTEXITCODE -ne 0) { throw "Apktool decode failed ($LASTEXITCODE)" }

# Only alter Waze's Android Auto map ActionStrip. Do not patch its CarApp
# templates, start screen, suggestions, or route flow.
$toolbar = Join-Path $decoded 'smali_classes5\com\waze\ca\a\c\b.smali'
$toolbarText = [System.IO.File]::ReadAllText($toolbar)
$signature = '.method public static b(Landroidx/car/app/CarContext;ZZZZZLg/h/a/a;Lg/h/a/a;Lg/h/a/a;Lg/h/a/a;Lg/h/a/a;Lg/h/a/a;)Landroidx/car/app/model/ActionStrip;'
$toolbarStart = $toolbarText.IndexOf($signature)
if ($toolbarStart -lt 0) { throw 'Waze AA map action-strip builder was not found.' }
$toolbarEnd = $toolbarText.IndexOf('.end method', $toolbarStart)
if ($toolbarEnd -lt 0) { throw 'Waze AA map action-strip builder has no end marker.' }
$toolbarMethod = $toolbarText.Substring($toolbarStart, $toolbarEnd - $toolbarStart)

# p2 is Waze's dynamic Home/Work Action. Null it before the builder adds it.
$homeWorkAnchor = '    :goto_1'
$homeWorkIndex = $toolbarMethod.IndexOf($homeWorkAnchor)
if ($homeWorkIndex -lt 0) { throw 'Could not locate the Home/Work map action.' }
$homeWorkInsert = $homeWorkIndex + $homeWorkAnchor.Length
$toolbarMethod = $toolbarMethod.Insert($homeWorkInsert, [Environment]::NewLine + '    const/4 p2, 0x0')

# Skip only the Saved Places action block (enum M); Search (enum aj) and
# Settings plus independent host actions remain on the strip.
$savedLabel = '    :cond_9'
$savedIndex = $toolbarMethod.IndexOf($savedLabel)
if ($savedIndex -lt 0) { throw 'Could not locate the Saved Places map action.' }
$toolbarMethod = $toolbarMethod.Insert($savedIndex + $savedLabel.Length, [Environment]::NewLine + '    goto :cond_a')

$toolbarText = $toolbarText.Substring(0, $toolbarStart) + $toolbarMethod + $toolbarText.Substring($toolbarEnd)
[System.IO.File]::WriteAllText($toolbar, $toolbarText, [System.Text.UTF8Encoding]::new($false))

& $java -jar $apktool b $decoded -o $rebuilt
if ($LASTEXITCODE -ne 0) { throw "Apktool build failed ($LASTEXITCODE)" }
& "$buildTools\zipalign.exe" -f -p 4 $rebuilt $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }
& "$buildTools\apksigner.bat" sign --ks $keyStore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $OutputApk $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed ($LASTEXITCODE)" }
& "$buildTools\apksigner.bat" verify --verbose $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

$outputHash = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "Input SHA256:  $inputHash"
Write-Output "Output SHA256: $outputHash"
Write-Output "Output APK:    $OutputApk"
