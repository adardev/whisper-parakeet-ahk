param(
    [string]$InputApk = "$env:TEMP\waze-base.apk",
    [string]$OutputApk = "$env:TEMP\nemotron-waze-aa-candidate-v1.apk"
)

$ErrorActionPreference = 'Stop'
$apktool = 'C:\Users\adaredu\develop\apktool\apktool_2.12.1.jar'
$java = 'C:\Users\adaredu\develop\temurin17-task\jdk-17.0.21+9\bin\java.exe'
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
$buildTools = 'C:\Users\adaredu\develop\android-sdk\build-tools\36.0.0'
$keyStore = "$env:USERPROFILE\.android\debug.keystore"
$work = Join-Path $env:TEMP ('nemotron-waze-aa-build-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
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

$suggestions = Join-Path $decoded 'smali_classes5\com\waze\car_lib\screens\q.smali'
$routeScreen = Join-Path $decoded 'smali_classes5\com\waze\c\a\b\h.smali'
$q = [System.IO.File]::ReadAllText($suggestions)
$methodStart = $q.IndexOf('.method static final f(Lcom/waze/car_lib/screens/q;Lcom/waze/car_lib/r/v;)Lg/ab;')
if ($methodStart -lt 0) { throw 'Waze AA suggestion update method was not found.' }
$methodEnd = $q.IndexOf('.end method', $methodStart)
if ($methodEnd -lt 0) { throw 'Waze AA suggestion update method has no end marker.' }
$method = $q.Substring($methodStart, $methodEnd - $methodStart)

# If Home and Work are both configured, retain Home and omit only Work.
$homeAnchor = '    aput-object v1, p1, v6'
$homeIndex = $method.IndexOf($homeAnchor)
if ($homeIndex -lt 0) { throw 'Could not locate the Home suggestion insertion point.' }
$homeBlock = @'

    invoke-static {v1}, Lg/a/i;->d(Ljava/lang/Object;)Ljava/util/List;

    move-result-object p1

    goto :goto_0
'@
$method = $method.Insert($homeIndex + $homeAnchor.Length, $homeBlock.Replace("`n", [Environment]::NewLine))

# If only Work exists, publish no Work shortcut.
$cond1Index = $method.IndexOf('    :cond_1')
$workGuardIndex = $method.IndexOf('    if-eqz p1, :cond_2', $cond1Index)
$workGotoIndex = $method.IndexOf('    goto :goto_0', $workGuardIndex)
if ($cond1Index -lt 0 -or $workGuardIndex -lt 0 -or $workGotoIndex -lt 0) { throw 'Could not locate the Work-only suggestion branch.' }
$workBodyStart = $method.IndexOf([Environment]::NewLine, $workGuardIndex) + [Environment]::NewLine.Length
$emptyWork = "    invoke-static {}, Lg/a/i;->i()Ljava/util/List;`r`n`r`n    move-result-object p1"
$methodNew = $method.Substring(0, $workBodyStart) + $emptyWork + $method.Substring($workGotoIndex)
$q = $q.Substring(0, $methodStart) + $methodNew + $q.Substring($methodEnd)
[System.IO.File]::WriteAllText($suggestions, $q, [System.Text.UTF8Encoding]::new($false))

# Suppress the dynamic Home/Work shortcut and Saved Places from the map
# toolbar, keeping Search and Settings (and any independent host action).
$toolbar = Join-Path $decoded 'smali_classes5\com\waze\ca\a\c\b.smali'
$toolbarText = [System.IO.File]::ReadAllText($toolbar)
$toolbarStart = $toolbarText.IndexOf('.method public static b(Landroidx/car/app/CarContext;ZZZZZLg/h/a/a;Lg/h/a/a;Lg/h/a/a;Lg/h/a/a;Lg/h/a/a;Lg/h/a/a;)Landroidx/car/app/model/ActionStrip;')
if ($toolbarStart -lt 0) { throw 'Waze AA map action-strip builder was not found.' }
$toolbarEnd = $toolbarText.IndexOf('.end method', $toolbarStart)
$toolbarMethod = $toolbarText.Substring($toolbarStart, $toolbarEnd - $toolbarStart)
$homeWorkAnchor = '    :goto_1'
$homeWorkIndex = $toolbarMethod.IndexOf($homeWorkAnchor)
if ($homeWorkIndex -lt 0) { throw 'Could not locate the Home/Work map action.' }
$toolbarMethod = $toolbarMethod.Insert($homeWorkIndex + $homeWorkAnchor.Length, [Environment]::NewLine + '    const/4 p2, 0x0')
$savedLabel = '    :cond_9'
$savedIndex = $toolbarMethod.IndexOf($savedLabel)
if ($savedIndex -lt 0) { throw 'Could not locate the Saved Places map action.' }
$toolbarMethod = $toolbarMethod.Insert($savedIndex + $savedLabel.Length, [Environment]::NewLine + '    goto :cond_a')
$toolbarText = $toolbarText.Substring(0, $toolbarStart) + $toolbarMethod + $toolbarText.Substring($toolbarEnd)
[System.IO.File]::WriteAllText($toolbar, $toolbarText, [System.Text.UTF8Encoding]::new($false))

# On Waze's initial Android Auto start state, show the map template directly
# instead of publishing its Drive now destination panel.
$startScreen = Join-Path $decoded 'smali_classes5\com\waze\car_lib\screens\q.smali'
$startText = [System.IO.File]::ReadAllText($startScreen)
$actionStripMarker = 'invoke-static/range {v1 .. v12}, Lcom/waze/ca/a/c/b;->b('
$actionStripIndex = $startText.IndexOf($actionStripMarker)
if ($actionStripIndex -lt 0) { throw 'Could not locate the Waze AA action strip for the start-state template.' }
$actionStripResult = 'move-result-object v2'
$actionStripResultIndex = $startText.IndexOf($actionStripResult, $actionStripIndex)
if ($actionStripResultIndex -lt 0) { throw 'Could not locate the Waze AA action-strip result.' }
$insertIndex = $actionStripResultIndex + $actionStripResult.Length
$startText = $startText.Insert($insertIndex, [Environment]::NewLine + '    move-object/from16 v33, v2')
$startBranch = '    :cond_3'
$startBranchIndex = $startText.IndexOf($startBranch, $actionStripIndex)
if ($startBranchIndex -lt 0) { throw 'Could not locate Waze AA start-state branch.' }
$startStateCall = 'invoke-virtual/range {p1 .. p1}, Lcom/waze/car_lib/r/v;->i()Z'
$startStateCallIndex = $startText.IndexOf($startStateCall, $startBranchIndex)
if ($startStateCallIndex -lt 0) { throw 'Could not locate Waze AA start-state loading check.' }
$mapOnly = @'
    invoke-virtual/range {p1 .. p1}, Lcom/waze/car_lib/r/v;->b()Lcom/waze/ca/a/c/l;

    move-result-object v2

    if-eqz v2, :nemotron_check_start_state_loading

    goto/16 :nemotron_map_only_start_state

    :nemotron_check_start_state_loading
    invoke-virtual/range {p1 .. p1}, Lcom/waze/car_lib/r/v;->i()Z

    move-result v2

    if-eqz v2, :nemotron_keep_start_state_content

    :nemotron_map_only_start_state

    new-instance v13, Landroidx/car/app/model/ListTemplate$Builder;

    invoke-direct {v13}, Landroidx/car/app/model/ListTemplate$Builder;-><init>()V

    new-instance v12, Landroidx/car/app/model/ItemList$Builder;

    invoke-direct {v12}, Landroidx/car/app/model/ItemList$Builder;-><init>()V

    new-instance v11, Landroidx/car/app/model/Row$Builder;

    invoke-direct {v11}, Landroidx/car/app/model/Row$Builder;-><init>()V

    const-string v10, " "

    invoke-virtual {v11, v10}, Landroidx/car/app/model/Row$Builder;->setTitle(Ljava/lang/CharSequence;)Landroidx/car/app/model/Row$Builder;

    invoke-virtual {v11}, Landroidx/car/app/model/Row$Builder;->build()Landroidx/car/app/model/Row;

    move-result-object v11

    invoke-virtual {v12, v11}, Landroidx/car/app/model/ItemList$Builder;->addItem(Landroidx/car/app/model/Item;)Landroidx/car/app/model/ItemList$Builder;

    invoke-virtual {v12}, Landroidx/car/app/model/ItemList$Builder;->build()Landroidx/car/app/model/ItemList;

    move-result-object v12

    invoke-virtual {v13, v12}, Landroidx/car/app/model/ListTemplate$Builder;->setSingleList(Landroidx/car/app/model/ItemList;)Landroidx/car/app/model/ListTemplate$Builder;

    move-object/from16 v12, v33

    invoke-virtual {v13, v12}, Landroidx/car/app/model/ListTemplate$Builder;->setActionStrip(Landroidx/car/app/model/ActionStrip;)Landroidx/car/app/model/ListTemplate$Builder;

    invoke-virtual {v13}, Landroidx/car/app/model/ListTemplate$Builder;->build()Landroidx/car/app/model/ListTemplate;

    move-result-object v13

    goto/16 :goto_e

    :nemotron_keep_start_state_content
'@
$startText = $startText.Insert($startStateCallIndex, $mapOnly.Replace("`n", [Environment]::NewLine) + [Environment]::NewLine)
[System.IO.File]::WriteAllText($startScreen, $startText, [System.Text.UTF8Encoding]::new($false))

# Waze's Android Auto alternative-routes screen receives the same callback as
# its Go now button. Invoke it once when routes are ready, skipping that screen.
$routeText = [System.IO.File]::ReadAllText($routeScreen)
$fieldsAnchor = '.field private final e:Landroidx/car/app/navigation/model/RoutePreviewNavigationTemplate;'
if (-not $routeText.Contains($fieldsAnchor)) { throw 'Alternative-routes screen fields changed.' }
$routeText = $routeText.Replace($fieldsAnchor, $fieldsAnchor + "`n.field private f:Z")
$autoStart = @'
    iget-boolean v5, v0, Lcom/waze/c/a/b/h;->f:Z

    if-nez v5, :auto_start_already_dispatched

    const/4 v5, 0x1

    iput-boolean v5, v0, Lcom/waze/c/a/b/h;->f:Z

    invoke-interface {v11}, Lg/h/a/a;->invoke()Ljava/lang/Object;

    return-void

    :auto_start_already_dispatched
    return-void
'@
$routeBranch = $routeText.IndexOf('    if-eqz v9, :cond_a')
if ($routeBranch -lt 0) { throw 'Could not find the Android Auto alternative-routes branch.' }
$routeBranchEnd = $routeText.IndexOf([Environment]::NewLine, $routeBranch) + [Environment]::NewLine.Length
$routeText = $routeText.Insert($routeBranchEnd, $autoStart.Replace("`n", [Environment]::NewLine))
[System.IO.File]::WriteAllText($routeScreen, $routeText, [System.Text.UTF8Encoding]::new($false))

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
