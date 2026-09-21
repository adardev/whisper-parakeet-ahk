#Requires AutoHotkey v2.0
#SingleInstance Force
Persistent

global BaseUrl := "http://127.0.0.1:17841"
global Busy := false
global SoundRoot := A_ScriptDir "\sounds"

^Space::ToggleTranscription()
Escape::CancelTranscription()

ToggleTranscription() {
    global Busy, BaseUrl, SoundRoot
    if Busy
        return
    Busy := true
    try {
        status := HttpGet(BaseUrl "/status")
        if RegExMatch(status, '"recording"\s*:\s*true') {
            text := HttpGet(BaseUrl "/stop")
            PlayFeedback("marimba_stop.wav")
            if (text != "")
                PasteText(text)
        } else {
            HttpGet(BaseUrl "/start")
            PlayFeedback("marimba_start.wav")
        }
    } catch Error as e {
        TrayTip("Handy separado", e.Message, 3)
    }
    Busy := false
}

PlayFeedback(name) {
    global SoundRoot
    try SoundPlay(SoundRoot "\" name)
}

CancelTranscription() {
    global Busy, BaseUrl
    if Busy
        return
    try HttpGet(BaseUrl "/stop")
}

HttpGet(url) {
    req := ComObject("WinHttp.WinHttpRequest.5.1")
    req.Open("GET", url, false)
    req.SetTimeouts(1000, 1000, 30000, 30000)
    req.Send()
    if req.Status != 200
        throw Error("Servidor de transcripción: HTTP " req.Status " - " req.ResponseText)
    return req.ResponseText
}

PasteText(text) {
    old := ClipboardAll()
    A_Clipboard := text
    ClipWait(1)
    Send("^v")
    Sleep(60)
    A_Clipboard := old
}
