import argparse
import json
import sys
from pathlib import Path


SETTINGS_PATH = Path.home() / "AppData" / "Roaming" / "com.pais.handy" / "settings_store.json"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--get", action="store_true")
    parser.add_argument("--set", choices=("direct", "none"))
    parser.add_argument("--model")
    parser.add_argument("--output")
    args = parser.parse_args()

    if not SETTINGS_PATH.exists():
        return 1

    with SETTINGS_PATH.open("r", encoding="utf-8") as settings_file:
        data = json.load(settings_file)
    settings = data.setdefault("settings", {})

    if args.get:
        value = settings.get("paste_method", "")
        if args.output:
            Path(args.output).write_text(value, encoding="utf-8")
        else:
            print(value)
        return 0

    if args.set:
        settings["paste_method"] = args.set
        with SETTINGS_PATH.open("w", encoding="utf-8", newline="\n") as settings_file:
            json.dump(data, settings_file, ensure_ascii=False, indent=2)
            settings_file.write("\n")
        return 0

    if args.model:
        settings["selected_model"] = args.model
        with SETTINGS_PATH.open("w", encoding="utf-8", newline="\n") as settings_file:
            json.dump(data, settings_file, ensure_ascii=False, indent=2)
            settings_file.write("\n")
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
