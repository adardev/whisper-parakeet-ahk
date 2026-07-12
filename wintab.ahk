#Requires AutoHotkey v2.0
#SingleInstance Force
#UseHook
InstallMouseHook
InstallKeybdHook
A_MenuMaskKey := "vkE8"

; --- SOLICITUD DE PRIVILEGIOS DE ADMINISTRADOR ---
if not A_IsAdmin {
    try {
        if A_IsCompiled
            Run '*RunAs "' A_ScriptFullPath '" /restart'
        else
            Run '*RunAs "' A_AhkPath '" /restart "' A_ScriptFullPath '"'
    }
    ExitApp
}

; --- CONFIGURACIÓN DE DICTADO LOCAL PARAKEET-V3 ---
; Opciones disponibles: "NPU", "GPU", "CPU"
; - NPU: Excelente para procesadores Intel Core Ultra (Meteor Lake o superior).
; - GPU: Excelente para Intel Iris Xe o gráficas integradas/dedicadas.
; - CPU: Modo seguro, compatible con cualquier procesador de cualquier generación.
global targetDevice := "GPU"
global isRecording := false
global isTranscribing := false

; --- VARIABLES GLOBALES ---
global lShiftClicks := 0
global rShiftClicks := 0
global PermitirMouse := false

global YTM_Exe := "YouTube Music Desktop App.exe"
global YTM_Shortcut := EnvGet("APPDATA") . "\Microsoft\Windows\Start Menu\Programs\YouTube Music.lnk"

global IsHidden := false
global OldX := 0
global OldY := 0

global CursorHidden := false ; Variable para rastrear el estado del cursor

; --- TIMERS CONTINUOS ---
; El temporizador CheckState es para la función de ocultar el cursor
SetTimer(CheckState, 50)
SetTimer ControlarBarreraMouse, 10

; --- FUNCIONES GLOBALES ---

CheckState() {
    Global CursorHidden

    ; Detectar si el escritorio activo es un escritorio seguro (como UAC o Bloqueo)
    ; Si OpenInputDesktop falla, es porque no tenemos acceso al escritorio activo (Secure Desktop).
    ; En ese caso, restauramos el cursor inmediatamente para que sea visible en la pantalla de UAC.
    hDesk := DllCall("OpenInputDesktop", "UInt", 0, "Int", 0, "UInt", 0, "Ptr")
    if (!hDesk) {
        if (CursorHidden) {
            RestoreCursors()
            CursorHidden := false
        }
        return
    }
    DllCall("CloseDesktop", "Ptr", hDesk)

    ; Si el ratón se acaba de mover, restaura el cursor
    if (A_TimeIdleMouse < 50) {
        if (CursorHidden) {
            RestoreCursors()
            CursorHidden := false
        }
    }
    ; Si tocas cualquier tecla (< 50ms) o si el ratón lleva inactivo 3 segundos (> 3000ms)
    else if (A_TimeIdleKeyboard < 50 or A_TimeIdleMouse > 3000) {
        if (!CursorHidden) {
            HideCursors()
            CursorHidden := true
        }
    }
}

HideCursors() {
    static BlankCursor := CreateBlankCursor()
    ; IDs de los cursores de Windows (flecha regular, selección de texto, mano, carga, etc.)
    CursorIDs := [32512, 32513, 32514, 32515, 32516, 32642, 32643, 32644, 32645, 32646, 32648, 32649, 32650]

    for id in CursorIDs {
        ; Sobrescribe los punteros activos del sistema con el cursor vacío
        DllCall("SetSystemCursor", "Ptr", DllCall("CopyIcon", "Ptr", BlankCursor), "UInt", id)
    }
}

RestoreCursors() {
    ; Llama a SPI_SETCURSORS (0x0057) para forzar a Windows 11 a recargar los cursores originales
    DllCall("SystemParametersInfo", "UInt", 0x0057, "UInt", 0, "Ptr", 0, "UInt", 0)
}

CreateBlankCursor() {
    AndMask := Buffer(128, 0xFF)
    XorMask := Buffer(128, 0)
    return DllCall("CreateCursor", "Ptr", 0, "Int", 0, "Int", 0, "Int", 32, "Int", 32, "Ptr", AndMask, "Ptr", XorMask)
}

LanzarApp(nombre, ruta) {
    if FileExist(ruta) {
        try Run(ruta)
    } else {
        MsgBox("No se encontró " . nombre . " en:`n" . ruta, "Error de Ejecución", 48)
    }
}

