param(
    [string]$InputApk = "$env:TEMP\waze-base.apk",
    [string]$OutputApk = "$env:TEMP\nemotron-waze-aa-toolbar-v7.apk"
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

# Remove the two requested map-toolbar actions without changing the rest of
# Waze's Car App model or action callbacks.
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

# For the initial suggestion model, use Waze's existing top-level
# NavigationTemplate path, which renders the map without a MapWithContent pane.
# Preserve both Waze action strips and its PanModeListener. Other map content,
# route previews, and navigation remain on Waze's original screen path.
$startScreen = Join-Path $decoded 'smali_classes5\com\waze\car_lib\screens\q.smali'
$startText = [System.IO.File]::ReadAllText($startScreen)
$methodSignature = '.method private final z(Lcom/waze/car_lib/r/v;)V'
$methodStart = $startText.IndexOf($methodSignature)
if ($methodStart -lt 0) { throw 'Waze AA map-screen template method was not found.' }
$methodEnd = $startText.IndexOf('.end method', $methodStart)
if ($methodEnd -lt 0) { throw 'Waze AA map-screen template method has no end marker.' }
$startMethod = $startText.Substring($methodStart, $methodEnd - $methodStart)

# Keep Waze's map pan listener and zoom/pan ActionStrip while swapping only
# the startup presentation into the fullscreen NavigationTemplate.
$panListener = '    invoke-virtual {v1, v2}, Landroidx/car/app/navigation/model/MapController$Builder;->setPanModeListener(Landroidx/car/app/navigation/model/PanModeListener;)Landroidx/car/app/navigation/model/MapController$Builder;'
$panIndex = $startMethod.IndexOf($panListener)
if ($panIndex -lt 0) { throw 'Could not locate the Waze pan-mode listener.' }
$panEnd = $panIndex + $panListener.Length
$startMethod = $startMethod.Insert($panEnd, [Environment]::NewLine + '    move-object/from16 v32, v2')

$mapAction = '    invoke-virtual {v1, v3}, Landroidx/car/app/navigation/model/MapController$Builder;->setMapActionStrip(Landroidx/car/app/model/ActionStrip;)Landroidx/car/app/navigation/model/MapController$Builder;'
$mapActionIndex = $startMethod.IndexOf($mapAction)
if ($mapActionIndex -lt 0) { throw 'Could not locate Waze map-control actions.' }
$startMethod = $startMethod.Insert($mapActionIndex, '    move-object/from16 v31, v3' + [Environment]::NewLine)

$toolbarCall = 'invoke-static/range {v1 .. v12}, Lcom/waze/ca/a/c/b;->b('
$toolbarIndex = $startMethod.IndexOf($toolbarCall)
if ($toolbarIndex -lt 0) { throw 'Could not locate Waze startup action strip.' }
$toolbarResult = '    move-result-object v2'
$toolbarResultIndex = $startMethod.IndexOf($toolbarResult, $toolbarIndex)
if ($toolbarResultIndex -lt 0) { throw 'Could not locate Waze startup action-strip result.' }
$toolbarResultEnd = $toolbarResultIndex + $toolbarResult.Length
$fullscreen = @'

    invoke-virtual/range {p1 .. p1}, Lcom/waze/car_lib/r/v;->b()Lcom/waze/ca/a/c/l;

    move-result-object v3

    if-eqz v3, :nemotron_keep_map_content

    new-instance v3, Landroidx/car/app/navigation/model/NavigationTemplate$Builder;

    invoke-direct {v3}, Landroidx/car/app/navigation/model/NavigationTemplate$Builder;-><init>()V

    invoke-virtual {v3, v2}, Landroidx/car/app/navigation/model/NavigationTemplate$Builder;->setActionStrip(Landroidx/car/app/model/ActionStrip;)Landroidx/car/app/navigation/model/NavigationTemplate$Builder;

    move-result-object v3

    move-object/from16 v14, v31

    invoke-virtual {v3, v14}, Landroidx/car/app/navigation/model/NavigationTemplate$Builder;->setMapActionStrip(Landroidx/car/app/model/ActionStrip;)Landroidx/car/app/navigation/model/NavigationTemplate$Builder;

    move-result-object v3

    move-object/from16 v15, v32

    invoke-virtual {v3, v15}, Landroidx/car/app/navigation/model/NavigationTemplate$Builder;->setPanModeListener(Landroidx/car/app/navigation/model/PanModeListener;)Landroidx/car/app/navigation/model/NavigationTemplate$Builder;

    move-result-object v3

    invoke-virtual {v3}, Landroidx/car/app/navigation/model/NavigationTemplate$Builder;->build()Landroidx/car/app/navigation/model/NavigationTemplate;

    move-result-object v3

    check-cast v3, Landroidx/car/app/model/Template;

    move-object/from16 v1, p0

    invoke-virtual {v1, v3}, Lcom/waze/car_lib/screens/core/n;->w(Landroidx/car/app/model/Template;)V

    return-void

    :nemotron_keep_map_content
'@
$startMethod = $startMethod.Insert($toolbarResultEnd, $fullscreen.Replace("`n", [Environment]::NewLine))
$startText = $startText.Substring(0, $methodStart) + $startMethod + $startText.Substring($methodEnd)
[System.IO.File]::WriteAllText($startScreen, $startText, [System.Text.UTF8Encoding]::new($false))

# Do not invoke UI action callbacks while constructing templates. Waze only
# handles those callbacks after the driver taps the matching action; eagerly
# calling them here consumes the action before the host can dispatch a tap.

# Keep the map Search action but omit Settings from Waze's map action strip.
$toolbarText = [System.IO.File]::ReadAllText($toolbar)
$toolbarStart = $toolbarText.IndexOf($signature)
$toolbarEnd = $toolbarText.IndexOf('.end method', $toolbarStart)
$toolbarMethod = $toolbarText.Substring($toolbarStart, $toolbarEnd - $toolbarStart)
$settingsLabel = '    :cond_b'
$settingsIndex = $toolbarMethod.IndexOf($settingsLabel)
if ($settingsIndex -lt 0) { throw 'Could not locate the map Settings action.' }
$settingsEnd = $settingsIndex + $settingsLabel.Length
if (-not $toolbarMethod.Substring($settingsEnd).StartsWith([Environment]::NewLine + '    goto :cond_c')) {
    $toolbarMethod = $toolbarMethod.Insert($settingsEnd, [Environment]::NewLine + '    goto :cond_c')
}
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
