"""Petits outils UI pour l'émulateur : dump, tap sur un texte, liste des textes."""
import os, re, subprocess, sys, time

def sh(*a): return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def dump():
    for _ in range(3):
        sh("uiautomator", "dump", "/sdcard/ui.xml")
        x = sh("cat", "/sdcard/ui.xml")
        if "<hierarchy" in x: return x
        time.sleep(1)
    return ""

def nodes(x):
    out = []
    for m in re.finditer(r"<node[^>]*>", x):
        n = m.group(0)
        t = re.search(r' text="([^"]*)"', n).group(1)
        d = re.search(r'content-desc="([^"]*)"', n).group(1)
        e = re.search(r'enabled="([^"]*)"', n).group(1)
        b = list(map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n).group(1))))
        out.append((t, d, e, b))
    return out

def texts():
    return "; ".join(f"{t or d}{'' if e=='true' else '(off)'}" for t, d, e, b in nodes(dump()) if t or d)

def tap(label, last=False):
    ns = [n for n in nodes(dump()) if n[0] == label or n[1] == label]
    if not ns: ns = [n for n in nodes(dump()) if label in n[0] or label in n[1]]
    if not ns: return f"!! introuvable: {label}"
    t, d, e, b = ns[-1] if last else ns[0]
    x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    sh("input", "tap", str(x), str(y))
    return f"tap {label} @{x},{y}"

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "texts": print(texts())
    elif cmd == "tap": print(tap(sys.argv[2], len(sys.argv) > 3))
