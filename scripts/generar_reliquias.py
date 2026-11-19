"""Reliquias del Oráculo del Cosmos: aspecto propio de los ítems legendarios.

Armas/herramientas a 32x32 dibujadas píxel a píxel (diagonal: mango abajo a la izquierda, punta arriba
a la derecha) y la Armadura del Sol (iconos + capas humanoid / humanoid_leggings para verla puesta).
Las texturas vanilla de netherite solo se usan como plantilla de forma para la armadura. Uso:
    python generar_reliquias.py <carpeta con texturas vanilla> <salida_resourcepack>
La carpeta vanilla necesita: item_netherite_{helmet,chestplate,leggings,boots}.png,
humanoid_netherite.png y humanoid_leggings_netherite.png.
"""
import json
import math
import os
import sys

from PIL import Image

VAN, PACK = sys.argv[1:3]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")
T = 32


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


ORO_B = hexc("fff6c8"); ORO_L = hexc("ffe27a"); ORO = hexc("f4c430"); ORO_M = hexc("c8961e"); ORO_S = hexc("8a5a0c"); ORO_D = hexc("4a2c05")
MOR_L = hexc("d9a8ff"); MOR = hexc("6a2fb5"); MOR_S = hexc("3a1670"); MOR_D = hexc("1c0a3a")
CIA_B = hexc("ffffff"); CIA_L = hexc("b8f8ff"); CIA = hexc("5fd8ec"); CIA_S = hexc("2a8aa8")
NEGRO = hexc("17121b"); BLANCO = hexc("ece6d6"); MADERA = hexc("6b4a2a"); MADERA_S = hexc("3e2a16")


def lienzo():
    return Image.new("RGBA", (T, T), (0, 0, 0, 0))


def contornear(img, color=ORO_D):
    """Contorno de 1 px alrededor de lo dibujado (como los ítems vanilla)."""
    px = img.load()
    fuera = []
    for y in range(T):
        for x in range(T):
            if px[x, y][3] == 0 and any(
                0 <= x + dx < T and 0 <= y + dy < T and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != color
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ):
                fuera.append((x, y))
    for x, y in fuera:
        px[x, y] = color
    return img


def diag(x, y):
    """(a lo largo, perpendicular) respecto a la diagonal de abajo-izquierda a arriba-derecha."""
    return x - y, x + y - 31


