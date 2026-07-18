import sys
import os
import subprocess
import json
import urllib.error
import urllib.request

MODEL = "opencode/deepseek-v4-flash-free"
PY_EXE = sys.executable
BASE_DIR = os.path.dirname(sys.executable) if getattr(sys, "frozen", False) else os.path.dirname(os.path.abspath(__file__))
PACKAGE_ROOT = os.path.dirname(BASE_DIR) if getattr(sys, "frozen", False) else BASE_DIR
BUNDLED_NODE_DIR = os.path.join(PACKAGE_ROOT, "runtime", "node")
NODE_DIR = BUNDLED_NODE_DIR if os.path.isdir(BUNDLED_NODE_DIR) else r"C:\Users\adarlpz\nodejs\node-v22.0.0-win-x64"
BUNDLED_OPENCODE = os.path.join(PACKAGE_ROOT, "runtime", "npm-global", "opencode.cmd")
OPENCODE_CMD = BUNDLED_OPENCODE if os.path.isfile(BUNDLED_OPENCODE) else r"C:\Users\adarlpz\nodejs\npm-global\opencode.cmd"
TTS_SCRIPT = os.path.join(PACKAGE_ROOT, "tts_speak", "tts_speak.exe") if getattr(sys, "frozen", False) else os.path.join(BASE_DIR, "tts_speak.py")
VOICE_SYSTEM = (
    "Modo voz: responde en español, directo y sin saludos, introducciones ni "
    "explicaciones extra. Da solo la respuesta final, en una frase corta, salvo "
    "que el usuario pida explícitamente detalle. No uses herramientas ni skills "
    "salvo que sean necesarias para ejecutar una acción solicitada. Conserva "
    "exactamente los caracteres Unicode del usuario, incluidos acentos y la ñ."
)

def progress(msg):
    """Write progress to stderr for AHK to see."""
    print(msg, file=sys.stderr, flush=True)

def tts_play(text):
    """Call tts_speak.py synchronously."""
    try:
        subprocess.run(
            [PY_EXE, TTS_SCRIPT, "full", text],
            capture_output=True, timeout=60
        )
    except Exception:
        pass

def tts_play_async(text):
    """Start speech independently, after the reply file is already ready."""
    try:
        flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        command = [TTS_SCRIPT, "full", text] if getattr(sys, "frozen", False) else [PY_EXE, TTS_SCRIPT, "full", text]
        subprocess.Popen(
            command,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=flags,
        )
    except Exception:
        pass

def deliver_result(result, output_file, speak):
    """Make the text available to AHK before optional speech."""
    result = result.strip()
    if not result:
        return
    if output_file:
        with open(output_file, "w", encoding="utf-8") as result_file:
            result_file.write(result)
    else:
        print(result)
    if speak:
        progress("Generando voz...")
        tts_play_async(result)

def server_request(base_url, method, path, payload=None, timeout=120):
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        base_url.rstrip("/") + path, data=data, headers=headers, method=method
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))

def run_via_server(base_url, text):
    """Use OpenCode's local HTTP API; unlike `run --attach`, it waits."""
    session = server_request(base_url, "POST", "/session", {
        "title": "Voice request",
        "model": {
            "providerID": "opencode",
            "id": "deepseek-v4-flash-free",
            "variant": "minimal",
        },
    }, timeout=15)
    reply = server_request(base_url, "POST", f"/session/{session['id']}/message", {
        "model": {
            "providerID": "opencode",
            "modelID": "deepseek-v4-flash-free",
        },
        "variant": "minimal",
        "system": VOICE_SYSTEM,
        "parts": [{"type": "text", "text": text}],
    })
    return "".join(
        part.get("text", "") for part in reply.get("parts", [])
        if part.get("type") == "text"
    ).strip()

def main():
    args = sys.argv[1:]
    output_file = None
    speak = False
    while args:
        if len(args) >= 2 and args[0] == "--output-file":
            output_file = args[1]
            args = args[2:]
        elif args[0] == "--speak":
            speak = True
            args = args[1:]
        else:
            break

    if not args:
        print("Usage: run_opencode_tts.py <text>")
        sys.exit(1)

    argumento = args[0]
    if argumento.startswith("@"):
        try:
            with open(argumento[1:], "r", encoding="utf-8") as prompt_file:
                texto = prompt_file.read()
        except OSError as exc:
            print(f"ERROR: no se pudo leer el prompt: {exc}", file=sys.stderr)
            sys.exit(1)
    else:
        texto = argumento
    if not texto.strip():
        sys.exit(0)

    env = os.environ.copy()
    env["PATH"] = NODE_DIR + ";" + env.get("PATH", "")

    attach_url = os.environ.get("OPENCODE_ATTACH_URL", "").strip()

    try:
        if attach_url:
            try:
                progress("Conectando con OpenCode... ")
                result = run_via_server(attach_url, texto)
                deliver_result(result, output_file, speak)
                progress("Listo")
                return
            except Exception as exc:
                # The voice shortcut must still work if the local server dies.
                progress(f"Servidor no disponible; usando respaldo: {exc}")

        cmd = [
            OPENCODE_CMD, "run", "--auto", "--format", "json",
            "-m", MODEL, "--variant", "minimal", texto,
        ]
        progress("Conectando con OpenCode...")
        proc = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            env=env, cwd=os.path.dirname(os.path.abspath(__file__)),
            creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0
        )

        full_output = []
        progress("OpenCode procesando...")
        for line in iter(proc.stdout.readline, b''):
            if not line:
                break
            line_str = line.decode('utf-8', errors='replace').strip()
            if not line_str:
                continue

            try:
                obj = json.loads(line_str)
                if obj.get("type") == "text" and obj.get("part", {}).get("text"):
                    chunk = obj["part"]["text"]
                    full_output.append(chunk)
            except json.JSONDecodeError:
                continue

        proc.wait(timeout=120)

        # OpenCode emits text in fragments.  Speaking a partial first fragment
        # loses the rest of the same sentence, so wait for the complete reply.
        result = "".join(full_output).strip()
        if result:
            deliver_result(result, output_file, speak)
        progress("Listo")

    except subprocess.TimeoutExpired:
        proc.kill()
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
