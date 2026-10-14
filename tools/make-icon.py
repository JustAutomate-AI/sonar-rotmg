"""The Sonar mark, cut three ways so it stays readable all the way down.

The taskbar was a blob. Two faults: the arcs and the dot were spaced for a
256px canvas and simply had no gaps left at 24, and supersampling 8x then
resampling with LANCZOS softened the little separation that survived.

Fixed by designing each size band on its own geometry with gaps that are
still whole pixels at the target size, and by downsampling with a box filter
at an integer factor, which keeps edges hard instead of ringing them soft.

  > 32 px   dot + three arcs, fading outward - the full mark
  21-32 px  dot + two arcs, heavier, wider gaps
  <= 20 px  dot + one arc - at that size a second arc is noise, not detail
"""
from PIL import Image, ImageDraw
import pathlib

ROOT = pathlib.Path(r"E:/user/.peach-workshop")
CYAN = (111, 216, 255, 255)
SS = 8  # integer supersample, reduced with a box filter


def draw(size, arcs, dot_x, dot_r, stroke, sweep=52):
    """arcs: list of (radius, alpha). All measurements in 64-unit space."""
    W = size * SS
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    u = W / 64.0
    ox, oy = dot_x * u, 32 * u

    for r, alpha in arcs:
        R = r * u
        col = (CYAN[0], CYAN[1], CYAN[2], int(255 * alpha))
        d.arc([ox - R, oy - R, ox + R, oy + R],
              start=-sweep, end=sweep, fill=col, width=max(1, int(stroke * u)))

    d.ellipse([ox - dot_r * u, oy - dot_r * u, ox + dot_r * u, oy + dot_r * u], fill=CYAN)
    # box filter at an integer ratio: no ringing, no softening of the gaps
    return img.resize((size, size), Image.BOX)


def mark(size):
    if size > 32:
        # the full mark: three wavefronts arriving, fading with distance
        return draw(size, [(15, 0.95), (25, 0.62), (35, 0.30)], dot_x=15, dot_r=5.5, stroke=7)
    if size > 20:
        # two arcs, gaps widened to a clear pixel at 24 and 32
        return draw(size, [(18, 1.0), (36, 1.0)], dot_x=13, dot_r=7.5, stroke=8)
    # one arc: at 16 px a second is noise rather than detail
    return draw(size, [(27, 1.0)], dot_x=14, dot_r=9, stroke=12, sweep=66)


def gaps(size):
    """How many whole pixels sit between the parts at this size - the thing that was zero before."""
    if size > 32:
        parts = [(15, 7), (25, 7), (35, 7)]
        dot_x, dot_r = 15, 5.5
    elif size > 20:
        parts = [(18, 8), (36, 8)]
        dot_x, dot_r = 13, 7.5
    else:
        parts = [(27, 12)]
        dot_x, dot_r = 14, 9
    px = size / 64.0
    out = []
    edge = dot_r
    for r, st in parts:
        out.append(round((r - st / 2 - edge) * px, 2))
        edge = r + st / 2
    return out


master = mark(256)
for out in ("src/main/resources/icon/sonarIcon.png", "tools/launcher/sonar.png"):
    master.save(ROOT / out)
    print("wrote", out)

# Every size the window icon might be asked for, shipped as its own file.
# setIconImage with one 256px image leaves Windows to scale it down to 24 for the taskbar, and it
# does that badly - which is the fuzziness, quite apart from the .ico. Handing Swing the whole set
# lets Windows pick an exact match and scale nothing.
for s in (16, 24, 32, 48, 64, 128):
    mark(s).save(ROOT / ("src/main/resources/icon/sonar%d.png" % s))
print("wrote per-size window icons 16/24/32/48/64/128")

sizes = (16, 24, 32, 48, 64, 256)
frames = [mark(s) for s in sizes]
# Pillow builds an ICO by resizing the BASE image to each requested size, so saving from the 16px
# cut silently produced a one-frame file and Windows upscaled 16px to fill the taskbar - which is
# what made it look smushed. The base has to be the largest, with the hand-cut smaller ones
# appended, and the result is checked below rather than assumed.
largest = frames[-1]
largest.save(ROOT / "tools/launcher/sonar.ico", format="ICO",
             sizes=[(s, s) for s in sizes], append_images=frames[:-1])

check = Image.open(ROOT / "tools/launcher/sonar.ico")
got = sorted(w for w, h in check.info["sizes"])
print("wrote tools/launcher/sonar.ico ->", got)
if got != sorted(sizes):
    raise SystemExit("ICO lost frames: wanted %s, got %s" % (sorted(sizes), got))
print()
print("clear pixels between the parts, at the size it is actually shown:")
for s in sizes:
    print("  %3d px  %s" % (s, gaps(s)))

# a sheet at true size and at 6x, so the small cuts can be judged as they really appear
sheet = Image.new("RGBA", (980, 250), (18, 20, 26, 255))
x = 22
for s in (16, 24, 32, 48):
    im = mark(s)
    sheet.alpha_composite(im, (x, 30 - s // 2 + 16))
    big = im.resize((s * 6, s * 6), Image.NEAREST)
    sheet.alpha_composite(big, (x, 70))
    x += max(s * 6, 60) + 18
sheet.save(r"C:/Users/user/AppData/Local/Temp/claude/C--Users-user/e84f26bb-659d-47d1-9f0d-933d610049f7/scratchpad/icon_sheet.png")
print("wrote contact sheet")
