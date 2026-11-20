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


def pocion(liquido, liquido_s, sello):
    """Frasco andino de cerámica (aríbalo chico) con ventana de vidrio que deja ver el líquido."""
    img = lienzo(); px = img.load()
    for y in range(T):
        for x in range(T):
            dx, dy = x - 15.5, y - 19.5
            cuerpo = (dx / 9.5) ** 2 + (dy / 9) ** 2 <= 1
            cuello = abs(dx) <= 2.6 and 5 <= y <= 11
            if cuerpo:
                vidrio = (dx / 6.5) ** 2 + ((dy + 1) / 6) ** 2 <= 1
                if vidrio:
                    c = liquido if dy > -2 else (hexc("dfe9f0") if dy < -4 else liquido_s)
                    if (x + y) % 9 == 0 and dy > 0:
                        c = hexc("ffffff")
                else:
                    c = hexc("b5643a") if (x // 3 + y // 3) % 2 else hexc("8e4a2a")   # barro con greca
                px[x, y] = c
            elif cuello:
                px[x, y] = hexc("b5643a")
    for x in range(12, 20):  # tapón y sello
        for y in range(2, 5):
            px[x, y] = hexc("8a5a32")
    px[15, 3] = sello; px[16, 3] = sello
    for x, y in ((6, 15), (25, 15)):  # asas
        px[x, y] = hexc("8e4a2a"); px[x, y + 1] = hexc("8e4a2a")
    return contornear(img, hexc("3a1e10"))


def maqueta(dibujo):
    """Plano enrollado (quipu-pergamino) con el dibujo de la máquina encima."""
    img = lienzo(); px = img.load()
    for y in range(6, 28):
        for x in range(4, 28):
            px[x, y] = hexc("e8d9b0") if (x + y) % 7 else hexc("d6c494")
    for y in range(5, 29):
        for x in (3, 4, 27, 28):
            px[x, y] = hexc("8a5a32")
    dibujo(px)
    return contornear(img, hexc("4a2c14"))


def d_cantera(px):
    for y in range(12, 24):
        for x in range(9, 23):
            if x in (9, 10, 21, 22) or y in (12, 13, 22, 23):
                px[x, y] = hexc("8a8a8a")
    for y in range(15, 21):
        for x in range(13, 19):
            px[x, y] = hexc("6b6b6b") if (x + y) % 3 else hexc("c8961e")


def d_fuente(px):
    for y in range(14, 25):
        a = (y - 14) // 2
        for x in range(15 - a - 2, 17 + a + 2):
            px[x, y] = hexc("2b2b33")
    for y in range(9, 16):
        px[15, y] = hexc("ff7a1a"); px[16, y] = hexc("ffd23f")


def d_tanque(px):
    for y in range(9, 26):
        for x in range(11, 21):
            borde = x in (11, 20) or y in (9, 25)
            px[x, y] = hexc("c9d2dc") if borde else (hexc("ff7a1a") if y > 17 else hexc("bfe3f0"))


def d_horno(px):
    for y in range(11, 25):
        for x in range(10, 22):
            px[x, y] = hexc("7a7a7a") if (x + y) % 4 else hexc("5a5a5a")
    for y in range(18, 22):
        for x in range(14, 18):
            px[x, y] = hexc("ff9d2e")
    for y in range(6, 11):
        px[16, y] = hexc("aaaaaa")


def mejora_icono(forma, c1, c2):
    img = lienzo(); px = img.load()
    forma(px, c1, c2)
    return contornear(img, hexc("2b1d14"))


def f_cincel(px, c1, c2):
    for t in range(18):
        px[8 + t, 24 - t] = c1; px[9 + t, 24 - t] = c2
    for y in range(20, 27):
        for x in range(5, 11):
            px[x, y] = hexc("8a5a32")


def f_veta(px, c1, c2):
    for y in range(6, 27):
        for x in range(6, 26):
            px[x, y] = hexc("7a7a7a") if (x * y) % 5 else hexc("5a5a5a")
    for (x, y, c) in ((10, 10, c1), (11, 10, c1), (18, 14, c2), (19, 15, c2), (13, 20, hexc("e07a3c")), (14, 21, hexc("e07a3c")), (21, 22, c1)):
        px[x, y] = c


def f_brazo(px, c1, c2):
    for t in range(16):
        for w in range(3):
            px[6 + t, 22 - t // 2 + w] = c1
    for y in range(6, 16):
        for x in range(20, 26):
            px[x, y] = c2


def f_corazon(px, c1, c2):
    for y in range(T):
        for x in range(T):
            dx, dy = (x - 15.5) / 10, (y - 15) / 10
            if (dx * dx + dy * dy - 0.3) ** 3 - dx * dx * dy ** 3 * -1 <= 0 and dy > -0.9:
                px[x, y] = c1 if (x + y) % 4 else c2


def f_aro(px, c1, c2):
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 15.5, y - 15.5)
            if 8 <= d <= 12:
                px[x, y] = c1 if (x + y) % 5 else c2


def tumbaga():
    img = lienzo(); px = img.load()
    pts = [(16, 5), (25, 12), (23, 24), (12, 27), (6, 17), (9, 8)]
    for y in range(T):
        for x in range(T):
            dentro = True
            for i in range(len(pts)):
                (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
                if (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) < 0:
                    dentro = False
            if dentro:
                luz = (x - y) / 30
                px[x, y] = hexc("ffd27a") if luz > 0.2 else hexc("e8a040") if luz > -0.2 else hexc("b0602a")
    for (x, y) in ((14, 10), (15, 10), (19, 15), (11, 18)):
        px[x, y] = hexc("fff6c8")
    return contornear(img, hexc("5a2a10"))


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
    guardar_item("pocion_amparo", pocion(hexc("f4c430"), hexc("c8961e"), hexc("ffffff")))
    guardar_item("pocion_regreso", pocion(hexc("3f8fe0"), hexc("2a5fa8"), hexc("ffd23f")))
    guardar_item("pocion_muerte", pocion(hexc("5a1e6b"), hexc("2a0f3d"), hexc("d7263d")))
    guardar_item("pocion_buscador", pocion(hexc("2fd07a"), hexc("1d8a50"), hexc("8ef3ff")))
    guardar_item("maqueta_cantera", maqueta(d_cantera))
    guardar_item("maqueta_fuente", maqueta(d_fuente))
    guardar_item("maqueta_tanque", maqueta(d_tanque))
    guardar_item("maqueta_horno", maqueta(d_horno))
    guardar_item("cincel_apu", mejora_icono(f_cincel, hexc("c9d2dc"), hexc("8a96a8")))
    guardar_item("veta_montana", mejora_icono(f_veta, hexc("f4c430"), hexc("f2e6da")))
    guardar_item("brazo_cantera", mejora_icono(f_brazo, hexc("8a8a8a"), hexc("c8961e")))
    guardar_item("corazon_misti", mejora_icono(f_corazon, hexc("ff5a1a"), hexc("ffd23f")))
    guardar_item("aro_acero", mejora_icono(f_aro, hexc("c9d2dc"), hexc("6b7785")))
    guardar_item("tumbaga", tumbaga())
    piel_condor().save(os.path.join(NS, "textures", "item", "condor_piel.png"))
    guardar_item("condor", modelo=modelo_condor())
    print("v0.7.5 lista")


main()
