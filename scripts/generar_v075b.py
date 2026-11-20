"""Texturas y modelos de la segunda tanda de la v0.7.5: mascotas (perro peruano sin pelo, cuy, vizcacha)
y sus ítems, el Apu de la montaña y su Champi, la maqueta del tambo, sombreros cosméticos y el panetón.
Uso: python generar_v075b.py <salida_resourcepack>
"""
import json
import math
import os
import random
import sys

from PIL import Image

PACK = sys.argv[1]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")
T = 32


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


NEGRO = hexc("1a1418")
ORO = hexc("f4c430"); ORO_S = hexc("b8741a"); ORO_L = hexc("ffe27a")


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


def guardar_item(clave, img=None, modelo=None, mano=False):
    if img is not None:
        img.save(os.path.join(NS, "textures", "item", f"{clave}.png"))
        modelo = {"parent": "minecraft:item/handheld" if mano else "minecraft:item/generated",
                  "textures": {"layer0": f"banco_minecraft_per:item/{clave}"}}
    with open(os.path.join(NS, "models", "item", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump(modelo, fh, indent=1)
        fh.write("\n")
    with open(os.path.join(NS, "items", f"{clave}.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"banco_minecraft_per:item/{clave}"}}, fh, indent=2)
        fh.write("\n")


# ---------------------------------------------------------------- modelos 3D por cajas de color
class Atlas:
    """Textura 64x64 con muestras de color de 4x4 (con un poco de grano) para modelos de cajas."""

    def __init__(self, nombre, semilla=1):
        self.nombre = nombre
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.colores = {}
        self.azar = random.Random(semilla)

    def color(self, c, grano=10):
        clave = (c, grano)
        if clave in self.colores:
            return self.colores[clave]
        i = len(self.colores)
        x0, y0 = (i % 16) * 4, (i // 16) * 4
        px = self.img.load()
        for y in range(4):
            for x in range(4):
                d = self.azar.randint(-grano, grano)
                px[x0 + x, y0 + y] = tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)
        uv = [x0 / 4, y0 / 4, (x0 + 4) / 4, (y0 + 4) / 4]
        self.colores[clave] = uv
        return uv

    def guardar(self):
        self.img.save(os.path.join(NS, "textures", "item", f"{self.nombre}.png"))


def caja(atlas, desde, hasta, c, caras=None, grano=10):
    """caras: dict cara -> color distinto (p. ej. {"south": ORO} para el frente)."""
    uv = atlas.color(c, grano)
    f = {k: {"uv": uv, "texture": "#t"} for k in ("north", "south", "east", "west", "up", "down")}
    for k, col in (caras or {}).items():
        f[k] = {"uv": atlas.color(col, grano), "texture": "#t"}
    return {"from": [round(v, 3) for v in desde], "to": [round(v, 3) for v in hasta], "faces": f}


def modelo(atlas, elementos, display=None):
    d = {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}
    d.update(display or {})
    return {"textures": {"t": f"banco_minecraft_per:item/{atlas.nombre}", "particle": f"banco_minecraft_per:item/{atlas.nombre}"},
            "elements": elementos, "display": d}


# ---- perro peruano sin pelo: piel gris pizarra con manchas rosadas, copete claro, orejas paradas
def perro():
    a = Atlas("mascota_perro_piel", 3)
    P = hexc("3b3a40"); P2 = hexc("4a4850"); ROSA = hexc("b98a86"); COPETE = hexc("e8dcc8"); OJO = hexc("101010")
    e = []
    for x0 in (5.5, 9.0):
        for z0 in (3.0, 11.0):
            e.append(caja(a, [x0, 0, z0], [x0 + 1.5, 6.5, z0 + 1.5], P))
    e.append(caja(a, [5, 6, 2], [11, 10.5, 13.5], P, {"up": P2}))
    e.append(caja(a, [6.5, 8.2, 3], [9.5, 10.6, 6], ROSA))             # mancha rosada en el lomo
    e.append(caja(a, [6.5, 9, 12.5], [9.5, 12.5, 14.5], P))            # cuello
    e.append(caja(a, [6, 11.5, 13.5], [10, 15, 17.5], P))              # cabeza
    e.append(caja(a, [7, 11.5, 17.5], [9, 13.5, 20.5], P2))            # hocico
    e.append(caja(a, [7.6, 12.6, 20.5], [8.4, 13.4, 20.8], OJO))       # nariz
    e.append(caja(a, [6.6, 13.2, 17.5], [7.4, 14, 17.7], OJO))         # ojos
    e.append(caja(a, [8.6, 13.2, 17.5], [9.4, 14, 17.7], OJO))
    e.append(caja(a, [6, 15, 14.5], [7.2, 18, 15.5], P2, {"south": ROSA}))   # orejas paradas
    e.append(caja(a, [8.8, 15, 14.5], [10, 18, 15.5], P2, {"south": ROSA}))
    e.append(caja(a, [7, 15, 13.8], [9, 16.2, 16.5], COPETE))          # copete de pelo claro
    e.append(caja(a, [7.4, 9, -1.5], [8.6, 10.2, 2], P))               # cola
    e.append(caja(a, [7.4, 10.2, -1.5], [8.6, 13, -0.3], P))
    a.guardar()
    return modelo(a, e)


# ---- cuy: redondito, tricolor (marrón, blanco y naranja)
def cuy():
    a = Atlas("mascota_cuy_piel", 5)
    MARRON = hexc("7a4a26"); BLANCO = hexc("f2ece0"); NARANJA = hexc("d9792b"); ROSA = hexc("e8a7a0"); OJO = hexc("121010")
    e = [
        caja(a, [5, 0.6, 3], [11, 6.2, 7], MARRON),
        caja(a, [5, 0.6, 7], [11, 6.4, 10.5], BLANCO),
        caja(a, [5, 0.6, 10.5], [11, 6.2, 13], NARANJA),
        caja(a, [5.6, 1.2, 12.5], [10.4, 6, 16.2], NARANJA, {"south": BLANCO}),   # cabeza con frente blanca
        caja(a, [7.2, 2.6, 16.2], [8.8, 3.6, 16.6], ROSA),                       # naricita
        caja(a, [5.9, 4, 15.6], [6.9, 5, 16.3], OJO),
        caja(a, [9.1, 4, 15.6], [10.1, 5, 16.3], OJO),
        caja(a, [5.4, 5.6, 13.2], [6.8, 6.8, 14.2], ROSA),                      # orejitas
        caja(a, [9.2, 5.6, 13.2], [10.6, 6.8, 14.2], ROSA),
    ]
    for x0 in (5.6, 9.2):
        for z0 in (4, 11.5):
            e.append(caja(a, [x0, 0, z0], [x0 + 1.2, 0.8, z0 + 1.2], ROSA))
    a.guardar()
    return modelo(a, e)


# ---- vizcacha: gris, sentada, orejas largas, bigote oscuro y cola larga enroscada hacia arriba
def vizcacha():
    a = Atlas("mascota_vizcacha_piel", 7)
    G = hexc("8f8a82"); G2 = hexc("b3ada3"); OSC = hexc("3a3632"); CREMA = hexc("e0d6c4"); OJO = hexc("101010")
    e = [
        caja(a, [5, 0, 4], [11, 7, 11], G, {"south": CREMA}),           # cuerpo sentado, panza clara
        caja(a, [5.5, 6, 9], [10.5, 10.5, 14], G),                     # cabeza
        caja(a, [6, 6.5, 14], [10, 8.5, 15.5], G2),                    # hocico
        caja(a, [5.6, 7.4, 14.6], [10.4, 7.8, 15.6], OSC),             # franja oscura del bigote
        caja(a, [7.6, 8.4, 15.5], [8.4, 9, 15.7], OSC),                # nariz
        caja(a, [6, 9, 13.5], [7, 10, 14.1], OJO),
        caja(a, [9, 9, 13.5], [10, 10, 14.1], OJO),
        caja(a, [6, 10.5, 10.5], [7.2, 16, 11.5], G2, {"south": CREMA}),  # orejas largas
        caja(a, [8.8, 10.5, 10.5], [10, 16, 11.5], G2, {"south": CREMA}),
        caja(a, [5.2, 0, 10], [6.8, 1.6, 13], G2),                     # patitas delanteras
        caja(a, [9.2, 0, 10], [10.8, 1.6, 13], G2),
        caja(a, [7.2, 1, -2], [8.8, 2.4, 4], OSC),                     # cola larga...
        caja(a, [7.2, 2.4, -2], [8.8, 7.5, -0.6], OSC),               # ...que se enrosca hacia arriba
        caja(a, [7.2, 6.5, -0.6], [8.8, 7.5, 1], OSC),
    ]
    a.guardar()
    return modelo(a, e)


# ---- Apu de la montaña: un nevado hecho guerrero. Cuerpo de roca con musgo y grietas, hombros que son
#      cumbres nevadas, cabeza-montaña con máscara de oro y ojos de turquesa, abanico de plumas, capa de
#      plumas de cóndor, unku con tocapus, Inti de oro en el pecho y su champi estrellado en la mano.
def apu():
    a = Atlas("apu_piel", 11)
    PI = hexc("6e6c70"); PI2 = hexc("58565b"); PI3 = hexc("8a8790"); OSC = hexc("2a2628"); GRIETA = hexc("3c3a40")
    NIEVE = hexc("eef4f8"); NIEVE2 = hexc("cfdde8"); MUSGO = hexc("4f7a3a")
    TURQ = hexc("2fd0c0"); ROJO = hexc("b3261e"); BLANCO = hexc("f2ece0"); MADERA = hexc("6b4426"); NEG = hexc("1e1a1c")
    e = []
    R = 20  # grano de la roca

    def c(desde, hasta, col, caras=None, grano=R):
        e.append(caja(a, desde, hasta, col, caras, grano))

    # piernas y sandalias
    c([3, 1, 5], [7, 8, 11], PI, {"up": PI3}); c([9, 1, 5], [13, 8, 11], PI, {"up": PI3})
    c([2.6, 0, 4.6], [7.4, 1.2, 12], OSC, grano=6); c([8.6, 0, 4.6], [13.4, 1.2, 12], OSC, grano=6)
    c([3.4, 3.5, 10.8], [5, 5, 11.2], GRIETA); c([11, 2, 10.8], [12.4, 3, 11.2], MUSGO, grano=14)
    # unku (falda) con banda de tocapus
    c([2, 6, 4], [14, 10, 12], ROJO, grano=10)
    for i in range(7):
        col = (hexc("f4c430"), NEG, BLANCO)[i % 3]
        c([2 + i * 12 / 7, 7, 11.9], [2 + (i + 1) * 12 / 7, 8.6, 12.3], col, grano=4)
    # torso de roca, musgo y grietas
    c([1, 10, 3], [15, 18, 13], PI, {"up": PI3})
    c([1.5, 12, 12.9], [3.5, 15, 13.3], MUSGO, grano=14); c([12, 16, 12.9], [14.6, 17.6, 13.3], MUSGO, grano=14)
    c([12.5, 11, 12.9], [13.2, 14.5, 13.2], GRIETA); c([0.8, 15, 6], [1.1, 17, 9], GRIETA)
    # Inti de oro en el pecho
    c([5, 11.5, 13], [11, 17, 13.6], hexc("f4c430"), grano=6)
    c([6.5, 13, 13.6], [9.5, 15.5, 14], TURQ, grano=4)
    for (x0, y0, x1, y1) in ((7.5, 17, 8.5, 18), (7.5, 10.5, 8.5, 11.5), (4, 13.8, 5, 14.8), (11, 13.8, 12, 14.8)):
        c([x0, y0, 13], [x1, y1, 13.5], ORO_L, grano=4)
    # capa de plumas de cóndor en la espalda
    c([1.5, 6, 1.8], [14.5, 18, 3], NEG, grano=8)
    c([1.5, 11, 1.6], [14.5, 12.5, 1.8], BLANCO, grano=4)
    # hombros: cumbres nevadas
    for x0, x1 in ((-4, 3), (13, 20)):
        c([x0, 15, 4], [x1, 19, 12], PI2)
        c([x0 + 0.5, 19, 4.5], [x1 - 0.5, 20.5, 11.5], NIEVE, grano=5)
        c([x0 + 1.5, 20.5, 5.5], [x1 - 1.5, 22, 10.5], NIEVE2, grano=5)
        c([x0 + 2.6, 22, 7], [x1 - 2.6, 23.2, 9], NIEVE, grano=4)
    # brazos: el izquierdo cuelga con el puño cerrado; el derecho sostiene el champi
    c([-3, 8, 6], [2, 15, 10], PI); c([-3.5, 5, 5.5], [2.5, 8.5, 10.5], PI2)
    c([14, 9, 6], [19, 15, 10], PI); c([13.6, 8, 9], [19.4, 11.5, 13.5], PI2)
    # champi: mango de madera y cabeza estrellada de oro con puntas
    c([15.8, 1, 10.8], [17.2, 21, 12.2], MADERA, grano=8)
    c([14.5, 20, 9.5], [18.5, 24, 13.5], hexc("f4c430"), grano=6)
    for (x0, y0, z0, x1, y1, z1) in ((13.5, 21.4, 11, 14.5, 22.6, 12), (18.5, 21.4, 11, 19.5, 22.6, 12),
                                     (16, 24, 11, 17, 25.2, 12), (16, 21.4, 13.5, 17, 22.6, 14.6),
                                     (16, 21.4, 8.4, 17, 22.6, 9.5)):
        c([x0, y0, z0], [x1, y1, z1], ORO_L, grano=4)
    # cabeza-montaña con máscara de oro
    c([4, 18, 4], [12, 25, 11], PI3)
    c([4.4, 18.4, 11], [11.6, 24.2, 11.6], hexc("f4c430"), grano=6)
    c([5.4, 21.4, 11.6], [7.4, 22.6, 12], TURQ, grano=3); c([8.6, 21.4, 11.6], [10.6, 22.6, 12], TURQ, grano=3)
    c([7.4, 19.6, 11.6], [8.6, 22.4, 12.1], ORO_L, grano=4)              # nariz
    c([6.2, 18.8, 11.6], [9.8, 19.4, 12], NEG, grano=3)                   # boca
    c([3.4, 20, 7], [4, 23, 9], hexc("f4c430"), grano=4); c([12, 20, 7], [12.6, 23, 9], hexc("f4c430"), grano=4)   # orejeras
    # corona de nieve en forma de cumbre
    c([3.8, 25, 3.8], [12.2, 26.2, 11.2], NIEVE, grano=5)
    c([5.5, 26.2, 5.5], [10.5, 27.6, 9.5], NIEVE2, grano=5)
    c([7, 27.6, 6.8], [9, 28.8, 8.4], NIEVE, grano=4)
    # abanico de plumas detrás de la cabeza
    for i, col in enumerate((ROJO, hexc("f4c430"), TURQ, BLANCO, TURQ, hexc("f4c430"), ROJO)):
        x = 2.5 + i * 1.6
        alto = 30.5 - abs(i - 3) * 1.4
        c([x, 23, 3.2], [x + 1.2, alto, 4], col, grano=6)
    a.guardar()
    return modelo(a, e)


# ---- sombreros (la base del modelo queda sobre la cabeza; la cabeza ocupa x/z 4..12)
VISTA_SOMBRERO = {
    "gui": {"rotation": [25, 45, 0], "translation": [0, 2, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 1], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 1], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]},
}


def chullo(nombre, franjas, borla, semilla):
    a = Atlas(f"sombrero_{nombre}_tela", semilla)
    e = []
    # cúpula tejida por franjas (de abajo hacia arriba se va angostando)
    capas = [(-2.5, -0.5, 3.6, 12.4), (-0.5, 1.5, 3.8, 12.2), (1.5, 3, 4.4, 11.6), (3, 4.2, 5.4, 10.6), (4.2, 5, 6.6, 9.4)]
    for i, (y0, y1, a0, a1) in enumerate(capas):
        e.append(caja(a, [a0, y0, a0], [a1, y1, a1], franjas[i % len(franjas)], grano=14))
    for x0 in (3.3, 12.0):          # orejeras
        e.append(caja(a, [x0, -6, 6.5], [x0 + 0.7, -2.5, 10.5], franjas[0], grano=14))
        e.append(caja(a, [x0 + 0.1, -9, 8.2], [x0 + 0.6, -6, 8.8], borla, grano=6))   # cordón
        e.append(caja(a, [x0 - 0.2, -10.2, 7.8], [x0 + 0.9, -9, 9.2], borla, grano=6))  # borlita
    e.append(caja(a, [7, 5, 7], [9, 7, 9], borla, grano=6))   # pompón
    a.guardar()
    return modelo(a, e, VISTA_SOMBRERO)


def montera():
    a = Atlas("sombrero_montera_tela", 21)
    NEG = hexc("1e1a1c"); ROJO = hexc("b3261e"); ORO_ = ORO; VERDE = hexc("2f7d4a"); AZUL = hexc("2b5a9b")
    e = [
        caja(a, [0, 0.4, 0], [16, 1.3, 16], NEG, {"up": ROJO}),              # ala ancha y plana
        caja(a, [3.8, 1.3, 3.8], [12.2, 2.6, 12.2], ROJO),                    # copa baja
        caja(a, [3.6, -0.6, 3.6], [12.4, 0.4, 12.4], NEG),                     # ajuste a la cabeza
        caja(a, [0, 1.3, 0], [16, 1.6, 0.6], ORO_), caja(a, [0, 1.3, 15.4], [16, 1.6, 16], ORO_),   # bordado del borde
        caja(a, [0, 1.3, 0.6], [0.6, 1.6, 15.4], ORO_), caja(a, [15.4, 1.3, 0.6], [16, 1.6, 15.4], ORO_),
    ]
    for i, x in enumerate(range(1, 16, 3)):                                   # flecos de colores
        c = (VERDE, AZUL, ORO_)[i % 3]
        e.append(caja(a, [x, -1.5, 15.6], [x + 1, 0.4, 16.2], c, grano=6))
        e.append(caja(a, [x, -1.5, -0.2], [x + 1, 0.4, 0.4], c, grano=6))
    a.guardar()
    return modelo(a, e, VISTA_SOMBRERO)


def sombrero_paja(nombre, paja, cinta, semilla, cinta2=None, copa=None):
    a = Atlas(f"sombrero_{nombre}_paja", semilla)
    e = [
        caja(a, [0.5, 0.4, 0.5], [15.5, 1.1, 15.5], paja, grano=18),           # ala
        caja(a, [4, 1.1, 4], [12, 5.6, 12], copa or paja, grano=18),           # copa
        caja(a, [4.6, 5.6, 4.6], [11.4, 6.2, 11.4], copa or paja, grano=18),
        caja(a, [3.8, 1.1, 3.8], [12.2, 2.4, 12.2], cinta, grano=6),            # cinta
        caja(a, [3.6, -0.6, 3.6], [12.4, 0.4, 12.4], paja, grano=18),
    ]
    if cinta2:
        e.append(caja(a, [3.85, 1.6, 3.85], [12.15, 2.0, 12.15], cinta2, grano=4))
    a.guardar()
    return modelo(a, e, VISTA_SOMBRERO)


def mascaipacha():
    a = Atlas("sombrero_mascaipacha_tela", 31)
    ROJO = hexc("c0281e"); ORO_ = ORO; NEG = hexc("1e1a1c"); BLAN = hexc("f4f0e6"); COL = [hexc("d7263d"), hexc("ffd23f"), hexc("2e9e4f"), hexc("1f4e9e")]
    e = []
    for i in range(4):   # llawt'u: cinta trenzada de colores alrededor de la cabeza
        e.append(caja(a, [3.6, -1.6 + i * 0.5, 3.6], [12.4, -1.1 + i * 0.5, 12.4], COL[i], grano=8))
    e.append(caja(a, [5, -5, 12.4], [11, -1.6, 12.8], ROJO, grano=12))        # borla roja sobre la frente
    e.append(caja(a, [6.5, 0.4, 11.8], [9.5, 3.2, 12.6], ORO_))                # placa de oro
    e.append(caja(a, [7.4, 3.2, 8], [8.6, 9.5, 8.8], BLAN))                     # plumas de corequenque
    e.append(caja(a, [7.4, 7.5, 8], [8.6, 9.5, 8.8], NEG))
    e.append(caja(a, [5.8, 2.5, 8.2], [7, 8, 8.8], BLAN))
    e.append(caja(a, [9, 2.5, 8.2], [10.2, 8, 8.8], BLAN))
    a.guardar()
    return modelo(a, e, VISTA_SOMBRERO)


def sombrero_fiesta():
    a = Atlas("sombrero_fiesta_tela", 41)
    ORO_ = ORO; PLATA = hexc("dfe6ee"); ROJO = hexc("d7263d")
    capas = [(0, 2, 4.6, 11.4), (2, 4, 5.4, 10.6), (4, 6, 6.2, 9.8), (6, 8, 7, 9), (8, 9.5, 7.6, 8.4)]
    e = [caja(a, [x0, y0, x0], [x1, y1, x1], (ORO_, PLATA)[i % 2], grano=6) for i, (y0, y1, x0, x1) in enumerate(capas)]
    e.append(caja(a, [7.3, 9.5, 7.3], [8.7, 10.9, 8.7], ROJO, grano=6))
    e.append(caja(a, [4.4, -0.4, 4.4], [11.6, 0.2, 11.6], ROJO, grano=6))
    a.guardar()
    return modelo(a, e, VISTA_SOMBRERO)


# ---------------------------------------------------------------- iconos 2D
def silbato():
    img = lienzo(); px = img.load()
    MAD = hexc("8a5a32"); MAD_S = hexc("5e3b1e")
    for x in range(6, 26):
        for y in range(13, 20):
            px[x, y] = MAD if y < 18 else MAD_S
    for y in range(10, 13):
        for x in range(20, 25):
            px[x, y] = MAD
    for x in range(6, 26, 5):
        for y in range(13, 20):
            px[x, y] = ORO
    for y in range(14, 18):
        px[9, y] = NEGRO; px[10, y] = NEGRO
    for t in range(14):  # cordón
        px[6 - t // 3, 19 + t // 2 if 19 + t // 2 < 31 else 30] = hexc("c2185b")
    return contornear(img)


def manojo(c1, c2, semilla, flor=None):
    img = lienzo(); px = img.load(); r = random.Random(semilla)
    for i in range(14):
        x = 16 + r.randint(-6, 6)
        alto = r.randint(12, 22)
        for y in range(29 - alto, 29):
            xx = x + (y - 29) * (x - 16) // 40
            if 0 <= xx < T:
                px[xx, y] = c1 if (y + i) % 3 else c2
        if flor and r.random() < 0.6:
            px[x, 29 - alto] = flor
    for x in range(12, 21):
        px[x, 24] = hexc("7a3b1d"); px[x, 25] = hexc("7a3b1d")
    return contornear(img)


def champi():
    img = lienzo(); px = img.load()
    MAD = hexc("6b4426"); MAD_L = hexc("8a5a32")
    for t in range(18):
        x, y = 6 + t, 26 - t
        px[x, y] = MAD; px[x + 1, y] = MAD_L
    cx, cy = 24, 8
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - cx, y - cy)
            if d <= 4:
                px[x, y] = ORO if d < 3 else ORO_S
    for dx, dy in ((0, -6), (0, 6), (-6, 0), (6, 0), (-4, -4), (4, 4), (-4, 4), (4, -4)):
        for k in range(1, 3):
            x, y = cx + dx * k // 2, cy + dy * k // 2
            if 0 <= x < T and 0 <= y < T:
                px[x, y] = ORO_L
    px[cx, cy] = hexc("2fd0c0")
    return contornear(img)


def maqueta_tambo():
    img = lienzo(); px = img.load()
    PAP = hexc("eadcb8"); PAP_S = hexc("cbb48a"); TINTA = hexc("5a3a22")
    for y in range(4, 28):
        for x in range(3, 29):
            px[x, y] = PAP if (x + y) % 9 else PAP_S
    for x in range(8, 24):          # techo de paja
        for y in range(9, 13):
            if abs(x - 16) <= 8 - (12 - y):
                px[x, y] = hexc("e3b23c")
    for y in range(13, 22):         # muros de adobe
        for x in range(9, 23):
            px[x, y] = hexc("a8714a") if (x // 2 + y) % 3 else hexc("8a5a32")
    for y in range(16, 22):
        for x in range(14, 18):
            px[x, y] = TINTA
    for x in range(5, 27):          # camino
        px[x, 24] = TINTA if x % 3 else PAP
    return contornear(img)


def paneton():
    img = lienzo(); px = img.load(); r = random.Random(9)
    for y in range(T):
        for x in range(T):
            dx = (x - 16) / 11
            if 14 <= y <= 27 and abs(x - 16) <= 11:
                px[x, y] = hexc("c98a3e") if y > 16 else hexc("7a4a22")
            elif y < 14 and abs(dx) <= 1 and (14 - y) / 8 <= math.sqrt(max(0, 1 - dx * dx)):
                px[x, y] = hexc("8a4a1e") if y < 9 else hexc("a8602a")
    for _ in range(18):            # frutas confitadas
        x, y = r.randint(7, 25), r.randint(17, 26)
        px[x, y] = r.choice([hexc("d7263d"), hexc("2e9e4f"), hexc("ffd23f")])
    for x in range(5, 28):          # papel del molde
        px[x, 27] = hexc("f2e6da")
        px[x, 28] = hexc("d4c3a8")
    return contornear(img)


def main():
    for d in ("textures/item", "models/item", "items"):
        os.makedirs(os.path.join(NS, d), exist_ok=True)
    guardar_item("mascota_perro", modelo=perro())
    guardar_item("mascota_cuy", modelo=cuy())
    guardar_item("mascota_vizcacha", modelo=vizcacha())
    guardar_item("apu", modelo=apu())
    guardar_item("sombrero_chullo", modelo=chullo("chullo", [hexc("c2185b"), hexc("ffd23f"), hexc("1f4e9e"), hexc("2e9e4f")], hexc("ff7a1a"), 13))
    guardar_item("sombrero_chullo_navideno", modelo=chullo("chullo_navideno", [hexc("d7263d"), hexc("f4f0e6"), hexc("2e9e4f")], hexc("ffffff"), 17))
    guardar_item("sombrero_montera", modelo=montera())
    guardar_item("sombrero_chotano", modelo=sombrero_paja("chotano", hexc("e6cf8a"), hexc("1e1a1c"), 23))
    guardar_item("sombrero_patrio", modelo=sombrero_paja("patrio", hexc("f4f0e6"), hexc("f4f0e6"), 27, copa=hexc("d7263d")))
    guardar_item("sombrero_mascaipacha", modelo=mascaipacha())
    guardar_item("sombrero_fiesta", modelo=sombrero_fiesta())
    guardar_item("silbato_perro", silbato())
    guardar_item("mazorca_cuy", manojo(hexc("4f9a3a"), hexc("2f6b2a"), 3, flor=hexc("9b6fd0")))
    guardar_item("pasto_vizcacha", manojo(hexc("d8b65a"), hexc("a8873a"), 5))
    guardar_item("champi_apu", champi(), mano=True)
    guardar_item("maqueta_tambo", maqueta_tambo())
    guardar_item("paneton", paneton())
    print("v0.7.5 (segunda tanda) lista")


main()
