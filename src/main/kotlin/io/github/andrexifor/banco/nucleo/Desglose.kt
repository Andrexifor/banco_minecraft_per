package io.github.andrexifor.banco.nucleo

/**
 * Convierte un monto en piezas de efectivo.
 * Con estas denominaciones (1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 5000, 10000)
 * el algoritmo "de mayor a menor" siempre da la menor cantidad de piezas.
 */
object Desglose {
    const val MAX_POR_STACK = 64

    /** Devuelve solo las denominaciones usadas, de mayor a menor. */
    fun desglosar(monto: Long): Map<Denominacion, Long> {
        require(monto >= 0) { "El monto no puede ser negativo: $monto" }
        val resultado = linkedMapOf<Denominacion, Long>()
        var resto = monto
        for (d in Denominacion.DESCENDENTE) {
            val cantidad = resto / d.valor
            if (cantidad > 0) {
                resultado[d] = cantidad
                resto -= cantidad * d.valor
            }
        }
        check(resto == 0L) { "Desglose incompleto, resto=$resto" }
        return resultado
    }

    /** Casillas de inventario necesarias para recibir el desglose. */
    fun casillasNecesarias(desglose: Map<Denominacion, Long>, maxPorStack: Int = MAX_POR_STACK): Long =
        desglose.values.sumOf { (it + maxPorStack - 1) / maxPorStack }

    fun total(desglose: Map<Denominacion, Long>): Long =
        desglose.entries.sumOf { (d, c) -> Math.multiplyExact(d.valor, c) }
}
