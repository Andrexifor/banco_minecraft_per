"""Skins incas (64x64, brazos anchos) para los maniquíes del banco + hoja de vista previa."""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "skins"
os.makedirs(OUT, exist_ok=True)

# Partes: (u, v, ancho, alto, prof). Capa exterior aparte.
PARTES = {
    "cabeza": (0, 0, 8, 8, 8), "sombrero": (32, 0, 8, 8, 8),
    "cuerpo": (16, 16, 8, 12, 4), "chaqueta": (16, 32, 8, 12, 4),
    "brazo_d": (40, 16, 4, 12, 4), "manga_d": (40, 32, 4, 12, 4),
    "brazo_i": (32, 48, 4, 12, 4), "manga_i": (48, 48, 4, 12, 4),
    "pierna_d": (0, 16, 4, 12, 4), "pantalon_d": (0, 32, 4, 12, 4),
    "pierna_i": (16, 48, 4, 12, 4), "pantalon_i": (0, 48, 4, 12, 4),
}


def caras(parte):
    u, v, w, h, d = PARTES[parte]
    return {
        "arriba": (u + d, v, w, d), "abajo": (u + d + w, v, w, d),
        "der": (u, v + d, d, h), "frente": (u + d, v + d, w, h),
        "izq": (u + d + w, v + d, d, h), "atras": (u + 2 * d + w, v + d, w, h),
    }


class Skin:
    def __init__(self):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def px(self, x, y, c):
        self.im.putpixel((x, y), c)

    def cara(self, parte, cara, fn):
        """fn(x, y, ancho, alto) -> color o None, en coordenadas locales de la cara."""
        x0, y0, w, h = caras(parte)[cara]
        for y in range(h):
            for x in range(w):
                c = fn(x, y, w, h)
                if c is not None:
                    self.px(x0 + x, y0 + y, c)

    def todo(self, parte, fn_lado, fn_tapa=None):
        """Pinta los 4 lados con fn_lado(x_vuelta, y) (x continuo alrededor) y tapas."""
        u, v, w, h, d = PARTES[parte]
        orden = [("frente", w), ("izq", d), ("atras", w), ("der", d)]
        off = 0
        for cara, ancho in orden:
            o = off
            self.cara(parte, cara, lambda x, y, W, H, o=o: fn_lado(x + o, y))
            off += ancho
        if fn_tapa:
            self.cara(parte, "arriba", lambda x, y, W, H: fn_tapa(x, y))
            self.cara(parte, "abajo", lambda x, y, W, H: fn_tapa(x, y))

    def save(self, nombre):
        self.im.save(os.path.join(OUT, f"{nombre}.png"))


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


PIEL = hexc("9c6440"); PIEL_S = hexc("80502f"); PIEL_L = hexc("b07650")
PELO = hexc("1e1712"); OJO_B = hexc("f2eee6"); OJO = hexc("2b1d14"); BOCA = hexc("6e3a24")
ORO = hexc("f4c430"); ORO_S = hexc("c8961e"); ORO_L = hexc("ffe27a")
ROJO = hexc("b3261e"); ROJO_S = hexc("861a14"); NEGRO = hexc("221c1c"); BLANCO = hexc("ece6d6")
VERDE = hexc("2f7d4a"); VERDE_S = hexc("215a35"); AZUL = hexc("2b5a9b"); TURQ = hexc("2fb5a8")
MARRON = hexc("6b4426"); MARRON_S = hexc("4e301a"); CUERO = hexc("8a5a32"); BEIGE = hexc("cbb48a")
GRIS = hexc("5a5654"); NARANJA = hexc("d9792b"); MORADO = hexc("6a3d8f"); AMARILLO = hexc("e3b23c")


def cabeza_base(s, ojos=OJO, cejas=MARRON_S):
    s.todo("cabeza", lambda x, y: PIEL if y > 1 else PELO, lambda x, y: PELO)
    s.cara("cabeza", "atras", lambda x, y, W, H: PELO if y < 6 else PIEL_S)
    for lado in ("der", "izq"):
        s.cara("cabeza", lado, lambda x, y, W, H: PELO if y < 3 or (x in (0, 7) and y < 5) else None)

    def frente(x, y, W, H):
        if y < 2:
            return PELO
        if y == 3 and x in (1, 2, 5, 6) and cejas:
            return cejas
        if y == 4:
            return {1: OJO_B, 2: ojos, 5: ojos, 6: OJO_B}.get(x)
        if y == 5 and x in (3, 4):
            return PIEL_S
        if y == 6 and x in (3, 4):
            return BOCA
        if y == 7:
            return PIEL_S if x in (0, 7) else None
        return None
    s.cara("cabeza", "frente", frente)


