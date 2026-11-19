"""Vestimenta (unku flotante) y armas ceremoniales de los seres cósmicos de la plaza.

De cerca, cada ser es una máscara sobre un unku (túnica inca) vacío que flota, con su arma al lado.
Uso: python generar_vestimenta.py <salida_resourcepack>
Genera item_model banco_minecraft_per:unku_<ser> y banco_minecraft_per:arma_<ser>.
"""
import json
import math
import os
import sys

from PIL import Image

PACK = sys.argv[1]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")
N = 64


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mezcla(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


NEGRO = hexc("1a1418")
UNKUS = {
    "banquero": dict(tela=hexc("b3261e"), tela_s=hexc("7a1712"), banda=[hexc("f2b822"), NEGRO, hexc("ece6d6"), hexc("2fb5a8")],
                     borde=hexc("f2b822"), brillo=hexc("ffd84a")),
    "tendero": dict(tela=hexc("2e7d4f"), tela_s=hexc("1a4d30"), banda=[hexc("f5d742"), NEGRO, hexc("c2185b"), hexc("ece6d6")],
                    borde=hexc("f5d742"), brillo=hexc("5cff9d")),
    "herrero": dict(tela=hexc("3a3f48"), tela_s=hexc("22262c"), banda=[hexc("ff6a00"), NEGRO, hexc("c9d2dc"), hexc("ffb347")],
                    borde=hexc("ff6a00"), brillo=hexc("ff8a2a")),
    "mercader": dict(tela=hexc("2a1a5e"), tela_s=hexc("170d38"), banda=[hexc("8ef3ff"), NEGRO, hexc("d9a8ff"), hexc("ffffff")],
                     borde=hexc("8ef3ff"), brillo=hexc("b06bff"), estrellas=True),
}


def unku(t):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    for y in range(N):
        # hombros redondeados arriba, se ensancha hacia abajo
        if y < 4:
            media = 12 + y * 2
        else:
            media = 20 + (y - 4) * 0.14
        for x in range(N):
            dx = x - 31.5
            if abs(dx) > media:
                continue
            c = t["tela"] if (x + y // 3) % 7 else t["tela_s"]
            if abs(dx) > media - 1.5:
                c = t["borde"]
            # cuello en V
            if y < 10 and abs(dx) < 6 - y * 0.6:
                continue
            if y < 11 and abs(abs(dx) - (6 - y * 0.6)) < 1:
                c = t["borde"]
            # banda de tocapus a la cintura y escalonado al centro
            if 26 <= y <= 33:
                c = t["banda"][((x // 4) + (y // 4)) % 4]
                if y in (26, 33):
                    c = t["borde"]
            if 12 <= y <= 24 and abs(dx) <= 4 and (abs(dx) + (y % 6)) % 6 < 2:
                c = t["borde"]
            if t.get("estrellas") and (x * 7 + y * 11) % 37 == 0 and not 26 <= y <= 33:
                c = hexc("ffffff")
            # el ruedo se deshace en hilos de luz (no hay nadie dentro)
            if y >= 46:
                fade = (y - 46) / 17
                if (x * 3 + y) % 5 < int(fade * 6):
                    continue
                c = mezcla(c, t["brillo"], fade)
                c = c[:3] + (int(255 * (1 - fade * 0.75)),)
            px[x, y] = c
    return img


# ---------------------------------------------------------------- armas (en diagonal, 64x64)
def linea(px, x0, y0, x1, y1, grosor, color_fn):
    pasos = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(pasos + 1):
        t = i / pasos
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for ox in range(-grosor, grosor + 1):
            for oy in range(-grosor, grosor + 1):
                if ox * ox + oy * oy <= grosor * grosor + 0.5:
                    X, Y = int(round(x + ox)), int(round(y + oy))
                    if 0 <= X < N and 0 <= Y < N:
                        px[X, Y] = color_fn(t, ox, oy)


def disco(px, cx, cy, r, color_fn):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                px[x, y] = color_fn(x - cx, y - cy, d)


def arma_banquero():
    """Báculo del Oro Estelar: vara de oro con disco solar y monedas colgando."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0)); px = img.load()
    linea(px, 10, 58, 44, 20, 1, lambda t, ox, oy: hexc("fff1a8") if ox + oy < 0 else hexc("c8961e"))
    for k in range(3):  # anillos de la vara
        tt = 0.25 + k * 0.2
        linea(px, 10 + 34 * tt - 1, 58 - 38 * tt - 1, 10 + 34 * tt + 1, 58 - 38 * tt + 1, 1, lambda *a: hexc("2fb5a8"))
    def sol(dx, dy, d):
        ang = math.degrees(math.atan2(dy, dx)) % 45
        if d > 7 and ang > 15:
            return (0, 0, 0, 0)
        return hexc("8a5a0c") if d > 9 or 6 < d <= 7 else hexc("fff1a8") if d < 3 else hexc("f2b822")
    disco(px, 48, 15, 11, sol)
    disco(px, 48, 15, 2, lambda *a: hexc("2fb5a8"))
    for cx, cy in ((36, 32), (40, 35)):  # monedas colgando
        linea(px, cx, cy - 6, cx, cy - 2, 0, lambda *a: hexc("8a5a0c"))
        disco(px, cx, cy, 2, lambda dx, dy, d: hexc("8a5a0c") if d > 1.5 else hexc("ffe27a"))
    return img


def arma_tendero():
    """Chaquitaclla del Vacío: arado de pie andino con hoja de esmeralda."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0)); px = img.load()
    linea(px, 46, 6, 20, 50, 1, lambda t, ox, oy: hexc("a87a4a") if ox + oy < 0 else hexc("6b4a2a"))
    linea(px, 46, 6, 50, 3, 1, lambda *a: hexc("2fbf71"))                      # mango curvo
    linea(px, 30, 34, 37, 37, 1, lambda *a: hexc("6b4a2a"))                    # apoyapié
    linea(px, 26, 31, 34, 34, 0, lambda *a: hexc("f5d742"))                    # amarre
    # hoja de cristal en forma de hoja, siguiendo la vara
    ux, uy = -26 / 51, 44 / 51
    for i in range(15):
        ancho = 4.2 * math.sin(math.pi * (i + 1) / 16)
        cx, cy = 20 + ux * i, 50 + uy * i
        for w in range(-5, 6):
            if abs(w) <= ancho:
                X, Y = int(round(cx - uy * w)), int(round(cy + ux * w))
                if 0 <= X < N and 0 <= Y < N:
                    px[X, Y] = hexc("d9ffe8") if w < -1 else hexc("2fbf71") if w < 2 else hexc("0e5a3a")
    for sx, sy in ((10, 46), (24, 60), (8, 58)):  # brotes de luz
        px[sx, sy] = hexc("5cff9d")
    return img


def arma_herrero():
    """Champi de las Estrellas: porra estrellada inca de hierro con núcleo de magma."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0)); px = img.load()
    linea(px, 10, 56, 38, 26, 1, lambda t, ox, oy: hexc("8a5a3a") if ox + oy < 0 else hexc("4a2c1a"))
    linea(px, 12, 54, 16, 50, 2, lambda *a: hexc("2b313a"))  # empuñadura
    def estrella(dx, dy, d):
        ang = math.atan2(dy, dx)
        radio = 8 + 7 * max(0.0, math.cos(6 * ang)) ** 2
        if d > radio:
            return (0, 0, 0, 0)
        if d < 3.2:
            return hexc("ffd27a") if d < 1.6 else hexc("ff6a00")
        return hexc("c9d2dc") if dx + dy < -3 else hexc("6b7785") if d < radio - 1 else hexc("15181d")
    disco(px, 44, 20, 15, estrella)
    for sx, sy in ((58, 10), (56, 34), (32, 8), (60, 22)):  # chispas
        px[sx, sy] = hexc("ffb347")
    return img


def arma_mercader():
    """Tumi del Cosmos: cuchillo ceremonial de media luna, hoja de cristal y mango con un ojo."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0)); px = img.load()
    linea(px, 32, 16, 32, 46, 2, lambda t, ox, oy: hexc("d9a8ff") if ox < 0 else hexc("6a2fb5"))
    disco(px, 32, 11, 6, lambda dx, dy, d: hexc("12072a") if d > 5 else hexc("f4f0ff") if abs(dy) < 2 and d > 1.2 else hexc("8ef3ff"))
    for y in range(46, 62):  # hoja en media luna
        for x in range(N):
            dx, dy = x - 32, y - 44
            d = math.hypot(dx, dy)
            if 10 <= d <= 17 and dy > 4:
                px[x, y] = hexc("12072a") if d > 16 else hexc("b8f8ff") if d < 12 else hexc("8ef3ff") if (x + y) % 5 else hexc("ffffff")
    linea(px, 22, 47, 42, 47, 1, lambda *a: hexc("f2b822"))  # guarda de oro
    return img


ARMAS = {"banquero": arma_banquero, "tendero": arma_tendero, "herrero": arma_herrero, "mercader": arma_mercader}


def guardar(img, clave):
    img.save(os.path.join(NS, "textures", "item", f"{clave}.png"))
    with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
        fh.write("\n")
    with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
        fh.write("\n")


def main():
    for d in ("textures/item", "models/item", "items"):
        os.makedirs(os.path.join(NS, d), exist_ok=True)
    for nombre, t in UNKUS.items():
        guardar(unku(t), f"unku_{nombre}")
        guardar(ARMAS[nombre](), f"arma_{nombre}")
        print("vestimenta y arma", nombre)


main()
