"""Texturas de la v0.7.5 (tercera tanda): quipu, boca de horno, soplo del wayra, sol de tumbaga,
polvo de diamante y polvo estelar.
Uso: python generar_v075c.py <resourcepack>
"""
import json
import math
import os
import sys

from PIL import Image

PACK = sys.argv[1]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")
T = 32


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def lienzo():
    return Image.new("RGBA", (T, T), (0, 0, 0, 0))


def contornear(img, color):
    px = img.load()
    fuera = [(x, y) for y in range(T) for x in range(T) if px[x, y][3] == 0 and any(
        0 <= x + dx < T and 0 <= y + dy < T and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != color
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in fuera:
        px[x, y] = color
    return img


def quipu():
    """Cordón principal arriba y cuerdas de colores colgando, con nudos."""
    img = lienzo(); px = img.load()
    for x in range(4, 28):
        y = 6 + int(round(math.sin(x / 4) * 0.8))
        px[x, y] = hexc("c9a46a"); px[x, y + 1] = hexc("8a6a3a")
    colores = ["d7263d", "ffd23f", "2e9e4f", "1f4e9e", "ff7a1a", "f2f2f2"]
    for i, c in enumerate(colores):
        x0 = 6 + i * 4
        largo = 16 + (i * 5) % 7
        for t in range(largo):
            x = x0 + int(round(math.sin(t / 3 + i) * 0.7))
            y = 8 + t
            if y < T - 2:
                px[x, y] = hexc(c)
        for k in range(1 + i % 3):
            y = 12 + k * 5 + i % 2
            for dx in (-1, 0, 1):
                px[x0 + dx, y] = hexc(c); px[x0 + dx, y + 1] = hexc("3a2a1a") if dx == 0 else hexc(c)
    return contornear(img, hexc("2b1d14"))


def boca_horno():
    """Boca de adobe con fuego adentro."""
    img = lienzo(); px = img.load()
    for y in range(6, 28):
        for x in range(5, 27):
            px[x, y] = hexc("8c5a3c") if (x // 4 + y // 3) % 2 else hexc("7a4a30")
            if y % 6 == 0:
                px[x, y] = hexc("5e3a24")
    for y in range(13, 26):
        for x in range(9, 23):
            d = math.hypot((x - 15.5) / 7, (y - 25) / 12)
            if d <= 1:
                fuego = (y - 13) / 12
                px[x, y] = hexc("ffe27a") if fuego > 0.75 else hexc("ff9a1f") if fuego > 0.4 else hexc("d7263d") if (x + y) % 3 else hexc("2a1408")
    return contornear(img, hexc("2b1d14"))


def soplo_wayra():
    """Remolinos de viento andino."""
    img = lienzo(); px = img.load()
    for k, (cy, r0, c) in enumerate(((11, 7, "dff3ff"), (19, 9, "a9dcf5"), (25, 5, "7cc4e8"))):
        for t in range(0, 300):
            a = t / 300 * 2 * math.pi * 1.3
            r = r0 * (1 - t / 420)
            x = int(round(16 + (k - 1) * 3 + math.cos(a) * r))
            y = int(round(cy + math.sin(a) * r * 0.45))
            if 0 <= x < T and 0 <= y < T:
                px[x, y] = hexc(c)
    return contornear(img, hexc("2b4a63"))


def sol_tumbaga():
    """Lingote de tumbaga con un sol grabado."""
    img = lienzo(); px = img.load()
    for y in range(10, 25):
        for x in range(4, 28):
            borde = min(x - 4, 27 - x) < (y - 10) // 3 + 1 if y < 14 else False
            if borde:
                continue
            luz = (x - y) / 30
            px[x, y] = hexc("ffe08a") if luz > 0.25 else hexc("f0a83c") if luz > -0.15 else hexc("c0682a")
    for t in range(16):
        a = t / 16 * 2 * math.pi
        for r in (3, 4):
            x = int(round(16 + math.cos(a) * r)); y = int(round(17 + math.sin(a) * r * 0.8))
            px[x, y] = hexc("fff6c8")
    for y in range(15, 20):
        for x in range(14, 19):
            if math.hypot(x - 16, y - 17) <= 2:
                px[x, y] = hexc("ffd23f")
    return contornear(img, hexc("5a2a10"))


def polvo(c1, c2, brillo):
    """Montoncito de polvo con destellos."""
    img = lienzo(); px = img.load()
    for y in range(14, 27):
        for x in range(5, 27):
            if (x - 16) ** 2 / 110 + (y - 27) ** 2 / 150 <= 1:
                px[x, y] = hexc(c1) if (x * 7 + y * 3) % 5 else hexc(c2)
    for (x, y) in ((10, 9), (21, 7), (16, 12), (25, 13), (7, 15)):
        px[x, y] = hexc(brillo)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[x + dx, y + dy] = hexc(c2)
    return contornear(img, hexc("1a1418"))


def guardar_item(clave, img):
    img.save(os.path.join(NS, "textures", "item", f"{clave}.png"))
    with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"banco_minecraft_per:item/{clave}"}}, fh, indent=1)
        fh.write("\n")
    with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
        fh.write("\n")


if __name__ == "__main__":
    for d in ("textures/item", "models/item", "items"):
        os.makedirs(os.path.join(NS, d), exist_ok=True)
    guardar_item("quipu", quipu())
    guardar_item("boca_horno", boca_horno())
    guardar_item("soplo_wayra", soplo_wayra())
    guardar_item("sol_tumbaga", sol_tumbaga())
    guardar_item("polvo_diamante", polvo("6fe3e1", "2fa8b8", "ffffff"))
    guardar_item("polvo_estelar", polvo("b388ff", "6a3fb8", "fff6a0"))
