"""Bounded offline MySQL slow-log analysis. Never connects to a database or emits SQL literals."""
import argparse
import collections
import datetime as dt
import json
import re
from pathlib import Path


def fingerprint(sql):
    sql = re.sub(r"'(?:''|\\.|[^'])*'|\"(?:\\.|[^\"])*\"", "?", sql)
    sql = re.sub(r"\b(?:0x[0-9a-f]+|\d+(?:\.\d+)?)\b", "?", sql, flags=re.I)
    return re.sub(r"\s+", " ", sql).strip().lower()[:1000]


def analyze(path, max_bytes=64 * 1024 * 1024, offset=0, start=None, end=None):
    groups = collections.defaultdict(lambda: dict(count=0, querySeconds=0.0, lockSeconds=0.0, rowsExamined=0, rowsSent=0, maxSeconds=0.0))
    record = []
    scanned = 0
    accepted = 0

    def accept(lines):
        nonlocal accepted
        text = "".join(lines)
        timing = re.search(r"# Query_time:\s*([\d.]+)\s+Lock_time:\s*([\d.]+)\s+Rows_sent:\s*(\d+)\s+Rows_examined:\s*(\d+)", text)
        if not timing:
            return
        timestamp = re.search(r"# Time:\s*(\S+)", text)
        if start or end:
            if not timestamp:
                return
            try:
                moment = dt.datetime.fromisoformat(timestamp[1].replace("Z", "+00:00"))
                if start and moment < start or end and moment > end:
                    return
            except ValueError:
                return
        sql = " ".join(line for line in lines if not line.startswith("#") and not line.lower().startswith(("set timestamp=", "use ")))
        key = fingerprint(sql)
        if not key:
            return
        entry = groups[key]
        seconds, lock, sent, examined = timing.groups()
        entry["count"] += 1
        entry["querySeconds"] += float(seconds)
        entry["lockSeconds"] += float(lock)
        entry["rowsSent"] += int(sent)
        entry["rowsExamined"] += int(examined)
        entry["maxSeconds"] = max(entry["maxSeconds"], float(seconds))
        accepted += 1

    with Path(path).open("rb") as source:
        source.seek(offset)
        if offset:
            source.readline()  # Discard a potentially partial line.
        while scanned < max_bytes:
            line = source.readline(min(max_bytes - scanned, 1024 * 1024))
            if not line:
                break
            scanned += len(line)
            text = line.decode("utf-8", errors="replace")
            if text.startswith("# Time:"):
                accept(record)
                record = []
            record.append(text)
            # An enormous single statement must not exhaust analyzer memory.
            if sum(map(len, record)) > 2 * 1024 * 1024:
                record = []
            if len(groups) >= 10000:
                break
        at_eof = source.peek(1) == b""
        if at_eof:
            accept(record)
    rows = [dict(fingerprint=key, **value) for key, value in groups.items()]
    rows.sort(key=lambda row: row["querySeconds"], reverse=True)
    return dict(scannedBytes=scanned, offset=offset, acceptedQueries=accepted, complete=at_eof and offset == 0,
                note="Offline slice only; not proof of production root cause. SQL literals redacted.", top=rows[:100])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--max-mib", type=int, default=64)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--start", help="ISO timestamp with timezone")
    parser.add_argument("--end", help="ISO timestamp with timezone")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not 1 <= args.max_mib <= 1024 or args.offset < 0:
        parser.error("max-mib must be 1..1024 and offset must be nonnegative")
    bounds = [dt.datetime.fromisoformat(value) if value else None for value in (args.start, args.end)]
    if any(value is not None and value.tzinfo is None for value in bounds):
        parser.error("start/end require a timezone")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(analyze(args.input, args.max_mib * 1024 * 1024, args.offset, *bounds), ensure_ascii=False, indent=2), encoding="utf-8")
