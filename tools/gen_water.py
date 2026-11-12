"""Generates a tileable 32x32 pixel-art ocean texture -> data/textures/world/water.png
Run from the repo root:  python tools/gen_water.py   (needs Pillow)"""
import math
from PIL import Image

N = 32
TAU = math.tau
BASE = (20, 150, 214)
img = Image.new("RGBA", (N, N))
for y in range(N):
    for x in range(N):
        # sums of sines with integer periods -> perfectly tileable
        w = (math.sin(TAU * (x * 1 + y * 2) / N)
             + 0.6 * math.sin(TAU * (x * 3 - y * 1) / N + 1.3)
             + 0.35 * math.sin(TAU * (x * 2 + y * 5) / N + 2.1))
        t = (w + 1.95) / 3.9                      # 0..1
        level = round(t * 4) / 4                  # posterize: crisp pixel-art bands
        shade = 0.82 + 0.34 * level
        r, g, b = (min(255, int(c * shade)) for c in BASE)
        if level >= 1.0 and (x * 7 + y * 13) % 5 == 0:   # sparse white-cap glints
            r, g, b = 188, 236, 248
        img.putpixel((x, y), (r, g, b, 255))
img.save("data/textures/world/water.png")
print("water.png written")
