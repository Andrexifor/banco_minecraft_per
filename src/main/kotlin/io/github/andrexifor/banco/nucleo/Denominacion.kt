package io.github.andrexifor.banco.nucleo

/** Tipo de pieza de efectivo. Define el ítem base y el estilo. */
enum class TipoEfectivo { MONEDA, BILLETE, BONO }

/**
 * Las 12 denominaciones del sol en el servidor.
 * Lógica pura (sin clases de Minecraft) para poder testearla sin arrancar el juego.
 */
enum class Denominacion(val valor: Long, val tipo: TipoEfectivo, val nombreLargo: String) {
    MONEDA_1(1, TipoEfectivo.MONEDA, "Moneda de un sol"),
    MONEDA_2(2, TipoEfectivo.MONEDA, "Moneda de dos soles"),
    MONEDA_5(5, TipoEfectivo.MONEDA, "Moneda de cinco soles"),
    BILLETE_10(10, TipoEfectivo.BILLETE, "Billete de diez soles"),
    BILLETE_20(20, TipoEfectivo.BILLETE, "Billete de veinte soles"),
    BILLETE_50(50, TipoEfectivo.BILLETE, "Billete de cincuenta soles"),
    BILLETE_100(100, TipoEfectivo.BILLETE, "Billete de cien soles"),
    BILLETE_200(200, TipoEfectivo.BILLETE, "Billete de doscientos soles"),
    BONO_500(500, TipoEfectivo.BONO, "Bono de reserva de quinientos soles"),
    BONO_1000(1_000, TipoEfectivo.BONO, "Bono de reserva de mil soles"),
    BONO_5000(5_000, TipoEfectivo.BONO, "Bono de reserva de cinco mil soles"),
    BONO_10000(10_000, TipoEfectivo.BONO, "Bono de reserva de diez mil soles"),
    ;

    /** Nombre del modelo/textura en el resource pack, p. ej. "billete_50". */
    val modelo: String
        get() = when (tipo) {
            TipoEfectivo.MONEDA -> "moneda_$valor"
            TipoEfectivo.BILLETE -> "billete_$valor"
            TipoEfectivo.BONO -> "bono_$valor"
        }

    /** Nombre corto que ve el jugador, p. ej. "S/ 50" o "Bono S/ 10,000". */
    val nombreCorto: String
        get() = if (tipo == TipoEfectivo.BONO) "Bono ${Formato.soles(valor)}" else Formato.soles(valor)

    companion object {
        /** De mayor a menor: el orden que usa el desglose. */
        val DESCENDENTE: List<Denominacion> = entries.sortedByDescending { it.valor }

        fun porValor(valor: Long): Denominacion? = entries.firstOrNull { it.valor == valor }
    }
}
