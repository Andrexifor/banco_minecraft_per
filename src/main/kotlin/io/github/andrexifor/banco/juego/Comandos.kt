package io.github.andrexifor.banco.juego

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Banco
import io.github.andrexifor.banco.nucleo.Denominacion
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.Resultado
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Comandos en español.
 *
 * Permisos (LuckPerms):
 *   banco_minecraft_per.saldo.otros  -> /saldo <jugador>                         (por defecto: op 2)
 *   banco_minecraft_per.admin        -> /banco dar|quitar|fijar|crear|recargar|npc|zonas (por defecto: op 3)
 *   banco_minecraft_per.zonas.editar -> construir en las zonas protegidas, solo en creativo (por defecto: op 2)
 */
object Comandos {
    private const val PERM = BancoMinecraftPer.MOD_ID
    private val banco: Banco get() = BancoMinecraftPer.banco

    fun registrar(d: CommandDispatcher<CommandSourceStack>) {
        d.register(
            literal("saldo")
                .executes { saldoPropio(it) }
                .then(
                    argument("jugador", EntityArgument.player())
                        .requires(Permissions.require("$PERM.saldo.otros", 2))
                        .executes { saldoDe(it, EntityArgument.getPlayer(it, "jugador")) },
                ),
        )

        d.register(
            literal("retirar").then(
                argument("monto", LongArgumentType.longArg(1)).executes {
                    responder(it, Operaciones.retirar(it.source.playerOrException, LongArgumentType.getLong(it, "monto")))
                },
            ),
        )

        d.register(
            literal("depositar")
                .executes { responder(it, Operaciones.depositar(it.source.playerOrException, soloMano = false)) }
                .then(literal("todo").executes { responder(it, Operaciones.depositar(it.source.playerOrException, soloMano = false)) })
                .then(literal("mano").executes { responder(it, Operaciones.depositar(it.source.playerOrException, soloMano = true)) }),
        )

        d.register(
            literal("pagar").then(
                argument("jugador", EntityArgument.player()).then(
                    argument("monto", LongArgumentType.longArg(1)).executes { pagar(it) },
                ),
            ),
        )

        d.register(literal("tienda").executes { GuiTienda.abrir(it.source.playerOrException); 1 })

        d.register(
            literal("vender")
                .then(literal("mano").executes { responder(it, GuiTienda.venderMano(it.source.playerOrException)) })
                .then(literal("todo").executes { responder(it, GuiTienda.venderTodo(it.source.playerOrException)) }),
        )

        val admin = Permissions.require("$PERM.admin", 3)
        d.register(
            literal("banco")
                .executes { ayuda(it) }
                .then(literal("top").executes { top(it) })
                .then(literal("dar").requires(admin).then(jugadorYMonto("dar", 1)))
                .then(literal("quitar").requires(admin).then(jugadorYMonto("quitar", 1)))
                .then(literal("fijar").requires(admin).then(jugadorYMonto("fijar", 0)))
                .then(
                    literal("crear").requires(admin).then(
                        argument("denominacion", StringArgumentType.word())
                            .suggests { _, b ->
                                Denominacion.entries.forEach { den -> b.suggest(den.modelo) }
                                b.buildFuture()
                            }
                            .then(
                                argument("cantidad", IntegerArgumentType.integer(1, 64)).executes { crearEfectivo(it) },
                            ),
                    ),
                )
                .then(
                    literal("npc").requires(Permissions.require("$PERM.admin", 2))
                        .then(literal("crear").then(argumentoTipoNpc().executes { crearNpc(it, crearAldeano = true) }))
                        .then(literal("asignar").then(argumentoTipoNpc().executes { crearNpc(it, crearAldeano = false) }))
                        .then(literal("quitar").executes { quitarNpc(it) })
                        .then(
                            literal("fijar").then(
                                argumentoTipoNpc().then(
                                    argument("entidades", EntityArgument.entities()).executes { fijarNpc(it) },
                                ),
                            ),
                        ),
                )
                .then(
                    literal("zonas").requires(Permissions.require("$PERM.admin", 2))
                        .executes { zonas(it) }
                        .then(literal("activar").then(argumentoZona().executes { cambiarZona(it, true) }))
                        .then(literal("desactivar").then(argumentoZona().executes { cambiarZona(it, false) })),
                )
                .then(literal("recargar").requires(admin).executes { recargar(it) }),
        )
    }

    private fun jugadorYMonto(op: String, minimo: Long) =
        argument("jugador", EntityArgument.player()).then(
            argument("monto", LongArgumentType.longArg(minimo)).executes { adminOperacion(it, op) },
        )