# ---------------------------------------------------------------- Filo del Inca
def espada():
    img = lienzo(); px = img.load()
    for y in range(T):
        for x in range(T):
            a, p = diag(x, y)
            c = None
            if 3 <= a <= 27 and abs(p) <= (2 if a < 24 else 1 if a < 27 else 0):
                if a >= 25:
                    c = CIA_B
                elif abs(p) == 2:
                    c = CIA if (a // 2) % 2 else CIA_L          # filo de cristal
                elif p == 0:
                    c = ORO_B if a > 6 else ORO_L
                else:
                    c = ORO if p < 0 else ORO_M
                if p == 0 and a in (9, 14, 19):                # runas moradas en la hoja
                    c = MOR
            if -2 <= a <= 1 and abs(p) <= 6:                    # guarda: sol
                d = abs(p)
                c = ORO_S if a in (-2, 1) or d == 6 else ORO_L if d < 2 else ORO
                if d <= 1 and a in (-1, 0):
                    c = CIA
            if -13 <= a <= -3 and abs(p) <= 1:                  # empuñadura
                c = ORO if a % 4 == 0 else (MOR_L if p < 0 else MOR if p == 0 else MOR_S)
            if -17 <= a <= -14 and abs(p) <= 2:                 # pomo
                c = CIA_L if abs(p) <= 1 and a in (-16, -15) else ORO_M
            if c:
                px[x, y] = c
    return contornear(img)


# ---------------------------------------------------------------- Hacha del Huayna (tumi)
def hacha():
    img = lienzo(); px = img.load()
    for y in range(T):
        for x in range(T):
            a, p = diag(x, y)
            c = None
            if -24 <= a <= 14 and abs(p) <= 1:                  # mango
                c = MADERA if p <= 0 else MADERA_S
                if a % 6 == 0:
                    c = ORO
            # hoja en media luna (tumi) hacia arriba a la izquierda del mango
            d1 = math.hypot(x - 12.5, y - 9.5)
            d2 = math.hypot(x - 17.5, y - 14.5)
            if d1 <= 9.5 and d2 >= 6.5 and p <= -1:
                if d1 > 8.5:
                    c = CIA_L                                   # filo
                elif 6.5 < d1 <= 7.5:
                    c = MOR if (x + y) % 3 else MOR_L           # incrustación morada
                else:
                    c = ORO_L if (x - y) < 2 else ORO
            if a > 12 and abs(p) <= 2 and math.hypot(x - 23.5, y - 7.5) <= 2.3:   # remate de sol
                c = ORO_L if math.hypot(x - 23.5, y - 7.5) < 1.2 else ORO_M
            if c:
                px[x, y] = c
    return contornear(img)


# ---------------------------------------------------------------- Pico de Cerro Rico
def pico():
    img = lienzo(); px = img.load()
    for y in range(T):
        for x in range(T):
            a, p = diag(x, y)
            c = None
            if -24 <= a <= 8 and abs(p) <= 1:                   # mango
                c = MADERA if p <= 0 else MADERA_S
                if a % 6 == 2:
                    c = ORO
            d = math.hypot(x - 0.5, y - 31.5)
            if 27.5 <= d <= 31 and 4 <= x <= 29 and 2 <= y <= 27:
                # cabeza de plata con vetas de mineral que brillan
                c = hexc("e6eaf0") if d > 29.8 else hexc("aab3c0") if d > 28.6 else hexc("6c7685")
                if (x * 5 + y * 3) % 11 == 0:
                    c = CIA_L
                if (x * 3 + y * 7) % 13 == 0:
                    c = hexc("ff7ad9")
                if abs(p) <= 2:
                    c = ORO if d > 28.6 else ORO_M                # abrazadera de oro
            if c:
                px[x, y] = c
    return contornear(img, hexc("2b313a"))


# ---------------------------------------------------------------- Arco del Cóndor
def arco(tension):
    """tension: 0 en reposo, 1..3 tirando (cuerda y flecha hacia abajo a la derecha)."""
    img = lienzo(); px = img.load()
    ext = [(4, 27), (27, 4)]
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 31, y - 31)
            if 25 <= d <= 27 and x <= 28 and y <= 28 and x >= 2 and y >= 2:
                c = MADERA if d < 26 else MADERA_S
                if abs(x - y) <= 1:
                    c = ORO_L                                   # empuñadura de oro
                elif abs(x - y) <= 3:
                    c = ORO
                px[x, y] = c
    # alas de cóndor en las puntas: plumas negras con punta blanca por fuera del arco
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 31, y - 31)
            cerca = min(math.hypot(x - ex, y - ey) for ex, ey in ext)
            if 27 < d <= 30.5 and cerca <= 7 and px[x, y][3] == 0:
                ang = math.degrees(math.atan2(y - 31, x - 31))
                px[x, y] = BLANCO if d > 29.5 else NEGRO if int(ang) % 4 < 3 else hexc("4a4a55")
    # cuerda
    tx, ty = (tension * 2, tension * 2)
    mx, my = 15 + tx, 15 + ty
    for (sx, sy) in ext:
        pasos = 40
        for i in range(pasos + 1):
            t = i / pasos
            X, Y = round(sx + (mx - sx) * t), round(sy + (my - sy) * t)
            if px[X, Y][3] == 0:
                px[X, Y] = CIA_L
    if tension:
        # flecha de luz cian apuntando arriba a la izquierda
        for k in range(14):
            X, Y = round(mx - k * 0.75), round(my - k * 0.75)
            if 0 <= X < T and 0 <= Y < T:
                px[X, Y] = CIA_B if k > 11 else CIA if k > 1 else MOR_L
    return img


# ---------------------------------------------------------------- Tridente de Pachacámac
def tridente():
    img = lienzo(); px = img.load()
    for y in range(T):
        for x in range(T):
            a, p = diag(x, y)
            c = None
            if -26 <= a <= 12 and abs(p) <= 1:                  # asta con olas
                c = ORO_M if p > 0 else ORO
                if (a + (2 if p < 0 else 0)) % 5 == 0:
                    c = CIA if p <= 0 else CIA_S
            if 10 <= a <= 13 and abs(p) <= 7:                   # travesaño
                c = ORO_L if a in (11, 12) else ORO_S
            for off in (-6, 0, 6):                               # tres puntas
                largo = 28 if off == 0 else 23
                if 13 < a <= largo and abs(p - off) <= (1 if a < largo - 2 else 0):
                    c = CIA_B if a >= largo - 2 else CIA_L if p - off < 0 else CIA
            if c:
                px[x, y] = c
    # rostro de Pachacámac en la base de las puntas
    for (x, y, c) in ((19, 12, NEGRO), (21, 14, NEGRO), (18, 15, MOR), (19, 16, MOR), (20, 16, MOR)):
        px[x, y] = c
    return contornear(img)


# ---------------------------------------------------------------- Armadura del Sol
RAMPA = [ORO_D, ORO_S, ORO_M, ORO, ORO_L, ORO_B]


def recolorear(ruta):
    base = Image.open(ruta).convert("RGBA")
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            r, g, b, a = base.getpixel((x, y))
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            out.putpixel((x, y), RAMPA[min(len(RAMPA) - 1, int(lum * 1.9 * len(RAMPA)))])
    return out


