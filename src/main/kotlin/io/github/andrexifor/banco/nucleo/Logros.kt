package io.github.andrexifor.banco.nucleo

/** Logros del servidor. El id coincide con data/banco_minecraft_per/advancement/<id>.json del mod. */
enum class Logro(val id: String) {
    PRIMER_MILLON("primer_millon"),
    RICO_MCPATO("rico_mcpato"),
    AVARICIA("avaricia"),
    SENCILLO_CAUSA("sencillo_causa"),
    PAGADOR_PUNTUAL("pagador_puntual"),
    NO_ARRIESGA("no_arriesga"),
    FIADO_NO_HAY("fiado_no_hay"),
    MERCACHIFLE("mercachifle"),
    CONTRIBUYENTE("contribuyente"),
    BANCARROTA("bancarrota"),
    TAHUANTINSUYO("tahuantinsuyo"),
    DIOSES("dioses"),
    COMO_NUEVO("como_nuevo"),
    COLECCIONISTA("coleccionista"),
    VERDAD_DUELE("verdad_duele"),
    VIP_MERCADO_NEGRO("vip_mercado_negro"),
    PLATA_COMO_CANCHA("plata_como_cancha"),
    VOLANDO_BAJITO("volando_bajito"),
    PEAJE_INTI("peaje_inti"),
}

/** Contadores acumulados por jugador. */
object Contador {
    const val VENDIDOS = "items_vendidos"
    const val IMPUESTOS = "impuestos_pagados"
    const val SALDO_MAXIMO = "saldo_maximo"
    const val COMPRAS_NEGRO = "compras_mercado_negro"
    const val GASTO_BROMAS = "gasto_bromas"
}

/** Umbrales de los logros por cantidad. */
object Metas {
    const val MILLON = 1_000_000L
    const val MCPATO = 65_000_000L
    const val AVARICIA = 666_666_666L
    const val VENDIDOS = 1_000L
    const val IMPUESTOS = 100_000L
    const val BANCARROTA_PREVIO = 100_000L
    const val COMPRAS_NEGRO = 5L
    const val GASTO_BROMAS = 1_000_000L
}

/** Lo que el mod recuerda de cada jugador: logros dados, contadores, legendarios comprados y mercado negro. */
data class DatosJugador(
    var nombre: String = "",
    var otorgados: MutableSet<String>? = linkedSetOf(),
    var contadores: MutableMap<String, Long>? = linkedMapOf(),
    var legendarios: MutableSet<String>? = linkedSetOf(),
    var mercadoNegro: Boolean = false,
) {
    fun otorgados(): MutableSet<String> = otorgados ?: linkedSetOf<String>().also { otorgados = it }
    fun contadores(): MutableMap<String, Long> = contadores ?: linkedMapOf<String, Long>().also { contadores = it }
    fun legendarios(): MutableSet<String> = legendarios ?: linkedSetOf<String>().also { legendarios = it }

    fun tiene(l: Logro) = l.id in otorgados()

    /** Suma y devuelve el total nuevo. */
    fun sumar(clave: String, n: Long): Long {
        val total = (contadores()[clave] ?: 0L) + n
        contadores()[clave] = total
        return total
    }

    fun valor(clave: String): Long = contadores()[clave] ?: 0L

    /** Logros de saldo que corresponden con este saldo (incluye bancarrota). */
    fun logrosPorSaldo(saldo: Long): List<Logro> {
        val previo = valor(Contador.SALDO_MAXIMO)
        if (saldo > previo) contadores()[Contador.SALDO_MAXIMO] = saldo
        val maximo = maxOf(previo, saldo)
        return buildList {
            if (saldo >= Metas.MILLON) add(Logro.PRIMER_MILLON)
            if (saldo >= Metas.MCPATO) add(Logro.RICO_MCPATO)
            if (saldo >= Metas.AVARICIA) add(Logro.AVARICIA)
            if (saldo == 0L && maximo > Metas.BANCARROTA_PREVIO) add(Logro.BANCARROTA)
        }.filterNot { tiene(it) }
    }
}

/** banco_minecraft_per/jugadores.json */
data class RegistroJugadores(var jugadores: MutableMap<String, DatosJugador>? = linkedMapOf()) {
    fun de(uuid: String): DatosJugador =
        (jugadores ?: linkedMapOf<String, DatosJugador>().also { jugadores = it }).getOrPut(uuid) { DatosJugador() }
}
