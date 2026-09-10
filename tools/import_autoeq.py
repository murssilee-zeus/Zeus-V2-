from pathlib import Path
from urllib.parse import unquote
from urllib.request import Request, urlopen
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
import re
import shutil
import time

BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"
INDEX_URL = BASE + "INDEX.md"
OUT_INDEX = Path("app/src/main/assets/autoeq/index.json")
OUT_TARGETS = Path("app/src/main/assets/targets")
OUT_INDEX.parent.mkdir(parents=True, exist_ok=True)
OUT_TARGETS.mkdir(parents=True, exist_ok=True)

def get(url, attempts=8):
    last = None
    for n in range(attempts):
        try:
            req = Request(url, headers={"User-Agent": "Zeus-V2-AutoEQ-Importer/2.0"})
            with urlopen(req, timeout=60) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:
            last = e
            time.sleep(min(30.0, 1.5 * (2 ** n)))
    raise last

catalog = get(INDEX_URL)
links = re.findall(r"^- \[([^\]]+)\]\(\./([^\)]+)\)", catalog, re.M)
if len(links) < 8000:
    raise SystemExit(f"AutoEQ catalog contains only {len(links)} profiles; expected 8000+")

preamp_re = re.compile(r"^Preamp:\s*([-+]?\d+(?:\.\d+)?)\s*dB\s*$", re.I)
filter_re = re.compile(r"^Filter\s+\d+:\s+ON\s+(\S+)\s+Fc\s+([-+]?\d+(?:\.\d+)?)\s+Hz\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s+dB\s+Q\s+([-+]?\d+(?:\.\d+)?)", re.I)

def scan(item):
    display_name, rel = item
    model_segment = rel.rsplit("/", 1)[-1]
    peq_url = BASE + rel + "/" + model_segment + "%20ParametricEQ.txt"
    text = get(peq_url)
    preamp = None
    count = 0
    types = set()
    for raw in text.splitlines():
        line = raw.strip()
        m = preamp_re.match(line)
        if m:
            preamp = float(m.group(1))
            continue
        m = filter_re.match(line)
        if m:
            count += 1
            types.add(m.group(1).upper())
    parts = rel.split("/")
    return {"id": hashlib.sha1(rel.encode()).hexdigest()[:12], "name": display_name, "source": unquote(parts[0]) if parts else "", "target": unquote(parts[1]) if len(parts) > 1 else "", "path": unquote(rel), "preampDb": preamp, "filterCount": count, "filterTypes": sorted(types)}

entries = []
failures = []
with ThreadPoolExecutor(max_workers=6) as pool:
    futures = {pool.submit(scan, item): item for item in links}
    for i, future in enumerate(as_completed(futures), 1):
        item = futures[future]
        try:
            entries.append(future.result())
        except Exception as e:
            failures.append({"name": item[0], "path": item[1], "error": str(e)})
        if i % 500 == 0:
            print(f"Scanned {i}/{len(futures)} profiles; failures={len(failures)}")

if failures:
    retry_items = [(x["name"], x["path"]) for x in failures]
    failures = []
    print(f"Retrying {len(retry_items)} failed profiles sequentially...")
    for i, item in enumerate(retry_items, 1):
        try:
            entries.append(scan(item))
        except Exception as e:
            failures.append({"name": item[0], "path": item[1], "error": str(e)})
        if i % 250 == 0:
            print(f"Retry {i}/{len(retry_items)}; remaining failures={len(failures)}")

if len(entries) < 8000:
    Path("autoeq_failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")
    raise SystemExit(f"Only {len(entries)} ParametricEQ profiles scanned successfully; failures={len(failures)}")

entries.sort(key=lambda x: (x["name"].lower(), x["source"].lower(), x["target"].lower()))
index = {"schema": 1, "source": "jaakkopasanen/AutoEq", "sourceCommit": os.environ.get("AUTOEQ_COMMIT", "master"), "generatedBy": "Zeus-V2 AutoEQ importer", "modelCount": len(entries), "entries": entries}
OUT_INDEX.write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

targets = Path("/tmp/AutoEq/targets")
copied = 0
if targets.exists():
    for p in sorted(targets.rglob("*")):
        if not p.is_file() or p.name.startswith(".") or p.suffix.lower() != ".csv":
            continue
        rel = p.relative_to(targets)
        dest = OUT_TARGETS / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, dest)
        copied += 1

(OUT_INDEX.parent / "SOURCE.txt").write_text("AutoEQ catalog and target curves imported from https://github.com/jaakkopasanen/AutoEq\n" f"AutoEQ source commit: {os.environ.get('AUTOEQ_COMMIT', 'master')}\n" "AutoEQ is MIT licensed. Target/measurement data may have their own provenance; source paths are preserved.\n", encoding="utf-8")

print(f"Catalog profiles: {len(links)}")
print(f"ParametricEQ.txt scanned: {len(entries)}")
print(f"Failed ParametricEQ fetches: {len(failures)}")
print(f"Target CSV files copied: {copied}")
if failures:
    Path("autoeq_failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")
