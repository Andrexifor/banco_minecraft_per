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


TELA = [5.0, 10.0, 6.0, 11.0]      # trozo liso de la tela del unku (px 20..24, 40..44)
PIEL = [5.25, 10.75, 6.25, 11.75]  # mejilla de la máscara: el "material" del ser


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


def ancho_min(img, y0, y1):
    """Mitad del ancho opaco más angosto entre las filas y0..y1 (así la franja no tiene huecos)."""
    a = img.getchannel("A")
    media = None
    for y in range(y0, y1):
        xs = [x for x in range(64) if a.getpixel((x, y)) > 0]
        if not xs:
            return 0
        m = min(31.5 - xs[0] + 0.5, xs[-1] - 31.5 + 0.5)
        media = m if media is None else min(media, m)
    return media or 0


def unku(img, hasta=56):
    """Unku sólido en franjas de 2 filas hasta la fila [hasta] (56 = de pie, sin los flecos;
    44 = sentado). Por delante y por detrás la tela; costados, hombros y bajo, tela lisa."""
    elementos = []
    for y0 in range(0, hasta, 2):
        y1 = y0 + 2
        hw = int(ancho_min(img, y0, y1))
        if hw <= 0:
            continue
        x0p, x1p = 32 - hw, 32 + hw
        top, bot = UNKU_TOP - y0 * K_UNKU, UNKU_TOP - y1 * K_UNKU
        x0, x1 = 8 - hw * K_UNKU, 8 + hw * K_UNKU
        lisa = {"uv": TELA, "texture": "#unku"}
        caras = {
            "south": {"uv": uv(x0p, y0, x1p, y1), "texture": "#unku"},
            "north": {"uv": uv(x1p, y0, x0p, y1), "texture": "#unku"},
            "east": lisa, "west": lisa, "up": lisa, "down": lisa,
        }
        elementos.append(caja([x0, bot, 5], [x1, top, Z_FRENTE], caras))
    if hasta >= 56:
        # flecos del ruedo: una placa delgada solo por delante y por detrás
        hw = int(ancho_min(img, 56, 58)) or 1
        top, bot = UNKU_TOP - 56 * K_UNKU, UNKU_TOP - 64 * K_UNKU
        elementos.append(caja([8 - hw * K_UNKU, bot, 5.5], [8 + hw * K_UNKU, top, Z_FRENTE - 0.5], {
            "south": {"uv": uv(32 - hw, 56, 32 + hw, 64), "texture": "#unku"},
            "north": {"uv": uv(32 + hw, 56, 32 - hw, 64), "texture": "#unku"},
        }))
    return elementos


def capa(px0, py0, px1, py1, z0, z1, solido=False):
    """Recorte de la máscara (píxeles) convertido en una placa entre z0 y z1."""
    x0, x1 = 8 + (px0 - 32) * K_MASC, 8 + (px1 - 32) * K_MASC
    top, bot = MASC_TOP - py0 * K_MASC, MASC_TOP - py1 * K_MASC
    caras = {
        "south": {"uv": uv(px0, py0, px1, py1), "texture": "#mascara"},
        "north": {"uv": uv(px1, py0, px0, py1), "texture": "#mascara"},
        # piezas chicas y llenas: costados del material del ser; las grandes (con forma recortada)
        # toman el borde de la propia textura para no dibujar un marco rectangular
        "east": {"uv": PIEL if solido else uv(px1 - 1, py0, px1, py1), "texture": "#mascara"},
        "west": {"uv": PIEL if solido else uv(px0, py0, px0 + 1, py1), "texture": "#mascara"},
        "up": {"uv": PIEL if solido else uv(px0, py0, px1, py0 + 1), "texture": "#mascara"},
        "down": {"uv": PIEL if solido else uv(px0, py1 - 1, px1, py1), "texture": "#mascara"},
    }
    return caja([x0, bot, z0], [x1, top, z1], caras)




def bloque(desde, hasta, cara, tex):
    return caja(desde, hasta, {f: {"uv": cara, "texture": tex} for f in ("north", "south", "east", "west", "up", "down")})


