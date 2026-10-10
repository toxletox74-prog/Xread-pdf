"""Remonte un journal en annotations GitHub (les journaux d'Actions ne sont pas lisibles via l'API)."""
import sys
title, path = sys.argv[1], sys.argv[2]
s = open(path, errors="replace").read()
parts = [s[i:i + 3500] for i in range(0, len(s), 3500)][-8:] or [""]
for k, t in enumerate(parts):
    print(f"::warning title={title} {k + 1}::" + t.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
