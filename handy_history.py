import argparse
import sqlite3
import sys
import time
from pathlib import Path


DB_PATH = Path.home() / "AppData" / "Roaming" / "com.pais.handy" / "history.db"


def latest_row_id(connection):
    row = connection.execute(
        "SELECT id FROM transcription_history ORDER BY id DESC LIMIT 1"
    ).fetchone()
    return int(row[0]) if row else 0


def latest_text_after(connection, row_id):
    row = connection.execute(
        "SELECT transcription_text FROM transcription_history "
        "WHERE id > ? AND transcription_text IS NOT NULL "
        "AND TRIM(transcription_text) <> '' ORDER BY id DESC LIMIT 1",
        (row_id,),
    ).fetchone()
    return row[0].strip() if row and row[0] else ""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--id", action="store_true")
    parser.add_argument("--wait-after-id", type=int)
    parser.add_argument("--timeout", type=float, default=12.0)
    parser.add_argument("--output")
    args = parser.parse_args()

    def emit(value):
        if args.output:
            Path(args.output).write_text(value, encoding="utf-8")
        else:
            sys.stdout.write(value)

    if not DB_PATH.exists():
        return 1

    deadline = time.monotonic() + args.timeout
    while True:
        try:
            with sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True, timeout=1) as db:
                if args.id:
                    emit(str(latest_row_id(db)))
                    return 0
                if args.wait_after_id is not None:
                    text = latest_text_after(db, args.wait_after_id)
                    if text:
                        emit(text)
                        return 0
        except sqlite3.Error:
            pass

        if args.wait_after_id is None or time.monotonic() >= deadline:
            return 0
        time.sleep(0.1)


if __name__ == "__main__":
    raise SystemExit(main())
