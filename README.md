# Banco Minecraft PER

Mod de economía en **soles** para servidores Fabric (Minecraft 26.3; la 26.2 está en la rama `mc/26.2`). Funciona
**solo en el servidor**: los jugadores entran con Minecraft vanilla y solo aceptan
el resource pack que el servidor les ofrece al conectarse.

> Fases 1-4 de 6 (ver `diseno-banco_minecraft_per.md` en el proyecto): banco + efectivo,
> menús con NPC (Banquero, Tendero, Herrero, Mercader legendario), tienda con impuesto,
> ítems legendarios, kits de reparación y regla de Reparación (Mending).
> Desde la v0.3.0 reemplaza a Viper Economy.

## Qué hace

- Cada jugador tiene una **cuenta** con saldo (empieza con S/ 500, configurable).
- El saldo se puede **retirar** como efectivo físico y volver a **depositar**.
- El efectivo son ítems vanilla con una marca interna (`custom_data`) que no se
  puede falsificar en supervivencia, y una textura propia (`item_model`).

| Valor | Tipo | Ítem base |
|---|---|---|
| S/ 1, 2, 5 | Monedas | pepita de oro |
| S/ 10, 20, 50, 100, 200 | Billetes | papel |
| S/ 500, 1,000, 5,000, 10,000 | Bonos de reserva (brillan) | papel |

## Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `/banco` | todos | Ayuda |
| `/saldo` | todos | Tu saldo |
| `/saldo <jugador>` | `banco_minecraft_per.saldo.otros` | Saldo de otro |
| `/retirar <monto>` | todos | Saca efectivo con desglose automático (87 → 50+20+10+5+2) |
| `/depositar` · `/depositar mano` | todos | Deposita todo el efectivo del inventario, o solo el de la mano |
| `/pagar <jugador> <monto>` | todos | Transferencia (con impuesto opcional) |
| `/tienda` | todos | Abre la tienda del servidor |
| `/vender mano` · `/vender todo` | todos | Vende a la tienda lo de la mano, o todo lo vendible del inventario |
| `/banco top` | todos | Ranking de saldos |
| `/banco dar\|quitar\|fijar <jugador> <monto>` | `banco_minecraft_per.admin` | Administración |
| `/banco crear <denominacion> <cantidad>` | `banco_minecraft_per.admin` | Crear efectivo (pruebas) |
| `/banco npc crear banquero\|tendero\|herrero\|mercader` | `banco_minecraft_per.admin` | Crea el aldeano donde estás parado; luego **clic derecho** sobre él para activarlo |
| `/banco npc asignar <tipo>` | `banco_minecraft_per.admin` | Convierte en NPC al próximo aldeano que toques |
| `/banco npc quitar` | `banco_minecraft_per.admin` | Elimina el próximo NPC del banco que toques |
| `/banco recargar` | `banco_minecraft_per.admin` | Recargar configuración y tienda |

## NPC y menús

Los NPC son aldeanos sin IA, invulnerables y persistentes. Clic derecho abre su menú
(menús tipo cofre que funcionan con Minecraft vanilla, gracias a SGUI):

- **Banquero:** saldo, depositar todo / lo de la mano, ordenar efectivo,
  cambiar a sencillo (100 → 50 + 50) y retirar S/ 10 · 50 · 100 · 500 · 1,000 · 5,000 · 10,000.
- **Tendero:** la tienda del servidor (igual que `/tienda`).
- **Herrero:** sobre el ítem de la mano principal:
  - **Reparar:** precio según el % de daño (×3 si es legendario).
  - **Combinar libro:** libro encantado en la mano secundaria; cobra soles en vez de
    niveles de XP y sin "¡Demasiado caro!". Muestra qué se aplicará y qué no antes de cobrar.
  - **Renombrar:** tarifa fija, con el menú del yunque.
  - **Kits de reparación** (armadura, armas, herramientas; 1 o 3 usos).
- **Mercader legendario:** ítems con encantamientos por encima del máximo vanilla
  (Afilado VI, Poder VI, Protección V…). Pide confirmación antes de cobrar y anuncia
  la compra en el chat. El **Tridente de Pachacámac** tiene **imán de botín**: lo que suelta
  el enemigo que mata (cuerpo a cuerpo o lanzado) va directo a tu inventario
  (`"iman": true` en `legendarios.json`; se puede activar en cualquier legendario).

## Kits de reparación

Clic derecho para usar. No se gasta el uso si no hay nada que reparar.

| Kit | Repara |
|---|---|
| Armadura | Las 4 piezas puestas + 1 escudo (mano secundaria o el primero del inventario). No repara élitros. |
| Armas | Espada, hacha, arco, ballesta, tridente o maza **en la otra mano** |
| Herramientas | Pico, pala, azada, hacha, tijeras, caña, mechero, escudo **en la otra mano**, o élitros puestos |