    // ---------- jugadores ----------

    private fun saldoPropio(ctx: CommandContext<CommandSourceStack>): Int {
        val jugador = ctx.source.playerOrException
        ok(ctx, "Tu saldo: ${Formato.soles(banco.saldo(jugador.uuid))}")
        return 1
    }

    private fun saldoDe(ctx: CommandContext<CommandSourceStack>, jugador: ServerPlayer): Int {
        ok(ctx, "Saldo de ${jugador.name.string}: ${Formato.soles(banco.saldo(jugador.uuid))}")
        return 1
    }

    private fun pagar(ctx: CommandContext<CommandSourceStack>): Int {
        val origen = ctx.source.playerOrException
        val destino = EntityArgument.getPlayer(ctx, "jugador")
        val monto = LongArgumentType.getLong(ctx, "monto")
        val r = Operaciones.pagar(origen, destino, monto)
        if (r.ok) {
            destino.sendSystemMessage(
                Component.literal("${origen.name.string} te pagó ${Formato.soles(monto)}").withStyle(ChatFormatting.GREEN),
            )
        }
        return responder(ctx, r)
    }

    private fun top(ctx: CommandContext<CommandSourceStack>): Int {
        val lista = banco.ranking(BancoMinecraftPer.config.rankingLimite)
        ok(ctx, "— Los más ricos del server —")
        lista.forEachIndexed { i, (_, c) ->
            ctx.source.sendSystemMessage(
                Component.literal("${i + 1}. ${c.nombre}: ${Formato.soles(c.saldo)}").withStyle(ChatFormatting.YELLOW),
            )
        }
        return 1
    }

    private fun ayuda(ctx: CommandContext<CommandSourceStack>): Int {
        val lineas = listOf(
            "— Banco Minecraft PER —",
            "/saldo — ver tu saldo",
            "/retirar <monto> — sacar billetes y monedas",
            "/depositar [todo|mano] — guardar tu efectivo",
            "/pagar <jugador> <monto> — transferir",
            "/tienda — comprar y vender",
            "/vender mano | todo — vender rápido a la tienda",
            "/banco top — ranking",
            "NPC: Banquero, Tendero, Herrero y Mercader legendario",
        )
        lineas.forEach { ctx.source.sendSystemMessage(Component.literal(it).withStyle(ChatFormatting.AQUA)) }
        return 1
    }

    // ---------- admin ----------

    private fun adminOperacion(ctx: CommandContext<CommandSourceStack>, op: String): Int {
        val jugador = EntityArgument.getPlayer(ctx, "jugador")
        val monto = LongArgumentType.getLong(ctx, "monto")
        val r = when (op) {
            "dar" -> banco.dar(jugador.uuid, monto)
            "quitar" -> banco.quitar(jugador.uuid, monto)
            else -> banco.fijar(jugador.uuid, monto)
        }
        if (r !is Resultado.Ok) return error(ctx, Operaciones.mensaje(r))
        BancoMinecraftPer.guardar()
        BancoMinecraftPer.almacen.registrar("ADMIN_" + op.uppercase(), ctx.source.textName, jugador.name.string, monto)
        ok(ctx, "Saldo de ${jugador.name.string}: ${Formato.soles(r.saldoFinal)}", avisarAdmins = true)
        return 1
    }

    private fun crearEfectivo(ctx: CommandContext<CommandSourceStack>): Int {
        val jugador = ctx.source.playerOrException
        val nombre = StringArgumentType.getString(ctx, "denominacion")
        val d = Denominacion.entries.firstOrNull { it.modelo == nombre }
            ?: return error(ctx, "Denominación desconocida: $nombre")
        val cantidad = IntegerArgumentType.getInteger(ctx, "cantidad")
        Inventarios.dar(jugador, Efectivo.crear(d, cantidad))
        BancoMinecraftPer.almacen.registrar("ADMIN_CREAR", jugador.name.string, "-", d.valor * cantidad, detalle = d.modelo)
        ok(ctx, "Creaste $cantidad × ${d.nombreCorto}", avisarAdmins = true)
        return 1
    }

    private fun argumentoTipoNpc() =
        argument("tipo", StringArgumentType.word()).suggests { _, b ->
            TipoNpc.entries.forEach { t -> b.suggest(t.id) }
            b.buildFuture()
        }

