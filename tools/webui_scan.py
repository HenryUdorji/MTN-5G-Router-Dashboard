#!/usr/bin/env python3
"""
Read-only scan of the router's own web page code.

Downloads the JavaScript files the router's admin page is built from (no
sign-in needed, nothing is sent to the router except normal page requests)
and lists every command ("goformId") the page knows, plus the code around
anything to do with bands, cells and network mode.

Usage (on the router's Wi-Fi, from the project folder):
  python3 tools/webui_scan.py            then send back webui_scan_output.txt
"""
import os
import re
import sys
import urllib.parse
import urllib.request

HOST = os.environ.get("MC888_HOST", "192.168.0.1")
BASE = f"http://{HOST}/"
MAX_FILES = 150
OUT_TXT = "webui_scan_output.txt"
OUT_DIR = "webui_js"

# Guesses for where ZTE pages keep their code, tried in addition to whatever the page links to.
EXTRA = ["js/service.js", "js/main.js", "js/util.js", "js/set.js", "js/config/config.js",
         "js/net_setting.js", "js/network.js", "js/lib/require/require-config.js"]

INTERESTING = re.compile(
    r"band|BAND|NR5G|nr5g|LTE_LOCK|lte_lock|CELL_LOCK|cell_lock|lock_cell|earfcn|EARFCN|"
    r"pci_lock|net_select|BearerPreference|NETWORK_MODE|SET_BEARER|nr_band|lte_band")
HEADERS = {"User-Agent": "Mozilla/5.0", "Referer": BASE + "index.html"}


def fetch(path):
    url = urllib.parse.urljoin(BASE, path)
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=10) as r:
            if r.status != 200:
                return None
            return r.read().decode("utf-8", "replace")
    except Exception:
        return None


def find_refs(text, current):
    """Script paths mentioned in a page or script: src="x.js", "x.js", and require(['name'])."""
    refs = set()
    for m in re.finditer(r"""["']([\w./-]+\.js)(?:\?[^"']*)?["']""", text):
        refs.add(urllib.parse.urljoin(current, m.group(1)))
    for m in re.finditer(r"""(?:define|require)\s*\(\s*\[([^\]]*)\]""", text):
        for name in re.findall(r"""["']([\w./-]+)["']""", m.group(1)):
            if not name.endswith(".js"):
                refs.add(urllib.parse.urljoin(BASE, f"js/{name}.js"))
    return {r for r in refs if urllib.parse.urlparse(r).netloc in ("", urllib.parse.urlparse(BASE).netloc)}


def main():
    index = fetch("index.html")
    if index is None:
        sys.exit(f"Couldn't open {BASE}index.html. Are you on the router's Wi-Fi?")
    os.makedirs(OUT_DIR, exist_ok=True)

    queue = list(find_refs(index, BASE + "index.html")) + [BASE + p for p in EXTRA]
    seen, files = set(), {}
    while queue and len(files) < MAX_FILES:
        url = queue.pop(0)
        if url in seen:
            continue
        seen.add(url)
        text = fetch(url)
        if not text or "<html" in text[:200].lower():
            continue
        path = urllib.parse.urlparse(url).path.lstrip("/")
        files[path] = text
        with open(os.path.join(OUT_DIR, path.replace("/", "__")), "w") as f:
            f.write(text)
        queue.extend(find_refs(text, url))

    all_text = "\n".join(files.values())
    goforms = sorted(set(re.findall(r"""goformId["']?\s*[:=]\s*["']([A-Za-z0-9_]+)["']""", all_text)))

    with open(OUT_TXT, "w") as out:
        out.write(f"Router web page scan of {BASE}\nFiles read: {len(files)}\n")
        for p in sorted(files):
            out.write(f"  {p} ({len(files[p])} bytes)\n")
        out.write(f"\nAll commands found ({len(goforms)}):\n")
        for g in goforms:
            out.write(f"  {g}\n")
        out.write("\nBand / cell / network-mode code:\n")
        shown = 0
        for p, text in sorted(files.items()):
            for m in re.finditer(r"""goformId["']?\s*[:=]\s*["']([A-Za-z0-9_]+)["']""", text):
                start, end = max(0, m.start() - 600), min(len(text), m.end() + 600)
                chunk = text[start:end]
                if INTERESTING.search(chunk) and shown < 60:
                    out.write(f"\n===== {p} @ {m.start()} ({m.group(1)}) =====\n{chunk}\n")
                    shown += 1
        out.write(f"\n({shown} code snippets)\n")

    print(f"Read {len(files)} files, found {len(goforms)} commands.")
    print(f"Saved {OUT_TXT} (send this back) and the raw files in {OUT_DIR}/.")


if __name__ == "__main__":
    main()
