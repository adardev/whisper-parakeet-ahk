#Requires AutoHotkey v2.0
Persistent ; Forzar a que el script se mantenga siempre activo en segundo plano
SetWorkingDir "C:\Users\adaredu\Downloads\whisper"

; CONFIGURACIÓN DE DISPOSITIVO: Opciones disponibles: "NPU", "GPU", "CPU"
; - NPU: Excelente para procesadores Intel Core Ultra (Meteor Lake o superior).
; - GPU: Excelente para Intel Iris Xe o gráficas integradas/dedicadas (utiliza la optimización FP32).
; - CPU: Modo seguro, compatible con cualquier procesador i5/i7 de cualquier generación.
global targetDevice := "NPU"

; Registramos un manejador global de errores para que cualquier error imprevisto
; no muestre el cuadro de diálogo de cierre de AHK y el script siga vivo en memoria.
OnError(errorHandler)

errorHandler(exception, mode) {
    ; En caso de error crítico, nos aseguramos de restablecer los estados globales
    global isRecording := false
    global isTranscribing := false
    return true
}

; Hotkey para recargar el script rápidamente con Ctrl + Shift + R
^+r::Reload

global isRecording := false
global isTranscribing := false

; Usamos el prefijo "$" para forzar el uso del gancho del teclado (Keyboard Hook)
; y asegurar que AHK intercepte Win + S anulando el buscador de Windows.
$#s:: {
    global isRecording, isTranscribing, targetDevice
    
    if (isTranscribing) {
        return
    }

    baseDir := "C:\Users\adaredu\Downloads\whisper"
    audioFile := baseDir "\temp_audio.mp3"
    wavFile := baseDir "\temp_clean.wav"
    txtFile := baseDir "\temp_clean.txt"
    logFile := baseDir "\conversion_log.txt"
    ffmpegExe := baseDir "\bin\ffmpeg.exe"
    exeFile := baseDir "\bin\parakeet_cli.exe"
    modelDir := "C:\Users\adaredu\AppData\Local\eddy\models\parakeet-v3\files"
    audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"
    winTitle := "ffmpeg_rec_window"

    DetectHiddenWindows(True)

    if (!isRecording) {
        ; ======================================================================
        ; INICIAR GRABACIÓN (MODO TOGGLE - PRIMER CLIC)
        ; ======================================================================
        isRecording := true

        ; Sonido de inicio (tonos ascendentes retro)
        SoundBeep(1000, 80)
        SoundBeep(1300, 80)

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

        ; Iniciar grabación directamente usando cmd minimizado para velocidad instantánea (<10ms)
        ffmpegCmd := Format('"{1}" -y -f dshow -i audio="{2}" -t 60 -q:a 9 -acodec libmp3lame -b:a 192k "{3}"', ffmpegExe, audioDevice, audioFile)
        fullCmd := A_ComSpec ' /c "title ' winTitle ' && ' ffmpegCmd '"'
        
        Run(fullCmd, , "Min")

        ; Esperar brevemente a que la ventana de consola se registre y ocultarla de inmediato
        if WinWait(winTitle, , 1.5) {
            WinHide(winTitle)
        }
    } else {
        ; ======================================================================
        ; DETENER GRABACIÓN Y TRANSCRIBIR (MODO TOGGLE - SEGUNDO CLIC)
        ; ======================================================================
        isRecording := false
        isTranscribing := true

        ; Sonido de parada (tonos descendentes)
        SoundBeep(1200, 80)
        SoundBeep(900, 80)

        try {
            ; Detener de forma segura enviando 'q' a la consola oculta de ffmpeg
            if WinExist(winTitle) {
                ControlSend("q", , winTitle)
                WinWaitClose(winTitle, , 4)
            }

            if !FileExist(audioFile) {
                MsgBox("Error: No se pudo grabar el audio. Asegúrate de que el micrófono esté conectado.")
                isTranscribing := false
                return
            }

            audioSize := FileGetSize(audioFile)
            if (audioSize = 0) {
                MsgBox("Error: El archivo de audio grabado está vacío (0 bytes).")
                isTranscribing := false
                return
            }

            ; 4. Convertir a formato requerido (16kHz mono WAV) y capturar log de errores
            convCmd := Format('""{1}" -y -i "{2}" -ar 16000 -ac 1 -c:a pcm_s16le "{3}" 2> "{4}""', ffmpegExe, audioFile, wavFile, logFile)
            RunWait(A_ComSpec " /c " convCmd, , "Hide")
            
            if !FileExist(wavFile) {
                logText := "No se pudo leer el archivo de log."
                if FileExist(logFile)
                    logText := FileRead(logFile, "UTF-8")
                MsgBox("Error: Falló la conversión a WAV con ffmpeg.`n`nTamaño MP3: " audioSize " bytes.`n`nDetalles de ffmpeg:`n" logText)
                isTranscribing := false
                return
            }

            ; Limpiar log anterior para capturar el de la transcripción
            try {
                if FileExist(logFile)
                    FileDelete(logFile)
            } catch {
            }
            
            ; 5. Transcribir usando parakeet_cli en modo silencioso y capturar errores de cmd.exe
            cmd := Format('""{1}" "{2}" --model parakeet-v3 --model_dir "{3}" --device {4} --silent > "{5}" 2> "{6}""', exeFile, wavFile, modelDir, targetDevice, txtFile, logFile)
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
                }
            } else {
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
            isTranscribing := false
        }
    }
}