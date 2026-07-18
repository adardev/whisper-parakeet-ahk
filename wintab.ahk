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

; --- INICIALIZACIÓN DE MAGNIFICACIÓN Y REGISTROS ---
DllCall("LoadLibrary", "Str", "magnification.dll")
if !DllCall("magnification\MagInitialize") {
    MsgBox "MagInitialize falló"
    ExitApp
}

try RegWrite 3, "REG_DWORD", "HKCU\Software\Microsoft\ScreenMagnifier", "Magnification"
try RegWrite 1, "REG_DWORD", "HKCU\Software\Microsoft\ScreenMagnifier", "Running"
try RegWrite 0, "REG_DWORD", "HKCU\Software\Microsoft\ScreenMagnifier", "StartMinimized"

; --- CONFIGURACIÓN DE PROCESOS Y ATANJOS NATIVOS ---
#HotIf
#!:: return
#HotIf

; --- LOCAL DICTATION CONFIGURATION ---
global targetDevice := "NPU"
global isRecording := false
global isTranscribing := false
global handyDirectBaselineId := 0
global handyDirectFinishing := false
global handyDirectStartPending := false
global handyDirectWaitPid := 0
global handyDirectWaitFile := ""
global handyDirectWaitDeadline := 0
global handyDirectTargetHwnd := 0
global handyExe := EnvGet("LOCALAPPDATA") . "\Handy\handy.exe"
global handyIdleTimeout := 180000
global openCodeIsRecording := false
global openCodeIsTranscribing := false
global openCodeTargetHwnd := 0
global handyBaselineId := 0
global openCodeServerPid := 0
global openCodeServerUrl := "http://127.0.0.1:4096"
global OpenCodeCmd := A_ScriptDir "\VoiceAssistant\runtime\npm-global\opencode.cmd"
global OpenCodeProjectDir := A_Desktop
global pendingAgentStart := false
global voiceForCurrentRequest := false
global voicePackageDir := A_ScriptDir "\VoiceAssistant"

ComandoVoiceHelper(nombre) {
    global voicePackageDir
    return '"' voicePackageDir "\" nombre "\" nombre ".exe" '"'
}

CerrarHandyWebView() {
    psCommand := "Get-CimInstance Win32_Process -Filter 'Name = ''msedgewebview2.exe''' | "
        . "Where-Object { $_.CommandLine -match '(?i)handy|com\.cjpais' } | "
        . "ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
    try RunWait('powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -Command "' psCommand '"', , "Hide")
}

ObtenerModoHandy() {
    settingsFile := EnvGet("APPDATA") "\com.pais.handy\settings_store.json"
    if !FileExist(settingsFile)
        return ""
    contents := FileRead(settingsFile, "UTF-8")
    return RegExMatch(contents, '"paste_method"\s*:\s*"([^"]+)"', &match) ? match[1] : ""
}

EstablecerModoHandy(modo) {
    settingsFile := EnvGet("APPDATA") "\com.pais.handy\settings_store.json"
    if !FileExist(settingsFile)
        return false
    contents := FileRead(settingsFile, "UTF-8")
    updated := RegExReplace(contents, '("paste_method"\s*:\s*")[^"]+("\s*)', '$1' modo '$2', &count, 1)
    if !count
        return false
    try {
        FileDelete(settingsFile)
        FileAppend(updated, settingsFile, "UTF-8-RAW")
        return true
    }
    return false
}

PrepararHandy(modo) {
    if (ObtenerModoHandy() = modo)
        return
    if ProcessExist("handy.exe") {
        try ProcessClose("handy.exe")
        Sleep(500)
        CerrarHandyWebView()
    }
    EstablecerModoHandy(modo)
}

HandyLatestId() {
    outFile := A_Temp "\handy_id_" A_TickCount ".txt"
    try FileDelete(outFile)
    try RunWait(ComandoVoiceHelper("handy_history") ' --id --output "' outFile '"', , "Hide")
    if !FileExist(outFile)
        return 0
    value := Trim(FileRead(outFile, "UTF-8"))
    try FileDelete(outFile)
    return (value ~= "^\d+$") ? Integer(value) : 0
}

HandyWaitForTranscript(afterId) {
    outFile := A_Temp "\handy_text_" A_TickCount ".txt"
    try FileDelete(outFile)
    try RunWait(ComandoVoiceHelper("handy_history") ' --wait-after-id ' afterId ' --output "' outFile '"', , "Hide")
    if !FileExist(outFile)
        return ""
    text := Trim(FileRead(outFile, "UTF-8"))
    try FileDelete(outFile)
    return text
}

