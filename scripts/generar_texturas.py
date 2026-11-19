"""
Genera las texturas 32x32 de monedas, billetes, bonos y kits del resource pack.
Inspiradas en los colores y formas de los soles peruanos (sin copiar diseños reales:
nada de retratos, escudos ni firmas). Puedes redibujarlas a mano con el mismo nombre.

Uso:  python scripts/generar_texturas.py
Requiere: pip install pillow
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "banco_minecraft_per"
NS = "banco_minecraft_per"
T = 32  # tamaño de la textura

# Fuente de píxeles 3x5
DIGITOS = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
    "k": ["101", "101", "110", "101", "101"],
    "S": ["111", "100", "111", "001", "111"],
    "/": ["001", "001", "010", "100", "100"],
}


def rgba(c, a=255):
    return tuple(c) + (a,)


def mezclar(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def aclarar(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def texto(img, s, x, y, color, escala=1):
    """Dibuja s con la fuente 3x5 desde (x, y). Devuelve el ancho dibujado."""
    cx = x
    for ch in s:
        patron = DIGITOS[ch]
        for fy, fila in enumerate(patron):
            for fx, bit in enumerate(fila):
                if bit == "1":
                    for ex in range(escala):
                        for ey in range(escala):
                            img.putpixel((cx + fx * escala + ex, y + fy * escala + ey), rgba(color))
        cx += 4 * escala
    return cx - x - escala


def ancho(s, escala=1):
    return len(s) * 4 * escala - escala


def poligono(cx, cy, r, lados, giro=0.0):
    return [
        (cx + r * math.cos(giro + 2 * math.pi * i / lados), cy + r * math.sin(giro + 2 * math.pi * i / lados))
        for i in range(lados)
    ]


# ---------------- monedas ----------------

PLATA = (198, 202, 208)
PLATA_OSCURA = (120, 126, 136)
ORO = (222, 182, 82)
ORO_OSCURO = (150, 112, 40)


def moneda_1():
    """Plata, forma de decágono, "1" grande a la izquierda y rayas verticales a la derecha."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon(poligono(16, 16, 14.5, 10, math.pi / 10), fill=rgba(PLATA_OSCURA))
    d.polygon(poligono(16, 16, 13, 10, math.pi / 10), fill=rgba(PLATA))
    # brillo
    d.polygon(poligono(14, 13, 6, 10, math.pi / 10), fill=rgba(aclarar(PLATA, 18)))
    # rayas verticales (lado derecho)
    for x in range(19, 25, 2):
        d.line([x, 9, x, 23], fill=rgba(PLATA_OSCURA))
    texto(img, "1", 9, 11, PLATA_OSCURA, escala=2)
    return img


def moneda_bimetalica(valor: int, r_total: float, color_anillo, color_centro):
    """Anillo exterior de un metal y centro de otro, con el número en el centro."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    oscuro_anillo = aclarar(color_anillo, -70)
    oscuro_centro = aclarar(color_centro, -80)
    d.polygon(poligono(16, 16, r_total, 10, math.pi / 10), fill=rgba(oscuro_anillo))
    d.polygon(poligono(16, 16, r_total - 1.5, 10, math.pi / 10), fill=rgba(color_anillo))
    rc = int(r_total) - 5
    d.ellipse([16 - rc - 1, 16 - rc - 1, 16 + rc + 1, 16 + rc + 1], fill=rgba(oscuro_centro))
    d.ellipse([16 - rc, 16 - rc, 16 + rc, 16 + rc], fill=rgba(color_centro))
    d.ellipse([11, 10, 17, 15], fill=rgba(aclarar(color_centro, 20)))  # brillo
    s = str(valor)
    texto(img, s, 16 - ancho(s, 2) // 2, 11, oscuro_centro, escala=2)
    return img


# ---------------- billetes ----------------

# (fondo, tinta) inspirados en la serie actual del BCRP
BILLETES = {
    10: ((150, 205, 120), (40, 110, 60)),     # verde
    20: ((230, 175, 110), (140, 80, 30)),     # naranja / marrón
    50: ((235, 130, 140), (150, 40, 60)),     # rosado / rojo
    100: ((120, 160, 225), (30, 60, 140)),    # azul
    200: ((200, 150, 215), (100, 50, 130)),   # lila / morado
}


def billete(valor: int, fondo, tinta):
    """
    Billete apaisado: franja de marca de agua clara a la izquierda, silueta de retrato
    (sin rostro) al centro, patrón geométrico, número grande arriba a la derecha.
    """
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    y0, y1 = 7, 24
    claro = aclarar(fondo, 40)
    # cuerpo con degradado horizontal
    for x in range(1, 31):
        c = mezclar(claro, fondo, x / 30)
        d.line([x, y0, x, y1], fill=rgba(c))
    d.rectangle([0, y0 - 1, 31, y1 + 1], outline=rgba(tinta))
    # franja de marca de agua
    d.rectangle([2, y0 + 1, 7, y1 - 1], fill=rgba(aclarar(fondo, 70)))
    d.line([4, y0 + 2, 4, y1 - 2], fill=rgba(aclarar(tinta, 60)))
    # silueta de retrato (genérica, sin rostro)
    piel = mezclar(fondo, (90, 80, 80), 0.55)
    d.ellipse([11, y0 + 4, 17, y0 + 10], fill=rgba(piel))
    d.rectangle([10, y0 + 10, 18, y1 - 1], fill=rgba(piel))
    # patrón geométrico (rombos) abajo a la derecha
    for i, xx in enumerate(range(21, 30, 3)):
        for j, yy in enumerate(range(y0 + 9, y1 - 2, 3)):
            if (i + j) % 2 == 0:
                d.point([(xx, yy), (xx + 1, yy + 1), (xx - 1, yy + 1), (xx, yy + 2)], fill=rgba(aclarar(tinta, 40)))
    # número grande arriba a la derecha
    s = str(valor)
    texto(img, s, 30 - ancho(s), y0 + 2, tinta)
    return img


# ---------------- bonos de reserva ----------------

BONOS = {
    500: ((45, 45, 55), (225, 185, 70)),
    1000: ((35, 30, 50), (235, 195, 80)),
    5000: ((30, 20, 55), (245, 205, 90)),
    10000: ((20, 12, 35), (255, 220, 105)),
}


def bono(valor: int, fondo, oro):
    """Diseño propio (no existe en la vida real): fondo oscuro, doble marco dorado y sello."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    y0, y1 = 6, 25
    d.rectangle([0, y0, 31, y1], fill=rgba(fondo), outline=rgba(oro))
    d.rectangle([2, y0 + 2, 29, y1 - 2], outline=rgba(aclarar(oro, -60)))
    # sello circular
    d.ellipse([4, y0 + 5, 13, y1 - 5], outline=rgba(oro))
    d.point([(8, y0 + 9), (9, y0 + 9), (8, y0 + 10), (9, y0 + 10)], fill=rgba(oro))
    s = f"{valor // 1000}k" if valor >= 1000 else str(valor)
    texto(img, s, 28 - ancho(s), y0 + 4, oro)
    texto(img, "S/", 17, y1 - 8, aclarar(oro, -40))
    return img


