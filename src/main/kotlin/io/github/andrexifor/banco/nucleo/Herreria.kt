package io.github.andrexifor.banco.nucleo

/**
 * Ítem legendario que vende el Mercader. config/banco_minecraft_per/legendarios.json
 * Los encantamientos pueden superar el máximo vanilla (Afilado 6, Protección 5…).
 */
data class Legendario(
    var id: String = "",
    var nombre: String = "",
    var item: String = "",
    /** "minecraft:sharpness" -> 6 */
    var encantamientos: MutableMap<String, Int> = linkedMapOf(),
    var precio: Long = 0,
    var descripcion: String = "",
)

data class CatalogoLegendarios(var legendarios: MutableList<Legendario> = mutableListOf()) {
    fun normalizado(): CatalogoLegendarios = CatalogoLegendarios(
        legendarios.filter { it.id.isNotBlank() && it.item.isNotBlank() && it.precio > 0 }
            .map { l -> l.copy(encantamientos = LinkedHashMap(l.encantamientos.filterValues { it in 1..255 })) }
            .distinctBy { it.id }
            .toMutableList(),
    )

    companion object {
        fun porDefecto(): CatalogoLegendarios {
            fun l(id: String, nombre: String, item: String, precio: Long, desc: String, vararg e: Pair<String, Int>) =
                Legendario(id, nombre, "minecraft:$item", e.associate { "minecraft:${it.first}" to it.second }.toMutableMap(), precio, desc)
            return CatalogoLegendarios(
                mutableListOf(
                    l("espada", "Filo del Inca", "netherite_sword", 1_000_000, "Afilado VI",
                        "sharpness" to 6, "unbreaking" to 5, "looting" to 4, "fire_aspect" to 2, "sweeping_edge" to 3),
                    l("hacha", "Hacha del Huayna", "netherite_axe", 900_000, "Afilado VI y Eficiencia VI",
                        "sharpness" to 6, "efficiency" to 6, "unbreaking" to 5),
                    l("pico", "Pico de Cerro Rico", "netherite_pickaxe", 900_000, "Eficiencia VI y Fortuna IV",
                        "efficiency" to 6, "fortune" to 4, "unbreaking" to 5),
                    l("arco", "Arco del Cóndor", "bow", 800_000, "Poder VI e Infinidad",
                        "power" to 6, "punch" to 2, "flame" to 1, "infinity" to 1, "unbreaking" to 5),
                    l("tridente", "Tridente de Pachacámac", "trident", 700_000, "Empalamiento VI",
                        "impaling" to 6, "loyalty" to 3, "channeling" to 1, "unbreaking" to 5),
                    l("casco", "Casco del Sol", "netherite_helmet", 600_000, "Protección V",
                        "protection" to 5, "unbreaking" to 5, "respiration" to 3, "aqua_affinity" to 1),
                    l("pechera", "Pechera del Sol", "netherite_chestplate", 600_000, "Protección V",
                        "protection" to 5, "unbreaking" to 5, "thorns" to 3),
                    l("pantalones", "Grebas del Sol", "netherite_leggings", 600_000, "Protección V",
                        "protection" to 5, "unbreaking" to 5, "swift_sneak" to 3),
                    l("botas", "Botas del Sol", "netherite_boots", 600_000, "Protección V y Caída de pluma V",
                        "protection" to 5, "unbreaking" to 5, "feather_falling" to 5, "depth_strider" to 3),
                ),
            )
        }
    }
}

/** Tipos de kit de reparación y qué reparan. */
enum class TipoKit(val id: String, val nombre: String) {
    ARMADURA("armadura", "Kit de reparación de armadura"),
    ARMAS("armas", "Kit de reparación de armas"),
    HERRAMIENTAS("herramientas", "Kit de reparación de herramientas"),
    ;