HandyToggleTranscription() {
    global handyExe
    if !FileExist(handyExe) {
        MsgBox("No se encontró Handy en:`n" handyExe, "Handy", "Iconx")
        return false
    }
    started := false
    if !ProcessExist("handy.exe") {
        started := true
        try Run('"' handyExe '" --start-hidden', , "Hide")
        Loop 40 {
            if ProcessExist("handy.exe")
                break
            Sleep(25)
        }
        Sleep(1100)
        try WinHide("ahk_exe handy.exe")
    }
    if !ProcessExist("handy.exe")
        return false
    ; Handy puede estar liberando el modelo de la transcripción anterior.
    ; Reintentamos sin introducir espera cuando ya está listo.
    Loop 4 {
        try {
            Run('"' handyExe '" --toggle-transcription', , "Hide")
            if started {
                Sleep(300)
                CerrarHandyWebView()
            }
            return true
        }
        catch {
            if A_Index < 4
                Sleep(80)
        }
    }
    return false
}

ProgramarCierreHandy() {
    global handyIdleTimeout
    SetTimer(CerrarHandyPorInactividad, 0)
    SetTimer(CerrarHandyPorInactividad, -handyIdleTimeout)
}

CerrarHandyPorInactividad() {
    global isRecording, openCodeIsRecording
    if isRecording || openCodeIsRecording {
        SetTimer(CerrarHandyPorInactividad, -30000)
        return
    }
    if ProcessExist("handy.exe") {
        try ProcessClose("handy.exe")
        Sleep(500)
        CerrarHandyWebView()
    }
    EstablecerModoHandy("ctrl_v")
}

OpenCodeServerReady() {
    global openCodeServerUrl
    try {
        request := ComObject("WinHttp.WinHttpRequest.5.1")
        request.Open("GET", openCodeServerUrl, false)
        request.SetTimeouts(100, 100, 100, 100)
        request.Send()
        return true
    }
    return false
}

CerrarOpenCodeServer() {
    global openCodeServerPid
    if openCodeServerPid {
        try RunWait(A_ComSpec " /c taskkill /PID " openCodeServerPid " /T /F", , "Hide")
        openCodeServerPid := 0
    }
}

AsegurarOpenCodeServer() {
    global OpenCodeCmd, OpenCodeProjectDir, openCodeServerPid
    if OpenCodeServerReady()
        return true
    if !FileExist(OpenCodeCmd)
        return false
    try Run('"' OpenCodeCmd '" serve --hostname 127.0.0.1 --port 4096', OpenCodeProjectDir, "Hide", &openCodeServerPid)
    catch
        return false
    Loop 80 {
        if OpenCodeServerReady()
            return true
        Sleep(100)
    }
    return false
}

ProgramarCierreOpenCode() {
    SetTimer(CerrarOpenCodeServer, 0)
    SetTimer(CerrarOpenCodeServer, -180000)
}

; --- VARIABLES GLOBALES (GENERALES Y MULTIMEDIA) ---
global lShiftClicks := 0
global rShiftClicks := 0
global PermitirMouse := false

global YTM_Exe := "YouTube Music Desktop App.exe"
global YTM_Shortcut := EnvGet("APPDATA") . "\Microsoft\Windows\Start Menu\Programs\YouTube Music.lnk"

global IsHidden := false
global OldX := 0
global OldY := 0
global CursorHidden := false 

; --- VARIABLES GLOBALES (MAGNIFICACIÓN Y DIBUJO) ---
global TargetZoom := 1.0
global CurrentZoom := 1.0
global SmoothOffX := 0, SmoothOffY := 0
global TimerActive := false
global drawing := false, prevX := 0, prevY := 0, drawGui := ""
global hdcScreen := 0, hdcMem := 0, hbmMem := 0, hbmBg := 0, hPen := 0, hBrush := 0

; --- CONFIGURACIÓN DE PROCESOS DE ENERGÍA (TOGGLE DE 4 MODOS) ---
global perfilesEnergia := [
    "381b4222-f694-41f0-9685-ff5bb260df2e", ; 1. Equilibrado
    "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c", ; 2. Alto Rendimiento
    "e9a42b02-d5df-448d-aa00-03f14749eb61", ; 3. Rendimiento Máximo (Gaming)
    "a1841308-3541-4fab-bc81-f71556f20b4a"  ; 4. Economizador (Power Saver)
]
global nombresEnergia := [
    "Modo: Equilibrado", 
    "Modo: Alto Rendimiento", 
    "Modo: Rendimiento Máximo (Gaming)", 
    "Modo: Economizador"
]

