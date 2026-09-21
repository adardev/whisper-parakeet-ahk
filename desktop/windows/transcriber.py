from __future__ import annotations

import http.server
import json
import logging
import os
import queue
import threading
import time
import gc
from pathlib import Path

import numpy as np
import sounddevice as sd
import transcribe_cpp

ROOT = Path(__file__).resolve().parent
MODEL = ROOT.parent / "models" / "nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf"
HOST, PORT = "127.0.0.1", 17841
LANGUAGE = os.environ.get("HANDY_LANGUAGE", "es-ES")
SAMPLE_RATE = 16_000
# Feed frequently so the native cache can emit partial hypotheses promptly.
CHUNK_SAMPLES = 16_000 // 10

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


class Engine:
    def __init__(self) -> None:
        self.lock = threading.RLock()
        self.model = None
        self.audio: queue.Queue[np.ndarray | None] = queue.Queue()
        self.input_stream: sd.InputStream | None = None
        self.stream = None
        self.worker: threading.Thread | None = None
        self.recording = False
        self.processing = False
        self.latest = ""
        self.error = ""

    def load(self) -> None:
        with self.lock:
            if self.model is not None:
                return
            if not MODEL.exists():
                raise FileNotFoundError(f"Falta el modelo: {MODEL}")
            transcribe_cpp.set_log_callback(lambda level, msg: logging.info("native[%s] %s", level, msg))
            logging.info("Cargando modelo bajo demanda: %s", MODEL.name)
            self.model = transcribe_cpp.Model(str(MODEL), backend="auto")
            logging.info("Modelo listo: %s / %s / backend=%s", self.model.arch, self.model.variant, self.model.backend)

    def start(self) -> None:
        with self.lock:
            if self.recording or self.processing:
                return
            self.load()
            self.audio = queue.Queue()
            self.latest = ""
            self.error = ""
            session = self.model.session()
            # Nemotron supports cache-aware streaming; Spanish is explicit.
            # R=3 adds about 240 ms of right context: a good accuracy/latency
            # balance for Spanish without the full R=13 delay.
            self.stream = session.stream(
                language=LANGUAGE,
                commit_policy="auto",
                family=transcribe_cpp.ParakeetStreamOptions(att_context_right=3),
            )
            self._session = session
            self.recording = True
            self.input_stream = sd.InputStream(
                samplerate=SAMPLE_RATE,
                channels=1,
                dtype="float32",
                blocksize=CHUNK_SAMPLES,
                callback=self._audio_callback,
            )
            self.input_stream.start()
            self.worker = threading.Thread(target=self._consume, name="transcribe-stream", daemon=True)
            self.worker.start()
            logging.info("Grabación iniciada")

    def _audio_callback(self, indata, frames, _time, status) -> None:
        if status:
            logging.warning("audio: %s", status)
        if self.recording:
            self.audio.put(indata[:, 0].copy())

    def _consume(self) -> None:
        try:
            while True:
                chunk = self.audio.get()
                if chunk is None:
                    return
                self.stream.feed(chunk)
                with self.lock:
                    self.latest = self.stream.text().display
        except Exception as exc:
            with self.lock:
                self.error = str(exc)
            logging.exception("Error en streaming")

    def stop(self) -> str:
        with self.lock:
            if not self.recording and not self.processing:
                return self.latest.strip()
            self.recording = False
            self.processing = True
            inp, worker, stream = self.input_stream, self.worker, self.stream
        if inp:
            inp.stop()
            inp.close()
        self.audio.put(None)
        if worker:
            worker.join(timeout=30)
        try:
            if self.error:
                raise RuntimeError(self.error)
            stream.finalize()
            text = stream.text().display.strip()
        finally:
            stream.reset()
            self._session.close()
            with self.lock:
                self.input_stream = None
                self.worker = None
                self.stream = None
                self.processing = False
                self.latest = text if "text" in locals() else self.latest
        logging.info("Transcripción final: %r", text)
        return text

    def close(self) -> None:
        with self.lock:
            if self.recording or self.processing:
                return
            if self.model is None:
                return
            logging.info("Descargando modelo por inactividad")
            self.model = None
            gc.collect()

    def status(self) -> dict:
        with self.lock:
            return {"loaded": self.model is not None, "recording": self.recording, "processing": self.processing, "text": self.latest, "error": self.error}


ENGINE = Engine()
HTTP_SERVER = None


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        logging.info("http %s", fmt % args)

    def _reply(self, code: int, body: str, content_type: str = "text/plain; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        # AdarBot Desktop runs in a Tauri WebView and talks to this local
        # service from a different origin.
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_OPTIONS(self):
        self._reply(204, "")

    def do_GET(self):
        try:
            if self.path == "/start":
                ENGINE.start(); self._reply(200, "OK"); return
            if self.path == "/stop":
                self._reply(200, ENGINE.stop()); return
            if self.path == "/status":
                self._reply(200, json.dumps(ENGINE.status(), ensure_ascii=False), "application/json; charset=utf-8"); return
            if self.path == "/shutdown":
                ENGINE.close()
                self._reply(200, "OK")
                threading.Thread(target=HTTP_SERVER.shutdown, daemon=True).start()
                return
            self._reply(404, "Not found")
        except Exception as exc:
            logging.exception("request failed")
            self._reply(500, str(exc))


if __name__ == "__main__":
    HTTP_SERVER = http.server.ThreadingHTTPServer((HOST, PORT), Handler)
    HTTP_SERVER.serve_forever()
