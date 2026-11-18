package io.github.andrexifor.banco.nucleo

import java.util.UUID

/** Resultado de una operación del banco. */
sealed interface Resultado {
    data class Ok(val saldoFinal: Long, val impuesto: Long = 0) : Resultado
    data class SaldoInsuficiente(val saldo: Long, val requerido: Long) : Resultado
    data class MontoInvalido(val motivo: String) : Resultado
    data object CuentaInexistente : Resultado
}

/** Cuenta de un jugador. El nombre se guarda solo para mostrarlo (ranking, logs). */
data class Cuenta(var nombre: String, var saldo: Long)

/**
 * Lógica de cuentas, sin dependencias de Minecraft ni de archivos.
 * Montos en soles enteros (no hay céntimos).
 */
class Banco(
    private val cuentas: MutableMap<UUID, Cuenta> = linkedMapOf(),
) {
    companion object {
        /** Tope por cuenta, para evitar desbordes y errores de tipeo de admins. */
        const val SALDO_MAXIMO: Long = 1_000_000_000_000L
    }

    fun existe(id: UUID): Boolean = id in cuentas

    fun saldo(id: UUID): Long = cuentas[id]?.saldo ?: 0

    fun cuenta(id: UUID): Cuenta? = cuentas[id]

    fun todas(): Map<UUID, Cuenta> = cuentas

    /** Crea la cuenta si no existe. Devuelve true si la creó. */
    fun abrirCuenta(id: UUID, nombre: String, saldoInicial: Long): Boolean {
        val existente = cuentas[id]
        if (existente != null) {
            existente.nombre = nombre
            return false
        }
        cuentas[id] = Cuenta(nombre, saldoInicial.coerceIn(0, SALDO_MAXIMO))
        return true
    }

    fun depositar(id: UUID, monto: Long): Resultado {
        if (monto <= 0) return Resultado.MontoInvalido("El monto debe ser mayor que cero")
        val c = cuentas[id] ?: return Resultado.CuentaInexistente
        val nuevo = sumarSeguro(c.saldo, monto) ?: return Resultado.MontoInvalido("Supera el saldo máximo")
        c.saldo = nuevo
        return Resultado.Ok(nuevo)
    }

    fun retirar(id: UUID, monto: Long): Resultado {
        if (monto <= 0) return Resultado.MontoInvalido("El monto debe ser mayor que cero")
        val c = cuentas[id] ?: return Resultado.CuentaInexistente
        if (c.saldo < monto) return Resultado.SaldoInsuficiente(c.saldo, monto)
        c.saldo -= monto
        return Resultado.Ok(c.saldo)
    }

    /**
     * El que paga entrega [monto] + impuesto; el que recibe obtiene [monto].
     * El impuesto desaparece de la economía (sumidero de dinero).
     */
    fun transferir(de: UUID, a: UUID, monto: Long, impuestoPorcentaje: Double): Resultado {
        if (monto <= 0) return Resultado.MontoInvalido("El monto debe ser mayor que cero")
        if (de == a) return Resultado.MontoInvalido("No puedes pagarte a ti mismo")
        val origen = cuentas[de] ?: return Resultado.CuentaInexistente
        val destino = cuentas[a] ?: return Resultado.CuentaInexistente
        val impuesto = calcularImpuesto(monto, impuestoPorcentaje)
        val total = Math.addExact(monto, impuesto)
        if (origen.saldo < total) return Resultado.SaldoInsuficiente(origen.saldo, total)
        val nuevoDestino = sumarSeguro(destino.saldo, monto)
            ?: return Resultado.MontoInvalido("El destinatario superaría el saldo máximo")
        origen.saldo -= total
        destino.saldo = nuevoDestino
        return Resultado.Ok(origen.saldo, impuesto)
    }

    /** Admin: suma sin límite de origen. */
    fun dar(id: UUID, monto: Long): Resultado = depositar(id, monto)

    /** Admin: resta; falla si no alcanza. */
    fun quitar(id: UUID, monto: Long): Resultado = retirar(id, monto)

    /** Admin: fija el saldo exacto. */
    fun fijar(id: UUID, monto: Long): Resultado {
        if (monto < 0 || monto > SALDO_MAXIMO) return Resultado.MontoInvalido("Monto fuera de rango")
        val c = cuentas[id] ?: return Resultado.CuentaInexistente
        c.saldo = monto
        return Resultado.Ok(monto)
    }

    fun ranking(limite: Int): List<Pair<UUID, Cuenta>> =
        cuentas.entries.sortedByDescending { it.value.saldo }.take(limite).map { it.key to it.value }

    fun calcularImpuesto(monto: Long, porcentaje: Double): Long = Impuestos.calcular(monto, porcentaje)

    private fun sumarSeguro(a: Long, b: Long): Long? {
        val r = try { Math.addExact(a, b) } catch (e: ArithmeticException) { return null }
        return if (r > SALDO_MAXIMO) null else r
    }
}

/** Impuesto redondeado hacia abajo: 5% de 19 = 0, 5% de 20 = 1. */
object Impuestos {
    fun calcular(monto: Long, porcentaje: Double): Long {
        if (porcentaje <= 0.0 || monto <= 0) return 0
        return Math.floorDiv(Math.round(monto * porcentaje), 100L).coerceAtLeast(0)
    }
}
