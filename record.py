import subprocess
import os
import time

base_dir = r"C:\Users\adaredu\Downloads\whisper"
ffmpeg_exe = os.path.join(base_dir, "ffmpeg.exe")
audio_device = "@device_cm_{33D9A762-90C8-11D0-BD43-00A0C911CE86}\\wave_{66E16202-66F3-4D6B-A7B8-8564C5377AC0}"
audio_file = os.path.join(base_dir, "temp_audio.mp3")

# Iniciar ffmpeg con entrada estándar redirigida
cmd = [
    ffmpeg_exe, "-y", "-f", "dshow", "-i", f"audio={audio_device}",
    "-t", "60", "-q:a", "9", "-acodec", "libmp3lame", "-b:a", "192k", audio_file
]

p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

# Crear started.txt para avisar a AHK que ya estamos grabando
started_file = os.path.join(base_dir, "started.txt")
try:
    with open(started_file, "w") as f:
        f.write("started")
except Exception:
    pass

stop_file = os.path.join(base_dir, "stop.txt")
# Esperar al archivo stop.txt con una respuesta ultra-rápida de 10ms
while p.poll() is None:
    if os.path.exists(stop_file):
        break
    time.sleep(0.01)

# Detener ffmpeg de forma segura enviando 'q' a su stdin
if p.poll() is None:
    try:
        p.communicate(input=b"q\n", timeout=5)
    except Exception:
        try:
            p.kill()
        except Exception:
            pass