def cabeza_y_cuello():
    return [
        bloque([6, 17.5, 6.5], [10, 21, 9.5], PIEL, "#mascara"),          # cuello
        # cabeza: volumen detrás de la máscara (el tocado la envuelve)
        # llega hasta la máscara y cubre toda su altura, así no queda hueco entre máscara y cabeza
        caja([3.5, 18.5, 3.5], [12.5, 31.5, 10], {
            "east": {"uv": uv(22, 4, 42, 16), "texture": "#mascara"},
            "west": {"uv": uv(22, 4, 42, 16), "texture": "#mascara"},
            "north": {"uv": uv(22, 4, 42, 16), "texture": "#mascara"},
            "up": {"uv": uv(24, 6, 40, 14), "texture": "#mascara"},
            "down": {"uv": PIEL, "texture": "#mascara"},
            "south": {"uv": PIEL, "texture": "#mascara"},
        }),
    ]


def nucleo(y0):
    """Relleno del cuerpo dentro del unku: lo que se vea entre flecos o rendijas es tela, no vacío."""
    return [bloque([4.5, y0, 5.5], [11.5, 19.5, 10.5], TELA, "#unku")]


def piernas_de_pie():
    return [
        # las piernas suben hasta dentro del unku: sin hueco entre el ruedo y las piernas
        bloque([4.5, -9, 6.5], [7.5, 0, 9.5], TELA, "#unku"),
        bloque([8.5, -9, 6.5], [11.5, 0, 9.5], TELA, "#unku"),
        bloque([4.25, -10, 6], [7.75, -9, 10.5], PIEL, "#mascara"),
        bloque([8.25, -10, 6], [11.75, -9, 10.5], PIEL, "#mascara"),
        # brazo izquierdo colgando al costado (el derecho es una pieza aparte que se anima)
        bloque([-3.25, 9, 6.5], [-0.25, 19, 10], TELA, "#unku"),
        bloque([-3, 7, 6.75], [-0.5, 9.25, 9.75], PIEL, "#mascara"),
    ]


def sentado_en_tiana():
    """Sentado en una tiana (asiento ceremonial inca): regazo cubierto por el unku con su ruedo
    de flecos sobre las rodillas, piernas, pies y el asiento."""
    oro = [0, 0, 16, 16]
    regazo = caja([1.5, 0, 5], [14.5, 3.6, 15.5], {
        "south": {"uv": uv(10, 44, 54, 56), "texture": "#unku"},
        "up": {"uv": uv(14, 36, 50, 50), "texture": "#unku"},
        "east": {"uv": uv(14, 44, 40, 52), "texture": "#unku"},
        "west": {"uv": uv(14, 44, 40, 52), "texture": "#unku"},
        "down": {"uv": TELA, "texture": "#unku"},
    })
    return [
        regazo,
        bloque([4.5, -9, 12], [7.5, 1, 15], TELA, "#unku"),
        bloque([8.5, -9, 12], [11.5, 1, 15], TELA, "#unku"),
        bloque([4.25, -10, 11.5], [7.75, -9, 17], PIEL, "#mascara"),
        bloque([8.25, -10, 11.5], [11.75, -9, 17], PIEL, "#mascara"),
        # el unku cae como un manto desde el regazo hasta el piso y tapa la tiana: solo asoman los pies
        caja([1, -10, 2.5], [15, 0.5, 15.25], {
            "south": {"uv": uv(8, 40, 56, 56), "texture": "#unku"},
            "north": {"uv": uv(56, 40, 8, 56), "texture": "#unku"},
            "east": {"uv": TELA, "texture": "#unku"},
            "west": {"uv": TELA, "texture": "#unku"},
            "down": {"uv": TELA, "texture": "#unku"},
        }),
    ]


# punto de agarre de cada arma en su textura de 64 px (dónde la sostiene la mano)
AGARRE = {"banquero": (22, 45), "tendero": (42, 12), "herrero": (17, 48), "mercader": (32, 38)}
K_ARMA = 0.42        # 64 px -> ~27 unidades (las armas se ven del tamaño de un bastón)
# brazo derecho en el cuerpo: hombro, antebrazo hacia adelante y mano
HOMBRO = (17.75, 18.5, 8.25)
BRAZO = [([16.25, 10, 6.5], [19.25, 19, 10]), ([16, 5.5, 7], [19, 10, 11.5])]
MANO = (17.0, 6.5, 13.0)