    private fun crearNpc(ctx: CommandContext<CommandSourceStack>, crearAldeano: Boolean): Int {
        val jugador = ctx.source.playerOrException
        val id = StringArgumentType.getString(ctx, "tipo")
        val tipo = TipoNpc.porId(id) ?: return error(ctx, "Tipo de NPC desconocido: $id (usa: ${TipoNpc.entries.joinToString { it.id }})")
        if (crearAldeano) {
            Npcs.crear(jugador, tipo)
            ok(ctx, "${tipo.nombre} creado. Hazle clic derecho (tienes 60 s) para activarlo.", avisarAdmins = true)
        } else {
            Npcs.esperarAsignacion(jugador, tipo)
            ok(ctx, "Haz clic derecho al aldeano que quieres convertir en ${tipo.nombre} (tienes 60 s).")
        }
        return 1
    }

    private fun quitarNpc(ctx: CommandContext<CommandSourceStack>): Int {
        Npcs.esperarQuitar(ctx.source.playerOrException)
        ok(ctx, "Haz clic derecho al NPC del banco que quieres eliminar (tienes 60 s).")
        return 1
    }

    private fun recargar(ctx: CommandContext<CommandSourceStack>): Int {
        BancoMinecraftPer.recargarConfiguracion()
        ok(
            ctx,
            "Recargado: ${BancoMinecraftPer.catalogo.productos.size} productos, " +
                "${BancoMinecraftPer.legendarios.legendarios.size} legendarios, precios del herrero y " +
                "${BancoMinecraftPer.zonas.zonas.orEmpty().size} zonas protegidas",
            avisarAdmins = true,
        )
        return 1
    }

    private fun fijarNpc(ctx: CommandContext<CommandSourceStack>): Int {
        val tipo = TipoNpc.porId(StringArgumentType.getString(ctx, "tipo"))
            ?: return error(ctx, "Tipo desconocido")
        val n = Npcs.fijar(EntityArgument.getEntities(ctx, "entidades"), tipo)
        if (n == 0) return error(ctx, "No se encontró ninguna entidad para ${tipo.nombre}")
        ok(ctx, "${tipo.nombre}: $n NPC registrado(s)")
        return n
    }

    private fun argumentoZona() =
        argument("nombre", StringArgumentType.word()).suggests { _, b ->
            BancoMinecraftPer.zonas.zonas.orEmpty().forEach { z -> b.suggest(z.nombre) }
            b.buildFuture()
        }

    private fun cambiarZona(ctx: CommandContext<CommandSourceStack>, activa: Boolean): Int {
        val nombre = StringArgumentType.getString(ctx, "nombre")
        val zonas = BancoMinecraftPer.zonas
        if (!zonas.cambiar(nombre, activa)) return error(ctx, "No existe la zona $nombre (mira /banco zonas)")
        BancoMinecraftPer.almacen.guardarZonas(zonas)
        ok(ctx, "Zona $nombre ${if (activa) "activada" else "desactivada"}", avisarAdmins = true)
        return 1
    }

    private fun zonas(ctx: CommandContext<CommandSourceStack>): Int {
        val lista = BancoMinecraftPer.zonas.zonas.orEmpty()
        if (lista.isEmpty()) return error(ctx, "No hay zonas protegidas (config/banco_minecraft_per/zonas.json)")
        val jugador = ctx.source.player
        val actual = jugador?.let { Proteccion.zonaEn(it.level(), it.blockPosition()) }
        val texto = buildString {
            append("Zonas protegidas (solo se edita en creativo y siendo op):")
            for (z in lista) {
                append("\n- ${z.nombre} [${z.dimension}] ${z.x1},${z.y1},${z.z1} -> ${z.x2},${z.y2},${z.z2}")
                if (!z.activa) append(" (desactivada)")
                if (z == actual) append("  <- estás aquí")
            }
        }
        ok(ctx, texto)
        return 1
    }

    // ---------- utilidades ----------

    private fun responder(ctx: CommandContext<CommandSourceStack>, r: Respuesta): Int {
        return if (r.ok) {
            ok(ctx, r.mensaje)
            1
        } else {
            error(ctx, r.mensaje)
        }
    }

    private fun ok(ctx: CommandContext<CommandSourceStack>, texto: String, avisarAdmins: Boolean = false) {
        ctx.source.sendSuccess({ Component.literal(texto).withStyle(ChatFormatting.GREEN) }, avisarAdmins)
    }

    private fun error(ctx: CommandContext<CommandSourceStack>, texto: String): Int {
        ctx.source.sendFailure(Component.literal(texto))
        return 0
    }
}
