"""Texturas de las monturas propias (sillas) de la mula, el caballo zombi y el caballo esqueleto.

La silla vanilla (64x64) es la misma para todos los equinos y solo se usa como plantilla de forma.
El asiento es una caja de 10x9x9 con UV desde (26, 0): tapa en x 35..44 / y 0..8 y costados en
y 9..17 (este 26..34, frente 35..44, oeste 45..53, atrás 54..63). Pintando los costados la silla
se vuelve una manta que cuelga a los lados. Uso:
    python generar_monturas.py <saddle.png vanilla> <salida_resourcepack>
La textura vanilla sale de assets/minecraft/textures/entity/equipment/horse_saddle/saddle.png.
"""
import json
import os
import sys

from PIL import Image

SILLA_VANILLA, PACK = sys.argv[1:3]
NS = os.path.join(PACK, "assets", "banco_minecraft_per")


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


ORO = hexc("f4c430"); ORO_S = hexc("c8961e"); NEGRO = hexc("1e1a1e")

TEMAS = {
    # mula: aguayo andino de franjas de colores, con pompones
    "aguayo_de_carga": dict(
        entidad="mule",
        franjas=[hexc(c) for c in ("c2185b", "ff7a1a", "ffd23f", "2e9e4f", "d7263d", "1f4e9e")],
        fondo=hexc("8e1b3a"), linea=NEGRO, cuero=hexc("7a3b1d"), cuero_s=hexc("5a2a14"),
        metal=ORO, flecos=[hexc(c) for c in ("ff3fa4", "ffd23f", "3fd0ff", "7bd23f")],
        caida=8, multicolor=[hexc(c) for c in ("ff3fa4", "ffd23f", "2e9e4f", "3fd0ff", "ff7a1a")],
    ),
    # caballo zombi: olas del lago Titicaca, azul y turquesa con oro
    "montura_titicaca": dict(
        entidad="zombie_horse",
        franjas=[hexc(c) for c in ("2fb5a8", "1f3f7a", "57d9c9", "16305e")],
        fondo=hexc("1f3f7a"), linea=ORO, cuero=hexc("16305e"), cuero_s=hexc("0f2244"),
        metal=ORO, flecos=[ORO, ORO_S],
    ),
    # caballo esqueleto: mundo de abajo, negro y morado con chakana de hueso
    "montura_ukhu_pacha": dict(
        entidad="skeleton_horse",
        franjas=[hexc(c) for c in ("4b1d6b", "2a0f3d", "6d2f99", "2a0f3d")],
        fondo=hexc("2a0f3d"), linea=hexc("e8e2cf"), cuero=hexc("2a2230"), cuero_s=hexc("17121b"),
        metal=hexc("c9c9d6"), flecos=[hexc("6d2f99"), hexc("e8e2cf")],
    ),
}

CAIDA = 6  # filas de manta en los costados (las de abajo quedan con la correa vanilla)


def chakana(x, y):
    """Cruz andina escalonada sobre la tapa (x 36..43, y 1..7)."""
    return x in (39, 40) or (y == 4 and 36 <= x <= 43) or (38 <= x <= 41 and 3 <= y <= 5)


def textura(t):
    base = Image.open(SILLA_VANILLA).convert("RGBA")
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    a = base.getchannel("A")
    franjas = t["franjas"]
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            tapa = 35 <= x <= 44 and y <= 8
            caida = t.get("caida", CAIDA)
            costado = 26 <= x <= 63 and 9 <= y < 9 + caida + 1
            if tapa:
                # tapa: borde de línea y en el centro el diseño del tema
                if x in (35, 44) or y in (0, 8):
                    c = t["linea"]
                elif t["entidad"] == "skeleton_horse":
                    c = t["linea"] if chakana(x, y) else t["fondo"]
                elif t["entidad"] == "mule":
                    # tapa del aguayo: rombo grande concéntrico de colores
                    d = abs(x - 39.5) + abs(y - 4)
                    c = [franjas[2], franjas[0], franjas[3], franjas[1], t["fondo"]][min(4, int(d // 1.5))]
                else:
                    c = franjas[(y - 1) % len(franjas)]
                out.putpixel((x, y), c)
            elif costado:
                fila = y - 9
                if fila == caida:  # flecos / pompones
                    if t["entidad"] == "mule":
                        out.putpixel((x, y), t["flecos"][x % len(t["flecos"])])
                    elif x % 2 == 0:
                        out.putpixel((x, y), t["flecos"][(x // 2) % len(t["flecos"])])
                elif fila == 0:
                    out.putpixel((x, y), t["linea"])
                elif t["entidad"] == "zombie_horse":
                    # olas: cresta clara que sube y baja sobre agua turquesa y fondo azul
                    cresta = (1, 2, 3, 2)[x % 4]
                    c = franjas[1] if fila < cresta else franjas[2] if fila == cresta else franjas[0]
                    out.putpixel((x, y), c)
                elif t["entidad"] == "skeleton_horse":
                    # escalones (grecas) de hueso sobre morado
                    paso = (x // 3 + fila) % 4
                    out.putpixel((x, y), t["linea"] if paso == 0 and fila in (2, 3, 4) else franjas[fila % len(franjas)])
                else:
                    # aguayo: franjas finas y dos bandas anchas con rombos de colores
                    if fila in (1, 5, 7):
                        c = franjas[{1: 0, 5: 3, 7: 5}[fila]]
                        if fila == 5 and x % 3 == 0:
                            c = franjas[2]
                    elif 2 <= fila <= 4:
                        m = x % 6
                        if fila == 3:
                            c = franjas[3] if m in (2, 3) else franjas[2] if m in (1, 4) else franjas[4]
                        else:
                            c = franjas[2] if m in (2, 3) else franjas[4]
                    else:  # fila 6
                        c = franjas[4] if (x % 4) in (1, 2) else franjas[1]
                    out.putpixel((x, y), c)
            elif a.getpixel((x, y)) > 0:
                # correas, riendas, cabezada y estribos: cuero del tema y metal
                r, g, b, _ = base.getpixel((x, y))
                gris = abs(r - g) < 18 and abs(g - b) < 18
                if gris:
                    out.putpixel((x, y), t["metal"])
                elif "multicolor" in t:
                    # cabezada y riendas tejidas de colores, como las de las llamas de fiesta
                    out.putpixel((x, y), t["multicolor"][(x + y) % len(t["multicolor"])])
                else:
                    out.putpixel((x, y), t["cuero"] if (r + g + b) > 180 else t["cuero_s"])
    return out


def main():
    for asset, t in TEMAS.items():
        capa = f"{t['entidad']}_saddle"
        destino = os.path.join(NS, "textures", "entity", "equipment", capa)
        os.makedirs(destino, exist_ok=True)
        textura(t).save(os.path.join(destino, f"{asset}.png"))
        os.makedirs(os.path.join(NS, "equipment"), exist_ok=True)
        with open(os.path.join(NS, "equipment", f"{asset}.json"), "w", encoding="utf-8") as fh:
            json.dump({"layers": {capa: [{"texture": f"banco_minecraft_per:{asset}"}]}}, fh, indent=2)
            fh.write("\n")
        print("montura", asset, "->", capa)


main()
