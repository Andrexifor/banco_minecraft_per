"""Modelos 3D de los seres cósmicos de cerca: unku con volumen + máscara en relieve.

Usa las texturas unku_<ser>.png y mascara_<ser>.png ya generadas (generar_vestimenta.py y
generar_mascaras.py) y arma un modelo de bloques (elements) para cada ser:
  - el unku es un cuerpo de 6 unidades de grosor hecho de franjas que se ensanchan hacia abajo,
    con la tela por delante y por detrás y el borde de la tela en los costados;
  - la máscara va en capas: tocado y orejeras atrás, el rostro más adelante y nariz, cejas,
    orejeras y nariguera sobresaliendo.
Uso: python generar_figuras.py <resourcepack>   -> item_model banco_minecraft_per:figura_<ser>
"""
import json
import os
import sys

from PIL import Image

PACK = sys.argv[1]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")
SERES = ("banquero", "tendero", "herrero", "mercader")

K_UNKU = 0.375        # unidades de modelo por píxel del unku (64 px -> 24 unidades)
K_MASC = 0.25         # unidades por píxel de la máscara (64 px -> 16 unidades)
UNKU_TOP = 20.0       # y del borde superior del unku
Z_FRENTE = 11.0       # cara delantera del unku (el cuerpo va de z 5 a 11)
MASC_TOP = 32.0       # y del borde superior de la máscara (límite del modelo)


def uv(x0, y0, x1, y1):
    """Píxeles de una textura de 64 -> coordenadas uv de 0 a 16."""
    return [round(x0 / 4, 3), round(y0 / 4, 3), round(x1 / 4, 3), round(y1 / 4, 3)]


def ancho_filas(img, y0, y1):
    """Mitad del ancho opaco (en píxeles desde el centro 31.5) entre las filas y0..y1."""
    a = img.getchannel("A")
    media = 0.0
    for y in range(y0, y1):
        xs = [x for x in range(64) if a.getpixel((x, y)) > 0]
        if xs:
            media = max(media, max(31.5 - xs[0] + 0.5, xs[-1] - 31.5 + 0.5))
    return media


def caja(desde, hasta, caras):
    return {"from": [round(v, 3) for v in desde], "to": [round(v, 3) for v in hasta], "faces": caras}


def unku(img):
    elementos = []
    tramos = [(0, 6), (6, 12), (12, 20), (20, 28), (28, 36), (36, 44)]   # sentado: del regazo para abajo va aparte
    for i, (y0, y1) in enumerate(tramos):
        hw = ancho_filas(img, y0, y1)
        if hw <= 0:
            continue
        x0p, x1p = 31.5 - hw + 0.5, 31.5 + hw + 0.5
        top, bot = UNKU_TOP - y0 * K_UNKU, UNKU_TOP - y1 * K_UNKU
        x0, x1 = 8 - hw * K_UNKU, 8 + hw * K_UNKU
        borde_d = int(x1p) - 1
        borde_i = int(x0p)
        caras = {
            "south": {"uv": uv(x0p, y0, x1p, y1), "texture": "#unku"},
            "north": {"uv": uv(x1p, y0, x0p, y1), "texture": "#unku"},
            "east": {"uv": uv(borde_d, y0, borde_d + 1, y1), "texture": "#unku"},
            "west": {"uv": uv(borde_i, y0, borde_i + 1, y1), "texture": "#unku"},
            "up": {"uv": uv(x0p, 14, x1p, 15), "texture": "#unku"},
        }
        elementos.append(caja([x0, bot, 5], [x1, top, Z_FRENTE], caras))
    return elementos


def capa(px0, py0, px1, py1, z0, z1):
    """Recorte de la máscara (píxeles) convertido en una placa entre z0 y z1."""
    x0, x1 = 8 + (px0 - 32) * K_MASC, 8 + (px1 - 32) * K_MASC
    top, bot = MASC_TOP - py0 * K_MASC, MASC_TOP - py1 * K_MASC
    caras = {
        "south": {"uv": uv(px0, py0, px1, py1), "texture": "#mascara"},
        "north": {"uv": uv(px1, py0, px0, py1), "texture": "#mascara"},
        "east": {"uv": uv(px1 - 1, py0, px1, py1), "texture": "#mascara"},
        "west": {"uv": uv(px0, py0, px0 + 1, py1), "texture": "#mascara"},
        "up": {"uv": uv(px0, py0, px1, py0 + 1), "texture": "#mascara"},
        "down": {"uv": uv(px0, py1 - 1, px1, py1), "texture": "#mascara"},
    }
    return caja([x0, bot, z0], [x1, top, z1], caras)


