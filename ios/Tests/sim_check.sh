#!/bin/bash
# Lance l'app sur un simulateur iPhone avec des arguments de test et prend des captures.
set -u
OUT=shots; mkdir -p $OUT
APP=build-sim/Build/Products/Debug-iphonesimulator/XreadPDF.app
UDID=$(xcrun simctl list devices available -j | python3 -c "
import json,sys
d=json.load(sys.stdin)['devices']
c=[x['udid'] for k,v in d.items() if 'iOS' in k for x in v if x['name'].startswith('iPhone 16')]
c=c or [x['udid'] for k,v in d.items() if 'iOS' in k for x in v if x['name'].startswith('iPhone')]
print(c[0])")
echo "Simulateur $UDID" | tee $OUT/sim.txt
xcrun simctl boot $UDID; xcrun simctl bootstatus $UDID -b >/dev/null
xcrun simctl status_bar $UDID override --time 9:41 --batteryLevel 100 --cellularBars 4 || true
xcrun simctl install $UDID $APP
shot() {
  xcrun simctl terminate $UDID com.csa.xreadpdf >/dev/null 2>&1
  xcrun simctl launch $UDID com.csa.xreadpdf -uitest-seed "$@" >> $OUT/sim.txt 2>&1
  sleep 7
}
shot;                                   xcrun simctl io $UDID screenshot $OUT/1-accueil.png
shot -uitest-screen viewer;             xcrun simctl io $UDID screenshot $OUT/2-visionneuse.png
shot -uitest-screen editor;             xcrun simctl io $UDID screenshot $OUT/3-editeur.png
shot -uitest-screen sign;               xcrun simctl io $UDID screenshot $OUT/4-signer.png
shot -uitest-screen signatures;         xcrun simctl io $UDID screenshot $OUT/5-signatures.png
shot -uitest-export; sleep 4;           xcrun simctl io $UDID screenshot $OUT/6-apres-export.png
xcrun simctl launch $UDID com.csa.xreadpdf >/dev/null 2>&1; sleep 4; xcrun simctl io $UDID screenshot $OUT/7-accueil-final.png
DATA=$(xcrun simctl get_app_container $UDID com.csa.xreadpdf data)
ls -la "$DATA/Documents" >> $OUT/sim.txt 2>&1
cp "$DATA/Documents/Exemple - modifié.pdf" $OUT/export.pdf 2>> $OUT/sim.txt
python3 - <<'PY' >> $OUT/sim.txt 2>&1
import pypdfium2 as pdfium
pdf = pdfium.PdfDocument("shots/export.pdf")
for i in range(len(pdf)):
    p = pdf[i]; print("page", i + 1, "taille", p.get_size())
    p.render(scale=1).to_pil().save(f"shots/export-p{i+1}.png")
PY
xcrun simctl spawn $UDID log show --last 3m --predicate 'process == "XreadPDF"' --style compact 2>/dev/null | grep -iE "error|fault|crash" | head -40 >> $OUT/sim.txt