def icono_armadura(pieza):
    ico = recolorear(os.path.join(VAN, f"item_netherite_{pieza}.png")).resize((T, T), Image.NEAREST)
    px = ico.load()
    # placas moradas: los píxeles medios pasan a morado en franjas
    for y in range(T):
        for x in range(T):
            if px[x, y][3] and px[x, y] == ORO_M and (y // 4) % 2 == 0:
                px[x, y] = MOR
    if pieza == "chestplate":                                   # Inti en el pecho
        for y in range(T):
            for x in range(T):
                d = math.hypot(x - 15.5, y - 13.5)
                if px[x, y][3] and d <= 4.2:
                    px[x, y] = ORO_B if d < 2 else ORO_L if d < 3.2 else CIA
    if pieza == "helmet":                                       # gema de la frente
        for (x, y) in ((15, 9), (16, 9), (15, 10), (16, 10)):
            px[x, y] = CIA_L
    return ico


def capa_puesta(ruta, piernas):
    img = recolorear(ruta)
    px = img.load()
    W, H = img.size
    for y in range(H):
        for x in range(W):
            if px[x, y][3] and px[x, y] == ORO_M and (y // 2) % 2 == 0:
                px[x, y] = MOR
    if not piernas:
        # Inti en el frente de la pechera (UV del cuerpo: frente en x 20..27, y 20..31)
        for y in range(20, 28):
            for x in range(20, 28):
                d = math.hypot(x - 23.5, y - 23.5)
                if d <= 3.3:
                    px[x, y] = ORO_B if d < 1.5 else ORO_L if d < 2.5 else CIA
        # gema en la frente del casco (frente de la cabeza: x 8..15, y 8..15)
        for (x, y) in ((11, 9), (12, 9), (11, 10), (12, 10)):
            px[x, y] = CIA_L
    return img


# ---------------------------------------------------------------- salida
def modelo(clave, padre="minecraft:item/handheld", textura=None):
    with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": padre, "textures": {"layer0": f"banco_minecraft_per:item/{textura or clave}"}}, fh, indent=2)
        fh.write("\n")


def definicion(clave, modelo_json):
    with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": modelo_json}, fh, indent=2)
        fh.write("\n")


def simple(clave):
    return {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}


def main():
    for d in ("textures/item", "models/item", "items", "equipment",
              "textures/entity/equipment/humanoid", "textures/entity/equipment/humanoid_leggings"):
        os.makedirs(os.path.join(NS, d), exist_ok=True)
    tex = os.path.join(NS, "textures", "item")

    for clave, img in (("reliquia_espada", espada()), ("reliquia_hacha", hacha()),
                       ("reliquia_pico", pico()), ("reliquia_tridente", tridente())):
        img.save(os.path.join(tex, f"{clave}.png"))
        modelo(clave)
        definicion(clave, simple(clave))

    # arco: reposo + tres tensiones, igual que el arco vanilla
    for i, nombre in enumerate(("reliquia_arco", "reliquia_arco_0", "reliquia_arco_1", "reliquia_arco_2")):
        arco(i).save(os.path.join(tex, f"{nombre}.png"))
        modelo(nombre, "minecraft:item/bow")
    definicion("reliquia_arco", {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": simple("reliquia_arco"),
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "fallback": simple("reliquia_arco_0"),
                    "entries": [{"threshold": 0.65, "model": simple("reliquia_arco_1")},
                                {"threshold": 0.9, "model": simple("reliquia_arco_2")}]},
    })

    # armadura del Sol: iconos y capas para verla puesta
    for pieza, clave in (("helmet", "reliquia_casco"), ("chestplate", "reliquia_pechera"),
                         ("leggings", "reliquia_pantalones"), ("boots", "reliquia_botas")):
        icono_armadura(pieza).save(os.path.join(tex, f"{clave}.png"))
        modelo(clave, "minecraft:item/generated")
        definicion(clave, simple(clave))
    capa_puesta(os.path.join(VAN, "humanoid_netherite.png"), False).save(
        os.path.join(NS, "textures", "entity", "equipment", "humanoid", "armadura_sol.png"))
    capa_puesta(os.path.join(VAN, "humanoid_leggings_netherite.png"), True).save(
        os.path.join(NS, "textures", "entity", "equipment", "humanoid_leggings", "armadura_sol.png"))
    with open(os.path.join(NS, "equipment", "armadura_sol.json"), "w", encoding="utf-8") as fh:
        json.dump({"layers": {"humanoid": [{"texture": "banco_minecraft_per:armadura_sol"}],
                              "humanoid_leggings": [{"texture": "banco_minecraft_per:armadura_sol"}]}}, fh, indent=2)
        fh.write("\n")
    print("reliquias listas")


main()
