package io.github.andrexifor.banco.nucleo

/**
 * config/banco_minecraft_per/banco.json
 * Todos los campos tienen valor por defecto: si falta alguno en el archivo, se usa este.
 */
data class Configuracion(
    /** Soles con los que empieza cada jugador nuevo. */
    var saldoInicial: Long = 500,
    /** % que paga extra quien usa /pagar (0 = sin impuesto). */
    var impuestoPagosPorcentaje: Double = 0.0,
    /** Máximo que se puede retirar en un solo /retirar. */
    var retiroMaximo: Long = 1_000_000,
    /** Cuántos jugadores muestra /banco top. */
    var rankingLimite: Int = 10,
) {
    fun normalizada(): Configuracion = copy(
        saldoInicial = saldoInicial.coerceIn(0, Banco.SALDO_MAXIMO),
        impuestoPagosPorcentaje = impuestoPagosPorcentaje.coerceIn(0.0, 100.0),
        retiroMaximo = retiroMaximo.coerceAtLeast(1),
        rankingLimite = rankingLimite.coerceIn(1, 50),
    )
}
