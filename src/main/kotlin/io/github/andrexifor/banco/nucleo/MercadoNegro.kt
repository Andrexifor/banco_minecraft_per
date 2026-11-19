package io.github.andrexifor.banco.nucleo

/** Punto de teletransporte. dimension: "overworld", "the_nether" o "the_end". */
data class Destino(
    var dimension: String = "the_nether",
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var yaw: Float = 0f,
)

/**
 * config/banco_minecraft_per/mercado_negro.json
 * Por defecto, la sala del mercado negro que construye el datapack plaza_inca
 * junto a la sala del portal en el Nether.
 */
data class ConfigMercadoNegro(
    /** Adonde lleva la puerta sellada (dentro del mercado negro). */
    var entrada: Destino? = Destino("the_nether", 11.5, 116.0, -5.5, 180f),
    /** Adonde lleva la puerta de salida (sala del portal). */
    var salida: Destino? = Destino("the_nether", 11.5, 116.0, 1.5, 0f),
) {
    fun normalizada() = ConfigMercadoNegro(entrada ?: ConfigMercadoNegro().entrada, salida ?: ConfigMercadoNegro().salida)
}
