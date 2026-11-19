package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Desglose
import io.github.andrexifor.banco.nucleo.Contador
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.Logro
import io.github.andrexifor.banco.nucleo.Metas
import io.github.andrexifor.banco.nucleo.Resultado
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Respuesta de una operación, para mostrarla igual en comandos y menús. */
data class Respuesta(val ok: Boolean, val mensaje: String)

/**
 * Operaciones de dinero que usan tanto los comandos como los menús de los NPC.
 * Todas guardan cuentas.json y escriben en transacciones.log.
 */
object Operaciones {
    private val banco get() = BancoMinecraftPer.banco
    private val almacen get() = BancoMinecraftPer.almacen
    private val config get() = BancoMinecraftPer.config

    fun saldo(j: ServerPlayer): Long = banco.saldo(j.uuid)

    fun retirar(j: ServerPlayer, monto: Long): Respuesta {
        if (monto <= 0) return Respuesta(false, "El monto debe ser mayor que cero")
        if (monto > config.retiroMaximo) {
            return Respuesta(false, "El máximo por retiro es ${Formato.soles(config.retiroMaximo)}")
        }
        val saldo = banco.saldo(j.uuid)
        if (saldo < monto) return Respuesta(false, "Saldo insuficiente. Tienes ${Formato.soles(saldo)}")
        val desglose = Desglose.desglosar(monto)
        val necesarias = Desglose.casillasNecesarias(desglose)
        val libres = Efectivo.casillasLibres(j)
        if (necesarias > libres) {
            return Respuesta(false, "Necesitas $necesarias casillas libres en el inventario (tienes $libres)")
        }
        val r = banco.retirar(j.uuid, monto)
        if (r !is Resultado.Ok) return Respuesta(false, mensaje(r))
        Efectivo.entregar(j, desglose)
        BancoMinecraftPer.guardar()
        almacen.registrar("RETIRO", j.name.string, "-", monto)
        val detalle = desglose.entries.joinToString(", ") { (d, c) -> "${c}×${d.nombreCorto}" }
        return Respuesta(true, "Retiraste ${Formato.soles(monto)} ($detalle). Saldo: ${Formato.soles(r.saldoFinal)}")
    }

    fun depositar(j: ServerPlayer, soloMano: Boolean): Respuesta {
        val monto = if (soloMano) Efectivo.recogerMano(j) else Efectivo.recogerTodo(j)
        if (monto <= 0) {
            return Respuesta(false, if (soloMano) "No tienes efectivo en la mano" else "No tienes efectivo en el inventario")
        }
        val r = banco.depositar(j.uuid, monto)
        if (r !is Resultado.Ok) {
            Efectivo.entregar(j, Desglose.desglosar(monto)) // devolver el efectivo
            return Respuesta(false, mensaje(r))
        }
        BancoMinecraftPer.guardar()
        almacen.registrar("DEPOSITO", j.name.string, "-", monto)
        return Respuesta(true, "Depositaste ${Formato.soles(monto)}. Saldo: ${Formato.soles(r.saldoFinal)}")
    }

    fun pagar(origen: ServerPlayer, destino: ServerPlayer, monto: Long): Respuesta {
        val r = banco.transferir(origen.uuid, destino.uuid, monto, config.impuestoPagosPorcentaje)
        if (r !is Resultado.Ok) return Respuesta(false, mensaje(r))
        BancoMinecraftPer.guardar()
        almacen.registrar("PAGO", origen.name.string, destino.name.string, monto, r.impuesto)
        Logros.otorgar(origen, Logro.PAGADOR_PUNTUAL)
        Logros.sumar(origen, Contador.IMPUESTOS, r.impuesto, Metas.IMPUESTOS, Logro.CONTRIBUYENTE)
        val extra = if (r.impuesto > 0) " (+${Formato.soles(r.impuesto)} de impuesto)" else ""
        return Respuesta(true, "Pagaste ${Formato.soles(monto)} a ${destino.name.string}$extra. Saldo: ${Formato.soles(r.saldoFinal)}")
    }