# ---------------- kits ----------------

def kit(color_caja, color_simbolo, simbolo: str):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    oscuro = aclarar(color_caja, -70)
    d.rectangle([11, 5, 20, 9], outline=rgba(oscuro), width=2)        # asa
    d.rectangle([3, 9, 28, 27], fill=rgba(color_caja), outline=rgba(oscuro))
    d.line([3, 15, 28, 15], fill=rgba(oscuro))
    d.rectangle([14, 13, 17, 17], fill=rgba((200, 200, 200)))         # cierre
    if simbolo == "armadura":   # peto
        d.polygon([(11, 18), (20, 18), (19, 25), (12, 25)], fill=rgba(color_simbolo))
        d.rectangle([9, 18, 11, 21], fill=rgba(color_simbolo))
        d.rectangle([20, 18, 22, 21], fill=rgba(color_simbolo))
    elif simbolo == "armas":    # espada en diagonal
        for i in range(8):
            d.point((10 + i, 25 - i), fill=rgba(color_simbolo))
            d.point((11 + i, 25 - i), fill=rgba(color_simbolo))
        d.line([9, 22, 13, 26], fill=rgba(aclarar(color_simbolo, -60)))
    else:                        # martillo
        d.rectangle([11, 18, 20, 20], fill=rgba(color_simbolo))
        d.rectangle([15, 20, 16, 26], fill=rgba(aclarar(color_simbolo, -40)))
    return img


# ---------------- salida ----------------

def escribir_json(ruta: Path, datos: dict):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, indent=2) + "\n", encoding="utf-8")


def registrar(nombre: str, img: Image.Image):
    tex = RAIZ / "textures" / "item" / f"{nombre}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex)
    escribir_json(RAIZ / "models" / "item" / f"{nombre}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/{nombre}"},
    })
    escribir_json(RAIZ / "items" / f"{nombre}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/{nombre}"},
    })


def main():
    registrar("moneda_1", moneda_1())
    registrar("moneda_2", moneda_bimetalica(2, 12.0, PLATA, ORO))
    registrar("moneda_5", moneda_bimetalica(5, 14.5, PLATA, ORO))
    for v, (f, t) in BILLETES.items():
        registrar(f"billete_{v}", billete(v, f, t))
    for v, (f, o) in BONOS.items():
        registrar(f"bono_{v}", bono(v, f, o))
    registrar("kit_armadura", kit((70, 110, 170), (225, 225, 235), "armadura"))
    registrar("kit_armas", kit((170, 60, 60), (230, 230, 235), "armas"))
    registrar("kit_herramientas", kit((90, 140, 70), (225, 225, 230), "herramientas"))
    print("Texturas generadas en", RAIZ)


if __name__ == "__main__":
    main()
