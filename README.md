# Banco Minecraft PER

Mod de economía en **soles** para servidores Fabric (Minecraft 26.2). Funciona
**solo en el servidor**: los jugadores entran con Minecraft vanilla y solo aceptan
el resource pack que el servidor les ofrece al conectarse.

> Fase 1 de 6 (ver `diseno-banco_minecraft_per.md` en el proyecto): banco + efectivo.
> Convive con Viper Economy hasta la fase 3; los comandos no chocan.

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
| `/banco top` | todos | Ranking de saldos |
| `/banco dar\|quitar\|fijar <jugador> <monto>` | `banco_minecraft_per.admin` | Administración |
| `/banco crear <denominacion> <cantidad>` | `banco_minecraft_per.admin` | Crear efectivo (pruebas) |
| `/banco recargar` | `banco_minecraft_per.admin` | Recargar la configuración |

Sin LuckPerms, los permisos de admin equivalen a nivel de operador 3.
Con LuckPerms, dáselos al grupo admin:
`lp group admin permission set banco_minecraft_per.admin true`
(el grupo admin del servidor ya tiene `*`).

## Archivos en el servidor

| Ruta (dentro de `/data`) | Contenido |
|---|---|
| `config/banco_minecraft_per/banco.json` | Configuración: saldo inicial, impuesto de pagos, retiro máximo, tamaño del ranking |
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
│   ├── Configuracion.kt
│   └── Formato.kt
└── juego/                 # todo lo que toca Minecraft
    ├── Efectivo.kt        # crear/reconocer ítems de efectivo, inventario
    ├── Comandos.kt
    └── Almacen.kt         # JSON + log de transacciones
resourcepack/              # texturas (16x16) y modelos
scripts/generar_texturas.py  # regenera las texturas de partida
```

Las texturas son de partida: redibújalas con el mismo nombre de archivo en
`resourcepack/assets/banco_minecraft_per/textures/item/`.

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
        https://github.com/Andrexifor/banco_minecraft_per/releases/latest/download/banco_minecraft_per.jar
      RESOURCE_PACK: https://github.com/Andrexifor/banco_minecraft_per/releases/latest/download/banco_minecraft_per-pack.zip
      RESOURCE_PACK_SHA1: ${RESOURCE_PACK_SHA1}
      RESOURCE_PACK_ENFORCE: "FALSE"
```

y en `.env` el SHA1 del release (`RESOURCE_PACK_SHA1=...`). Cada vez que cambies
texturas, actualiza el SHA1 para que los clientes vuelvan a descargar el pack.
Luego: `.\scripts\update-mods.ps1`.
