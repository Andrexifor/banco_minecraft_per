package io.github.andrexifor.banco.nucleo

/**
 * Convierte un monto en piezas de efectivo.
 * Con estas denominaciones (1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 5000, 10000)
 * el algoritmo "de mayor a menor" siempre da la menor cantidad de piezas.
 */
object Desglose {
    const val MAX_POR_STACK = 64

    /** Devuelve solo las denominaciones usadas, de mayor a menor. */
    fun desglosar(monto: Long): Map<Denominacion, Long> = desglosar(monto, Long.MAX_VALUE)

    /**
     * Igual que [desglosar], pero solo usa denominaciones con valor < [menorQue].
     * Sirve para "cambiar a sencillo": un billete de 100 -> 50 + 50.
     * Si no se puede desglosar completo (S/ 1 sin monedas menores), devuelve un mapa vacío.
     */
    fun desglosar(monto: Long, menorQue: Long): Map<Denominacion, Long> {
        require(monto >= 0) { "El monto no puede ser negativo: $monto" }
        val resultado = linkedMapOf<Denominacion, Long>()
        var resto = monto
        for (d in Denominacion.DESCENDENTE.filter { it.valor < menorQue }) {
            val cantidad = resto / d.valor
            if (cantidad > 0) {
                resultado[d] = cantidad
                resto -= cantidad * d.valor
            }
        }
        if (resto != 0L) {
            // Solo pasa si se restringen las denominaciones (p. ej. cambiar una moneda de S/ 1)
            check(menorQue != Long.MAX_VALUE) { "Desglose incompleto, resto=$resto" }
            return emptyMap()
        }
        return resultado
    }

    /** Casillas de inventario necesarias para recibir el desglose. */
    fun casillasNecesarias(desglose: Map<Denominacion, Long>, maxPorStack: Int = MAX_POR_STACK): Long =
        desglose.values.sumOf { (it + maxPorStack - 1) / maxPorStack }

    fun total(desglose: Map<Denominacion, Long>): Long =
        desglose.entries.sumOf { (d, c) -> Math.multiplyExact(d.valor, c) }
}