def piernas(s, pantalon, calzado, banda=None):
    for p in ("pierna_d", "pierna_i"):
        def lado(x, y):
            if y >= 10:
                return calzado
            if banda and y == 8:
                return banda
            return pantalon
        s.todo(p, lado, lambda x, y: calzado)


def brazos(s, manga, hasta, puno=None, piel=PIEL):
    for p in ("brazo_d", "brazo_i"):
        def lado(x, y):
            if y < hasta:
                return manga
            if puno and y == hasta:
                return puno
            return piel
        s.todo(p, lado, lambda x, y: manga)


def tocapu(x, y, a, b, c):
    """Patrón tocapu: cuadros de 2x2 con escalón."""
    cx, cy = (x // 2) % 3, (y // 2) % 2
    if (cx + cy) % 3 == 0:
        return a
    if (x + y) % 2 == 0:
        return b
    return c


# ------------------------------------------------------------------ Banquero
def banquero():
    s = Skin()
    cabeza_base(s)
    # chullo (gorro andino a rayas, con orejeras) en la capa exterior
    rayas = [ROJO, ORO, AZUL, BLANCO, ROJO]
    s.todo("sombrero", lambda x, y: rayas[y] if y < 3 else None, lambda x, y: [ROJO, ORO][(x + y) % 2])
    for lado in ("der", "izq"):
        s.cara("sombrero", lado, lambda x, y, W, H: (ORO if y % 2 else ROJO) if (y < 3 or (2 <= x <= 5 and y < 7)) else None)
    s.cara("sombrero", "frente", lambda x, y, W, H: (rayas[y] if y < 3 else None) if not (y == 1 and x % 2) else BLANCO)
    # poncho rojo con bandas doradas
    def poncho(x, y):
        if y in (2, 3) or y == 9:
            return ORO if (x + y) % 3 else ORO_S
        if y >= 11:
            return ROJO_S
        return ROJO
    s.todo("cuerpo", poncho, lambda x, y: ROJO)
    s.cara("cuerpo", "frente", lambda x, y, W, H: (ORO_L if (x in (3, 4) and 5 <= y <= 7) else None))
    # cruz andina (chakana) dorada en el pecho
    s.cara("chaqueta", "frente", lambda x, y, W, H: ORO if ((x in (3, 4) and 4 <= y <= 8) or (y in (5, 6, 7) and x in (2, 5))) and not (x in (3, 4) and y == 6) else None)
    s.cara("chaqueta", "frente", lambda x, y, W, H: ORO_L if (x in (3, 4) and y == 6) else None)
    brazos(s, ROJO, 6, ORO)
    piernas(s, NEGRO, MARRON, banda=ORO_S)
    return s


# ------------------------------------------------------------------ Tendero
def tendero():
    s = Skin()
    cabeza_base(s)
    # montera (sombrero ancho) verde con borde
    s.todo("sombrero", lambda x, y: (VERDE if y == 0 else (AMARILLO if y == 1 else None)), lambda x, y: VERDE)
    s.cara("sombrero", "frente", lambda x, y, W, H: VERDE if y == 0 else (AMARILLO if y == 1 and x % 2 == 0 else None))
    def poncho(x, y):
        if y in (1, 6, 10):
            return [AMARILLO, ROJO, AMARILLO][(x // 2) % 3]
        return VERDE if y < 11 else VERDE_S
    s.todo("cuerpo", poncho, lambda x, y: VERDE)
    # bolso de aguayo cruzado (tira en diagonal)
    s.cara("chaqueta", "frente", lambda x, y, W, H: (MARRON if x == y else None))
    s.cara("chaqueta", "atras", lambda x, y, W, H: (MARRON if x == 7 - y else None))
    s.cara("chaqueta", "frente", lambda x, y, W, H: ([ROJO, NARANJA, MORADO, AMARILLO][(x + y) % 4] if (5 <= x <= 7 and 8 <= y <= 11) else None))
    brazos(s, VERDE, 5, AMARILLO)
    piernas(s, MARRON, NEGRO)
    return s


# ------------------------------------------------------------------ Herrero
def herrero():
    s = Skin()
    cabeza_base(s)
    # vincha (cinta) roja en la frente
    s.todo("sombrero", lambda x, y: (ROJO if y == 2 else None))
    s.cara("sombrero", "frente", lambda x, y, W, H: (ORO if (y == 2 and x in (3, 4)) else (ROJO if y == 2 else None)))
    # tollín en la cara
    s.cara("cabeza", "frente", lambda x, y, W, H: GRIS if (x, y) in ((1, 6), (6, 5), (7, 6)) else None)
    s.todo("cuerpo", lambda x, y: BEIGE if y < 11 else MARRON, lambda x, y: BEIGE)
    # mandil de cuero
    s.cara("chaqueta", "frente", lambda x, y, W, H: (CUERO if (1 <= x <= 6 and y >= 2) or (y < 2 and x in (2, 5)) else None))
    s.cara("chaqueta", "frente", lambda x, y, W, H: (MARRON_S if (y == 7 and 1 <= x <= 6) else None))
    s.cara("chaqueta", "der", lambda x, y, W, H: CUERO if y == 6 else None)
    s.cara("chaqueta", "izq", lambda x, y, W, H: CUERO if y == 6 else None)
    s.cara("chaqueta", "atras", lambda x, y, W, H: CUERO if y == 6 else None)
    # brazos descubiertos con brazaletes de cobre y guantes de cuero
    brazos(s, BEIGE, 3, NARANJA)
    for p in ("manga_d", "manga_i"):
        s.todo(p, lambda x, y: CUERO if y >= 9 else None)
    piernas(s, MARRON, NEGRO)
    return s


# ------------------------------------------------------------------ Mercader legendario
def mercader():
    s = Skin()
    cabeza_base(s, ojos=hexc("1a120c"))
    # mascapaicha: borla roja en la frente + tocado de oro con plumas
    s.todo("sombrero", lambda x, y: (ORO if y == 1 else (ORO_S if y == 0 else None)), lambda x, y: ORO)
    s.cara("sombrero", "frente", lambda x, y, W, H: (ORO if y == 1 else (ROJO if y == 2 and 1 <= x <= 6 else (ROJO_S if y == 3 and x % 2 == 1 else (TURQ if y == 0 and x in (3, 4) else (ORO_S if y == 0 else None))))))
    # orejeras de oro (pendientes incas)
    for lado in ("der", "izq"):
        s.cara("sombrero", lado, lambda x, y, W, H: (ORO_L if (3 <= x <= 4 and 4 <= y <= 5) else (ORO if y == 1 else (ORO_S if y == 0 else None))))
    # uncu (túnica) con tocapus
    def uncu(x, y):
        if 3 <= y <= 7:
            return tocapu(x, y, ROJO, NEGRO, BLANCO)
        if y in (2, 8):
            return ORO
        return hexc("1f3f7a") if y < 11 else hexc("16305e")
    s.todo("cuerpo", uncu, lambda x, y: hexc("1f3f7a"))
    # pectoral de oro (sol) en el pecho
    s.cara("chaqueta", "frente", lambda x, y, W, H: (ORO_L if (x, y) in ((3, 4), (4, 4)) else (ORO if (2 <= x <= 5 and 3 <= y <= 5 and not (x in (2, 5) and y in (3, 5))) else None)))
    s.cara("chaqueta", "frente", lambda x, y, W, H: (TURQ if (x, y) in ((3, 5), (4, 3)) else None))
    brazos(s, hexc("1f3f7a"), 4, ORO)
    for p in ("manga_d", "manga_i"):
        s.todo(p, lambda x, y: ORO if y in (4, 9) else None)
    piernas(s, hexc("16305e"), ORO_S, banda=ORO)
    return s


# ------------------------------------------------------------------ Mercader de bromas (danzante de tijeras)
def bromista():
    s = Skin()
    cabeza_base(s)
    # sombrero de danzante con espejos y flecos de colores
    colores = [ROJO, AMARILLO, VERDE, AZUL, MORADO, NARANJA]
    s.todo("sombrero", lambda x, y: (colores[x % 6] if y == 0 else (ORO if y == 1 else None)), lambda x, y: colores[(x + y) % 6])
    s.cara("sombrero", "frente", lambda x, y, W, H: (TURQ if (y == 0 and x in (3, 4)) else (colores[x % 6] if y == 0 else (ORO_L if y == 1 and x % 2 else (ORO if y == 1 else (colores[x % 6] if y == 2 and x % 2 == 0 else None))))))
    # chaqueta bordada con lentejuelas
    def chaqueta(x, y):
        if y in (0, 11):
            return ORO
        if (x + y) % 4 == 0:
            return ORO_L
        return [ROJO, ROJO, MORADO][(y // 4) % 3]
    s.todo("cuerpo", chaqueta, lambda x, y: ROJO)
    s.cara("chaqueta", "frente", lambda x, y, W, H: (TURQ if (x, y) in ((2, 3), (5, 3), (3, 6), (4, 6), (2, 9), (5, 9)) else None))
    brazos(s, MORADO, 7, ORO)
    for p in ("manga_d", "manga_i"):
        s.todo(p, lambda x, y: [ROJO, AMARILLO, VERDE][y % 3] if y in (2, 5) else None)
    piernas(s, hexc("1f3f7a"), NEGRO, banda=ORO)
    for p in ("pantalon_d", "pantalon_i"):
        s.todo(p, lambda x, y: [ROJO, AMARILLO, VERDE, AZUL][x % 4] if y in (6, 7) else None)
    return s


# ------------------------------------------------------------------ Contrabandista (mercado negro)
def contrabandista():
    s = Skin()
    cabeza_base(s, ojos=hexc("b3261e"))
    CAPA = hexc("1b1717"); CAPA_L = hexc("2c2626")
    # capucha oscura que tapa casi toda la cabeza
    s.todo("sombrero", lambda x, y: CAPA, lambda x, y: CAPA)
    s.cara("sombrero", "frente", lambda x, y, W, H: (CAPA if (y < 3 or x in (0, 7)) else (CAPA_L if y == 3 else (0, 0, 0, 0))))
    # pañuelo que tapa la boca
    s.cara("cabeza", "frente", lambda x, y, W, H: (hexc("3a2f2f") if y >= 5 else None))
    s.cara("cabeza", "frente", lambda x, y, W, H: ({1: OJO_B, 2: hexc("b3261e"), 5: hexc("b3261e"), 6: OJO_B}.get(x) if y == 4 else None))
    # túnica negra con cinturón y bolsas de oro escondidas
    def tunica(x, y):
        if y == 7:
            return CUERO
        return CAPA if (x + y) % 5 else CAPA_L
    s.todo("cuerpo", tunica, lambda x, y: CAPA)
    s.cara("chaqueta", "frente", lambda x, y, W, H: (ORO if (x, y) in ((5, 8), (6, 8), (5, 9), (6, 9)) else (ORO_S if (x, y) == (6, 7) else None)))
    s.todo("chaqueta", lambda x, y: CAPA_L if y < 2 else None)
    brazos(s, CAPA, 10, CAPA_L)
    piernas(s, CAPA_L, NEGRO)
    return s


# ------------------------------------------------------------------ Criador de animales (pastor andino)
def criador():
    s = Skin()
    cabeza_base(s)
    LANA = hexc("e8dcc4"); LANA_S = hexc("c9b896"); CAFE = hexc("7a4a28")
    # chullo de lana con rombos y pompón
    s.todo("sombrero", lambda x, y: ([CAFE, LANA, ROJO][y] if y < 3 else None), lambda x, y: LANA if (x, y) != (3, 3) else ROJO)
    s.cara("sombrero", "frente", lambda x, y, W, H: (CAFE if y == 0 else (ROJO if (y == 1 and x % 3 == 1) else (LANA if y == 1 else (ROJO if y == 2 else None)))))
    for lado in ("der", "izq"):
        s.cara("sombrero", lado, lambda x, y, W, H: ((LANA if y % 2 else CAFE) if (y < 3 or (2 <= x <= 5 and y < 7)) else None))
    # poncho de lana de alpaca con franjas y figuras de llama
    def poncho(x, y):
        if y in (2, 10):
            return ROJO
        if y in (3, 9):
            return VERDE
        if 5 <= y <= 7 and (x % 4) in (1, 2) and not (y == 7 and x % 4 == 2):
            return CAFE
        return LANA if y < 11 else LANA_S
    s.todo("cuerpo", poncho, lambda x, y: LANA)
    brazos(s, LANA, 6, ROJO)
    piernas(s, CAFE, MARRON_S, banda=LANA_S)
    return s


# ------------------------------------------------------------------ Kallawaya (curandero andino)
def kallawaya():
    s = Skin()
    cabeza_base(s)
    FIELTRO = hexc("4a3222"); FIELTRO_S = hexc("33221a"); VINO = hexc("7a1e2c"); VINO_S = hexc("5a1420")
    # sombrero de fieltro de ala ancha con cinta de colores (capa exterior de la cabeza)
    s.todo("sombrero", lambda x, y: (FIELTRO if y < 2 else (ROJO if y == 2 and x % 4 < 2 else (AMARILLO if y == 2 else (FIELTRO_S if y == 3 else None)))),
           lambda x, y: FIELTRO)
    # poncho color vino con franjas finas y, cruzando el pecho, la correa de su chuspa de hierbas
    def poncho(x, y):
        if (x + y) in (4, 5) and x < 8:
            return CUERO
        if y in (3, 8):
            return AMARILLO
        if y in (4, 9):
            return VERDE
        return VINO if y < 11 else VINO_S
    s.todo("cuerpo", poncho, lambda x, y: VINO)
    # chuspa de colores colgando a un costado (capa exterior del cuerpo)
    s.cara("chaqueta", "frente", lambda x, y, W, H: ([ROJO, AMARILLO, VERDE, TURQ][(x + y) % 4] if (5 <= x <= 7 and 7 <= y <= 10) else None))
    brazos(s, VINO, 7, AMARILLO)
    piernas(s, NEGRO, MARRON_S, banda=VINO_S)
    return s


SKINS = {"kallawaya": kallawaya, "criador": criador, "banquero": banquero, "tendero": tendero, "herrero": herrero, "mercader": mercader,
         "bromista": bromista, "contrabandista": contrabandista}
for nombre, fn in SKINS.items():
    fn().save(nombre)


# ------------------------------------------------------------------ vista previa (frente y espalda)
def vista(img, atras=False, escala=8):
    """Arma la figura plana (cabeza 8, cuerpo 12, piernas 12) en vista frontal o trasera."""
    c = "atras" if atras else "frente"
    W, H = 16, 32
    lienzo = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def pega(parte, cara, x, y):
        x0, y0, w, h = caras(parte)[cara]
        lienzo.alpha_composite(img.crop((x0, y0, x0 + w, y0 + h)), (x, y))

    capas = [("cabeza", "sombrero", 4, 0), ("cuerpo", "chaqueta", 4, 8)]
    if not atras:
        capas += [("brazo_d", "manga_d", 0, 8), ("brazo_i", "manga_i", 12, 8),
                  ("pierna_d", "pantalon_d", 4, 20), ("pierna_i", "pantalon_i", 8, 20)]
    else:
        capas += [("brazo_i", "manga_i", 0, 8), ("brazo_d", "manga_d", 12, 8),
                  ("pierna_i", "pantalon_i", 4, 20), ("pierna_d", "pantalon_d", 8, 20)]
    for base, capa, x, y in capas:
        pega(base, c, x, y)
        pega(capa, c, x, y)
    return lienzo.resize((W * escala, H * escala), Image.NEAREST)


E = 8
hoja = Image.new("RGBA", (len(SKINS) * (2 * 16 * E + 40) + 20, 32 * E + 70), (235, 228, 210, 255))
d = ImageDraw.Draw(hoja)
try:
    fuente = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
except OSError:
    fuente = ImageFont.load_default()
titulos = {"kallawaya": "Kallawaya", "criador": "Criador", "banquero": "Banquero", "tendero": "Tendero", "herrero": "Herrero", "mercader": "Mercader legendario",
           "bromista": "Bromas", "contrabandista": "Mercado negro"}
x = 20
for nombre in SKINS:
    img = Image.open(os.path.join(OUT, f"{nombre}.png")).convert("RGBA")
    hoja.alpha_composite(vista(img, False, E), (x, 50))
    hoja.alpha_composite(vista(img, True, E), (x + 16 * E + 10, 50))
    d.text((x, 12), titulos[nombre], fill=(60, 40, 20), font=fuente)
    x += 2 * 16 * E + 40
hoja.convert("RGB").save(os.path.join(OUT, "vista_previa_skins.png"))
print("ok", os.listdir(OUT))
