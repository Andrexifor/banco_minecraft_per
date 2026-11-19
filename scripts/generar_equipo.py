"""Texturas del equipo especial (arnés del Puyu del Inti y armadura inca de caballo).

Usa las texturas vanilla SOLO como plantilla de forma (qué píxeles existen en el modelo);
los colores y diseños son propios. Uso:
    python generar_equipo.py <yellow_harness.png> <horse_white.png> <salida_resourcepack>
Las texturas vanilla salen del jar del cliente (no se incluyen en el repo).
"""
import math
import os
import sys

from PIL import Image

ARNES_VANILLA, CABALLO_VANILLA, PACK = sys.argv[1:4]
NS_TEX = os.path.join(PACK, "assets", "banco_minecraft_per", "textures", "entity", "equipment")


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


ORO = hexc("f4c430"); ORO_S = hexc("c8961e"); ORO_L = hexc("ffe27a")
ROJO = hexc("b3261e"); ROJO_S = hexc("861a14"); NEGRO = hexc("221c1c"); BLANCO = hexc("ece6d6")
TURQ = hexc("2fb5a8"); AZUL = hexc("1f3f7a"); AZUL_S = hexc("16305e")


def tocapu(x, y):
    """Cuadros escalonados de 2x2 rojo / negro / blanco con turquesa."""
    cx, cy = (x // 2) % 4, (y // 2) % 2
    return [ROJO, NEGRO, BLANCO, TURQ][(cx + cy * 2) % 4]


# ---------------------------------------------------------------- arnés del Puyu del Inti (128x128)
def arnes():
    base = Image.open(ARNES_VANILLA).convert("RGBA")
    W, H = base.size
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    a = base.getchannel("A")
    usado = lambda x, y: a.getpixel((x, y)) > 0
    for y in range(H):
        for x in range(W):
            if y < 32 and 32 <= x < 64:
                # tapa superior: sol de oro sobre azul
                cx, cy = x - 48 + 0.5, y - 16 + 0.5
                r = math.hypot(cx, cy)
                ang = (math.degrees(math.atan2(cy, cx)) + 360) % 45
                if r < 7:
                    c = ORO_L if r < 3 else ORO
                elif r < 13 and ang < 12:
                    c = ORO_S
                elif x in (32, 63) or y in (0, 31):
                    c = ORO
                else:
                    c = AZUL if (x + y) % 6 else AZUL_S
                out.putpixel((x, y), c)
            elif 32 <= y < 56 and (usado(x, y) or (32 <= x < 64 and y < 37)):
                # costados (y una franja dorada sobre la cara, que queda libre)
                fila = y - 32
                if fila in (0, 1):
                    c = ORO
                elif fila == 2 or fila == 9:
                    c = ORO_S
                elif 3 <= fila <= 8:
                    c = tocapu(x, fila)
                elif fila >= 20:
                    c = ORO if (x % 4) < 2 else ORO_S  # flecos dorados
                else:
                    c = ROJO if (x // 3) % 2 else ROJO_S
                out.putpixel((x, y), c)
            elif usado(x, y):
                # lentes y demás piezas: lente turquesa con marco de oro
                r, g, b, _ = base.getpixel((x, y))
                es_lente = b > 150 and g > 150 and r < 210
                out.putpixel((x, y), TURQ if es_lente else ORO_S if (r + g + b) < 200 else ORO)
    destino = os.path.join(NS_TEX, "happy_ghast_body")
    os.makedirs(destino, exist_ok=True)
    out.save(os.path.join(destino, "puyu_inti.png"))
    return out


# ---------------------------------------------------------------- armadura inca de caballo (64x64)
def armadura():
    base = Image.open(CABALLO_VANILLA).convert("RGBA")
    W, H = base.size
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for y in range(H):
        for x in range(W):
            r, g, b, al = base.getpixel((x, y))
            if al == 0:
                continue
            if r + g + b < 160:
                c = NEGRO  # ojos y fosas nasales del caballo: se mantienen oscuros
            elif y < 32:
                # cabeza, cuello y orejas: tocado de oro con turquesas
                c = ORO if (x + y) % 5 else ORO_S
                if (x * 7 + y * 3) % 23 == 0:
                    c = TURQ
            else:
                # cuerpo y patas: manto inca rojo con franjas de oro y tocapus
                fila = (y - 32) % 12
                if fila in (0, 1):
                    c = ORO
                elif fila == 2:
                    c = ORO_S
                elif fila in (5, 6, 7):
                    c = tocapu(x, fila)
                else:
                    c = ROJO if (x // 4 + y // 4) % 2 else ROJO_S
            out.putpixel((x, y), c)
    destino = os.path.join(NS_TEX, "horse_body")
    os.makedirs(destino, exist_ok=True)
    out.save(os.path.join(destino, "armadura_inca.png"))
    return out


a = arnes()
b = armadura()
# hojas de vista previa (no van al pack)
prev = os.path.join(PACK, "..", "vista_previa_equipo.png")
hoja = Image.new("RGBA", (128 * 4 + 64 * 8 + 30, 128 * 4), (235, 228, 210, 255))
hoja.alpha_composite(a.resize((512, 512), Image.NEAREST), (0, 0))
hoja.alpha_composite(b.resize((512, 512), Image.NEAREST), (542, 0))
hoja.convert("RGB").save(prev)
print("ok", prev)