; --- CORRECCIÓN DE MEMORIA EN REINICIO ---
SincronizarPerfilActual() {
    try {
        shell := ComObject("WScript.Shell")
        exec := shell.Exec("powercfg /getactivescheme")
        output := exec.StdOut.ReadAll()
        
        for index, guid in perfilesEnergia {
            if InStr(output, guid) {
                return index
            }
        }
    }
    return 1 
}

global energiaIndex := SincronizarPerfilActual()
global perfilPrevio := (energiaIndex == 4) ? 2 : energiaIndex 
global ultimoEstadoAhorro := 0 

; --- TIMERS CONTINUOS ---
SetTimer(CheckState, 50)
SetTimer ControlarBarreraMouse, 10
SetTimer MonitorearBotonAhorro, 1000 
SetTimer CheckWarp, 100 
SetTimer KillMagnify, 16

; --- CONTROL DE FINALIZACIÓN Y LIMPIEZA ---
OnExit(ExitCleanup)

ExitCleanup(*) {
    CerrarOpenCodeServer()
    DllCall("ClipCursor", "Ptr", 0)
    RestoreCursors()
    CleanupMagnifier()
}

; --- FUNCIONES GLOBALES ---

KillMagnify() {
    try {
        m := WinExist("ahk_exe Magnify.exe")
        if m
            WinKill m
    }
}

CheckWarp() {
    if WinExist("ahk_exe warp.exe") {
        WinWait "ahk_exe warp.exe"
        Sleep 750 
        WinActivate "ahk_exe warp.exe"
        WinWaitActive "ahk_exe warp.exe"
        
        Send "^+b"
        
        SetTimer CheckWarp, 0
        WinWaitClose "ahk_exe warp.exe"
        SetTimer CheckWarp, 100
    }
}

CheckState() {
    Global CursorHidden

    hDesk := DllCall("OpenInputDesktop", "UInt", 0, "Int", 0, "UInt", 0, "Ptr")
    if (!hDesk) {
        if (CursorHidden) {
            RestoreCursors()
            CursorHidden := false
        }
        return
    }
    DllCall("CloseDesktop", "Ptr", hDesk)

    if (A_TimeIdleMouse < 50) {
        if (CursorHidden) {
            RestoreCursors()
            CursorHidden := false
        }
    }
    else if (A_TimeIdleKeyboard < 50 or A_TimeIdleMouse > 3000) {
        if (!CursorHidden) {
            HideCursors()
            CursorHidden := true
        }
    }
}

HideCursors() {
    static BlankCursor := CreateBlankCursor()
    CursorIDs := [32512, 32513, 32514, 32515, 32516, 32642, 32643, 32644, 32645, 32646, 32648, 32649, 32650]
    
    for id in CursorIDs {
       DllCall("SetSystemCursor", "Ptr", DllCall("CopyIcon", "Ptr", BlankCursor), "UInt", id)
    }
}

RestoreCursors() {
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
        MsgBox("No se encontró " . nombre . " en:`n" . ruta, "Error de Execution", 48)
    }
}

ControlarBarreraMouse() {
    global PermitirMouse
    
    if (PermitirMouse) {
        DllCall("ClipCursor", "Ptr", 0) 
        return
    }

    MouseGetPos(, &mouseY)
    if (mouseY >= A_ScreenHeight - 15) {
        rect := Buffer(16, 0)
        NumPut("Int", 0, rect, 0)           
        NumPut("Int", 0, rect, 4)                  
        NumPut("Int", A_ScreenWidth, rect, 8)       
        NumPut("Int", A_ScreenHeight - 2, rect, 12) 
        
        DllCall("ClipCursor", "Ptr", rect)
    } else {
        DllCall("ClipCursor", "Ptr", 0)
    }
}

RCtrlSingleAction() 
{
    LanzarApp("Gemini", EnvGet("APPDATA") . "\Gemini\gemini.exe")
}

