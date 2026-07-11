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
        ffmpegExe := baseDir "\ffmpeg.exe"
        exeFile := baseDir "\eddy-audio-main\build\examples\cpp\Release\parakeet_cli.exe"
        modelDir := "C:\Users\adaredu\AppData\Local\eddy\models\parakeet-v3\files"
        audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"
        winTitle := "ffmpeg_rec_window"

        ; Activar búsqueda de ventanas ocultas para AHK
        DetectHiddenWindows(True)

        ; Cerrar procesos y ventanas huérfanas de ejecuciones anteriores
        if WinExist(winTitle) {
            WinClose(winTitle)
            WinWaitClose(winTitle, , 2)
        }
        while ProcessExist("ffmpeg.exe") {
            try ProcessClose("ffmpeg.exe")
            Sleep(50)
        }
        while ProcessExist("parakeet_cli.exe") {
            try ProcessClose("parakeet_cli.exe")
            Sleep(50)
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

        ; 1. Iniciar grabación directamente usando cmd minimizado para velocidad instantánea (<10ms)
        ffmpegCmd := Format('"{1}" -y -f dshow -i audio="{2}" -t 60 -q:a 9 -acodec libmp3lame -b:a 192k "{3}"', ffmpegExe, audioDevice, audioFile)
        fullCmd := A_ComSpec ' /c "title ' winTitle ' && ' ffmpegCmd '"'
        
        Run(fullCmd, , "Min")

        ; Esperar brevemente a que la ventana de consola se registre y ocultarla de inmediato
        if WinWait(winTitle, , 1.5) {
            WinHide(winTitle)
        }

        ; Informar al usuario que ya está grabando de forma instantánea
        ToolTip("🎙️ GRABANDO - ¡Habla ahora! (Suelta S al finalizar)")

        ; 2. Esperar a que se suelte la tecla 'S' (Push-to-Talk)
        KeyWait("s")

        ToolTip("⚙️ Deteniendo grabación y transcribiendo...")

        ; 3. Detener de forma segura enviando 'q' a la consola oculta de ffmpeg
        if WinExist(winTitle) {
            ControlSend("q", , winTitle)
            WinWaitClose(winTitle, , 4)
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
        
        ; 6. Leer resultado, copiar al portapapeles y pegar (filtrando logs de la NPU)
        if FileExist(txtFile) {
            textoRaw := FileRead(txtFile, "UTF-8")
            resultado := ""
            Loop Parse, textoRaw, "`n", "`r" {
                linea := Trim(A_LoopField)
                if (linea = "")
                    continue
                ; Omitir líneas de advertencias/errores del compilador de OpenVINO/NPU
                if (SubStr(linea, 1, 1) = "[" || InStr(linea, "vpux-compiler") || InStr(linea, "AlignDimensionsForDPU") || InStr(linea, "Failed Pass"))
                    continue
                resultado .= (resultado = "" ? "" : "`n") . linea
            }
            resultado := Trim(resultado)
            
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