## Regla de Reparación (Mending)

Reparación existe para todos, pero **no se puede poner** en un ítem legendario ni en
uno con algún encantamiento por encima del máximo vanilla (Afilado VI, Protección V…).
El yunque deja la casilla de resultado vacía y el Herrero no la ofrece. Esos ítems
solo se mantienen pagando al Herrero o con kits.

## Tienda del servidor

- Categorías y productos en `config/banco_minecraft_per/tienda.json` (se crea con
  precios base la primera vez). Edítalo y usa `/banco recargar`.
- En el menú: **clic izquierdo** compra 1 lote, **shift + izquierdo** compra 8 lotes,
  **clic derecho** vende 1 lote, **shift + derecho** vende todo lo que tengas.
- Las compras se cobran del banco; si no alcanza, se deposita automáticamente el
  efectivo del inventario y se cobra de ahí.
- Solo se compran ítems "limpios" (sin encantar, renombrar ni dañar).
- Impuestos en `banco.json`: `impuestoComprasPorcentaje` (5% por defecto, se suma al
  precio) e `impuestoVentasPorcentaje` (0% por defecto, se descuenta de lo que recibes).

Formato de un producto:

```json
{ "item": "minecraft:diamond", "categoria": "minerales", "compra": 160, "venta": 80, "lote": 1 }
```

`compra` = lo que paga el jugador (0 = no se vende), `venta` = lo que recibe (0 = no se compra),
`lote` = unidades por precio (p. ej. 16 flechas).

Sin LuckPerms, los permisos de admin equivalen a nivel de operador 3.
Con LuckPerms, dáselos al grupo admin:
`lp group admin permission set banco_minecraft_per.admin true`
(el grupo admin del servidor ya tiene `*`).

## Skins de los NPC (maniquíes)

El resource pack trae 4 skins con temática inca en `textures/entity/npc/`
(banquero, tendero, herrero, mercader), generadas con `scripts/generar_skins.py`.
Se usan con maniquíes vanilla, que cualquier cliente ve sin mods:

```
summon minecraft:mannequin ~ ~ ~ {profile:{texture:"banco_minecraft_per:entity/npc/banquero",model:"wide"}}
```

Luego `/banco npc asignar banquero` + clic al maniquí, o sin clic (sirve en funciones):
`/banco npc fijar banquero @e[type=minecraft:mannequin,tag=mi_banquero,limit=1]`. Quien no acepte el resource pack
los ve con textura faltante.

## Zonas protegidas

Dentro de una zona protegida **nadie en supervivencia** puede romper o poner bloques, abrir
cofres, usar cubetas o tocar los soportes de armadura, ni siquiera un op. Para editarla hay que
estar en **modo creativo** y ser op (nivel 2 o más, o tener el permiso `banco_minecraft_per.zonas.editar`). Los demás pueden caminar, hablar con los aldeanos del banco
y usar el portal. Las explosiones (creepers, TNT, ghasts) no rompen bloques ahí.

Se configuran en `config/banco_minecraft_per/zonas.json` (las esquinas, en cualquier orden):

```json
{
  "zonas": [
    { "nombre": "plaza_spawn", "dimension": "overworld", "x1": 76, "y1": -64, "z1": 44,
      "x2": 116, "y2": 320, "z2": 84, "activa": true },
    { "nombre": "sala_portal_nether", "dimension": "the_nether", "x1": 4, "y1": 76, "z1": -1,
      "x2": 56, "y2": 127, "z2": 11, "activa": true }
  ]
}
```

Además, los creepers, la TNT y las bolas de fuego que entran a una zona (o a 4 bloques de ella)
desaparecen antes de explotar, y si igual hay una explosión, no rompe bloques de la zona
(en el log sale "Explosión en zona protegida").

Por defecto protege la plaza inca del spawn y la sala del portal en el Nether (con su túnel).
`/banco zonas` lista las zonas y te dice si estás dentro de una; `/banco recargar` aplica cambios.

## Archivos en el servidor

| Ruta (dentro de `/data`) | Contenido |
|---|---|
| `config/banco_minecraft_per/banco.json` | Configuración: saldo inicial, impuestos, retiro máximo, tamaño del ranking |
| `config/banco_minecraft_per/tienda.json` | Categorías y precios de la tienda |
| `config/banco_minecraft_per/legendarios.json` | Ítems del Mercader: nombre, ítem, encantamientos y precio |
| `config/banco_minecraft_per/herrero.json` | Precios de reparar/combinar/renombrar, kits, multiplicador legendario, tope de niveles, impuesto de servicios |
| `banco_minecraft_per/npcs.json` | NPC creados (UUID → tipo) |
| `banco_minecraft_per/cuentas.json` | Saldos (entra en los backups automáticos) |
| `banco_minecraft_per/transacciones.log` | Registro de cada movimiento: `fecha;tipo;origen;destino;monto;impuesto;detalle` |