LShiftAction() {
    global lShiftClicks
    if (lShiftClicks = 2) {
        Run("D:\adaredu\tempfiles\tempfiles.vbs")
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

; --- DINÁMICA DE MAGNIFICACIÓN ---

UpdateTransform() {
    global TargetZoom, CurrentZoom, SmoothOffX, SmoothOffY, TimerActive

    CurrentZoom += (TargetZoom - CurrentZoom) * 0.12
    
    if (TargetZoom <= 1.0 && Abs(CurrentZoom - 1.0) < 0.01) {
        DllCall("magnification\MagSetFullscreenTransform", "Float", 1.0, "Int", 0, "Int", 0)
        SetTimer UpdateTransform, 0
        TimerActive := false
        CurrentZoom := 1.0
        TargetZoom := 1.0
        SmoothOffX := 0
        SmoothOffY := 0
        return
    }

    targetX := 0
    targetY := 0
    hasCaret := false

    guiInfo := Buffer(72, 0)
    NumPut("UInt", 72, guiInfo, 0)
    if DllCall("GetGUIThreadInfo", "UInt", 0, "Ptr", guiInfo) {
        flags := NumGet(guiInfo, 4, "UInt")
        if (flags & 0x2) {
            cLeft   := NumGet(guiInfo, 48, "Int")
            cTop    := NumGet(guiInfo, 52, "Int")
            hwndFocus := NumGet(guiInfo, 16, "Ptr")
            if hwndFocus && (!drawGui || hwndFocus != drawGui.Hwnd) {
                ptCaret := Buffer(8)
                NumPut("Int", cLeft, ptCaret, 0)
                NumPut("Int", cTop, ptCaret, 4)
                DllCall("ClientToScreen", "Ptr", hwndFocus, "Ptr", ptCaret)
                targetX := NumGet(ptCaret, 0, "Int")
                targetY := NumGet(ptCaret, 4, "Int")
                hasCaret := true
            }
        }
    }

    if (!hasCaret) {
        ptMouse := Buffer(8)
        DllCall("GetCursorPos", "Ptr", ptMouse)
        targetX := NumGet(ptMouse, 0, "Int")
        targetY := NumGet(ptMouse, 4, "Int")
    }

    maxX := A_ScreenWidth - A_ScreenWidth / CurrentZoom
    maxY := A_ScreenHeight - A_ScreenHeight / CurrentZoom

    tX := targetX - A_ScreenWidth * 0.5 / CurrentZoom
    tY := targetY - A_ScreenHeight * 0.5 / CurrentZoom

    tX := tX < 0 ? 0 : (tX > maxX ? maxX : tX)
    tY := tY < 0 ? 0 : (tY > maxY ? maxY : tY)

    if (SmoothOffX == 0 && SmoothOffY == 0 && (tX != 0 || tY != 0)) {
        SmoothOffX := tX
        SmoothOffY := tY
    }

    SmoothOffX += (tX - SmoothOffX) * 0.15
    SmoothOffY += (tY - SmoothOffY) * 0.15

    DllCall("magnification\MagSetFullscreenTransform", "Float", CurrentZoom, "Int", Round(SmoothOffX), "Int", Round(SmoothOffY))
}

SetTarget(Level) {
    global TargetZoom, TimerActive
    TargetZoom := Level
    if (!TimerActive && TargetZoom > 1.0) {
        SetTimer UpdateTransform, 8
        TimerActive := true
    }
}

; --- MANEJO DE DICTADO INTERNO ---

Dictar(lang) {
    global isRecording, isTranscribing, targetDevice
    
    if (isTranscribing) {
        return
    }

    baseDir := A_ScriptDir
    audioFile := baseDir "\temp_audio.mp3"
    wavFile := baseDir "\temp_clean.wav"
    txtFile := baseDir "\temp_clean.txt"
    logFile := baseDir "\conversion_log.txt"
    ffmpegExe := baseDir "\bin\ffmpeg.exe"
    exeFile := baseDir "\bin\whisper_example.exe"
 
    modelDir := EnvGet("LOCALAPPDATA") "\eddy\models\whisper-small-int8-ov"
    
    audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"
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
                    if (SubStr(linea, 1, 1) = "[" || InStr(linea, "vpux-compiler") || InStr(linea, "AlignDimensionsForDPU") || InStr(linea, "Failed Pass"))
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

                    hasActiveInput := false
                    activeWin := WinExist("A")
                    
                    if activeWin {
                        ; Only trust ControlGetFocus on the ACTIVE window - this returns the focused control
                        try {
                            focusedCtrl := ControlGetFocus("ahk_id " activeWin)
                            if (focusedCtrl != "") {
                                ; Get the class of the SPECIFIC focused control
                                classNN := ControlGetClassNN(focusedCtrl, "ahk_id " activeWin)
                                if (classNN != "") {
                                    className := StrSplit(classNN, "1234567890")[1]
                                    ; Strict list of actual text input control classes
                                    if (className ~= "i)^(Edit|RichEdit20W|RichEdit50W|Scintilla|TextBox|TextArea|Password|SearchBox|ComboBox|ComboLBox|EditControl|Chrome_RenderWidgetHostHWND|Chrome_WidgetWin_1|Internet Explorer_Server|EdgeView|WebView2|MozillaWindowClass)$") {
                                        hasActiveInput := true
                                    }
                                }
                            }
                        }
                        
                        ; If no focused control found, check if the active window ITSELF is a text input
                        if (!hasActiveInput) {
                            try {
                                className := WinGetClass("ahk_id " activeWin)
                                if (className ~= "i)^(Edit|RichEdit20W|RichEdit50W|Scintilla|TextBox|TextArea|Password|SearchBox|ComboBox|ComboLBox|EditControl|Chrome_RenderWidgetHostHWND|Chrome_WidgetWin_1|Internet Explorer_Server|EdgeView|WebView2|MozillaWindowClass)$") {
                                    hasActiveInput := true
                                }
                            }
                        }
                    }

                    if (hasActiveInput) {
                        ClipSaved := ClipboardAll()
                        A_Clipboard := resultado
                        if ClipWait(1) {
                            Send("^v")
                            Sleep(100)
                        }
                        A_Clipboard := ClipSaved
                    } else {
                        A_Clipboard := resultado
                        ClipWait(1)
                        SoundBeep(880, 200)
                    }
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

; --- DICTADO HANDY (Win+S) ---
IniciarDictadoHandyDirecto() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing
    global handyDirectBaselineId, handyDirectFinishing, handyDirectStartPending, handyDirectTargetHwnd
    if isRecording || openCodeIsRecording || openCodeIsTranscribing
        return
    if handyDirectFinishing {
        handyDirectStartPending := true
        return
    }
    ; Handy sólo reconoce la voz. El script pega después en la ventana original,
    ; evitando fallos silenciosos del Ctrl+V simulado por Handy.
    handyDirectTargetHwnd := WinExist("A")
    PrepararHandy("none")
    handyDirectBaselineId := HandyLatestId()
    if HandyToggleTranscription() {
        isRecording := true
        ProgramarCierreHandy()
    }
}

EsperarFinDictadoHandyDirecto() {
    global handyDirectBaselineId, handyDirectFinishing, handyDirectWaitPid
    global handyDirectWaitFile, handyDirectWaitDeadline
    handyDirectWaitFile := A_Temp "\handy_direct_" A_TickCount ".txt"
    handyDirectWaitDeadline := A_TickCount + 13000
    try Run(ComandoVoiceHelper("handy_history") ' --wait-after-id ' handyDirectBaselineId ' --timeout 12 --output "' handyDirectWaitFile '"', , "Hide", &handyDirectWaitPid)
    catch {
        handyDirectFinishing := false
        handyDirectWaitPid := 0
        return
    }
    SetTimer(RevisarFinDictadoHandyDirecto, -60)
}

RevisarFinDictadoHandyDirecto() {
    global handyDirectFinishing, handyDirectStartPending, handyDirectWaitPid, handyDirectWaitFile
    global handyDirectTargetHwnd, handyDirectWaitDeadline

    ; El ejecutable empaquetado puede cambiar de PID al arrancar. El archivo es
    ; la fuente de verdad: esperamos hasta que tenga texto o venza el plazo.
    transcripcion := ""
    if (handyDirectWaitFile != "" && FileExist(handyDirectWaitFile))
        transcripcion := Trim(FileRead(handyDirectWaitFile, "UTF-8"))

    if (transcripcion = "" && A_TickCount < handyDirectWaitDeadline) {
        SetTimer(RevisarFinDictadoHandyDirecto, -75)
        return
    }
    handyDirectWaitPid := 0
    handyDirectFinishing := false
    try FileDelete(handyDirectWaitFile)
    handyDirectWaitFile := ""
    handyDirectWaitDeadline := 0

    if (transcripcion != "") {
        A_Clipboard := transcripcion
        if ClipWait(1) {
            KeyWait("LWin")
            KeyWait("RWin")
            if handyDirectTargetHwnd && WinExist("ahk_id " handyDirectTargetHwnd) {
                WinActivate("ahk_id " handyDirectTargetHwnd)
                WinWaitActive("ahk_id " handyDirectTargetHwnd, , 2)
            }
            Send("^v")
        }
    }
    handyDirectTargetHwnd := 0

    if handyDirectStartPending {
        handyDirectStartPending := false
        SetTimer(IniciarDictadoHandyDirecto, -80)
    }
}

$#s:: {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing, handyDirectFinishing
    KeyWait("s")
    if openCodeIsRecording || openCodeIsTranscribing
        return
    if !isRecording {
        IniciarDictadoHandyDirecto()
        return
    }
    if HandyToggleTranscription() {
        isRecording := false
        handyDirectFinishing := true
        ProgramarCierreHandy()
        EsperarFinDictadoHandyDirecto()
    }
}

; --- AGENTE OPENCODE (Win+O) ---
#o:: {
    KeyWait("o")
    ManejarWinO()
}

