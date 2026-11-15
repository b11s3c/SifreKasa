#!/usr/bin/env python3
"""Emülatörde arayüzü adb ile sürmek için küçük yardımcı (FLAG_SECURE yüzünden ekran görüntüsü alınamaz;
erişilebilirlik ağacı okunur).  Komutlar art arda yazılabilir:
  dump | tap <metin> [n] | tapc <Sınıf> | long <metin> | type <metin> | key <kod> | scroll | wait <sn>"""
import re, subprocess, sys, time

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def nodes():
    sh("uiautomator", "dump", "/sdcard/ui.xml")
    out = []
    for m in re.finditer(r'<node [^>]*>', sh("cat", "/sdcard/ui.xml")):
        n = m.group(0)
        g = lambda k: (re.search(k + r'="([^"]*)"', n) or [None, ""])[1]
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        out.append(dict(text=g("text"), desc=g("content-desc"), cls=g("class").split(".")[-1],
                        enabled=g("enabled"), b=tuple(map(int, b.groups())) if b else None))
    return out

def center(n):
    x0, y0, x1, y1 = n["b"]
    return str((x0 + x1) // 2), str((y0 + y1) // 2)

args = sys.argv[1:]
i = 0
while i < len(args):
    c = args[i]
    if c == "dump":
        for n in nodes():
            if n["text"] or n["desc"]:
                print(f'{n["cls"]:<12} en={n["enabled"]:<5} {n["text"] or "[" + n["desc"] + "]"}')
        i += 1
    elif c in ("tap", "long"):
        target = args[i + 1]; idx = 0
        if c == "tap" and i + 2 < len(args) and args[i + 2].isdigit(): idx = int(args[i + 2]); i += 1
        hits = [n for n in nodes() if target in (n["text"], n["desc"])]
        if len(hits) <= idx: print("BULUNAMADI:", target); sys.exit(1)
        x, y = center(hits[idx])
        sh("input", "tap", x, y) if c == "tap" else sh("input", "swipe", x, y, x, y, "900")
        time.sleep(0.8); i += 2
    elif c == "tapc":
        hits = [n for n in nodes() if n["cls"] == args[i + 1] and n["b"] and n["b"][1] > 300]
        if not hits: print("BULUNAMADI:", args[i + 1]); sys.exit(1)
        sh("input", "tap", *center(hits[0])); time.sleep(0.8); i += 2
    elif c == "type":
        sh("input", "text", args[i + 1].replace(" ", "%s")); time.sleep(0.5); i += 2
    elif c == "key":
        sh("input", "keyevent", args[i + 1]); time.sleep(0.6); i += 2
    elif c == "scroll":
        sh("input", "swipe", "540", "1700", "540", "700", "300"); time.sleep(0.8); i += 1
    elif c == "wait":
        time.sleep(float(args[i + 1])); i += 2
    else:
        print("bilinmeyen komut:", c); sys.exit(2)
