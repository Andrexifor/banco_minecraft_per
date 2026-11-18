"""
Genera las texturas 16x16 de billetes, monedas y bonos del resource pack.
Son texturas de partida: puedes redibujarlas a mano (mismo nombre de archivo).

Uso:  python scripts/generar_texturas.py
Requiere: pip install pillow
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "banco_minecraft_per"
NS = "banco_minecraft_per"

# Fuente de píxeles 3x5 para los dígitos
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
}

# Colores inspirados en los billetes reales del Perú
BILLETES = {
    10: ((96, 160, 80), (40, 90, 40)),     # verde
    20: ((200, 140, 70), (120, 70, 30)),   # naranja/marrón
    50: ((210, 90, 80), (120, 40, 40)),    # rojo
    100: ((80, 120, 200), (30, 50, 120)),  # azul
    200: ((200, 110, 180), (110, 50, 100)),  # rosado
}
BONOS = {
    500: ((60, 60, 70), (230, 190, 70)),
    1000: ((40, 40, 50), (240, 200, 80)),
    5000: ((30, 20, 50), (250, 210, 90)),
    10000: ((15, 10, 30), (255, 220, 100)),
}
MONEDAS = {1: (200, 200, 205), 2: (215, 180, 90), 5: (225, 185, 70)}


def texto(valor: int) -> str:
    if valor >= 1000:
        return f"{valor // 1000}k"
    return str(valor)


def dibujar_texto(img: Image.Image, s: str, color, centro_y: int = 8):
    ancho = len(s) * 4 - 1
    x0 = (16 - ancho) // 2
    y0 = centro_y - 2
    for i, ch in enumerate(s):
        patron = DIGITOS[ch]
        for fy, fila in enumerate(patron):
            for fx, bit in enumerate(fila):
                if bit == "1":
                    img.putpixel((x0 + i * 4 + fx, y0 + fy), color + (255,))


def billete(valor: int, fondo, tinta) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 3, 15, 12], fill=fondo + (255,), outline=tinta + (255,))
    dibujar_texto(img, texto(valor), tinta)
    return img


def bono(valor: int, fondo, oro) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 2, 15, 13], fill=fondo + (255,), outline=oro + (255,))
    d.rectangle([1, 3, 14, 12], outline=oro + (255,))
    dibujar_texto(img, texto(valor), oro)
    return img


def moneda(valor: int, color) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    borde = tuple(max(0, c - 70) for c in color)
    d.ellipse([2, 2, 13, 13], fill=color + (255,), outline=borde + (255,))
    dibujar_texto(img, texto(valor), borde)
    return img


def kit(color_caja, color_simbolo, simbolo: str) -> Image.Image:
    """Caja de herramientas 16x16 con un símbolo: A (armadura), E (espada), H (herramienta)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    oscuro = tuple(max(0, c - 70) for c in color_caja)
    d.rectangle([5, 2, 10, 4], outline=oscuro + (255,))          # asa
    d.rectangle([1, 4, 14, 13], fill=color_caja + (255,), outline=oscuro + (255,))
    d.line([1, 7, 14, 7], fill=oscuro + (255,))
    patrones = {
        "A": ["010", "101", "111", "101", "101"],
        "E": ["001", "010", "100", "110", "010"],
        "H": ["111", "010", "010", "010", "010"],
    }
    for fy, fila in enumerate(patrones[simbolo]):
        for fx, bit in enumerate(fila):
            if bit == "1":
                img.putpixel((7 + fx - 1, 8 + fy), color_simbolo + (255,))
    return img


def escribir_json(ruta: Path, datos: dict):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, indent=2) + "\n", encoding="utf-8")


def registrar(nombre: str, img: Image.Image):
    tex = RAIZ / "textures" / "item" / f"{nombre}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex)
    # Modelo plano tipo ítem
    escribir_json(RAIZ / "models" / "item" / f"{nombre}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/{nombre}"},
    })
    # Definición del ítem (lo que referencia el componente item_model)
    escribir_json(RAIZ / "items" / f"{nombre}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/{nombre}"},
    })


def main():
    for v, c in MONEDAS.items():
        registrar(f"moneda_{v}", moneda(v, c))
    for v, (f, t) in BILLETES.items():
        registrar(f"billete_{v}", billete(v, f, t))
    for v, (f, o) in BONOS.items():
        registrar(f"bono_{v}", bono(v, f, o))
    registrar("kit_armadura", kit((70, 110, 170), (230, 230, 240), "A"))
    registrar("kit_armas", kit((170, 60, 60), (240, 230, 230), "E"))
    registrar("kit_herramientas", kit((90, 140, 70), (240, 240, 230), "H"))
    print("Texturas generadas en", RAIZ)


if __name__ == "__main__":
    main()
