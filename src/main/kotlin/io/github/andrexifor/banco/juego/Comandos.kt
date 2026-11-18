package io.github.andrexifor.banco.juego

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Banco
import io.github.andrexifor.banco.nucleo.Denominacion
import io.github.andrexifor.banco.nucleo.Desglose
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
 * Comandos en español. No chocan con Viper (/bal, /pay, /withdraw…), que sigue
 * activo hasta la fase 3.
 *
 * Permisos (LuckPerms):
 *   banco_minecraft_per.saldo.otros  -> /saldo <jugador>            (por defecto: nivel op 2)
 *   banco_minecraft_per.admin        -> /banco dar|quitar|fijar|crear|recargar (por defecto: nivel op 3)
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
                argument("monto", LongArgumentType.longArg(1))
                    .executes { retirar(it, LongArgumentType.getLong(it, "monto")) },
            ),
        )

        d.register(
            literal("depositar")
                .executes { depositar(it, soloMano = false) }
                .then(literal("todo").executes { depositar(it, soloMano = false) })
                .then(literal("mano").executes { depositar(it, soloMano = true) }),
        )

        d.register(
            literal("pagar").then(
                argument("jugador", EntityArgument.player()).then(
                    argument("monto", LongArgumentType.longArg(1))
                        .executes {
                            pagar(it, EntityArgument.getPlayer(it, "jugador"), LongArgumentType.getLong(it, "monto"))
                        },
                ),
            ),
        )

        val admin = Permissions.require("$PERM.admin", 3)
        d.register(
            literal("banco")
                .executes { ayuda(it) }
                .then(literal("top").executes { top(it) })
                .then(
                    literal("dar").requires(admin).then(
                        argument("jugador", EntityArgument.player()).then(
                            argument("monto", LongArgumentType.longArg(1)).executes {
                                adminOperacion(it, "dar")
                            },
                        ),
                    ),
                )
                .then(
                    literal("quitar").requires(admin).then(
                        argument("jugador", EntityArgument.player()).then(
                            argument("monto", LongArgumentType.longArg(1)).executes {
                                adminOperacion(it, "quitar")
                            },
                        ),
                    ),
                )
                .then(
                    literal("fijar").requires(admin).then(
                        argument("jugador", EntityArgument.player()).then(
                            argument("monto", LongArgumentType.longArg(0)).executes {
                                adminOperacion(it, "fijar")
                            },
                        ),
                    ),
                )
                .then(
                    literal("crear").requires(admin).then(
                        argument("denominacion", StringArgumentType.word())
                            .suggests { _, b ->
                                Denominacion.entries.forEach { den -> b.suggest(den.modelo) }
                                b.buildFuture()
                            }
                            .then(
                                argument("cantidad", IntegerArgumentType.integer(1, 64)).executes {
                                    crearEfectivo(it)
                                },
                            ),
                    ),
                )
                .then(literal("recargar").requires(admin).executes { recargar(it) }),
        )
    }

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

    private fun retirar(ctx: CommandContext<CommandSourceStack>, monto: Long): Int {
        val jugador = ctx.source.playerOrException
        val cfg = BancoMinecraftPer.config
        if (monto > cfg.retiroMaximo) {
            return error(ctx, "El máximo por retiro es ${Formato.soles(cfg.retiroMaximo)}")
        }
        val saldo = banco.saldo(jugador.uuid)
        if (saldo < monto) {
            return error(ctx, "Saldo insuficiente. Tienes ${Formato.soles(saldo)}")
        }
        val desglose = Desglose.desglosar(monto)
        val necesarias = Desglose.casillasNecesarias(desglose)
        val libres = Efectivo.casillasLibres(jugador)
        if (necesarias > libres) {
            return error(ctx, "Necesitas $necesarias casillas libres en el inventario (tienes $libres)")
        }
        return when (val r = banco.retirar(jugador.uuid, monto)) {
            is Resultado.Ok -> {
                Efectivo.entregar(jugador, desglose)
                BancoMinecraftPer.guardar()
                BancoMinecraftPer.almacen.registrar("RETIRO", jugador.name.string, "-", monto)
                val detalle = desglose.entries.joinToString(", ") { (den, c) -> "${c}×${den.nombreCorto}" }
                ok(ctx, "Retiraste ${Formato.soles(monto)} ($detalle). Saldo: ${Formato.soles(r.saldoFinal)}")
                1
            }
            else -> error(ctx, mensaje(r))
        }
    }

    private fun depositar(ctx: CommandContext<CommandSourceStack>, soloMano: Boolean): Int {
        val jugador = ctx.source.playerOrException
        val monto = if (soloMano) Efectivo.recogerMano(jugador) else Efectivo.recogerTodo(jugador)
        if (monto <= 0) {
            return error(ctx, if (soloMano) "No tienes efectivo en la mano" else "No tienes efectivo en el inventario")
        }
        return when (val r = banco.depositar(jugador.uuid, monto)) {
            is Resultado.Ok -> {
                BancoMinecraftPer.guardar()
                BancoMinecraftPer.almacen.registrar("DEPOSITO", jugador.name.string, "-", monto)
                ok(ctx, "Depositaste ${Formato.soles(monto)}. Saldo: ${Formato.soles(r.saldoFinal)}")
                1
            }
            else -> {
                // No debería pasar (p. ej. saldo máximo): devolver el efectivo
                Efectivo.entregar(jugador, Desglose.desglosar(monto))
                error(ctx, mensaje(r))
            }
        }
    }

    private fun pagar(ctx: CommandContext<CommandSourceStack>, destino: ServerPlayer, monto: Long): Int {
        val origen = ctx.source.playerOrException
        val pct = BancoMinecraftPer.config.impuestoPagosPorcentaje
        return when (val r = banco.transferir(origen.uuid, destino.uuid, monto, pct)) {
            is Resultado.Ok -> {
                BancoMinecraftPer.guardar()
                BancoMinecraftPer.almacen.registrar("PAGO", origen.name.string, destino.name.string, monto, r.impuesto)
                val extra = if (r.impuesto > 0) " (+${Formato.soles(r.impuesto)} de impuesto)" else ""
                ok(ctx, "Pagaste ${Formato.soles(monto)} a ${destino.name.string}$extra. Saldo: ${Formato.soles(r.saldoFinal)}")
                destino.sendSystemMessage(
                    Component.literal("${origen.name.string} te pagó ${Formato.soles(monto)}")
                        .withStyle(ChatFormatting.GREEN),
                )
                1
            }
            else -> error(ctx, mensaje(r))
        }
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
            "/banco top — ranking",
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
        if (r !is Resultado.Ok) return error(ctx, mensaje(r))
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
        jugador.inventory.placeItemBackInInventory(Efectivo.crear(d, cantidad))
        BancoMinecraftPer.almacen.registrar("ADMIN_CREAR", jugador.name.string, "-", d.valor * cantidad, detalle = d.modelo)
        ok(ctx, "Creaste $cantidad × ${d.nombreCorto}", avisarAdmins = true)
        return 1
    }

    private fun recargar(ctx: CommandContext<CommandSourceStack>): Int {
        BancoMinecraftPer.recargarConfiguracion()
        ok(ctx, "Configuración recargada", avisarAdmins = true)
        return 1
    }

    // ---------- utilidades ----------

    private fun ok(ctx: CommandContext<CommandSourceStack>, texto: String, avisarAdmins: Boolean = false) {
        ctx.source.sendSuccess({ Component.literal(texto).withStyle(ChatFormatting.GREEN) }, avisarAdmins)
    }

    private fun error(ctx: CommandContext<CommandSourceStack>, texto: String): Int {
        ctx.source.sendFailure(Component.literal(texto))
        return 0
    }

    private fun mensaje(r: Resultado): String = when (r) {
        is Resultado.SaldoInsuficiente ->
            "Saldo insuficiente: tienes ${Formato.soles(r.saldo)} y necesitas ${Formato.soles(r.requerido)}"
        is Resultado.MontoInvalido -> r.motivo
        Resultado.CuentaInexistente -> "Esa cuenta no existe (el jugador debe entrar al servidor al menos una vez)"
        is Resultado.Ok -> "OK"
    }
}
