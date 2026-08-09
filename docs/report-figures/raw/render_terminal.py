"""
Renders real captured command output into a terminal-styled PNG for the report.

The text is never edited here - whatever was captured from the actual run is what
gets drawn. Only colour highlighting is applied, so the screenshots in the report
are faithful to the console session that produced them.
"""
import sys
import re
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "C:/Windows/Fonts/consola.ttf"
FONT_BOLD = "C:/Windows/Fonts/consolab.ttf"

BG = (30, 30, 30)
CHROME = (58, 58, 58)
FG = (220, 220, 220)
GREEN = (95, 215, 95)
CYAN = (86, 199, 214)
YELLOW = (229, 192, 123)
GREY = (140, 140, 140)
BLUE = (97, 175, 239)
RED = (224, 108, 117)

PAD = 22
TITLEBAR = 42


def colour_for(line):
    if re.search(r"BUILD SUCCESS|SUCCESS \[|Started ScoringApplication|SCORING UP", line):
        return GREEN
    if re.search(r"BUILD FAILURE|ERROR|FAIL", line):
        return RED
    if re.search(r"Tests run:.*Failures: 0, Errors: 0", line):
        return CYAN
    if line.lstrip().startswith("$"):
        return YELLOW
    if re.search(r"^\s*\{|\"score\"", line):
        return BLUE
    if line.lstrip().startswith(("[INFO] ---", "[INFO] --", "---")):
        return GREY
    return FG


def render(text, out_path, title, width_chars=None, font_size=17):
    lines = text.rstrip("\n").split("\n")
    font = ImageFont.truetype(FONT_PATH, font_size)
    bold = ImageFont.truetype(FONT_BOLD, font_size)

    probe = Image.new("RGB", (10, 10))
    d = ImageDraw.Draw(probe)
    char_w = d.textlength("M", font=font)
    line_h = int(font_size * 1.55)

    cols = width_chars or max(len(l) for l in lines)
    W = int(cols * char_w) + PAD * 2
    H = TITLEBAR + len(lines) * line_h + PAD * 2

    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # window chrome
    d.rectangle([0, 0, W, TITLEBAR], fill=CHROME)
    for i, c in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        cx = PAD + i * 22
        d.ellipse([cx, TITLEBAR // 2 - 7, cx + 14, TITLEBAR // 2 + 7], fill=c)
    tw = d.textlength(title, font=bold)
    d.text(((W - tw) / 2, TITLEBAR // 2 - font_size // 2 - 1), title, font=bold, fill=(205, 205, 205))

    y = TITLEBAR + PAD
    for line in lines:
        f = bold if ("BUILD SUCCESS" in line or line.lstrip().startswith("$")) else font
        d.text((PAD, y), line, font=f, fill=colour_for(line))
        y += line_h

    img.save(out_path)
    print(f"{out_path}  {img.size}")


if __name__ == "__main__":
    src, out, title = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(src, encoding="utf-8", errors="replace") as fh:
        render(fh.read(), out, title)