def cuerpo_oculto():
    """Cuerpo sentado en una tiana (el asiento ceremonial de los señores incas): cuello, regazo
    cubierto por el unku (con su ruedo de flecos cayendo sobre las rodillas), piernas, pies y el asiento."""
    tela = uv(20, 40, 24, 44)          # trozo de la tela del unku
    piel = uv(21, 43, 25, 47)          # mejilla de la máscara: el "material" del ser
    oro = [0, 0, 16, 16]
    def bloque(desde, hasta, cara, tex):
        return caja(desde, hasta, {f: {"uv": cara, "texture": tex} for f in ("north", "south", "east", "west", "up", "down")})
    regazo = caja([1.5, 0, 5], [14.5, 3.6, 15.5], {
        "south": {"uv": uv(10, 52, 54, 64), "texture": "#unku"},     # ruedo con flecos sobre las rodillas
        "up": {"uv": uv(14, 36, 50, 50), "texture": "#unku"},
        "east": {"uv": uv(14, 44, 40, 52), "texture": "#unku"},
        "west": {"uv": uv(14, 44, 40, 52), "texture": "#unku"},
        "down": {"uv": tela, "texture": "#unku"},
    })
    return [
        bloque([6, 17.5, 6.5], [10, 21, 9.5], piel, "#mascara"),          # cuello
        regazo,
        bloque([4.5, -9, 12], [7.5, 0.5, 15], tela, "#unku"),             # pierna izquierda
        bloque([8.5, -9, 12], [11.5, 0.5, 15], tela, "#unku"),            # pierna derecha
        bloque([4.25, -10, 11.5], [7.75, -9, 17], piel, "#mascara"),      # pie izquierdo
        bloque([8.25, -10, 11.5], [11.75, -9, 17], piel, "#mascara"),     # pie derecho
        bloque([2, -1, 3], [14, 0, 13], oro, "#tiana"),                    # asiento de la tiana
        bloque([4, -10, 5], [12, -1, 11], oro, "#base"),                   # pie de la tiana
        # cabeza: volumen detrás de la máscara (el tocado la envuelve)
        caja([4.25, 20.5, 4], [11.75, 30.5, 9.5], {
            "east": {"uv": uv(22, 4, 42, 16), "texture": "#mascara"},
            "west": {"uv": uv(22, 4, 42, 16), "texture": "#mascara"},
            "north": {"uv": uv(22, 4, 42, 16), "texture": "#mascara"},
            "up": {"uv": uv(24, 6, 40, 14), "texture": "#mascara"},
            "down": {"uv": uv(21, 43, 25, 47), "texture": "#mascara"},
        }),
    ]


# punto de agarre de cada arma en su textura de 64 px (dónde la sostiene la mano)
AGARRE = {"banquero": (22, 45), "tendero": (34, 26), "herrero": (17, 48), "mercader": (32, 38)}
K_ARMA = 0.3125      # 64 px -> 20 unidades
MANO = (17.0, 6.5, 13.0)


def brazo_y_arma(ser):
    """Brazo que sale del costado del unku hacia adelante, mano del material del ser y el arma en ella."""
    tela = uv(20, 40, 24, 44)
    piel = uv(21, 43, 25, 47)
    def bloque(desde, hasta, cara, tex):
        return caja(desde, hasta, {f: {"uv": cara, "texture": tex} for f in ("north", "south", "east", "west", "up", "down")})
    mx, my, mz = MANO
    gx, gy = AGARRE[ser]
    x0 = mx - gx * K_ARMA
    y1 = my + gy * K_ARMA
    arma = caja([x0, y1 - 64 * K_ARMA, mz - 0.25], [x0 + 64 * K_ARMA, y1, mz + 0.25], {
        "south": {"uv": uv(0, 0, 64, 64), "texture": "#arma"},
        "north": {"uv": uv(64, 0, 0, 64), "texture": "#arma"},
    })
    return [
        bloque([16.25, 10, 6.5], [19.25, 19, 10], tela, "#unku"),        # brazo (manga)
        bloque([16, 5.5, 7], [19, 10, 11.5], tela, "#unku"),            # antebrazo hacia adelante
        bloque([mx - 1.5, my - 1.5, mz - 1.5], [mx + 1.5, my + 1.5, mz + 1.5], piel, "#mascara"),  # mano
        arma,
    ]


def mascara():
    z = Z_FRENTE - 1.5
    return [
        capa(0, 0, 64, 56, z, z + 1),                 # tocado y orejeras (atrás)
        capa(17, 16, 47, 54, z + 1, z + 2),           # rostro
        capa(5, 26, 19, 40, z + 1, z + 2.5),          # orejera izquierda
        capa(45, 26, 59, 40, z + 1, z + 2.5),         # orejera derecha
        capa(20, 25, 44, 28, z + 2, z + 2.5),         # cejas
        capa(30, 28, 34, 40, z + 2, z + 3),           # nariz
        capa(24, 40, 40, 48, z + 2, z + 2.75),        # nariguera
        capa(29, 19, 35, 24, z + 2, z + 2.5),         # joya de la frente
    ]


def main():
    tex = os.path.join(NS, "textures", "item")
    for ser in SERES:
        img = Image.open(os.path.join(tex, f"unku_{ser}.png")).convert("RGBA")
        modelo = {
            "textures": {"unku": f"banco_minecraft_per:item/unku_{ser}",
                         "mascara": f"banco_minecraft_per:item/mascara_{ser}",
                         "arma": f"banco_minecraft_per:item/arma_{ser}",
                         "tiana": "minecraft:block/gold_block",
                         "base": "minecraft:block/chiseled_red_sandstone",
                         "particle": f"banco_minecraft_per:item/unku_{ser}"},
            "elements": unku(img) + mascara() + cuerpo_oculto() + brazo_y_arma(ser),
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
        }
        with open(os.path.join(NS, "models", "item", f"figura_{ser}.json"), "w", encoding="utf-8") as fh:
            json.dump(modelo, fh, indent=1)
            fh.write("\n")
        with open(os.path.join(NS, "items", f"figura_{ser}.json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/figura_{ser}"}}, fh, indent=2)
            fh.write("\n")
        print("figura", ser)


main()
