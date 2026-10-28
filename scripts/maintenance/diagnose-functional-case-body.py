"""Read-only BLOB diagnosis. Input is HEX exported from a trusted database query.
No database writes, no plaintext logging. Exit 2 means source recovery is required.
"""
import argparse
import hashlib
import io
import json
import zipfile
from pathlib import Path

MAX_BYTES = 8 * 1024 * 1024
MAX_LAYERS = 3


def diagnose(raw, steps=True):
    result = {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest(), "layers": 0}
    body = raw
    total = 0
    try:
        while body.startswith((b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")):
            if result["layers"] >= MAX_LAYERS:
                raise ValueError("layer-limit")
            with zipfile.ZipFile(io.BytesIO(body)) as archive:
                items = archive.infolist()
                if len(items) != 1 or items[0].filename != "zip" or items[0].is_dir():
                    raise ValueError("unexpected-entry")
                with archive.open(items[0]) as stream:
                    body = stream.read(MAX_BYTES - total + 1)
                total += len(body)
                if total > MAX_BYTES:
                    raise ValueError("size-limit")
            result["layers"] += 1
        if len(body) > MAX_BYTES:
            raise ValueError("size-limit")
        text = body.decode("utf-8", errors="strict")
        if steps and text.strip():
            items = json.loads(text)
            if not isinstance(items, list) or any(not isinstance(item, dict) for item in items):
                raise ValueError("steps-not-array-of-objects")
        result["classification"] = ("PLAIN_VALID" if result["layers"] == 0 else
                                    "SINGLE_ZIP_VALID" if result["layers"] == 1 else "NESTED_RECOVERABLE")
        result["logicalSha256"] = hashlib.sha256(body).hexdigest()
        return result
    except (ValueError, OSError, EOFError, zipfile.BadZipFile, RuntimeError):
        result["classification"] = "NEEDS_SOURCE"
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("hex_file", type=Path)
    parser.add_argument("--text", action="store_true", help="Validate a text field instead of STEP JSON")
    args = parser.parse_args()
    if args.hex_file.stat().st_size > MAX_BYTES * 2 + 4096:
        parser.error("HEX input exceeds limit")
    try:
        raw = bytes.fromhex(args.hex_file.read_text(encoding="ascii"))
    except (ValueError, UnicodeError):
        parser.error("Input must contain only exported HEX")
    result = diagnose(raw, not args.text)
    print(json.dumps(result, ensure_ascii=True, indent=2))
    return 2 if result["classification"] == "NEEDS_SOURCE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
