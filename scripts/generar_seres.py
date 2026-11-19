"""Texturas de los seres cósmicos que atienden los cuatro puestos de la plaza.

Cada ser es un orbe flotante con un ojo, un anillo orbital y adornos propios; el datapack de la
plaza los muestra con item_display (item_model banco_minecraft_per:ser_<puesto>) que siempre
miran al jugador. Uso: python generar_seres.py <salida_resourcepack>
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
R_ORBE = 14.5


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mezcla(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


SERES = {
    # Banquero: guardián del oro de las estrellas, rodeado de monedas
    "banquero": dict(
        claro=hexc("fff3b0"), medio=hexc("f2b822"), oscuro=hexc("8a5a0c"), borde=hexc("4a2c05"),
        halo=hexc("ffd84a"), anillo=hexc("ffe27a"), anillo_s=hexc("b37a12"),
        iris=hexc("2fb5a8"), iris_s=hexc("15665e"), inclinacion=-18, adorno="monedas",
    ),
    # Tendero: sembrador del vacío, con cristales verdes que orbitan como hojas
    "tendero": dict(
        claro=hexc("b8ffcf"), medio=hexc("2fbf71"), oscuro=hexc("0e5a3a"), borde=hexc("05301e"),
        halo=hexc("5cff9d"), anillo=hexc("9dffd0"), anillo_s=hexc("1c8a6a"),
        iris=hexc("f5d742"), iris_s=hexc("9a7a10"), inclinacion=15, adorno="cristales",
    ),
    # Herrero: forjador de estrellas, núcleo de hierro con grietas de magma y chispas
    "herrero": dict(
        claro=hexc("c9d2dc"), medio=hexc("6b7785"), oscuro=hexc("2b313a"), borde=hexc("15181d"),
        halo=hexc("ff8a2a"), anillo=hexc("ffb347"), anillo_s=hexc("b8460e"),
        iris=hexc("ff6a00"), iris_s=hexc("8a2a00"), inclinacion=-8, adorno="grietas",
    ),
    # Mercader legendario: oráculo del cosmos, galaxia viva con púas de cristal
    "mercader": dict(
        claro=hexc("d9a8ff"), medio=hexc("6a2fb5"), oscuro=hexc("25104a"), borde=hexc("12072a"),
        halo=hexc("b06bff"), anillo=hexc("8ef3ff"), anillo_s=hexc("2a8aa8"),
        iris=hexc("8ef3ff"), iris_s=hexc("1d6b8a"), inclinacion=22, adorno="galaxia",
    ),
}

ESTRELLAS = [(-26, -24), (25, -22), (-28, 18), (27, 21), (-6, -29), (10, 28), (29, -3), (-30, -4)]


def anillo(dx, dy, t):
    """Devuelve (en_anillo, delante) para un anillo elíptico inclinado."""
    ang = math.radians(t["inclinacion"])
    u = dx * math.cos(ang) + dy * math.sin(ang)
    v = -dx * math.sin(ang) + dy * math.cos(ang)
    e = (u / 26.0) ** 2 + (v / 6.5) ** 2
    return abs(e - 1) < 0.16, v > 0, u


def dibujar(t):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            en_anillo, delante, u = anillo(dx, dy, t)
            c = None
            # halo suave alrededor del orbe
            if R_ORBE < r < R_ORBE + 6:
                a = int(150 * (1 - (r - R_ORBE) / 6) ** 1.6)
                c = t["halo"][:3] + (a,)
            # anillo por detrás del orbe
            if en_anillo and not delante:
                c = t["anillo_s"]
            # orbe con luz desde arriba a la izquierda
            if r <= R_ORBE:
                if r > R_ORBE - 1:
                    c = t["borde"]
                else:
                    luz = (-dx - dy) / (R_ORBE * 2) + 0.15 - r / (R_ORBE * 3)
                    c = mezcla(t["oscuro"], t["medio"], luz + 0.75) if luz < 0.2 else mezcla(t["medio"], t["claro"], (luz - 0.2) * 2.2)
                    c = adorno_interior(t, x, y, dx, dy, r, c)
            # anillo por delante del orbe
            if en_anillo and delante:
                c = t["anillo"] if (int(u) % 7) else t["anillo_s"]
            if c:
                px[x, y] = c
    adornos_exteriores(t, px)
    ojo(t, px)
    for sx, sy in ESTRELLAS:  # destellos
        for ox, oy, a in ((0, 0, 255), (1, 0, 160), (-1, 0, 160), (0, 1, 160), (0, -1, 160)):
            px[int(C + 0.5 + sx + ox), int(C + 0.5 + sy + oy)] = t["anillo"][:3] + (a,)
    return img


def adorno_interior(t, x, y, dx, dy, r, c):
    if t["adorno"] == "grietas":
        # grietas de magma: líneas onduladas que brillan
        g = math.sin(dx * 0.7 + math.sin(dy * 0.5) * 2.2) * math.cos(dy * 0.45 - dx * 0.2)
        if abs(g) < 0.09 and r < R_ORBE - 2:
            return hexc("ffb347") if abs(g) < 0.04 else hexc("ff5a00")
    if t["adorno"] == "galaxia":
        # brazos de galaxia y estrellitas dentro del orbe
        ang = math.atan2(dy, dx)
        brazo = math.sin(2 * ang + r * 0.45)
        if brazo > 0.75 and r < R_ORBE - 2:
            c = mezcla(c, hexc("e3c4ff"), 0.45)
        if (x * 7 + y * 13) % 29 == 0 and r < R_ORBE - 2:
            c = hexc("ffffff")
    if t["adorno"] == "monedas":
        # vetas de oro
        if (x + 2 * y) % 11 == 0 and r < R_ORBE - 2:
            c = mezcla(c, hexc("fff8d0"), 0.5)
    if t["adorno"] == "cristales":
        # facetas: rombos más claros
        if (abs((x % 8) - 4) + abs((y % 8) - 4)) == 4 and r < R_ORBE - 2:
            c = mezcla(c, hexc("d9ffe8"), 0.35)
    return c


def adornos_exteriores(t, px):
    def punto(x, y, c):
        x, y = math.floor(x + 0.5) + 32, math.floor(y + 0.5) + 32
        if 0 <= x < N and 0 <= y < N:
            px[x, y] = c

    if t["adorno"] == "monedas":
        # 6 monedas que orbitan
        for k in range(6):
            a = math.radians(k * 60 + 15)
            cx, cy = round(21 * math.cos(a)), round(21 * math.sin(a))
            for ox in range(-2, 3):
                for oy in range(-2, 3):
                    d = math.hypot(ox, oy)
                    if d <= 2.3:
                        punto(cx + ox, cy + oy, hexc("8a5a0c") if d > 1.6 else hexc("ffe27a") if (ox + oy) < 0 else hexc("f2b822"))
    if t["adorno"] == "cristales":
        # 5 cristales en rombo
        for k in range(5):
            a = math.radians(k * 72 - 90)
            cx, cy = round(22 * math.cos(a)), round(22 * math.sin(a))
            for ox in range(-3, 4):
                for oy in range(-5, 6):
                    if abs(ox) / 3.2 + abs(oy) / 5.5 <= 1:
                        punto(cx + ox, cy + oy, hexc("d9ffe8") if ox < 0 else hexc("2fbf71") if ox == 0 else hexc("0e5a3a"))
    if t["adorno"] == "grietas":
        # chispas de forja alrededor
        for k in range(10):
            a = math.radians(k * 36 + 7)
            rr = 18 + (k % 3) * 3
            punto(rr * math.cos(a), rr * math.sin(a), hexc("ffd27a"))
            punto((rr + 1) * math.cos(a), (rr + 1) * math.sin(a), hexc("ff6a00"))
    if t["adorno"] == "galaxia":
        # 4 púas de cristal (arriba, abajo, izquierda, derecha)
        for ang in (0, 90, 180, 270):
            a = math.radians(ang)
            for k in range(8):
                ancho = 2.5 * (1 - k / 8)
                for w in range(-2, 3):
                    if abs(w) <= ancho:
                        rr = R_ORBE + 1 + k
                        x = rr * math.cos(a) - w * math.sin(a)
                        y = rr * math.sin(a) + w * math.cos(a)
                        punto(x, y, hexc("8ef3ff") if w <= 0 else hexc("2a8aa8"))


def ojo(t, px):
    """Ojo en el centro: almendra clara, iris del color del ser y pupila rasgada."""
    for y in range(-7, 8):
        for x in range(-10, 11):
            alto = 6.2 * (1 - (x / 10.5) ** 2)
            if alto <= 0:
                continue
            X, Y = int(C + 0.5 + x), int(C + 0.5 + y)
            if abs(y) <= alto:
                if abs(y) > alto - 1:
                    c = t["borde"]
                else:
                    ri = math.hypot(x, y)
                    if ri <= 4.6:
                        if abs(x) <= 0.6 and ri <= 4.2:
                            c = hexc("0a0a12")
                        else:
                            c = t["iris"] if ri < 3.2 else t["iris_s"]
                    else:
                        c = hexc("f4f0ff")
                px[X, Y] = c
    px[int(C + 0.5 - 2), int(C + 0.5 - 2)] = hexc("ffffff")  # brillo


def main():
    tex = os.path.join(NS, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(os.path.join(NS, "models", "item"), exist_ok=True)
    os.makedirs(os.path.join(NS, "items"), exist_ok=True)
    for nombre, t in SERES.items():
        clave = f"ser_{nombre}"
        dibujar(t).save(os.path.join(tex, f"{clave}.png"))
        with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
            fh.write("\n")
        with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
            fh.write("\n")
        print("ser", clave)


main()
