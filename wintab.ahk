#Requires AutoHotkey v2.0+
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

; --- CONFIGURACIÓN DE PROCESOS Y ATAJOS NATIVOS ---
#!:: return

; --- CONFIGURACIÓN DE DICTADO LOCAL PARAKEET-V3 ---
global targetDevice := "GPU"
global isRecording := false
global isTranscribing := false
; Estado del dictado directo: Handy tarda unos instantes en liberar el modelo
; después de detener una grabación. Guardamos el siguiente inicio solicitado
; para que un Win+S temprano no se pierda.
global handyDirectBaselineId := 0
global handyDirectFinishing := false
global handyDirectStartPending := false
global handyDirectWaitPid := 0
global handyDirectWaitFile := ""
global handyExe := EnvGet("LOCALAPPDATA") . "\Handy\handy.exe"
global handyIdleTimeout := 180000 ; 3 minutos
global handyModeScript := A_ScriptDir "\handy_mode.py"
global openCodeIsRecording := false
global openCodeIsTranscribing := false
global openCodeTargetHwnd := 0
global handyHistoryScript := A_ScriptDir "\handy_history.py"
global handyBaselineId := 0
global openCodeServerPid := 0
global openCodeServerUrl := "http://127.0.0.1:4096"
global OpenCodeCmd := A_ScriptDir "\VoiceAssistant\runtime\npm-global\opencode.cmd"
global OpenCodeProjectDir := A_Desktop
global pendingVoiceStart := false
global voiceForCurrentRequest := false
global raycastIdleTimeout := 180000 ; 3 minutos
global voicePackageDir := A_ScriptDir "\VoiceAssistant"

ComandoVoiceHelper(nombre) {
    global voicePackageDir
    exe := voicePackageDir "\" nombre "\" nombre ".exe"
    if FileExist(exe)
        return '"' exe '"'
    pyExe := "C:\Users\adarlpz\AppData\Local\Programs\Python\Python312\python.exe"
    return '"' pyExe '" "' A_ScriptDir "\" nombre ".py"
}

CerrarHandyWebView() {
    ; Handy usa WebView2 para su interfaz. Cerramos únicamente los procesos
    ; cuya línea de comandos identifica a Handy, no todos los WebView2 del sistema.
    psCommand := "Get-CimInstance Win32_Process -Filter 'Name = ''msedgewebview2.exe''' | "
        . "Where-Object { $_.CommandLine -match '(?i)handy|com\.cjpais' } | "
        . "ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
    try RunWait('powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -Command "' psCommand '"', , "Hide")
}

ObtenerModoHandy() {
    global handyModeScript
    outFile := A_ScriptDir "\temp_handy_mode.txt"
    try FileDelete(outFile)
    try RunWait(ComandoVoiceHelper("handy_mode") ' --get --output "' outFile '"', , "Hide")
    if !FileExist(outFile)
        return ""
    mode := Trim(FileRead(outFile, "UTF-8"))
    try FileDelete(outFile)
    return mode
}

EstablecerModoHandy(modo) {
    global handyModeScript
    try RunWait(ComandoVoiceHelper("handy_mode") " --set " modo, , "Hide")
}

PrepararHandyParaOpenCode() {
    ; OpenCode lee la transcripción desde el historial: Handy no debe pegarla.
    if (ObtenerModoHandy() = "none" && ProcessExist("handy.exe"))
        return

    if ProcessExist("handy.exe") {
        try ProcessClose("handy.exe")
        Sleep(500)
        CerrarHandyWebView()
    }
    EstablecerModoHandy("none")
}

HandyLatestId() {
    global handyHistoryScript
    outFile := A_ScriptDir "\temp_handy_id.txt"
    try FileDelete(outFile)
    try RunWait(ComandoVoiceHelper("handy_history") ' --id --output "' outFile '"', , "Hide")
    if !FileExist(outFile)
        return 0
    value := Trim(FileRead(outFile, "UTF-8"))
    try FileDelete(outFile)
    return (value ~= "^\d+$") ? Integer(value) : 0
}