    /** ¿Este kit repara el ítem en la mano? [ruta] es el id sin "minecraft:", p. ej. "diamond_sword". */
    fun reparaEnMano(ruta: String): Boolean = when (this) {
        ARMADURA -> false // la armadura se repara desde los espacios equipados
        ARMAS -> ruta.endsWith("_sword") || ruta.endsWith("_axe") || ruta in ARMAS_SUELTAS
        HERRAMIENTAS -> ruta.endsWith("_pickaxe") || ruta.endsWith("_shovel") || ruta.endsWith("_hoe") ||
            ruta.endsWith("_axe") || ruta in HERRAMIENTAS_SUELTAS
    }

    companion object {
        private val ARMAS_SUELTAS = setOf("bow", "crossbow", "trident", "mace")
        private val HERRAMIENTAS_SUELTAS = setOf("shears", "fishing_rod", "flint_and_steel", "elytra", "shield")

        fun porId(id: String): TipoKit? = entries.firstOrNull { it.id == id }
    }
}

/** config/banco_minecraft_per/herrero.json */
data class ConfigHerrero(
    /** Reparar = (base + porPorcentaje × % de daño) × multiplicador si es legendario. */
    var reparacionBase: Long = 50,
    var reparacionPorPorcentaje: Long = 5,
    var multiplicadorLegendario: Double = 3.0,
    /** Combinar libro: precio × nivel resultante de cada encantamiento aplicado. */
    var combinarPorNivel: Long = 150,
    /** Nivel máximo que fabrica el herrero (0 = el máximo vanilla de cada encantamiento). */
    var nivelMaximoHerrero: Int = 0,
    /** Encantamientos que el herrero no aplica, p. ej. ["minecraft:mending"]. */
    var noCombinables: MutableList<String> = mutableListOf(),
    var renombrar: Long = 50,
    /** Precio de cada kit: "armadura_1", "armadura_3", "armas_1"… */
    var kits: MutableMap<String, Long> = linkedMapOf(
        "armadura_1" to 2_500, "armadura_3" to 6_000,
        "armas_1" to 1_500, "armas_3" to 3_500,
        "herramientas_1" to 1_500, "herramientas_3" to 3_500,
    ),
    /** % extra sobre los servicios del herrero y los kits. */
    var impuestoServiciosPorcentaje: Double = 5.0,
) {
    fun normalizada(): ConfigHerrero = copy(
        reparacionBase = reparacionBase.coerceAtLeast(0),
        reparacionPorPorcentaje = reparacionPorPorcentaje.coerceAtLeast(0),
        multiplicadorLegendario = multiplicadorLegendario.coerceIn(1.0, 100.0),
        combinarPorNivel = combinarPorNivel.coerceAtLeast(0),
        nivelMaximoHerrero = nivelMaximoHerrero.coerceIn(0, 255),
        renombrar = renombrar.coerceAtLeast(0),
        impuestoServiciosPorcentaje = impuestoServiciosPorcentaje.coerceIn(0.0, 100.0),
    )

    fun precioKit(tipo: TipoKit, usos: Int): Long? = kits["${tipo.id}_$usos"]?.takeIf { it > 0 }

    /** Precio de reparar un ítem con [dano] de [danoMaximo]. 0 si no está dañado. */
    fun precioReparacion(dano: Int, danoMaximo: Int, legendario: Boolean): Long {
        if (dano <= 0 || danoMaximo <= 0) return 0
        val porcentaje = ((dano.toLong() * 100 + danoMaximo - 1) / danoMaximo).coerceIn(1, 100)
        val bruto = reparacionBase + reparacionPorPorcentaje * porcentaje
        return if (legendario) Math.round(bruto * multiplicadorLegendario) else bruto
    }

    /**
     * Nivel que queda al combinar un libro de nivel [libro] sobre un ítem que ya tiene [actual]:
     * iguales suben uno (como en el yunque), distintos se queda el mayor, con tope [tope].
     * Devuelve [actual] si no mejora.
     */
    fun nivelCombinado(actual: Int, libro: Int, tope: Int): Int {
        if (actual > tope) return actual // ya supera lo que fabrica el herrero (p. ej. legendario)
        val nuevo = if (actual == libro) actual + 1 else maxOf(actual, libro)
        return minOf(nuevo, tope).coerceAtLeast(actual)
    }
}