ControlarBarreraMouse() {
    global PermitirMouse

    if (PermitirMouse) {
        DllCall("ClipCursor", "Ptr", 0) ; Liberación total si se activa un shortcut legítimo
        return
    }

    MouseGetPos(, &mouseY)
    if (mouseY >= A_ScreenHeight - 15) {
        rect := Buffer(16, 0)
        NumPut("Int", 0, rect, 0)                  ; Left
        NumPut("Int", 0, rect, 4)                  ; Top
        NumPut("Int", A_ScreenWidth, rect, 8)       ; Right
        NumPut("Int", A_ScreenHeight - 2, rect, 12) ; Bottom

        DllCall("ClipCursor", "Ptr", rect)
    } else {
        DllCall("ClipCursor", "Ptr", 0)
    }
}

RCtrlSingleAction() {
    LanzarApp("Gemini", EnvGet("APPDATA") . "\Gemini\gemini.exe")
}

LShiftAction() {
    global lShiftClicks

    if (lShiftClicks = 2) {
        Run("F:\tempfiles\tempfiles.vbs")
    }

    lShiftClicks := 0
}

RShiftAction() {
    global rShiftClicks
    if (rShiftClicks = 3) {
        Run("C:\Windows\System32\cmd.exe /c bcdedit /bootsequence {31a96ccf-7b47-11f1-9cc3-ebca569cb2e5} & shutdown /r /t 0")
    } else if (rShiftClicks = 2) {
        LanzarApp("Salt Player", "C:\Program Files (x86)\steamcmd\steamapps\common\Salt Player for Windows\Salt Player for Windows.exe")
    } else if (rShiftClicks = 1) {
        RShiftSingleAction()
    }
    rShiftClicks := 0
}

RShiftSingleAction() {
    if ProcessExist(YTM_Exe) {
        try Run(YTM_Shortcut)
    } else {
        LanzarApp("YouTube Music", YTM_Shortcut)
    }
}

MonitorearCierre() {
    global PermitirMouse
    MouseGetPos(, &mouseY)
    if (mouseY >= A_ScreenHeight - 60)
        return
    if WinActive("ahk_class #32768") || WinActive("ahk_class NotifyIconOverflowWindow")
        return
    PermitirMouse := false
    SetTimer(, 0)
}

; ======================================================================
; FUNCIÓN REUTILIZABLE DE DICTADO LOCAL (WHISPER EN GPU)
; Parámetros:
;   lang - Código de idioma para Whisper: "es" (español), "en" (inglés), "auto" (auto)
; Atajos:
;   Win + S       → Dictar en Español
;   Win + S, S    → Dictar en Inglés (doble pulsación de S)
; ======================================================================
Dictar(lang) {
    global isRecording, isTranscribing, targetDevice

    if (isTranscribing) {
        return
    }

    baseDir := "F:\autohotkey"
    audioFile := baseDir "\temp_audio.mp3"
    wavFile := baseDir "\temp_clean.wav"
    txtFile := baseDir "\temp_clean.txt"
    logFile := baseDir "\conversion_log.txt"
    ffmpegExe := baseDir "\bin\ffmpeg.exe"
    exeFile := baseDir "\bin\whisper_example.exe"
    
    modelName := "whisper-small-int8-ov"
    modelDir := baseDir "\models\" modelName
    audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{08E80C7F-338C-4C96-9F52-06121768C053}"
    winTitle := "ffmpeg_rec_window"

    DetectHiddenWindows(True)

    if (!isRecording) {
        isRecording := true
        SoundPlay(A_WinDir "\Media\Speech On.wav")

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
        while ProcessExist("whisper_example.exe") {
            try ProcessClose("whisper_example.exe")
            Sleep(50)
        }

        try FileDelete(audioFile)
        try FileDelete(wavFile)
        try FileDelete(txtFile)
        try FileDelete(logFile)

        ffmpegCmd := Format('"{1}" -y -f dshow -i audio="{2}" -t 60 -q:a 9 -acodec libmp3lame -b:a 192k "{3}"', ffmpegExe, audioDevice, audioFile)
        fullCmd := A_ComSpec ' /c "title ' winTitle ' && ' ffmpegCmd '"'

        Run(fullCmd, , "Min")

        dummyWav := baseDir "\scratch\silent_test.wav"
        if FileExist(dummyWav) {
            warmCmd := Format('""{1}" "{2}" "{3}" {4} {5} --silent > nul 2> nul"', exeFile, modelDir, dummyWav, targetDevice, lang)
            Run(A_ComSpec " /c " warmCmd, baseDir, "Hide")
        }

        if WinWait(winTitle, , 1.5) {
            WinHide(winTitle)
        }
    } else {
        isRecording := false
        isTranscribing := true

        SoundPlay(A_WinDir "\Media\Speech Off.wav")

        try {
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

            try FileDelete(logFile)

            cmd := Format('""{1}" "{2}" "{3}" {4} {5} --silent > "{6}" 2> "{7}""', exeFile, modelDir, wavFile, targetDevice, lang, txtFile, logFile)
            RunWait(A_ComSpec " /c " cmd, baseDir, "Hide")

            if FileExist(txtFile) {
                textoRaw := FileRead(txtFile, "UTF-8")
                resultado := ""
                Loop Parse, textoRaw, "`n", "`r" {
                    linea := Trim(A_LoopField)
                    if (linea = "")
                        continue
                    if (SubStr(linea, 1, 1) = "[" || InStr(linea, "vpux-compiler") || InStr(linea, "AlignDimensionsForDPU") || InStr(linea, "Failed Pass") || InStr(linea, "onednn_verbose"))
                        continue
                    resultado .= (resultado = "" ? "" : "`n") . linea
                }
                resultado := Trim(resultado)

                if (resultado != "") {
                    KeyWait("LWin")
                    KeyWait("RWin")
                    KeyWait("LShift")
                    KeyWait("RShift")
                    KeyWait("Ctrl")
                    Sleep(150)

                    ClipSaved := ClipboardAll()
                    A_Clipboard := resultado
                    ClipWait(1)
                    Send("^v")
                    Sleep(100)
                    A_Clipboard := ClipSaved
                }
            } else {
                logText := "No se pudo leer el archivo de log."
                if FileExist(logFile)
                    logText := FileRead(logFile, "UTF-8")
                MsgBox("Error: No se generó la transcripción.`n`nDetalles del error:`n" logText)
            }

            try FileDelete(audioFile)
            try FileDelete(wavFile)
            try FileDelete(txtFile)
            try FileDelete(logFile)
        } catch as err {
            MsgBox("Error crítico en la transcripción: " err.Message)
        } finally {
            isTranscribing := false
        }
    }
}

