"""Génère les PDF d'entrée (CropBox décalée, /Rotate) puis vérifie les PDF exportés."""
import sys, os
from pypdf import PdfWriter
from pypdf.generic import RectangleObject, NameObject, StreamObject, NumberObject
import pypdfium2 as pdfium

D = sys.argv[2]
X0, Y0, W, H = 50, 50, 300, 500

def mat(r0):
    return {0: (1, 0, 0, 1, X0, Y0), 90: (0, 1, -1, 0, X0 + W, Y0),
            180: (-1, 0, 0, -1, X0 + W, Y0 + H), 270: (0, -1, 1, 0, X0, Y0 + H)}[r0]

def gen():
    for r0 in (0, 90, 180, 270):
        dw, dh = (W, H) if r0 % 180 == 0 else (H, W)
        w = PdfWriter(); p = w.add_blank_page(400, 600)
        p.cropbox = RectangleObject([X0, Y0, X0 + W, Y0 + H]); p[NameObject("/Rotate")] = NumberObject(r0)
        a, b, c, d, e, f = mat(r0)
        # marqueur rouge du contenu d'origine en intrinsèque (30..40, 20..30)
        ops = f"q {a} {b} {c} {d} {e} {f} cm 1 0 0 rg 30 {dh - 30} 10 10 re f Q"
        s = StreamObject(); s._data = ops.encode(); p[NameObject("/Contents")] = w._add_object(s)
        w.write(os.path.join(D, f"in{r0}.pdf"))

def bbox(img, pred):
    px = img.load(); W_, H_ = img.size; xs = []; ys = []
    for y in range(H_):
        for x in range(W_):
            if pred(px[x, y]): xs.append(x); ys.append(y)
    return (min(xs), min(ys), max(xs) + 1, max(ys) + 1) if xs else None

def check():
    ok = True
    for r0 in (0, 90, 180, 270):
        dw, dh = (W, H) if r0 % 180 == 0 else (H, W)
        for e in (0, 90):
            pdf = pdfium.PdfDocument(os.path.join(D, f"out{r0}_{e}.pdf"))
            img = pdf[0].render(scale=1).to_pil().convert("RGB")
            red = bbox(img, lambda q: q[0] > 200 and q[1] < 80 and q[2] < 80)
            blue = bbox(img, lambda q: q[2] > 180 and q[0] < 90 and q[1] < 120)
            green = bbox(img, lambda q: q[1] > 100 and q[0] < 80 and q[2] < 100)
            if e == 0:
                exp_size, exp_red, exp_blue = (dw, dh), (30, 20), (95, 55)
            else:  # vue tournée 90° horaire : (x, y) -> (dh - y, x)
                exp_size, exp_red, exp_blue = (dh, dw), (dh - 30, 30), (dh - 65, 95)
            good = img.size == exp_size and red and abs(red[0] - exp_red[0]) <= 2 and abs(red[1] - exp_red[1]) <= 2 \
                and blue and abs(blue[0] - exp_blue[0]) <= 3 and abs(blue[1] - exp_blue[1]) <= 3
            # texte : en vue tournée il doit être horizontal (plus large que haut)
            txt_ok = green is not None and (green[2] - green[0]) > (green[3] - green[1])
            ok &= bool(good) and txt_ok
            print(f"{'OK ' if good and txt_ok else 'KO '} r0={r0} e={e} size={img.size} red={red} blue={blue} green={green}")
    return ok

if sys.argv[1] == "gen": gen()
else: sys.exit(0 if check() else 1)