ManejarWinO() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing, pendingAgentStart, voiceForCurrentRequest
    if isRecording || openCodeIsTranscribing
        return
    if openCodeIsRecording {
        OpenCodeDictarHandy()
        return
    }
    ; Dos pulsaciones dentro de 280 ms: misma respuesta escrita y además voz.
    if pendingAgentStart {
        pendingAgentStart := false
        voiceForCurrentRequest := true
        SetTimer(IniciarAgentePendiente, 0)
        OpenCodeDictarHandy()
        return
    }
    voiceForCurrentRequest := false
    pendingAgentStart := true
    SetTimer(IniciarAgentePendiente, -280)
}

IniciarAgentePendiente() {
    global pendingAgentStart, isRecording, openCodeIsRecording, openCodeIsTranscribing
    if !pendingAgentStart || isRecording || openCodeIsRecording || openCodeIsTranscribing
        return
    pendingAgentStart := false
    OpenCodeDictarHandy()
}

OpenCodeDictarHandy() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing
    global openCodeTargetHwnd, openCodeServerUrl, handyBaselineId, voicePackageDir, voiceForCurrentRequest
    if isRecording || openCodeIsTranscribing
        return
    if !openCodeIsRecording {
        openCodeTargetHwnd := WinExist("A")
        PrepararHandy("none")
        handyBaselineId := HandyLatestId()
        if HandyToggleTranscription() {
            openCodeIsRecording := true
            ProgramarCierreHandy()
        }
        return
    }
    openCodeIsRecording := false
    openCodeIsTranscribing := true
    savedClipboard := ClipboardAll()
    try {
        if !HandyToggleTranscription()
            throw Error("No se pudo detener Handy")
        transcripcion := HandyWaitForTranscript(handyBaselineId)
        if (transcripcion = "")
            return
        EnvSet("OPENCODE_ATTACH_URL", AsegurarOpenCodeServer() ? openCodeServerUrl : "")
        promptFile := A_Temp "\opencode_prompt_" A_TickCount ".txt"
        outFile := A_Temp "\opencode_output_" A_TickCount ".txt"
        FileAppend(transcripcion, promptFile, "UTF-8-RAW")
        runner := voicePackageDir "\run_opencode_tts\run_opencode_tts.exe"
        RunWait('"' runner '" --output-file "' outFile '" "@' promptFile '"', , "Hide")
        ProgramarCierreOpenCode()
    if FileExist(outFile) {
            resultado := Trim(FileRead(outFile, "UTF-8"))
            if (resultado != "") {
                A_Clipboard := resultado
                ClipWait(1)
                if openCodeTargetHwnd && WinExist("ahk_id " openCodeTargetHwnd)
                    WinActivate("ahk_id " openCodeTargetHwnd)
                Send("^v")
                Sleep(100)
                if voiceForCurrentRequest
                    ReproducirVoz(resultado)
            }
        }
        try FileDelete(promptFile)
        try FileDelete(outFile)
    } catch as err {
        MsgBox("El agente no pudo completar la solicitud:`n" err.Message, "OpenCode", "Iconx")
    } finally {
        A_Clipboard := savedClipboard
        openCodeIsTranscribing := false
        ProgramarCierreHandy()
    }
}

