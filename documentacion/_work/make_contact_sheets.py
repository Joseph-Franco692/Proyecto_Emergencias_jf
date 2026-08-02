from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
run_name = sys.argv[1] if len(sys.argv) > 1 else "final-render"
PAGES = ROOT / "documentacion" / "_work" / run_name / "pages"
OUT = ROOT / "documentacion" / "_work" / run_name / "sheets"
OUT.mkdir(parents=True, exist_ok=True)

try:
    label_font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 20)
except Exception:
    label_font = ImageFont.load_default()

files = sorted(PAGES.glob("page-*.png"))
per_sheet = 9
thumb_w = 340
thumb_h = 440
gap = 28

for start in range(0, len(files), per_sheet):
    batch = files[start:start+per_sheet]
    canvas = Image.new("RGB", (3*thumb_w + 4*gap, 3*(thumb_h+34) + 4*gap), "white")
    draw = ImageDraw.Draw(canvas)
    for i, path in enumerate(batch):
        im = Image.open(path).convert("RGB")
        im.thumbnail((thumb_w, thumb_h))
        col, row = i % 3, i // 3
        x = gap + col*(thumb_w+gap)
        y = gap + row*(thumb_h+34+gap)
        canvas.paste(im, (x+(thumb_w-im.width)//2, y))
        draw.text((x, y+thumb_h+6), path.stem, font=label_font, fill="#102A43")
    out = OUT / f"sheet-{start//per_sheet+1:02d}.png"
    canvas.save(out, quality=90)
    print(out)
