param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
# Read-only: never changes HOME, launcher data, overlays or Android processes.
Write-Output 'HOME role:'
& $Adb shell cmd role get-role-holders android.app.role.HOME
Write-Output 'Active Recents component and UID:'
& $Adb shell dumpsys activity recents | Select-String '^mRecents(Uid|Component)='
Write-Output 'Framework Recents resource:'
& $Adb shell cmd overlay lookup android android:string/config_recentsComponentName
Write-Output 'Nemotron Quickstep service:'
& $Adb shell cmd package query-services --brief -a android.intent.action.QUICKSTEP_SERVICE -p com.nemotron.voiceime2
Write-Output 'Recent close-target diagnostics (historical buffer):'
& $Adb logcat -d -t 3000 | Select-String 'getCloseAnimator|target=null|target-null' | Select-Object -Last 12