ReproducirVoz(texto) {
    ; Voz local de Windows, sin depender de un runtime TTS externo.
    try {
        voz := ComObject("SAPI.SpVoice")
        voz.Rate := 0
        voz.Volume := 100
        voz.Speak(texto, 1) ; SVSFlagsAsync: no bloquea el agente.
    }
}

; --- DIBUJO EN PANTALLA TIPO ZOOMIT ---

#!d:: ToggleDrawGuard()

ToggleDrawGuard() {
    global TargetZoom
    if (TargetZoom > 1.0)
        return
    ToggleDraw()
}

ToggleDraw() {
    global drawing, prevX, prevY, drawGui
    global hdcMem, hbmMem, hbmBg, hPen, hBrush, hdcScreen

    if (drawGui) {
        OnMessage(0x0201, OnDrawDown, 0)
        OnMessage(0x0202, OnDrawUp, 0)
        OnMessage(0x0200, OnDrawMove, 0)
        OnMessage(0x0084, OnNcHitTest, 0)
        
        DllCall("DeleteObject", "Ptr", hPen)
        DllCall("DeleteObject", "Ptr", hBrush)
        DllCall("DeleteObject", "Ptr", hbmMem)
        if (hbmBg)
            DllCall("DeleteObject", "Ptr", hbmBg)
        DllCall("DeleteDC", "Ptr", hdcMem)
        if (hdcScreen)
            DllCall("ReleaseDC", "Ptr", 0, "Ptr", hdcScreen)
        
        drawGui.Destroy()
        drawGui := ""
        drawing := false
        return
    }

    hPen := DllCall("CreatePen", "Int", 0, "Int", 4, "UInt", 0x0000FF, "Ptr")
    hBrush := DllCall("CreateSolidBrush", "UInt", 0x0000FF, "Ptr")

    hdcScreen := DllCall("GetDC", "Ptr", 0, "Ptr")
    hdcMem := DllCall("CreateCompatibleDC", "Ptr", hdcScreen, "Ptr")
    
    bmi := Buffer(40, 0)
    NumPut("UInt", 40, bmi, 0)
    NumPut("Int", A_ScreenWidth, bmi, 4)
    NumPut("Int", -A_ScreenHeight, bmi, 8)
    NumPut("UShort", 1, bmi, 12)
    NumPut("UShort", 32, bmi, 14)
    NumPut("UInt", 0, bmi, 16)
    
    hbmMem := DllCall("CreateDIBSection", "Ptr", hdcMem, "Ptr", bmi, "UInt", 0, "Ptr*", 0, "Ptr", 0, "UInt", 0, "Ptr")
    DllCall("SelectObject", "Ptr", hdcMem, "Ptr", hbmMem)
    
    hBrushKey := DllCall("CreateSolidBrush", "UInt", 0xFF00FF, "Ptr")
    DllCall("SelectObject", "Ptr", hdcMem, "Ptr", hBrushKey)
    DllCall("Rectangle", "Ptr", hdcMem, "Int", 0, "Int", 0, "Int", A_ScreenWidth, "Int", A_ScreenHeight)
    DllCall("DeleteObject", "Ptr", hBrushKey)
    
    hbmBg := 0

    drawGui := Gui("+AlwaysOnTop -Caption +ToolWindow +E0x80000", "DrawCanvas")
    drawGui.Opt("+MaxSize" A_ScreenWidth "x" A_ScreenHeight)
    drawGui.Show("x0 y0 w" A_ScreenWidth " h" A_ScreenHeight)

    DllCall("SetLayeredWindowAttributes", "Ptr", drawGui.Hwnd, "UInt", 0xFF00FF, "UChar", 0, "UInt", 1)

    OnMessage(0x0084, OnNcHitTest)

    RedrawCanvas(0, 0)

    OnMessage(0x0201, OnDrawDown)
    OnMessage(0x0202, OnDrawUp)
    OnMessage(0x0200, OnDrawMove)

    drawGui.OnEvent("Escape", (*) => ToggleDraw())
}

