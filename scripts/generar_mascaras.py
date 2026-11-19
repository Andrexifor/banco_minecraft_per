"""Máscaras ceremoniales de los seres cósmicos (lo que el jugador ve de cerca).

Estilo de máscara funeraria de oro precolombina: tocado en abanico, orejeras redondas, ojos alados,
nariguera en media luna; cada una con los colores de su ser y los ojos encendidos con su luz.
Uso: python generar_mascaras.py <salida_resourcepack>   (item_model banco_minecraft_per:mascara_<ser>)
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


MASCARAS = {
    "banquero": dict(claro=hexc("fff1a8"), medio=hexc("f2b822"), oscuro=hexc("9a6510"), borde=hexc("4a2c05"),
                     ojo=hexc("4ff0e0"), ojo_s=hexc("15867a"), joya=hexc("2fb5a8"), dibujo="vetas"),
    "tendero": dict(claro=hexc("b8ffcf"), medio=hexc("2fbf71"), oscuro=hexc("0e5a3a"), borde=hexc("05301e"),
                    ojo=hexc("ffe95c"), ojo_s=hexc("b08a10"), joya=hexc("f5d742"), dibujo="facetas"),
    "herrero": dict(claro=hexc("c9d2dc"), medio=hexc("6b7785"), oscuro=hexc("2b313a"), borde=hexc("15181d"),
                    ojo=hexc("ffb347"), ojo_s=hexc("d04a00"), joya=hexc("ff6a00"), dibujo="grietas"),
    "mercader": dict(claro=hexc("d9a8ff"), medio=hexc("6a2fb5"), oscuro=hexc("25104a"), borde=hexc("12072a"),
                     ojo=hexc("b8f8ff"), ojo_s=hexc("2a8aa8"), joya=hexc("8ef3ff"), dibujo="galaxia"),
}


def en_cara(x, y):
    """Rostro: más ancho en las mejillas y afinado hacia el mentón."""
    if not 17 <= y <= 53:
        return False
    if y <= 40:
        media = 14
    else:
        media = 14 - (y - 40) * 0.55
    return abs(x - 31.5) <= media


def en_tocado(x, y):
    """Tocado en abanico (media luna) sobre la frente."""
    dx, dy = x - 31.5, y - 20
    r = math.hypot(dx, dy)
    return dy <= 0 and r <= 19.5


def en_orejera(x, y):
    return min(math.hypot(x - 12, y - 33), math.hypot(x - 51, y - 33)) <= 6.2


def ojo_alado(x, y):
    """Ojo almendrado con 'ala' que sube hacia la sien. 2 = ojo encendido, 1 = contorno."""
    for lado in (-1, 1):
        cx = 31.5 + lado * 7.5
        dx = (x - cx) * lado            # positivo hacia afuera
        dy = y - 31
        if -5 <= dx <= 5:
            alto = 3.0 * (1 - (dx / 5.4) ** 2)
            if abs(dy) <= alto:
                return 2
            if abs(dy) <= alto + 1:
                return 1
        if 4 <= dx <= 9 and -1 - (dx - 4) * 0.8 <= dy <= 0 - (dx - 4) * 0.8:  # ala
            return 1
    return 0


def dibujo(t, x, y, c):
    if t["dibujo"] == "vetas" and (x + 2 * y) % 11 == 0:
        return mezcla(c, hexc("fff8d0"), 0.45)
    if t["dibujo"] == "facetas" and (abs((x % 8) - 4) + abs((y % 8) - 4)) == 4:
        return mezcla(c, hexc("d9ffe8"), 0.35)
    if t["dibujo"] == "grietas":
        g = math.sin(x * 0.7 + math.sin(y * 0.5) * 2.2) * math.cos(y * 0.45 - x * 0.2)
        if abs(g) < 0.035 and (x + y) % 3:
            return hexc("ffb347") if abs(g) < 0.015 else hexc("ff5a00")
    if t["dibujo"] == "galaxia":
        if (x * 7 + y * 13) % 29 == 0:
            return hexc("ffffff")
        ang = math.atan2(y - 34, x - 31.5)
        if math.sin(2 * ang + math.hypot(x - 31.5, y - 34) * 0.45) > 0.8:
            return mezcla(c, hexc("e3c4ff"), 0.4)
    return c


def sombra(t, x, y, base_luz):
    luz = base_luz + (31.5 - x) / 60 + (30 - y) / 80
    return mezcla(t["oscuro"], t["medio"], luz + 0.7) if luz < 0.15 else mezcla(t["medio"], t["claro"], (luz - 0.15) * 2.4)


def dibujar(t):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    for y in range(N):
        for x in range(N):
            c = None
            if en_tocado(x, y):
                r = math.hypot(x - 31.5, y - 20)
                ang = math.degrees(math.atan2(y - 20, x - 31.5))
                if r > 18.5 or 9 <= r < 10:
                    c = t["borde"]
                elif r < 9:   # sol de la frente dentro del abanico
                    c = t["joya"] if r < 3.5 else t["claro"] if int(ang) % 30 < 12 else t["medio"]
                elif int(ang) % 15 in (0, 1):   # placas radiales
                    c = t["oscuro"]
                else:
                    c = sombra(t, x, y, 0.25 if r > 15 else 0.05)
                if 16.5 <= r <= 18.5 and int(ang) % 30 in range(12, 18):   # cuentas del borde
                    c = t["joya"]
            if en_orejera(x, y):
                d = min(math.hypot(x - 12, y - 33), math.hypot(x - 51, y - 33))
                c = t["borde"] if d > 5.3 else t["joya"] if d < 2 else t["oscuro"] if d < 3 else sombra(t, x, y, 0.1)
            if en_cara(x, y):
                borde = not (en_cara(x - 1, y) and en_cara(x + 1, y) and en_cara(x, y - 1) and en_cara(x, y + 1))
                c = t["borde"] if borde else dibujo(t, x, y, sombra(t, x, y, 0.1))
                o = ojo_alado(x, y)
                if o == 2:
                    # ojos encendidos: el ser mira desde dentro de la máscara
                    c = t["ojo"] if abs(y - 31) <= 1 else t["ojo_s"]
                elif o == 1:
                    c = t["borde"]
                # cejas en relieve
                if y == 26 and 4 <= abs(x - 31.5) <= 12:
                    c = t["oscuro"]
                # nariz
                if 28 <= y <= 39 and abs(x - 31.5) <= 1:
                    c = t["claro"] if x < 31.5 else t["oscuro"]
                # boca
                if y == 47 and abs(x - 31.5) <= 6:
                    c = t["borde"]
                if y == 48 and abs(x - 31.5) <= 4:
                    c = t["oscuro"]
                # joya de la frente
                if math.hypot(x - 31.5, y - 21) <= 1.8:
                    c = t["joya"]
            # nariguera en media luna con colgantes
            dn = math.hypot(x - 31.5, y - 39)
            if y >= 41 and 4.5 <= dn <= 7 and y <= 45:
                c = t["claro"] if x < 31.5 else t["medio"]
            if y in (46, 47) and abs(x - 31.5) in (4.5, 6.5) and False:
                c = t["joya"]
            if c:
                px[x, y] = c
    # colgantes de la nariguera
    for x in (26, 29, 34, 37):
        for y in (46, 47):
            px[x, y] = t["joya"] if y == 47 else t["claro"]
    return img


def main():
    tex = os.path.join(NS, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(os.path.join(NS, "models", "item"), exist_ok=True)
    os.makedirs(os.path.join(NS, "items"), exist_ok=True)
    for nombre, t in MASCARAS.items():
        clave = f"mascara_{nombre}"
        dibujar(t).save(os.path.join(tex, f"{clave}.png"))
        with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
            fh.write("\n")
        with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
            fh.write("\n")
        print("mascara", clave)


main()
