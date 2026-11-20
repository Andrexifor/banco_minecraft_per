"""Texturas de la v0.7.5 (tercera tanda): quipu (une máquinas) y boca de horno (mejora de la Huayrachina).
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