global currentLang := "es"

; --- ATAJOS DE DICTADO (Win + S) ---

; Win + S = Dictar (Toque simple: Español | Doble toque en <500ms: Inglés)
$#s:: {
    global isRecording, currentLang
    
    ; Si YA está grabando, detener de inmediato con una sola pulsación usando el mismo idioma
    if (isRecording) {
        Dictar(currentLang)
        return
    }
    
    ; Si NO está grabando, detectar si es toque simple o doble
    static lastS := 0
    if (A_TickCount - lastS < 500) {
        lastS := 0
        SetTimer(IniciarDictadoEspanol, 0)  ; Cancelar el timer de español
        currentLang := "en"
        Dictar("en")
        return
    }
    lastS := A_TickCount
    SetTimer(IniciarDictadoEspanol, -500)
}

IniciarDictadoEspanol() {
    global currentLang
    currentLang := "es"
    Dictar("es")
}

; --- ACCESOS RÁPIDOS GENERALES ---
#q:: {
    if (active_pid := WinActive("A") ? WinGetPID("A") : 0)
        try ProcessClose(active_pid)
}

~LWin Up:: {
    if (A_PriorKey = "LWin") {
        A_Clipboard := ""
        Send("^c")
        if ClipWait(0.5)
            Run("https://www.google.com/search?q=" . A_Clipboard)
    }
}

*#Tab::Send("{Blind}f")

; --- LÓGICA DE GEMINI / CLAUDE ---
<+<#F23::
$RCtrl:: {
    if (A_ThisHotkey = "<+<#F23") {
        Send("{Blind}{LShift Up}{LWin Up}")
    }

    static lastClick := 0
    if (A_TickCount - lastClick < 300) {
        lastClick := 0
        SetTimer(RCtrlSingleAction, 0)
        LanzarApp("Claude", EnvGet("APPDATA") . "\Claude\claude.exe")
    } else {
        lastClick := A_TickCount
        SetTimer(RCtrlSingleAction, -300)
    }
}

; --- MANEJO DE SHIFTS ---
~$LShift::Send("{Blind}{vkE8}")
~$LShift Up:: {
    global lShiftClicks
    if (A_PriorKey != "LShift")
        return

    lShiftClicks++
    SetTimer(LShiftAction, -400)
}

~$RShift::Send("{Blind}{vkE8}")
~$RShift Up:: {
    global rShiftClicks
    if (A_PriorKey != "RShift")
        return

    rShiftClicks++
    SetTimer(RShiftAction, -400)
}

; --- INTEGRACIÓN CON RAYCAST ---
#.:: {
    if !ProcessExist("Raycast.exe") {
        Run("explorer.exe shell:AppsFolder\Raycast.Raycast_qypenmj9wpt2a!Raycast")
        WinWait("ahk_exe Raycast.exe", , 4)
        Sleep(1000)
    }
    Send("!#.")
}

; --- GESTIÓN EXCLUSIVA POR TECLADO PARA LA BARRA DE TAREAS ---
~#b::
~#t::
~LWin::
~RWin::
{
    global PermitirMouse := true
    DllCall("ClipCursor", "Ptr", 0)
    SetTimer(MonitorearCierre, 100)
}

; Medida de seguridad: Si cierras el script o se interrumpe, restaura el cursor automáticamente y libera la barrera
OnExit((*) => (DllCall("ClipCursor", "Ptr", 0), RestoreCursors()))
