package io.github.andrexifor.banco.nucleo

/**
 * Oferta de un mercader especial (bromas o mercado negro).
 * [item] es un ítem en formato de comando, con componentes, p. ej.
 *   minecraft:stick[enchantments={"minecraft:knockback":5}]
 * El mod lo interpreta igual que /give, así que se puede editar a mano en el JSON.
 */
data class Oferta(
    var id: String = "",
    var nombre: String = "",
    var item: String = "",
    var cantidad: Int = 1,
    var precio: Long = 0,
    var descripcion: String = "",
    /** Anunciar la compra en el chat de todos. */
    var anunciar: Boolean = true,
)

data class CatalogoOfertas(var ofertas: MutableList<Oferta>? = mutableListOf()) {
    fun normalizado(defecto: () -> CatalogoOfertas): CatalogoOfertas {
        val lista = ofertas ?: return defecto()
        return CatalogoOfertas(
            lista.filter { it.id.isNotBlank() && it.item.isNotBlank() && it.precio > 0 }
                .map { it.copy(cantidad = it.cantidad.coerceIn(1, 64)) }
                .distinctBy { it.id }
                .toMutableList(),
        )
    }

    fun buscar(id: String): Oferta? = ofertas.orEmpty().firstOrNull { it.id == id }

    companion object {
        private const val MARCA = "banco_minecraft_per"

        /** Marca de broma dentro de custom_data, para reconocer el ítem después. */
        private fun marca(id: String) = "custom_data={$MARCA:{broma:\"$id\"}}"

        private fun nombre(t: String, color: String) = "custom_name={text:\"$t\",color:\"$color\",italic:false}"

        private fun lore(vararg lineas: String) =
            "lore=[" + lineas.joinToString(",") { "{text:\"$it\",color:\"gray\",italic:false}" } + "]"

        const val VARA_VERDAD = "vara_verdad"
        const val NOMBRE_GHAST = "Puyu del Inti"

        fun bromasPorDefecto() = CatalogoOfertas(
            mutableListOf(
                Oferta(
                    VARA_VERDAD, "Vara de la Verdad",
                    "minecraft:stick[" + listOf(
                        "enchantments={\"minecraft:knockback\":5}",
                        nombre("Vara de la Verdad", "gold"),
                        lore("Empuje V", "A veces la verdad cae como un rayo"),
                        marca(VARA_VERDAD),
                    ).joinToString(",") + "]",
                    precio = 2_500_000, descripcion = "Empuje V y, muy de vez en cuando, un rayo",
                ),
                Oferta(
                    "puyu_inti", NOMBRE_GHAST,
                    "minecraft:happy_ghast_spawn_egg[" + listOf(
                        "entity_data={id:\"minecraft:happy_ghast\",CustomName:{text:\"$NOMBRE_GHAST\",color:\"gold\"}," +
                            "PersistenceRequired:1b,attributes:[{id:\"minecraft:flying_speed\",base:0.1d}]}",
                        nombre("Huevo de $NOMBRE_GHAST", "gold"),
                        lore("Ghast feliz que vuela el doble de rápido", "Nube del Sol, en quechua"),
                        marca("puyu_inti"),
                    ).joinToString(",") + "]",
                    precio = 3_000_000, descripcion = "Ghast feliz que vuela el doble de rápido",
                ),
                Oferta(
                    "papa_legendaria", "Papa legendaria",
                    "minecraft:potato[" + listOf(
                        "enchantment_glint_override=true",
                        nombre("Papa legendaria", "light_purple"),
                        lore("No sirve para nada.", "Pero es legendaria."),
                        marca("papa_legendaria"),
                    ).joinToString(",") + "]",
                    precio = 5_000_000, descripcion = "No sirve para nada, pero es legendaria",
                ),
                Oferta(
                    "corona_barrio", "Corona del rey del barrio",
                    "minecraft:golden_helmet[" + listOf(
                        "enchantments={\"minecraft:binding_curse\":1,\"minecraft:vanishing_curse\":1}",
                        nombre("Corona del rey del barrio", "yellow"),
                        lore("Una vez puesta, no te la quitas", "Si mueres, desaparece"),
                        marca("corona_barrio"),
                    ).joinToString(",") + "]",
                    precio = 1_000_000, descripcion = "No te la puedes quitar. Si mueres, desaparece",
                ),
            ),
        )

        fun contrabandoPorDefecto() = CatalogoOfertas(
            mutableListOf(
                Oferta("elitros", "Élitros", "minecraft:elytra", precio = 3_000_000, descripcion = "Recién traídos del End"),
                Oferta("totem", "Tótem de la inmortalidad", "minecraft:totem_of_undying", precio = 800_000),
                Oferta("manzana_encantada", "Manzana de oro encantada", "minecraft:enchanted_golden_apple", precio = 1_500_000),
                Oferta(
                    "libro_reparacion", "Libro de Reparación",
                    "minecraft:enchanted_book[stored_enchantments={\"minecraft:mending\":1}]",
                    precio = 500_000, descripcion = "No sirve en legendarios",
                ),
                Oferta(
                    "plantilla_netherite", "Plantilla de mejora de netherita",
                    "minecraft:netherite_upgrade_smithing_template", precio = 400_000,
                ),
                Oferta("estrella_nether", "Estrella del Nether", "minecraft:nether_star", precio = 2_000_000),
            ),
        )
    }
}
