"""Textura del Inti (sol inca con rostro) que flota sobre la fuente de la plaza.

Se muestra con un item_display gigante (ítem con item_model banco_minecraft_per:inti_sol).
Uso: python generar_inti.py <salida_resourcepack>
"""
import json
import math
import os
import sys

from PIL import Image

PACK = sys.argv[1]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")
N = 64
C = (N - 1) / 2


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


ORO_L = hexc("fff1a8"); ORO = hexc("f9c934"); ORO_M = hexc("e8a723"); ORO_S = hexc("b8741a")
CAFE = hexc("6b3a12"); ROJO = hexc("d9481c"); TURQ = hexc("2fb5a8")

R_CARA = 15.5   # radio del rostro
R_ARO = 19.5    # aro exterior del disco
R_RAYO = 31.5   # punta de los rayos


def rayo(dx, dy):
    """16 rayos: rectos (punta triangular) alternados con rayos ondulados (llamas)."""
    r = math.hypot(dx, dy)
    if r <= R_ARO or r > R_RAYO:
        return None
    ang = math.degrees(math.atan2(dy, dx)) % 360
    sector = 360 / 16
    i = int(ang // sector)
    rel = ang - i * sector - sector / 2           # -11.25 .. 11.25, 0 en el centro del rayo
    t = (r - R_ARO) / (R_RAYO - R_ARO)            # 0 en la base, 1 en la punta
    if i % 2 == 0:
        ancho = 7.5 * (1 - t) + 0.8               # rayo recto que se afina
        if abs(rel) <= ancho:
            return ORO_L if abs(rel) < ancho * 0.35 else ORO if abs(rel) < ancho * 0.75 else ORO_S
    else:
        if r > R_RAYO - 4:
            return None
        centro = 4.5 * math.sin(t * math.pi * 2.2)  # llama ondulada
        ancho = 5.5 * (1 - t) + 1.3
        if abs(rel - centro) <= ancho:
            return ROJO if abs(rel - centro) < ancho * 0.4 else ORO_M
    return None


def disco(dx, dy):
    r = math.hypot(dx, dy)
    if r > R_ARO:
        return None
    if r > R_ARO - 1.2:
        return ORO_S
    if r > R_CARA + 1.2:
        # aro con puntos turquesa (incrustaciones)
        ang = math.degrees(math.atan2(dy, dx)) % 360
        if abs((ang % 30) - 15) < 4 and abs(r - (R_CARA + R_ARO) / 2 - 0.3) < 1:
            return TURQ
        return ORO_M
    if r > R_CARA:
        return ORO_S
    # rostro con sombreado: más claro arriba a la izquierda
    luz = (-dx - dy) / (R_CARA * 2)
    return ORO_L if luz > 0.32 else ORO if luz > -0.25 else ORO_M


def rostro(img):
    """Cejas, ojos, nariz y boca del Inti, dibujados píxel a píxel (coordenadas desde el centro)."""
    def p(x, y, c):
        img.putpixel((int(C + 0.5 + x), int(C + 0.5 + y)), c)

    for lado in (-1, 1):
        # cejas: arco que sube hacia la nariz
        for k, y in zip(range(2, 10), (-6, -7, -7, -8, -8, -8, -7, -7)):
            p(lado * k - (1 if lado < 0 else 0), y, CAFE)
        # ojos: almendra con pupila
        for k in range(3, 9):
            p(lado * k - (1 if lado < 0 else 0), -4, CAFE)
            p(lado * k - (1 if lado < 0 else 0), -2, CAFE)
        p(lado * 2 - (1 if lado < 0 else 0), -3, CAFE)
        p(lado * 9 - (1 if lado < 0 else 0), -3, CAFE)
        for k in range(3, 9):
            p(lado * k - (1 if lado < 0 else 0), -3, ORO_L)
        for k in (5, 6):
            p(lado * k - (1 if lado < 0 else 0), -3, CAFE)
        # mejillas
        for k in (7, 8):
            p(lado * k - (1 if lado < 0 else 0), 2, ROJO)
    # nariz: línea desde las cejas y base ancha
    for y in range(-6, 3):
        p(0, y, ORO_S)
        p(-1, y, ORO_S)
    for x in range(-3, 3):
        p(x, 3, ORO_S)
    # boca: sonrisa
    for x in range(-5, 5):
        p(x, 7, CAFE)
    for x in (-6, 5):
        p(x, 6, CAFE)
    for x in range(-4, 4):
        p(x, 8, ROJO)


def main():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            c = disco(dx, dy) or rayo(dx, dy)
            if c:
                img.putpixel((x, y), c)
    rostro(img)
    tex = os.path.join(NS, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    img.save(os.path.join(tex, "inti_sol.png"))
    os.makedirs(os.path.join(NS, "models", "item"), exist_ok=True)
    with open(os.path.join(NS, "models", "item", "inti_sol.json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "banco_minecraft_per:item/inti_sol"}}, fh, indent=2)
        fh.write("\n")
    os.makedirs(os.path.join(NS, "items"), exist_ok=True)
    with open(os.path.join(NS, "items", "inti_sol.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": "banco_minecraft_per:item/inti_sol"}}, fh, indent=2)
        fh.write("\n")
    print("inti_sol listo")


main()