## Desarrollo

Requisitos: JDK 25. No hace falta instalar Gradle (usa el wrapper).

```bash
./gradlew build        # compila + tests -> build/libs/banco_minecraft_per-<versión>.jar
./gradlew test         # solo tests
```

Sin JDK instalado, con Docker:

```bash
docker run --rm -v "$PWD":/p -w /p eclipse-temurin:25-jdk ./gradlew build
```

Estructura:

```
src/main/kotlin/io/github/andrexifor/banco/
├── BancoMinecraftPer.kt   # punto de entrada: eventos y carga
├── nucleo/                # lógica pura, sin Minecraft (testeable)
│   ├── Denominacion.kt    # las 12 piezas
│   ├── Desglose.kt        # monto -> piezas (mínimo número de piezas)
│   ├── Banco.kt           # cuentas, transferencias, impuesto
│   ├── Tienda.kt          # catálogo y cálculo de precios
│   ├── Herreria.kt        # legendarios, kits y precios del herrero
│   ├── Configuracion.kt
│   └── Formato.kt
└── juego/                 # todo lo que toca Minecraft
    ├── Efectivo.kt        # crear/reconocer ítems de efectivo, inventario
    ├── Operaciones.kt     # retirar, depositar, pagar, cobrar, abonar (comandos y menús)
    ├── Comandos.kt
    ├── Npcs.kt            # crear/quitar NPC y abrir su menú
    ├── GuiBanquero.kt     # menú del banquero
    ├── GuiTienda.kt       # tienda del servidor y /vender
    ├── GuiHerrero.kt      # menú del herrero
    ├── GuiMercader.kt     # menú del mercader legendario
    ├── Herrero.kt         # reparar, combinar, renombrar, kits, legendarios
    ├── KitsReparacion.kt  # kits portátiles (clic derecho)
    ├── Encantamientos.kt  # legendarios y regla de Mending
    ├── GuiUtil.kt
    └── Almacen.kt         # JSON + log de transacciones
src/main/java/.../mixin/AnvilMenuMixin.java  # bloquea Mending en el yunque (solo servidor)
resourcepack/              # texturas (16x16) y modelos
scripts/generar_texturas.py  # regenera las texturas de partida
```

Las texturas son de partida: redibújalas con el mismo nombre de archivo en
`resourcepack/assets/banco_minecraft_per/textures/item/`.

## Versiones de Minecraft

Cada versión del mod sirve para **una** versión de Minecraft, y lo dice en su número
(`0.4.2+26.2`). Si se pone en un servidor de otra versión, Fabric lo rechaza al arrancar.

| Versión del mod | Minecraft | Rama |
|---|---|---|
| 0.4.2 (y anteriores) | 26.2 | `mc/26.2` (congelada, solo arreglos) |
| 0.5.0 en adelante | 26.3 | `main` |

## Publicar una versión

```bash
git tag v0.1.0
git push origin v0.1.0
```

El workflow **Publicar versión** crea un release con:
- `banco_minecraft_per.jar`
- `banco_minecraft_per-pack.zip` (resource pack)
- `banco_minecraft_per-pack.zip.sha1`

> El repo debe ser **público**: el servidor descarga el `.jar` y los jugadores
> descargan el resource pack desde el release, sin credenciales.

## Instalar en `serv_minecraft`

En `docker-compose.yml`, servicio `mc` → `environment`:

```yaml
      MODS: |
        https://github.com/Andrexifor/banco_minecraft_per/releases/download/v${BANCO_VERSION}/banco_minecraft_per.jar
      RESOURCE_PACK: https://github.com/Andrexifor/banco_minecraft_per/releases/download/v${BANCO_VERSION}/banco_minecraft_per-pack.zip
      RESOURCE_PACK_SHA1: ${RESOURCE_PACK_SHA1}
      RESOURCE_PACK_ENFORCE: "FALSE"
```

y en `.env` la versión del mod que corresponde a `MC_VERSION` (`BANCO_VERSION=0.5.0`
para 26.3) y el SHA1 de ese release (`RESOURCE_PACK_SHA1=...`). Cada vez que cambies
texturas, actualiza el SHA1 para que los clientes vuelvan a descargar el pack.
Luego: `.\scripts\update-mods.ps1`.