OnDrawDown(wParam, lParam, msg, hwnd) {
    global drawing, prevX, prevY, drawGui
    if (!drawGui || hwnd != drawGui.Hwnd)
        return
    drawing := true
    prevX := lParam & 0xFFFF
    prevY := (lParam >> 16) & 0xFFFF
    return 0
}

OnDrawUp(wParam, lParam, msg, hwnd) {
    global drawing, drawGui
    if (!drawGui || hwnd != drawGui.Hwnd)
        return
    drawing := false
    return 0
}

OnDrawMove(wParam, lParam, msg, hwnd) {
    global drawing, prevX, prevY, hdcMem, hPen, drawGui
    if (!drawGui || hwnd != drawGui.Hwnd)
        return

    x := lParam & 0xFFFF
    y := (lParam >> 16) & 0xFFFF

    if (drawing) {
        DllCall("SelectObject", "Ptr", hdcMem, "Ptr", hPen)
        DllCall("MoveToEx", "Ptr", hdcMem, "Int", prevX, "Int", prevY, "Ptr", 0)
        DllCall("LineTo", "Ptr", hdcMem, "Int", x, "Int", y)
        prevX := x
        prevY := y
    }

    RedrawCanvas(x, y)
    return 0
}

OnNcHitTest(wParam, lParam, msg, hwnd) {
    global drawGui, drawing
    if (!drawGui || hwnd != drawGui.Hwnd)
        return
    return drawing ? 1 : -1
}