    /**
     * Cobra [monto] para una compra o servicio. Usa primero el saldo del banco; si no
     * alcanza, deposita automáticamente el efectivo del inventario y vuelve a intentar.
     */
    fun cobrar(j: ServerPlayer, monto: Long, concepto: String, impuesto: Long = 0): Respuesta {
        val total = Math.addExact(monto, impuesto)
        val saldo = banco.saldo(j.uuid)
        if (saldo < total) {
            val efectivo = Efectivo.valorEnInventario(j)
            if (saldo + efectivo < total) {
                Logros.otorgar(j, Logro.FIADO_NO_HAY)
                return Respuesta(
                    false,
                    "No te alcanza: cuesta ${Formato.soles(total)} y tienes ${Formato.soles(saldo)} en el banco" +
                        (if (efectivo > 0) " + ${Formato.soles(efectivo)} en efectivo" else ""),
                )
            }
            val dep = depositar(j, soloMano = false)
            if (!dep.ok) return dep
        }
        val r = banco.retirar(j.uuid, total)
        if (r !is Resultado.Ok) return Respuesta(false, mensaje(r))
        BancoMinecraftPer.guardar()
        almacen.registrar("COBRO", j.name.string, "-", monto, impuesto, concepto)
        Logros.sumar(j, Contador.IMPUESTOS, impuesto, Metas.IMPUESTOS, Logro.CONTRIBUYENTE)
        return Respuesta(true, "Pagaste ${Formato.soles(total)}. Saldo: ${Formato.soles(r.saldoFinal)}")
    }

    /** Abona [monto] a la cuenta (p. ej. venta a la tienda). */
    fun abonar(j: ServerPlayer, monto: Long, concepto: String, impuesto: Long = 0): Respuesta {
        val r = banco.depositar(j.uuid, monto)
        if (r !is Resultado.Ok) return Respuesta(false, mensaje(r))
        BancoMinecraftPer.guardar()
        almacen.registrar("ABONO", "-", j.name.string, monto, impuesto, concepto)
        Logros.sumar(j, Contador.IMPUESTOS, impuesto, Metas.IMPUESTOS, Logro.CONTRIBUYENTE)
        return Respuesta(true, "Recibiste ${Formato.soles(monto)}. Saldo: ${Formato.soles(r.saldoFinal)}")
    }

    /** Junta todo el efectivo del inventario en la menor cantidad de piezas. */
    fun ordenarEfectivo(j: ServerPlayer): Respuesta {
        val total = Efectivo.valorEnInventario(j)
        if (total <= 0) return Respuesta(false, "No tienes efectivo en el inventario")
        val dep = depositar(j, soloMano = false)
        if (!dep.ok) return dep
        val ret = retirar(j, total)
        if (!ret.ok) return Respuesta(false, "Tu efectivo quedó depositado en el banco: ${ret.mensaje}")
        return Respuesta(true, "Efectivo ordenado: ${Formato.soles(total)}")
    }

    /** Cambia UNA pieza de la mano por piezas menores (p. ej. 100 -> 50 + 50). */
    fun cambiarASencillo(j: ServerPlayer): Respuesta {
        val stack = j.mainHandItem
        val d = Efectivo.denominacionDe(stack) ?: return Respuesta(false, "Ten en la mano el billete o moneda que quieres cambiar")
        val cambio = Desglose.desglosar(d.valor, menorQue = d.valor)
        if (cambio.isEmpty()) return Respuesta(false, "Una moneda de S/ 1 no se puede cambiar")
        // Al sacar 1 pieza puede liberarse la casilla si era la última
        val libres = Efectivo.casillasLibres(j) + (if (stack.count == 1) 1 else 0)
        val necesarias = Desglose.casillasNecesarias(cambio)
        if (necesarias > libres) return Respuesta(false, "Necesitas $necesarias casillas libres para el cambio")
        stack.shrink(1)
        Efectivo.entregar(j, cambio)
        almacen.registrar("CAMBIO", j.name.string, "-", d.valor, detalle = d.modelo)
        Logros.otorgar(j, Logro.SENCILLO_CAUSA)
        val detalle = cambio.entries.joinToString(" + ") { (den, c) -> "${c}×${den.nombreCorto}" }
        return Respuesta(true, "Cambiaste ${d.nombreCorto} por $detalle")
    }

    /** Casillas libres + espacio en stacks del mismo ítem, para comprar sin que caiga al suelo. */
    fun cabe(j: ServerPlayer, ejemplo: ItemStack, cantidad: Int): Boolean {
        val porStack = ejemplo.maxStackSize
        var espacio = 0L
        val inv = j.inventory
        for (i in 0 until 36) {
            val s = inv.getItem(i)
            if (s.isEmpty) {
                espacio += porStack
            } else if (ItemStack.isSameItemSameComponents(s, ejemplo)) {
                espacio += (porStack - s.count).coerceAtLeast(0)
            }
            if (espacio >= cantidad) return true
        }
        return espacio >= cantidad
    }

    fun mensaje(r: Resultado): String = when (r) {
        is Resultado.SaldoInsuficiente ->
            "Saldo insuficiente: tienes ${Formato.soles(r.saldo)} y necesitas ${Formato.soles(r.requerido)}"
        is Resultado.MontoInvalido -> r.motivo
        Resultado.CuentaInexistente -> "Esa cuenta no existe (el jugador debe entrar al servidor al menos una vez)"
        is Resultado.Ok -> "OK"
    }
}
