#!/usr/bin/env python3
import http.server
import socketserver
import json
import urllib.request
import urllib.error
import os
import ssl
import sqlite3
import uuid
import time
from urllib.parse import urlparse

PORT = 8888
DB_PATH = os.environ.get("HERMES_CHAT_DB", "/root/.hermes/webchat/hermes-chat.db")
HERMES_DB = os.environ.get("HERMES_STATE_DB", "/root/.hermes/state.db")
XIAOMI_KEY = os.environ.get("XIAOMI_API_KEY", "")
NVIDIA_KEY = os.environ.get("NVIDIA_API_KEY", "")

API_CONFIG = {
    "mimo-v2.5": ("https://api.xiaomimimo.com/v1/chat/completions", XIAOMI_KEY, "mimo-v2.5"),
    "mimo": ("https://api.xiaomimimo.com/v1/chat/completions", XIAOMI_KEY, "mimo-v2.5"),
    "nvidia/nemotron-3.5-lightning-30b-a3b": ("https://integrate.api.nvidia.com/v1/chat/completions", NVIDIA_KEY, "nvidia/nemotron-3.5-lightning-30b-a3b"),
    "nemotron": ("https://integrate.api.nvidia.com/v1/chat/completions", NVIDIA_KEY, "nvidia/nemotron-3.5-lightning-30b-a3b"),
    "deepseek-flash": ("https://api.xiaomimimo.com/v1/chat/completions", XIAOMI_KEY, "mimo-v2.5"),
    "deepseek": ("https://api.xiaomimimo.com/v1/chat/completions", XIAOMI_KEY, "mimo-v2.5"),
}
DEFAULT_MODEL = "deepseek-flash"

def db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    c.executescript("""
      CREATE TABLE IF NOT EXISTS conversations (
        id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL, incognito INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL,
        role TEXT NOT NULL, content TEXT NOT NULL, model TEXT, created_at INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id, id);
    """)
    return c

def now(): return int(time.time() * 1000)

def conversation(c, cid, with_messages=False):
    row = c.execute("SELECT * FROM conversations WHERE id=?", (cid,)).fetchone()
    if not row: return None
    out = dict(row)
    out["incognito"] = bool(out["incognito"])
    if with_messages:
        out["messages"] = [dict(x) for x in c.execute(
            "SELECT id, role, content, model, created_at FROM messages WHERE conversation_id=? ORDER BY id", (cid,)
        )]
    return out

def hermes_conversations(with_messages=False):
    if not os.path.exists(HERMES_DB): return []
    try:
        h = sqlite3.connect(f"file:{HERMES_DB}?mode=ro", uri=True)
        h.row_factory = sqlite3.Row
        rows = h.execute("SELECT id,title,source,display_name,started_at,last_activity_at,model,message_count FROM sessions WHERE archived=0 AND hidden=0 ORDER BY COALESCE(last_activity_at,started_at) DESC").fetchall()
        result = []
        for row in rows:
            item = {"id": row["id"], "title": row["title"] or row["display_name"] or "Hermes", "created_at": int((row["started_at"] or 0) * 1000), "updated_at": int((row["last_activity_at"] or row["started_at"] or 0) * 1000), "source": row["source"], "model": row["model"], "message_count": row["message_count"], "remote": True}
            if with_messages:
                msgs = h.execute("SELECT role,content,timestamp FROM messages WHERE session_id=? AND active=1 AND role IN ('user','assistant') ORDER BY id", (row["id"],)).fetchall()
                item["messages"] = [{"role": m["role"], "content": m["content"] or "", "created_at": int((m["timestamp"] or 0) * 1000)} for m in msgs if m["content"]]
            result.append(item)
        h.close(); return result
    except Exception:
        return []

def archive_hermes_conversation(cid):
    """Hide a Hermes session from the chat list without touching agent memory."""
    if not os.path.exists(HERMES_DB): return
    try:
        h = sqlite3.connect(HERMES_DB)
        h.execute("UPDATE sessions SET archived=1 WHERE id=?", (cid,))
        h.commit(); h.close()
    except Exception:
        pass

def send_json(h, status, data):
    raw = json.dumps(data, ensure_ascii=False).encode()
    h.send_response(status)
    h.send_header("Content-Type", "application/json; charset=utf-8")
    h.send_header("Access-Control-Allow-Origin", "*")
    h.send_header("Cache-Control", "no-store")
    h.end_headers()
    h.wfile.write(raw)

class ChatHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory="/root/.hermes/webchat", **kwargs)

    def read_body(self):
        return json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))

    def do_GET(self):
        path = urlparse(self.path).path.rstrip("/")
        c = db()
        try:
            if path == "/api/conversations":
                rows = [dict(x) for x in c.execute("SELECT * FROM conversations WHERE incognito=0 ORDER BY updated_at DESC").fetchall()]
                local_ids = {x["id"] for x in rows}
                rows.extend(x for x in hermes_conversations() if x["id"] not in local_ids)
                rows.sort(key=lambda x: x.get("updated_at", 0), reverse=True)
                return send_json(self, 200, {"conversations": rows})
            if path.startswith("/api/conversations/"):
                cid = path.rsplit("/", 1)[-1]
                item = conversation(c, cid, True) or next((x for x in hermes_conversations(True) if x["id"] == cid), None)
                return send_json(self, 200, item or {"error": "Conversacion no encontrada"})
            return super().do_GET()
        finally:
            c.close()

    def do_DELETE(self):
        path = urlparse(self.path).path
        if not path.startswith("/api/conversations/"):
            return send_json(self, 404, {"error": "No encontrado"})
        cid = path.rsplit("/", 1)[-1]
        c = db()
        c.execute("DELETE FROM messages WHERE conversation_id=?", (cid,))
        c.execute("DELETE FROM conversations WHERE id=?", (cid,))
        c.commit(); c.close()
        archive_hermes_conversation(cid)
        send_json(self, 200, {"ok": True})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            data = self.read_body()
            c = db()
            if path == "/api/conversations":
                ts = now(); cid = uuid.uuid4().hex
                c.execute("INSERT INTO conversations VALUES (?,?,?,?,0)", (cid, "Nuevo chat", ts, ts))
                c.commit(); item = conversation(c, cid, True); c.close()
                return send_json(self, 201, item)
            if path == "/api/chat":
                cid = data.get("conversation_id") or uuid.uuid4().hex
                item = conversation(c, cid)
                if not item:
                    ts = now()
                    c.execute("INSERT INTO conversations VALUES (?,?,?,?,?)", (cid, "Nuevo chat", ts, ts, int(bool(data.get("incognito")))))
                    c.commit()
                model = data.get("model", DEFAULT_MODEL)
                if model not in API_CONFIG: model = DEFAULT_MODEL
                history = data.get("messages") or []
                raw_message = history[-1].get("content", "") if history else data.get("message", "")
                message = next((part.get("text", "") for part in raw_message if isinstance(part, dict) and part.get("type") == "text"), "[Captura de pantalla adjunta]") if isinstance(raw_message, list) else raw_message
                if data.get("save", True) and not data.get("incognito", False):
                    ts = now(); c.execute("INSERT INTO messages(conversation_id,role,content,model,created_at) VALUES(?,?,?,?,?)", (cid, "user", message, model, ts))
                    if conversation(c, cid)["title"] == "Nuevo chat": c.execute("UPDATE conversations SET title=? WHERE id=?", (message[:48] or "Nuevo chat", cid))
                url, key, api_model = API_CONFIG[model]
                payload = dict(data); payload.pop("conversation_id", None); payload["model"] = api_model
                req = urllib.request.Request(url, data=json.dumps(payload, ensure_ascii=False).encode(), method="POST")
                req.add_header("Content-Type", "application/json"); req.add_header("Authorization", "Bearer " + key)
                with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=120) as resp: result = json.loads(resp.read())
                answer = ((result.get("choices") or [{}])[0].get("message") or {}).get("content", "")
                if data.get("save", True) and not data.get("incognito", False):
                    ts = now(); c.execute("INSERT INTO messages(conversation_id,role,content,model,created_at) VALUES(?,?,?,?,?)", (cid, "assistant", answer, model, ts)); c.execute("UPDATE conversations SET updated_at=? WHERE id=?", (ts, cid)); c.commit()
                result["conversation_id"] = cid; c.close(); return send_json(self, 200, result)
            return send_json(self, 404, {"error": "No encontrado"})
        except urllib.error.HTTPError as e:
            return send_json(self, e.code, {"error": e.read().decode("utf-8", errors="replace")})
        except Exception as e:
            return send_json(self, 500, {"error": str(e)})

    def do_OPTIONS(self):
        self.send_response(200); self.send_header("Access-Control-Allow-Origin", "*"); self.send_header("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS"); self.send_header("Access-Control-Allow-Headers", "Content-Type"); self.end_headers()
    def log_message(self, fmt, *args): print("[Chat] " + str(args[0]))

socketserver.TCPServer.allow_reuse_address = True
with socketserver.ThreadingTCPServer(("0.0.0.0", PORT), ChatHandler) as httpd:
    print("Hermes Chat server en http://0.0.0.0:" + str(PORT)); httpd.serve_forever()
