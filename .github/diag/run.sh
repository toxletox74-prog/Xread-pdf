#!/bin/bash
# Parcours complet sur émulateur ; rapport remonté en annotations GitHub
R=report.txt; : > $R
log() { echo "$*" >> $R; }
ui() { python3 .github/diag/ui.py "$@"; }
step() { sleep "${2:-2}"; log "== $1 :: $(ui texts)"; }
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}; log "écran $W x $H"
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
python3 - <<'PY'
from reportlab.pdfgen import canvas
c = canvas.Canvas("test.pdf", pagesize=(595, 842))
for i in range(2):
    c.setFont("Helvetica", 28); c.drawString(72, 760, f"Document de test - page {i+1}")
    c.setFillGray(0.85); c.rect(72, 300, 450, 380, fill=1, stroke=0); c.showPage()
c.save()
PY
adb push test.pdf /data/local/tmp/test.pdf >/dev/null
adb shell run-as com.csa.xreadpdf sh -c "'mkdir -p files/pdfs && cp /data/local/tmp/test.pdf files/pdfs/Test.pdf'"
adb logcat -c
adb shell am start -W -n com.csa.xreadpdf/.MainActivity >/dev/null
step "ACCUEIL" 6
log "$(ui tap Test)"; step "VISIONNEUSE" 3
log "$(ui tap Modifier)"; step "EDITEUR" 3
log "$(ui tap Stylo)"; sleep 1
adb shell input swipe $((W*25/100)) $((H*40/100)) $((W*75/100)) $((H*45/100)) 600
step "APRES TRAIT" 1
log "$(ui tap Texte)"; sleep 1
adb shell input tap $((W*30/100)) $((H*30/100)); step "DIALOGUE TEXTE" 2
adb shell input text "Bonjour%sCed"; sleep 1
log "$(ui tap OK)"; step "APRES TEXTE" 2
log "$(ui tap Organiser)"; step "PAGES" 2
log "$(ui tap Pivoter)"; sleep 1; adb shell input keyevent 4; step "APRES ROTATION" 2
log "$(ui tap Enregistrer)"; step "DIALOGUE ENREGISTRER" 2
log "$(ui tap Remplacer)"; step "APRES ENREGISTREMENT" 6
log "$(ui tap Signer)"; step "SIGNER (pad attendu)" 3
adb shell input swipe $((W*30/100)) $((H*45/100)) $((W*45/100)) $((H*40/100)) 300
adb shell input swipe $((W*45/100)) $((H*40/100)) $((W*70/100)) $((H*47/100)) 300
log "$(ui tap Enregistrer last)"; step "APRES PAD" 2
adb shell input tap $((W*50/100)) $((H*55/100)); step "APRES POSE SIGNATURE" 2
log "$(ui tap Enregistrer)"; step "DIALOGUE 2" 2
log "$(ui tap 'Créer une copie')"; step "APRES COPIE" 6
adb shell input keyevent 4; step "RETOUR ACCUEIL" 3
adb shell run-as com.csa.xreadpdf ls -la files/pdfs files/signatures >> $R 2>&1
adb exec-out run-as com.csa.xreadpdf cat files/pdfs/Test.pdf > out1.pdf
adb exec-out run-as com.csa.xreadpdf cat "files/pdfs/Test - modifié.pdf" > out2.pdf
for f in out1 out2; do
  pdfinfo $f.pdf 2>&1 | grep -E "Pages|Page size|Producer" >> $R
  pdftoppm -r 30 -png $f.pdf $f >/dev/null 2>&1
done
python3 - >> $R <<'PY'
import glob
from PIL import Image
for p in sorted(glob.glob("out*-*.png")):
    im = Image.open(p).convert("RGB"); W, H = im.size
    px = im.load(); cnt = {}
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            k = "noir" if r < 60 and g < 60 and b < 60 else "bleu" if b > 150 and r < 80 else "rouge" if r > 150 and g < 80 else None
            if k: cnt[k] = cnt.get(k, 0) + 1
    print(p, im.size, cnt)
PY
adb logcat -d | grep -A30 -E "FATAL EXCEPTION" | head -60 >> $R
adb logcat -d | grep -E " E (AndroidRuntime|xreadpdf)|PdfBox|tom_roush" | head -20 >> $R
python3 - <<'PY'
s = open("report.txt").read()
enc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
for i in range(0, min(len(s), 6 * 20000), 20000):
    print(f"::warning title=RAPPORT {i//20000+1}::" + enc(s[i:i+20000]))
PY