def brazo_y_arma(ser, desplazar=(0.0, 0.0, 0.0)):
    """Brazo derecho, mano del material del ser y el arma en la mano. [desplazar] mueve todo
    (para la pieza animada, el hombro queda en el centro del modelo y gira desde ahí)."""
    dx, dy, dz = desplazar
    mov = lambda p: [p[0] + dx, p[1] + dy, p[2] + dz]
    mx, my, mz = MANO[0] + dx, MANO[1] + dy, MANO[2] + dz
    gx, gy = AGARRE[ser]
    x0 = mx - gx * K_ARMA
    y1 = my + gy * K_ARMA
    arma = caja([x0, y1 - 64 * K_ARMA, mz - 0.25], [x0 + 64 * K_ARMA, y1, mz + 0.25], {
        "south": {"uv": uv(0, 0, 64, 64), "texture": "#arma"},
        "north": {"uv": uv(64, 0, 0, 64), "texture": "#arma"},
    })
    return [bloque(mov(a), mov(b), TELA, "#unku") for a, b in BRAZO] + [
        bloque([mx - 1.5, my - 1.5, mz - 1.5], [mx + 1.5, my + 1.5, mz + 1.5], PIEL, "#mascara"),
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
        capa(30, 28, 34, 40, z + 2, z + 3, True),     # nariz
        capa(24, 40, 40, 48, z + 2, z + 2.75),        # nariguera
        capa(29, 19, 35, 24, z + 2, z + 2.5),         # joya de la frente
    ]


SENTADOS = {"tendero"}


def guardar(clave, texturas, elementos):
    modelo = {"textures": texturas, "elements": elementos,
              "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}}
    with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump(modelo, fh, indent=1)
        fh.write("\n")
    with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
        fh.write("\n")


def texturas_tiana(tex):
    """Asiento de oro con borde y base de piedra rojiza con grecas (16x16)."""
    oro = Image.new("RGBA", (16, 16))
    base = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            borde = x in (0, 15) or y in (0, 15)
            oro.putpixel((x, y), (138, 90, 12, 255) if borde else (255, 226, 122, 255) if (x + y) % 7 == 0 else (244, 196, 48, 255))
            greca = (y in (3, 12)) or (4 <= y <= 11 and ((x // 2 + (y - 4) // 2) % 4 == 0))
            base.putpixel((x, y), (90, 40, 22, 255) if borde else (242, 184, 34, 255) if greca else (168, 74, 40, 255))
    oro.save(os.path.join(tex, "tiana_oro.png"))
    base.save(os.path.join(tex, "tiana_base.png"))


def main():
    tex = os.path.join(NS, "textures", "item")
    texturas_tiana(tex)
    for ser in SERES:
        img = Image.open(os.path.join(tex, f"unku_{ser}.png")).convert("RGBA")
        texturas = {"unku": f"banco_minecraft_per:item/unku_{ser}",
                    "mascara": f"banco_minecraft_per:item/mascara_{ser}",
                    "arma": f"banco_minecraft_per:item/arma_{ser}",
                    # texturas propias: un modelo de ítem no puede mezclar texturas de bloques (otro atlas)
                    "tiana": "banco_minecraft_per:item/tiana_oro",
                    "base": "banco_minecraft_per:item/tiana_base",
                    "particle": f"banco_minecraft_per:item/unku_{ser}"}
        if ser in SENTADOS:
            # sentado en su tiana, con el arma en la mano (sin animación)
            guardar(f"figura_{ser}", texturas, unku(img, 44) + nucleo(2) + mascara() + cabeza_y_cuello()
                    + sentado_en_tiana() + brazo_y_arma(ser))
        else:
            # de pie trabajando: cuerpo sin brazo derecho + brazo con arma aparte (gira desde el hombro)
            guardar(f"figura_{ser}", texturas, unku(img) + nucleo(-1) + mascara() + cabeza_y_cuello() + piernas_de_pie())
            hx, hy, hz = HOMBRO
            guardar(f"brazo_{ser}", texturas, brazo_y_arma(ser, (8 - hx, 8 - hy, 8 - hz)))
        print("figura", ser)


main()
