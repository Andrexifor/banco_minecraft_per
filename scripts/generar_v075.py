"""Texturas y modelos de la v0.7.5: mochilas andinas, chuspas, hilo de alpaca, flor de traslado,
pluma y modelo 3D del cóndor guardián. Uso: python generar_v075.py <salida_resourcepack>
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


NEGRO = hexc("1a1418")
ORO = hexc("f4c430"); ORO_S = hexc("b8741a")
AGUAYO = [hexc(c) for c in ("c2185b", "ff7a1a", "ffd23f", "2e9e4f", "d7263d", "1f4e9e")]


def lienzo():
    return Image.new("RGBA", (T, T), (0, 0, 0, 0))


def contornear(img, color=NEGRO):
    px = img.load()
    fuera = [(x, y) for y in range(T) for x in range(T) if px[x, y][3] == 0 and any(
        0 <= x + dx < T and 0 <= y + dy < T and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != color
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in fuera:
        px[x, y] = color
    return img


def bolsa(ancho, alto, franjas, base, correa=True, simbolo=None):
    """Bolsa tejida: cuerpo con franjas de aguayo, solapa, correa y un símbolo opcional."""
    img = lienzo(); px = img.load()
    x0, x1 = 16 - ancho // 2, 16 + ancho // 2
    y1 = 29; y0 = y1 - alto
    for y in range(y0, y1):
        for x in range(x0, x1):
            # esquinas redondeadas abajo
            if y >= y1 - 2 and (x in (x0, x1 - 1)):
                continue
            fila = y - y0
            c = base if fila < 3 else franjas[(fila // 2) % len(franjas)]
            if fila >= 3 and (fila // 2) % 3 == 1 and (x + fila) % 4 == 0:
                c = hexc("ffffff")
            px[x, y] = c
    # solapa
    for y in range(y0, y0 + 5):
        for x in range(x0 + 1, x1 - 1):
            if y - y0 <= 4 - abs(x - 16) // 4:
                px[x, y] = base
    px[16, y0 + 4] = ORO; px[15, y0 + 4] = ORO
    if correa:
        for t in range(40):
            a = math.pi * t / 39
            x = int(round(16 + (ancho // 2 - 1) * math.cos(a)))
            y = int(round(y0 - 1 - (y0 - 3) * math.sin(a) * 0.9))
            if 0 <= y < T:
                px[x, y] = hexc("7a3b1d")
    if simbolo:
        simbolo(px, y0)
    return contornear(img)


def ojo_ukhu(px, y0):
    for (x, y) in ((14, 21), (15, 20), (16, 20), (17, 21), (15, 22), (16, 22)):
        px[x, y] = hexc("8ef3ff")
    px[15, 21] = NEGRO; px[16, 21] = NEGRO


def candado(px, y0):
    for y in range(19, 25):
        for x in range(13, 19):
            px[x, y] = ORO if (x + y) % 5 else ORO_S
    for x in (14, 17):
        px[x, 17] = ORO_S; px[x, 18] = ORO_S
    px[15, 16] = ORO_S; px[16, 16] = ORO_S
    px[15, 21] = NEGRO; px[16, 21] = NEGRO; px[15, 22] = NEGRO


def dos_candados(px, y0):
    for cx in (11, 20):
        for y in range(19, 25):
            for x in range(cx - 2, cx + 3):
                px[x, y] = ORO if (x + y) % 5 else ORO_S
        px[cx - 1, 17] = ORO_S; px[cx + 1, 17] = ORO_S; px[cx - 1, 18] = ORO_S; px[cx + 1, 18] = ORO_S; px[cx, 16] = ORO_S
        px[cx, 21] = NEGRO; px[cx, 22] = NEGRO


def hilo():
    img = lienzo(); px = img.load()
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 15.5, y - 17)
            if d <= 9:
                vuelta = int((math.atan2(y - 17, x - 15.5) * 3 + d) % 3)
                px[x, y] = [hexc("f1ece0"), hexc("d8cfbd"), hexc("bfb39b")][vuelta]
    for t in range(14):  # hebra suelta
        px[min(31, 24 + t // 2), 22 + t // 3 if 22 + t // 3 < 32 else 31] = hexc("f1ece0")
    for x, y in ((10, 12), (20, 21), (13, 23)):
        px[x, y] = hexc("e8a0c0")
    return contornear(img, hexc("5a4a38"))


def flor(llena):
    """Diente de león dorado; la llena lleva un brillo y un corazón de luz (hay un animal adentro)."""
    img = lienzo(); px = img.load()
    for y in range(18, 31):  # tallo
        px[16 + (1 if y > 26 else 0), y] = hexc("2e8b3a")
    for x, y in ((13, 24), (12, 23), (19, 26), (20, 25)):  # hojas
        px[x, y] = hexc("3fae4a")
    for k in range(16):  # pétalos
        a = 2 * math.pi * k / 16
        for r in range(3, 9):
            x, y = int(round(16 + r * math.cos(a))), int(round(11 + r * math.sin(a)))
            px[x, y] = ORO if r < 7 else hexc("ffe27a")
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 16, y - 11)
            if d <= 3:
                px[x, y] = hexc("ff9d2e") if not llena else (hexc("ffffff") if d < 1.5 else hexc("ff7ad9"))
    img = contornear(img, hexc("6b4a12"))
    if llena:
        px = img.load()
        for x, y in ((6, 4), (26, 6), (24, 18), (5, 17)):
            px[x, y] = hexc("ffffff")
    return img


def pluma():
    img = lienzo(); px = img.load()
    for t in range(26):  # cañón
        x, y = 6 + t * 0.8, 28 - t
        px[int(x), int(y)] = hexc("d8d0c0")
    for t in range(4, 26):
        x, y = 6 + t * 0.8, 28 - t
        ancho = int(4 * math.sin(math.pi * (t - 4) / 22)) + 1
        for w in range(1, ancho + 1):
            for sgn in (-1, 1):
                X, Y = int(x + sgn * w * 0.8), int(y + w * 0.6 * (1 if sgn > 0 else -0.2))
                if 0 <= X < T and 0 <= Y < T:
                    px[X, Y] = hexc("ece6d6") if t > 20 else NEGRO if w > 1 else hexc("3a3a44")
    return contornear(img, hexc("0d0b0e"))


# ---------------------------------------------------------------- cóndor 3D
def piel_condor():
    """Atlas 32x32: plumaje negro, collar blanco, cabeza roja, pico, alas con banda blanca."""
    img = Image.new("RGBA", (32, 32)); px = img.load()
    for y in range(32):
        for x in range(32):
            if y < 8 and x < 8:      # plumaje negro
                c = NEGRO if (x + y) % 3 else hexc("2c2a33")
            elif y < 8 and x < 16:   # collar blanco
                c = hexc("f4f0e6") if (x + y) % 4 else hexc("d9d2c3")
            elif y < 8 and x < 24:   # piel roja de la cabeza
                c = hexc("c4473a") if (x * y) % 5 else hexc("8e2a22")
            elif y < 8:              # pico
                c = hexc("e8dcc0") if x < 30 else hexc("4a3a2a")
            elif y < 24:             # ala por arriba: negra con banda blanca de plumas secundarias
                fila = y - 8
                c = hexc("ece6d6") if 6 <= fila <= 9 and x % 4 != 3 else (NEGRO if (x + fila) % 3 else hexc("2c2a33"))
            else:                    # ala por abajo
                c = hexc("23212a")
            px[x, y] = c
    return img


def uv(x0, y0, x1, y1):
    return [x0 / 2, y0 / 2, x1 / 2, y1 / 2]


def caja(desde, hasta, region, caras=("north", "south", "east", "west", "up", "down"), arriba=None, abajo=None):
    f = {c: {"uv": region, "texture": "#piel"} for c in caras}
    if arriba:
        f["up"] = {"uv": arriba, "texture": "#piel"}
    if abajo:
        f["down"] = {"uv": abajo, "texture": "#piel"}
    return {"from": desde, "to": hasta, "faces": f}


def modelo_condor():
    negro, blanco, rojo, pico = uv(0, 0, 8, 8), uv(8, 0, 16, 8), uv(16, 0, 24, 8), uv(24, 0, 32, 8)
    ala_arriba, ala_abajo = uv(0, 8, 32, 24), uv(0, 24, 32, 32)
    elementos = [
        caja([5, 5.5, 3], [11, 10, 13], negro),                     # cuerpo
        caja([4.5, 6, 12], [11.5, 10.5, 14], blanco),               # collar de plumas blancas
        caja([6.5, 7, 14], [9.5, 10, 17], rojo),                    # cabeza calva roja
        caja([7.25, 7, 17], [8.75, 8.75, 19.5], pico),              # pico
        caja([6, 7, 0], [10, 8.5, 3], negro),                       # cola
        caja([-11, 9, 4], [5, 9.75, 12], negro, arriba=ala_arriba, abajo=ala_abajo),   # ala izquierda
        caja([11, 9, 4], [27, 9.75, 12], negro, arriba=ala_arriba, abajo=ala_abajo),   # ala derecha
        caja([-15, 9.1, 5], [-11, 9.65, 10], negro),                # plumas de la punta (dedos)
        caja([27, 9.1, 5], [31, 9.65, 10], negro),
        caja([6, 4.5, 6], [7, 5.5, 8], pico), caja([9, 4.5, 6], [10, 5.5, 8], pico),   # patas recogidas
    ]
    return {"textures": {"piel": "banco_minecraft_per:item/condor_piel", "particle": "banco_minecraft_per:item/condor_piel"},
            "elements": elementos,
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}}


def guardar_item(clave, img=None, modelo=None):
    if img is not None:
        img.save(os.path.join(NS, "textures", "item", f"{clave}.png"))
        modelo = {"parent": "minecraft:item/generated", "textures": {"layer0": f"banco_minecraft_per:item/{clave}"}}
    with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump(modelo, fh, indent=1)
        fh.write("\n")
    with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
        fh.write("\n")


def main():
    for d in ("textures/item", "models/item", "items"):
        os.makedirs(os.path.join(NS, d), exist_ok=True)
    guardar_item("chuspa", bolsa(14, 12, AGUAYO[:3], hexc("c2185b")))
    guardar_item("qepi", bolsa(20, 17, AGUAYO, hexc("7a3b1d")))
    guardar_item("gran_qepi", bolsa(26, 22, AGUAYO[::-1], hexc("5a2a14")))
    guardar_item("chuspa_ukhu", bolsa(16, 14, [hexc("4b1d6b"), hexc("2a0f3d")], hexc("6d2f99"), simbolo=ojo_ukhu))
    guardar_item("chuspa_enlazada", bolsa(16, 14, [hexc("b8741a"), hexc("f4c430")], hexc("8a5a0c"), simbolo=candado))
    guardar_item("chuspa_enlazada_doble", bolsa(24, 18, [hexc("f4c430"), hexc("b8741a"), hexc("ffe27a")], hexc("8a5a0c"), simbolo=dos_candados))
    guardar_item("hilo_alpaca", hilo())
    guardar_item("flor_traslado", flor(False))
    guardar_item("flor_llena", flor(True))
    guardar_item("pluma_condor", pluma())
    piel_condor().save(os.path.join(NS, "textures", "item", "condor_piel.png"))
    guardar_item("condor", modelo=modelo_condor())
    print("v0.7.5 lista")


main()
