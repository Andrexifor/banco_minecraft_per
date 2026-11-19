package io.github.andrexifor.banco.nucleo

/**
 * Zona protegida del servidor: dentro de ella solo los op (o quien tenga
 * banco_minecraft_per.zonas.editar) pueden romper, poner o usar bloques.
 * dimension: "overworld", "the_nether" o "the_end". Las esquinas se pueden dar en cualquier orden.
 */
data class Zona(
    var nombre: String = "zona",
    var dimension: String = "overworld",
    var x1: Int = 0,
    var y1: Int = -64,
    var z1: Int = 0,
    var x2: Int = 0,
    var y2: Int = 320,
    var z2: Int = 0,
    var activa: Boolean = true,
) {
    /** true si (x, y, z) está dentro, ampliando la zona 'margen' bloques por cada lado. */
    fun contiene(dim: String, x: Int, y: Int, z: Int, margen: Int = 0): Boolean =
        activa && dim == dimension &&
            x in (minOf(x1, x2) - margen)..(maxOf(x1, x2) + margen) &&
            y in (minOf(y1, y2) - margen)..(maxOf(y1, y2) + margen) &&
            z in (minOf(z1, z2) - margen)..(maxOf(z1, z2) + margen)
}

/** config/banco_minecraft_per/zonas.json */
data class ConfigZonas(
    var zonas: List<Zona>? = porDefecto(),
) {
    fun normalizada(): ConfigZonas = ConfigZonas(
        (zonas ?: porDefecto()).map {
            it.copy(
                nombre = it.nombre.ifBlank { "zona" },
                dimension = it.dimension.removePrefix("minecraft:").ifBlank { "overworld" },
            )
        },
    )

    /** Activa o desactiva por nombre. Devuelve false si no existe. */
    fun cambiar(nombre: String, activa: Boolean): Boolean {
        val zona = zonas.orEmpty().firstOrNull { it.nombre == nombre } ?: return false
        zona.activa = activa
        return true
    }

    fun buscar(dim: String, x: Int, y: Int, z: Int, margen: Int = 0): Zona? =
        zonas.orEmpty().firstOrNull { it.contiene(dim, x, y, z, margen) }

    companion object {
        /**
         * Plaza inca del spawn (datapack plaza_inca) y sala del portal en el Nether con su túnel
         * y el mercado negro.
         * Vienen apagadas: las enciende /function plaza_inca:construir con /banco zonas activar.
         */
        fun porDefecto(): List<Zona> = listOf(
            Zona("plaza_spawn", "overworld", 76, -64, 44, 116, 320, 84, activa = false),
            Zona("sala_portal_nether", "the_nether", 4, 76, -17, 56, 127, 11, activa = false),
        )
    }
}
