"""Skins de los NPC nuevos de la v0.7.5 (Kuraka y Llamero viajero), con los mismos ayudantes de
generar_skins.py (se cargan de ese archivo sin correr su parte final).
Uso: python generar_skins_v075b.py <carpeta_salida>
"""
import os
import sys

_fuente = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "generar_skins.py"), encoding="utf-8").read()
exec(_fuente[:_fuente.index("\nSKINS = {")])  # Skin, cabeza_base, brazos, piernas, tocapu, colores...


# ------------------------------------------------------------------ Kuraka (jefe del ayllu, reparte encargos)
def kuraka():
    s = Skin()
    cabeza_base(s)
    # llawt'u de colores y orejeras de oro (los "orejones" de la nobleza inca)
    s.todo("sombrero", lambda x, y: ([ROJO, AMARILLO, VERDE, AZUL][x % 4] if y == 1 else None), None)
    for lado in ("der", "izq"):
        s.cara("sombrero", lado, lambda x, y, W, H: (ORO if (3 <= x <= 4 and 4 <= y <= 5) else ([ROJO, AMARILLO, VERDE, AZUL][x % 4] if y == 1 else None)))
    # unku granate con banda de tocapus en la cintura y cuello de oro
    GRANATE = hexc("6e1a2a"); GRANATE_S = hexc("521320")

    def unku(x, y):
        if y == 0:
            return ORO
        if 6 <= y <= 8:
            return tocapu(x, y, ORO, NEGRO, BLANCO)
        return GRANATE if y < 11 else GRANATE_S
    s.todo("cuerpo", unku, lambda x, y: GRANATE)
    # quipu colgando del cinturón (capa exterior)
    s.cara("chaqueta", "frente", lambda x, y, W, H: ([ROJO, AMARILLO, BLANCO, VERDE][x % 4] if (1 <= x <= 3 and 8 <= y <= 11 and (x + y) % 2 == 0) else None))
    brazos(s, GRANATE, 5, ORO)
    piernas(s, GRANATE_S, MARRON_S, banda=ORO_S)
    return s


# ------------------------------------------------------------------ Llamero viajero (cuenta el rumor del Apu)
def llamero():
    s = Skin()
    cabeza_base(s)
    CH = [ROJO, BLANCO, NARANJA, AZUL]
    # chullo de colores con orejeras (capa exterior de la cabeza)
    s.todo("sombrero", lambda x, y: (CH[(x // 2 + y) % 4] if y < 3 else None), lambda x, y: CH[(x + y) % 4])
    for lado in ("der", "izq"):
        s.cara("sombrero", lado, lambda x, y, W, H: (CH[(x + y) % 4] if (y < 3 or (2 <= x <= 5 and y < 6)) else None))

    # poncho de lana marrón con franjas claras y un q'epi (atado) a la espalda
    def poncho(x, y):
        if y in (2, 7):
            return BEIGE
        if y == 3:
            return ROJO
        return MARRON if y < 11 else MARRON_S
    s.todo("cuerpo", poncho, lambda x, y: MARRON)
    s.cara("chaqueta", "atras", lambda x, y, W, H: ([ROJO, AMARILLO, VERDE][(x + y) % 3] if (1 <= x <= 6 and 2 <= y <= 8) else None))
    brazos(s, MARRON, 8, BEIGE)
    piernas(s, NEGRO, CUERO, banda=BEIGE)
    return s


for _nombre, _fn in (("kuraka", kuraka), ("llamero", llamero)):
    _fn().save(_nombre)
print("ok", OUT)
