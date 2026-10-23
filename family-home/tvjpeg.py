import sys, os, glob
from PIL import Image, ImageOps
out = sys.argv[1]
for src in sys.argv[2:]:
    im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
    w, h = im.size
    k = max(1920 / w, 1080 / h)
    if k < 1: im = im.resize((round(w * k), round(h * k)), Image.LANCZOS)
    name = os.path.splitext(os.path.basename(src))[0] + ".jpg"
    im.save(os.path.join(out, name), "JPEG", quality=88, optimize=True)
    print(name, im.size, os.path.getsize(os.path.join(out, name)) // 1024, "KB")
