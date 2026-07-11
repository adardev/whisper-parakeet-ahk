#Requires AutoHotkey v2.0
Persistent ; Forzar a que el script se mantenga siempre activo en segundo plano
SetWorkingDir "C:\Users\adaredu\Downloads\whisper"

; Registramos un manejador global de errores para que cualquier error imprevisto
; no muestre el cuadro de diálogo de cierre de AHK y el script siga vivo en memoria.
OnError(errorHandler)

errorHandler(exception, mode) {
    ToolTip("⚠️ Error de ejecución: " exception.Message)
    SetTimer () => ToolTip(), -5000
    return true ; Evita el diálogo por defecto de AHK y mantiene el script corriendo
}

; Hotkey para recargar el script rápidamente con Ctrl + Shift + R
^+r::Reload

global isRunning := false

; Usamos el prefijo "$" para forzar el uso del gancho del teclado (Keyboard Hook)
; y asegurar que AHK intercepte Win + S anulando el buscador de Windows.
$#s:: {
    global isRunning
    if (isRunning) {
        ToolTip("⚠️ Transcripción en curso... espera un momento.")
        SetTimer () => ToolTip(), -2000
        return
    }
    isRunning := true

    try {
        baseDir := "C:\Users\adaredu\Downloads\whisper"
        audioFile := baseDir "\temp_audio.mp3"
        wavFile := baseDir "\temp_clean.wav"
        txtFile := baseDir "\temp_clean.txt"
        logFile := baseDir "\conversion_log.txt"
        stopFile := baseDir "\stop.txt"
        startedFile := baseDir "\started.txt"
        ffmpegExe := baseDir "\ffmpeg.exe"
        exeFile := baseDir "\eddy-audio-main\build\examples\cpp\Release\parakeet_cli.exe"
        modelDir := "C:\Users\adaredu\AppData\Local\eddy\models\parakeet-v3\files"
        audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"

        ; Cerrar procesos huérfanos de ffmpeg o parakeet que puedan bloquear archivos
        while ProcessExist("ffmpeg.exe") {
            try ProcessClose("ffmpeg.exe")
            Sleep(100)
        }
        while ProcessExist("parakeet_cli.exe") {
            try ProcessClose("parakeet_cli.exe")
            Sleep(100)
        }

        ; Limpiar archivos anteriores de forma segura
        try {
            if FileExist(audioFile)
                FileDelete(audioFile)
        } catch {
        }
        try {
            if FileExist(wavFile)
                FileDelete(wavFile)
        } catch {
        }
        try {
            if FileExist(txtFile)
                FileDelete(txtFile)
        } catch {
        }
        try {
            if FileExist(logFile)
                FileDelete(logFile)
        } catch {
        }
        try {
            if FileExist(stopFile)
                FileDelete(stopFile)
        } catch {
        }
        try {
            if FileExist(startedFile)
                FileDelete(startedFile)
        } catch {
        }

        ; 1. Lanzar el proceso de grabación en segundo plano
        ToolTip("🎙️ Inicializando micrófono... espera un momento.")
        
        psPID := 0
        psCmd := Format('powershell.exe -NoProfile -ExecutionPolicy Bypass -File "{1}\record.ps1"', baseDir)
        Run(psCmd, , "Hide", &psPID)

        ; Esperar a que el script de PowerShell avise que inició ffmpeg (máximo 2 segundos)
        loop 40 {
            if FileExist(startedFile)
                break
            Sleep(50)
        }
        
        ; Limpiar el indicador de inicio
        try {
            if FileExist(startedFile)
                FileDelete(startedFile)
        } catch {
        }

        ; Cambiar el ToolTip para avisar al usuario que YA puede hablar
        ToolTip("🎙️ GRABANDO - ¡Habla ahora! (Suelta S al finalizar)")

        ; 2. Esperar a que se suelte la tecla 'S' (Push-to-Talk)
        KeyWait("s")

        ToolTip("⚙️ Deteniendo grabación y transcribiendo...")

        ; 3. Crear el archivo de parada para avisar al proceso de PowerShell que envíe 'q' y cierre ordenadamente
        FileAppend("", stopFile)
        if (psPID) {
            ; Esperar hasta 5 segundos a que se cierre el proceso de grabación
            ProcessWaitClose(psPID, 5)
        }
        
        ; Asegurar el borrado del trigger
        try {
            if FileExist(stopFile)
                FileDelete(stopFile)
        } catch {
        }

        if !FileExist(audioFile) {
            ToolTip()
            MsgBox("Error: No se pudo grabar el audio. Asegúrate de que el micrófono esté conectado.")
            return
        }

        audioSize := FileGetSize(audioFile)
        if (audioSize = 0) {
            ToolTip()
            MsgBox("Error: El archivo de audio grabado está vacío (0 bytes).")
            return
        }

        ; 4. Convertir a formato requerido (16kHz mono WAV) y capturar log de errores
        convCmd := Format('""{1}" -y -i "{2}" -ar 16000 -ac 1 -c:a pcm_s16le "{3}" 2> "{4}""', ffmpegExe, audioFile, wavFile, logFile)
        RunWait(A_ComSpec " /c " convCmd, , "Hide")
        
        if !FileExist(wavFile) {
            ToolTip()
            logText := "No se pudo leer el archivo de log."
            if FileExist(logFile)
                logText := FileRead(logFile, "UTF-8")
            MsgBox("Error: Falló la conversión a WAV con ffmpeg.`n`nTamaño MP3: " audioSize " bytes.`n`nDetalles de ffmpeg:`n" logText)
            return
        }

        ; Limpiar log anterior para capturar el de la transcripción
        try {
            if FileExist(logFile)
                FileDelete(logFile)
        } catch {
        }
        
        ; 5. Transcribir usando parakeet_cli en modo silencioso y capturar errores de cmd.exe
        cmd := Format('""{1}" "{2}" --model parakeet-v3 --model_dir "{3}" --device NPU --silent > "{4}" 2> "{5}""', exeFile, wavFile, modelDir, txtFile, logFile)
        RunWait(A_ComSpec " /c " cmd, , "Hide")
        
        ; 6. Leer resultado, copiar al portapapeles y pegar
        if FileExist(txtFile) {
            resultado := Trim(FileRead(txtFile, "UTF-8"))
            if (resultado != "") {
                A_Clipboard := resultado
                ; Pegar el texto en la aplicación activa
                Send("^v")
                ToolTip("✅ Transcripción pegada!")
            } else {
                ToolTip("⚠️ Transcripción vacía.")
            }
        } else {
            ToolTip()
            logText := "No se pudo leer el archivo de log."
            if FileExist(logFile)
                logText := FileRead(logFile, "UTF-8")
            MsgBox("Error: No se generó la transcripción.`n`nDetalles del error:`n" logText)
        }
        
        ; Limpiar archivos temporales de forma segura
        try {
            if FileExist(audioFile)
                FileDelete(audioFile)
        } catch {
        }
        try {
            if FileExist(wavFile)
                FileDelete(wavFile)
        } catch {
        }
        try {
            if FileExist(txtFile)
                FileDelete(txtFile)
        } catch {
        }
        try {
            if FileExist(logFile)
                FileDelete(logFile)
        } catch {
        }
    } finally {
        isRunning := false
        SetTimer () => ToolTip(), -3000
    }
}