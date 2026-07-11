# record.ps1
$baseDir = "C:\Users\adaredu\Downloads\whisper"
$ffmpegExe = "$baseDir\ffmpeg.exe"
$audioDevice = "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"
$audioFile = "$baseDir\temp_audio.mp3"

# Iniciar ffmpeg con entrada estándar redirigida
$p = New-Object System.Diagnostics.Process
$p.StartInfo.FileName = $ffmpegExe
$p.StartInfo.Arguments = "-y -f dshow -i audio=`"$audioDevice`" -q:a 9 -acodec libmp3lame -b:a 192k `"$audioFile`""
$p.StartInfo.UseShellExecute = $false
$p.StartInfo.RedirectStandardInput = $true
$p.StartInfo.CreateNoWindow = $true
$p.Start()

# Crear archivo started.txt para avisar a AHK que ya se inició el proceso
New-Item -Path "$baseDir\started.txt" -ItemType File -Force > $null

# Esperar a que se cree el archivo stop.txt o a que ffmpeg termine solo
$stopFile = "$baseDir\stop.txt"
while ($p.HasExited -eq $false -and !(Test-Path $stopFile)) {
    Start-Sleep -Milliseconds 50
}

# Detener de forma segura enviando 'q' a la entrada estándar
if ($p.HasExited -eq $false) {
    $p.StandardInput.WriteLine("q")
    $p.WaitForExit(5000)
}