RedrawCanvas(mx, my) {
    global drawGui, hdcMem, hPen, hBrush
    if (!drawGui)
        return

    if (drawing) {
        DllCall("SelectObject", "Ptr", hdcMem, "Ptr", hBrush)
        DllCall("Ellipse", "Ptr", hdcMem, "Int", mx - 5, "Int", my - 5, "Int", mx + 5, "Int", my + 5)
    }

    ptSrc := Buffer(8, 0)
    ptDst := Buffer(8, 0)
    szWnd := Buffer(8, 0)
    NumPut("Int", A_ScreenWidth, szWnd, 0)
    NumPut("Int", A_ScreenHeight, szWnd, 4)

    DllCall("UpdateLayeredWindow", "Ptr", drawGui.Hwnd, "Ptr", 0, "Ptr", ptDst, "Ptr", szWnd
        , "Ptr", hdcMem, "Ptr", ptSrc, "UInt", 0xFF00FF, "Ptr", 0, "UInt", 1)
}

CleanupMagnifier() {
    global drawGui, hdcMem, hPen, hBrush, hbmMem, hbmBg
    if hdcMem {
        DllCall("DeleteObject", "Ptr", hPen)
        DllCall("DeleteObject", "Ptr", hBrush)
        DllCall("DeleteObject", "Ptr", hbmMem)
        DllCall("DeleteObject", "Ptr", hbmBg)
        DllCall("DeleteDC", "Ptr", hdcMem)
    }
    DllCall("magnification\MagUninitialize")
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

; --- ATAJOS PERSONALIZADOS DE MAGNIFICACIÓN ---
^+!q:: SetTarget(TargetZoom < 4.0 ? TargetZoom + 0.75 : TargetZoom)
^+!w:: SetTarget(TargetZoom > 1.0 ? TargetZoom - 0.75 : 1.0)
^+!e:: SetTarget(1.0)

; --- MONITOREO DEL BOTÓN DE AHORRO ---
MonitorearBotonAhorro() {
    global energiaIndex, perfilesEnergia, nombresEnergia, ultimoEstadoAhorro, perfilPrevio
    
    try {
        ahorroActivo := RegRead("HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Controls Folder\PowerCfg", "EnergySaverStatus")
    } catch {
        ahorroActivo := 0
        for obj in ComObjGet("winmgmts:").ExecQuery("Select * from Win32_PowerPlan Where IsActive = True") {
            if InStr(obj.Description, "saver") || InStr(obj.ElementName, "Economizador")
                ahorroActivo := 1
        }
    }

    if (ahorroActivo == 1 && ultimoEstadoAhorro == 0) {
        ultimoEstadoAhorro := 1
        if (energiaIndex != 4) {
            perfilPrevio := energiaIndex
        }
        energiaIndex := 4 
        try {
            Run("powercfg /setactive " . perfilesEnergia[energiaIndex], , "Hide")
            ToolTip("Ahorro de Windows Activado -> Cambiando a Economizador")
            SetTimer(() => ToolTip(), -2000)
        }
    }
    else if (ahorroActivo == 0 && ultimoEstadoAhorro == 1) {
        ultimoEstadoAhorro := 0
        energiaIndex := perfilPrevio 
        try {
            Run("powercfg /setactive " . perfilesEnergia[energiaIndex], , "Hide")
            ToolTip("Ahorro de Windows Desactivado -> Restaurando " . nombresEnergia[energiaIndex])
            SetTimer(() => ToolTip(), -2000)
        }
    }
}

; --- GESTIÓN EXCLUSIVA POR TECLADO PARA LA BARRA DE TAREAS Y ENERGÍA ---

~#b::
{
    global PermitirMouse := true
    DllCall("ClipCursor", "Ptr", 0)
    SetTimer(MonitorearCierre, 100)
    
    global energiaIndex, perfilesEnergia, nombresEnergia, perfilPrevio, ultimoEstadoAhorro
    
    energiaIndex++
    if (energiaIndex > perfilesEnergia.Length) {
        energiaIndex := 1
    }
    
    if (ultimoEstadoAhorro == 0 && energiaIndex != 4) {
        perfilPrevio := energiaIndex
    }
    
    targetGUID := perfilesEnergia[energiaIndex]
    
    try {
        Run("powercfg /setactive " . targetGUID, , "Hide")
    } catch {
        if (targetGUID == "e9a42b02-d5df-448d-aa00-03f14749eb61") {
            try {
                Run("powercfg /duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61", , "Hide")
                Sleep(100)
                Run("powercfg /setactive e9a42b02-d5df-448d-aa00-03f14749eb61", , "Hide")
            }
        }
    }
    
    ToolTip(nombresEnergia[energiaIndex])
    SetTimer(() => ToolTip(), -1500)
}

~#t::
~LWin::
~RWin::
{
    global PermitirMouse := true
    DllCall("ClipCursor", "Ptr", 0)
    SetTimer(MonitorearCierre, 100)
}