HandyWaitForTranscript(afterId) {
    global handyHistoryScript
    outFile := A_ScriptDir "\temp_handy_transcript.txt"
    try FileDelete(outFile)
    try RunWait(ComandoVoiceHelper("handy_history") ' --wait-after-id ' afterId ' --output "' outFile '"', , "Hide")
    if !FileExist(outFile)
        return ""
    text := Trim(FileRead(outFile, "UTF-8"))
    try FileDelete(outFile)
    return text
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

ProgramarCierreOpenCode() {
    SetTimer(CerrarOpenCodeServer, 0)
    SetTimer(CerrarOpenCodeServer, -180000)
}

ProgramarCierreRaycast() {
    global raycastIdleTimeout
    SetTimer(CerrarRaycastPorInactividad, 0)
    SetTimer(CerrarRaycastPorInactividad, -raycastIdleTimeout)
}

CerrarRaycastPorInactividad() {
    if ProcessExist("Raycast.exe")
        try ProcessClose("Raycast.exe")
}

AsegurarOpenCodeServer() {
    global OpenCodeCmd, OpenCodeProjectDir, openCodeServerPid, openCodeServerUrl
    if OpenCodeServerReady()
        return true
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

PrepararHandyParaWintap() {
    global handyModeScript
    if (ObtenerModoHandy() = "direct")
        return

    ; OpenCode usa "none". Wintap requiere que Handy vuelva a pegar texto.
    if ProcessExist("handy.exe") {
        try ProcessClose("handy.exe")
        Sleep(500)
        CerrarHandyWebView()
    }
    try RunWait(ComandoVoiceHelper("handy_mode") " --set direct", , "Hide")
}

ProgramarCierreHandy() {
    global handyIdleTimeout
    SetTimer(CerrarHandyPorInactividad, 0)
    SetTimer(CerrarHandyPorInactividad, -handyIdleTimeout)
}

CerrarHandyPorInactividad() {
    global isRecording, openCodeIsRecording

    ; Nunca cerrar Handy mientras el usuario sigue grabando.
    if isRecording || openCodeIsRecording {
        SetTimer(CerrarHandyPorInactividad, -30000)
        return
    }

    if !ProcessExist("handy.exe")
        return

    try ProcessClose("handy.exe")
    Sleep(500)
    CerrarHandyWebView()
    EstablecerModoHandy("direct")
}

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
global perfilPrevio := (energiaIndex == 4) ? 2 : energiaIndex ; Si arranca en economizador, respalda Alto Rendimiento
global ultimoEstadoAhorro := 0

; --- TIMERS CONTINUOS ---
SetTimer(CheckState, 50)
SetTimer ControlarBarreraMouse, 10
SetTimer MonitorearBotonAhorro, 1000
SetTimer CheckStremio, 100 ; Monitoreo automático de Stremio (Fullscreen)
SetTimer CheckWarp, 100    ; Monitoreo automático de Warp (Ctrl+Shift+B)
SetTimer KillMagnify, 16

; --- CONTROL DE FINALIZACIÓN Y LIMPIEZA ---
OnExit(ExitCleanup)

ExitCleanup(*) {
    CerrarOpenCodeServer()
    DllCall("ClipCursor", "Ptr", 0)
    RestoreCursors()
    CleanupMagnifier()
}

; --- FUNCIONES DE MAGNIFICACIÓN ---

KillMagnify() {
    try {
        m := WinExist("ahk_exe Magnify.exe")
        if m
            WinKill m
    }
}

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

CheckStremio() {
    if WinExist("ahk_exe stremio-shell-ng.exe") {
        WinWait "ahk_exe stremio-shell-ng.exe"
        Sleep 800
        WinActivate "ahk_exe stremio-shell-ng.exe"
        WinWaitActive "ahk_exe stremio-shell-ng.exe"
        Send "{F11}"
        SetTimer CheckStremio, 0
        WinWaitClose "ahk_exe stremio-shell-ng.exe"
        SetTimer CheckStremio, 100
    }
}

CheckWarp() {
    if WinExist("ahk_exe warp.exe") {
        WinWait "ahk_exe warp.exe"
        Sleep 1000  ; Puedes ajustar el delay modificando este número en milisegundos
        WinActivate "ahk_exe warp.exe"
        WinWaitActive "ahk_exe warp.exe"

        Send "^+b" ; Envía Control + Shift + B

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
        MsgBox("No se encontró " . nombre . " en:`n" . ruta, "Error de Ejecución", 48)
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

RCtrlSingleAction() {
    LanzarApp("Gemini", EnvGet("APPDATA") . "\Gemini\gemini.exe")
}

LShiftAction() {
    global lShiftClicks

    if (lShiftClicks = 2) {
        if FileExist("F:\adarlpz\tempfiles\tempfiles.vbs")
            Run("F:\adarlpz\tempfiles\tempfiles.vbs")
        else
            Run("D:\adarlpz\tempfiles\tempfiles.vbs")
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

    modelName := "whisper-small-int8-ov"
    modelDir := baseDir "\models\" modelName
    if (!DirExist(modelDir)) {
        modelDir := EnvGet("LOCALAPPDATA") "\eddy\models\" modelName
    }

    friendlyName1 := RegRead("HKLM\System\CurrentControlSet\Control\DeviceClasses\{2eef81be-33fa-4800-9670-1cd474972c3f}\##?#SWD#MMDEVAPI#{0.0.1.00000000}.{08e80c7f-338c-4c96-9f52-06121768c053}#{2eef81be-33fa-4800-9670-1cd474972c3f}\#\Device Parameters", "FriendlyName", "")
    friendlyName2 := RegRead("HKLM\System\CurrentControlSet\Control\DeviceClasses\{2eef81be-33fa-4800-9670-1cd474972c3f}\##?#SWD#MMDEVAPI#{0.0.1.00000000}.{66e16202-66f3-4d6b-a7b8-8564c5377ac0}#{2eef81be-33fa-4800-9670-1cd474972c3f}\#\Device Parameters", "FriendlyName", "")

    if (friendlyName2 != "") {
        audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"
    } else {
        audioDevice := "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\wave_{08E80C7F-338C-4C96-9F52-06121768C053}"
    }
    winTitle := "ffmpeg_rec_window"

    DetectHiddenWindows(True)

    if (!isRecording) {
        isRecording := true

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

        dummyWav := baseDir "\bin\silent_test.wav"
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

global currentLang := "auto"

; --- CONTROL DE HANDY ---
; Handy mantiene el modelo, micrófono, modo de pegado y shortcut configurados.
; Usamos su interfaz de línea de comandos para evitar abrir/enfocar la ventana.
HandyToggleTranscription() {
    global handyExe
    handyWasStarted := false

    if !FileExist(handyExe) {
        MsgBox("No se encontró Handy en:`n" handyExe, "Handy", "Iconx")
        return false
    }

    ; Si Handy no está en ejecución, se inicia minimizado/oculto una sola vez.
    if !ProcessExist("handy.exe") {
        handyWasStarted := true
        try Run('"' handyExe '" --start-hidden', , "Hide")
        loop 40 {
            if ProcessExist("handy.exe")
                break
            Sleep(25)
        }
    }

    if !ProcessExist("handy.exe") {
        MsgBox("Handy no pudo iniciarse.", "Handy", "Iconx")
        return false
    }

    ; Que el proceso exista no significa que Tauri ya esté listo para recibir
    ; comandos. Esperamos a que termine de levantar su instancia residente.
    if handyWasStarted {
        Sleep(1100)
        ; Evita que la ventana principal llegue a quedar visible durante el arranque.
        try WinHide("ahk_exe handy.exe")
    }

    ; La instancia residente recibe la orden y alterna iniciar/detener.
    try Run('"' handyExe '" --toggle-transcription', , "Hide")
    catch as err {
        MsgBox("No se pudo activar Handy:`n" err.Message, "Handy", "Iconx")
        return false
    }

    if handyWasStarted {
        Sleep(300)
        CerrarHandyWebView()
    }
    return true
}

IniciarDictadoHandyDirecto() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing
    global handyDirectBaselineId, handyDirectFinishing, handyDirectStartPending

    if isRecording || openCodeIsRecording || openCodeIsTranscribing
        return

    ; Si Handy aún está cerrando la transcripción anterior, conservar esta
    ; intención y arrancarla automáticamente cuando el historial se actualice.
    if handyDirectFinishing {
        handyDirectStartPending := true
        return
    }

    PrepararHandyParaWintap()
    handyDirectBaselineId := HandyLatestId()
    if HandyToggleTranscription() {
        isRecording := true
        ProgramarCierreHandy()
    }
}

EsperarFinDictadoHandyDirecto() {
    global handyDirectBaselineId, handyDirectFinishing, handyDirectWaitPid, handyDirectWaitFile

    handyDirectWaitFile := A_Temp "\handy_direct_" A_TickCount ".txt"
    try FileDelete(handyDirectWaitFile)
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

    if handyDirectWaitPid && ProcessExist(handyDirectWaitPid) {
        SetTimer(RevisarFinDictadoHandyDirecto, -75)
        return
    }

    handyDirectWaitPid := 0
    handyDirectFinishing := false
    if (handyDirectWaitFile != "") {
        try FileDelete(handyDirectWaitFile)
        handyDirectWaitFile := ""
    }

    ; Una pulsación durante el procesamiento arranca aquí, apenas Handy termina.
    if handyDirectStartPending {
        handyDirectStartPending := false
        SetTimer(IniciarDictadoHandyDirecto, -80)
    }
}

; --- OPENCODE VOICE (Win+O) ---
; Un toque inicia texto; doble toque rápido inicia texto + voz.
; Cuando Handy ya graba para OpenCode, un toque siempre la detiene.
#o:: {
    KeyWait("o")
    ManejarWinO()
}

ManejarWinO() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing
    global pendingVoiceStart, voiceForCurrentRequest
    if isRecording || openCodeIsTranscribing
        return
    if openCodeIsRecording {
        OpenCodeDictarHandy()
        return
    }
    if pendingVoiceStart {
        pendingVoiceStart := false
        SetTimer(IniciarGrabacionOpenCodePendiente, 0)
        voiceForCurrentRequest := true
        OpenCodeDictarHandy()
        return
    }
    pendingVoiceStart := true
    voiceForCurrentRequest := false
    SetTimer(IniciarGrabacionOpenCodePendiente, -280)
}

IniciarGrabacionOpenCodePendiente() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing, pendingVoiceStart
    if !pendingVoiceStart || isRecording || openCodeIsRecording || openCodeIsTranscribing
        return
    pendingVoiceStart := false
    OpenCodeDictarHandy()
}

OpenCodeDictarHandy() {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing
    global openCodeTargetHwnd, openCodeServerUrl, handyBaselineId, voiceForCurrentRequest, voicePackageDir

    if isRecording || openCodeIsTranscribing
        return

    if !openCodeIsRecording {
        openCodeTargetHwnd := WinExist("A")
        handyBaselineId := HandyLatestId()
        PrepararHandyParaOpenCode()
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
        A_Clipboard := savedClipboard
        if (transcripcion = "")
            return

        if AsegurarOpenCodeServer()
            EnvSet("OPENCODE_ATTACH_URL", openCodeServerUrl)
        else
            EnvSet("OPENCODE_ATTACH_URL", "")

        runScript := voicePackageDir "\run_opencode_tts\run_opencode_tts.exe"
        usePackagedRunner := FileExist(runScript)
        if !usePackagedRunner
            runScript := A_ScriptDir "\run_opencode_tts.py"
        promptFile := A_ScriptDir "\temp_opencode_prompt.txt"
        outFile := A_ScriptDir "\temp_run_output.txt"
        try FileDelete(promptFile)
        try FileDelete(outFile)
        FileAppend(transcripcion, promptFile, "UTF-8-RAW")

        speakArg := voiceForCurrentRequest ? " --speak" : ""
        if usePackagedRunner
            RunWait('"' runScript '" --output-file "' outFile '"' speakArg ' "@' promptFile '"', , "Hide")
        else {
            pyExe := "C:\Users\adarlpz\AppData\Local\Programs\Python\Python312\python.exe"
            RunWait('"' pyExe '" "' runScript '" --output-file "' outFile '"' speakArg ' "@' promptFile '"', , "Hide")
        }
        ProgramarCierreOpenCode()

        resultado := ""
        if FileExist(outFile) {
            resultado := Trim(FileRead(outFile, "UTF-8"))
            try FileDelete(outFile)
        }
        try FileDelete(promptFile)

        if (resultado != "") {
            A_Clipboard := resultado
            ClipWait(1)
            if openCodeTargetHwnd && WinExist("ahk_id " openCodeTargetHwnd)
                WinActivate("ahk_id " openCodeTargetHwnd)
            Send("^v")
            Sleep(100)
        }
    } catch {
        A_Clipboard := savedClipboard
    } finally {
        openCodeIsTranscribing := false
        ProgramarCierreHandy()
    }
}

; --- ATAJO DE DICTADO (Win + S) ---
$#s:: {
    global isRecording, openCodeIsRecording, openCodeIsTranscribing
    global handyDirectFinishing, handyDirectStartPending

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
    ProgramarCierreRaycast()
}

; --- MONITOREAR EL BOTÓN DE AHORRO ---
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

; --- ATAJOS DE ZOOM (MAGNIFICACIÓN) ---
^+!q:: SetTarget(TargetZoom < 4.0 ? TargetZoom + 0.75 : TargetZoom)
^+!w:: SetTarget(TargetZoom > 1.0 ? TargetZoom - 0.75 : 1.0)
^+!e:: SetTarget(1.0)

~#t::
~LWin::
~RWin::
{
    global PermitirMouse := true
    DllCall("ClipCursor", "Ptr", 0)
    SetTimer(MonitorearCierre, 100)
}
