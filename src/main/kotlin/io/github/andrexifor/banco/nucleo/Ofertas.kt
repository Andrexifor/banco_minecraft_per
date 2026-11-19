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

        /** Cuerpo de entity_data para huevos: animal domesticado, persistente y con nombre. */
        private fun animal(id: String, nombre: String, color: String, extra: String) =
            "entity_data={id:\"minecraft:$id\",Tame:1b,PersistenceRequired:1b," +
                "CustomName:{text:\"$nombre\",color:\"$color\"},$extra}"

        private fun huevo(egg: String, entityData: String, nombreHuevo: String, color: String, vararg l: String) =
            "minecraft:$egg[" + listOf(entityData, nombre(nombreHuevo, color), lore(*l)).joinToString(",") + "]"

        private const val SILLA = "saddle:{id:\"minecraft:saddle\",count:1}"
        const val NOMBRE_ZOMBI = "Caballo zombi"
        const val NOMBRE_MULA = "Mula de carga"

        /** Velocidad de los caballos especiales: por encima del máximo natural (0.3375). */
        private const val VELOCIDAD_ESPECIAL = 0.36

        /**
         * Equipo con aspecto propio: el resource pack dibuja assets/banco_minecraft_per/equipment/<asset>.json
         * solo en los animales que llevan ESTE ítem (el resto de su especie se ve normal).
         */
        private fun equipoCustom(item: String, asset: String, entidades: String, sonido: String, nombre: String) =
            "{id:\"minecraft:$item\",count:1,components:{" +
                "\"minecraft:equippable\":{slot:\"body\",asset_id:\"banco_minecraft_per:$asset\"," +
                "allowed_entities:\"$entidades\",equip_sound:\"$sonido\",can_be_sheared:true}," +
                "\"minecraft:custom_name\":{text:\"$nombre\",color:\"gold\",italic:false}}}"

        private val ARNES_PUYU = equipoCustom(
            "yellow_harness", "puyu_inti", "minecraft:happy_ghast", "minecraft:entity.happy_ghast.equip", "Arnés del Inti",
        )
        private val ARMADURA_INCA = equipoCustom(
            "golden_horse_armor", "armadura_inca", "minecraft:horse", "minecraft:item.armor.equip_horse", "Armadura inca",
        )

        fun animalesPorDefecto() = CatalogoOfertas(
            mutableListOf(
                Oferta(
                    "puyu_inti", NOMBRE_GHAST,
                    "minecraft:happy_ghast_spawn_egg[" + listOf(
                        "entity_data={id:\"minecraft:happy_ghast\",PersistenceRequired:1b," +
                            "CustomName:{text:\"$NOMBRE_GHAST\",color:\"gold\"}," +
                            "attributes:[{id:\"minecraft:flying_speed\",base:0.1d}]," +
                            "equipment:{body:$ARNES_PUYU}}",
                        nombre("Huevo de $NOMBRE_GHAST", "gold"),
                        lore("Ghast feliz que vuela el doble de rápido", "Viene con el Arnés del Inti", "Nube del Sol, en quechua"),
                        marca("puyu_inti"),
                    ).joinToString(",") + "]",
                    precio = 3_000_000, descripcion = "Vuela el doble de rápido; con el Arnés del Inti",
                ),
                Oferta(
                    "caballo", "Caballo de paso",
                    huevo(
                        "horse_spawn_egg",
                        animal(
                            "horse", "Caballo de paso", "gold",
                            "Variant:1024," +
                                "attributes:[{id:\"minecraft:movement_speed\",base:0.3d},{id:\"minecraft:jump_strength\",base:0.9d}]," +
                                "equipment:{$SILLA,body:$ARMADURA_INCA}",
                        ),
                        "Huevo de caballo de paso", "gold",
                        "Con armadura inca", "Rápido y buen saltador; ya ensillado",
                    ),
                    precio = 250_000, descripcion = "Rápido, buen saltador, ensillado y con armadura inca", anunciar = false,
                ),
                Oferta(
                    "caballo_esqueleto", "Caballo esqueleto",
                    huevo(
                        "skeleton_horse_spawn_egg",
                        animal(
                            "skeleton_horse", "Caballo esqueleto", "gray",
                            "Health:40f,attributes:[{id:\"minecraft:max_health\",base:40d}," +
                                "{id:\"minecraft:movement_speed\",base:${VELOCIDAD_ESPECIAL}d}],equipment:{$SILLA}",
                        ),
                        "Huevo de caballo esqueleto", "gray",
                        "40 de vida (el doble de un caballo)", "Más rápido que cualquier caballo; ensillado",
                    ),
                    precio = 400_000, descripcion = "El doble de vida y más rápido que cualquier caballo", anunciar = false,
                ),
                Oferta(
                    "caballo_zombi", NOMBRE_ZOMBI,
                    huevo(
                        "zombie_horse_spawn_egg",
                        animal(
                            "zombie_horse", NOMBRE_ZOMBI, "dark_green",
                            "attributes:[{id:\"minecraft:movement_speed\",base:${VELOCIDAD_ESPECIAL}d}," +
                                "{id:\"minecraft:water_movement_efficiency\",base:1d}],equipment:{$SILLA}",
                        ),
                        "Huevo de caballo zombi", "dark_green",
                        "Camina sobre el agua (la congela al pasar)", "Más rápido que cualquier caballo; ensillado",
                    ),
                    precio = 350_000, descripcion = "Camina sobre el agua y es más rápido que cualquier caballo", anunciar = false,
                ),
                Oferta(
                    "mula", NOMBRE_MULA,
                    huevo(
                        "mule_spawn_egg",
                        animal(
                            "mule", NOMBRE_MULA, "gold",
                            "ChestedHorse:1b,attributes:[{id:\"minecraft:movement_speed\",base:0.3d}],equipment:{$SILLA}",
                        ),
                        "Huevo de mula de carga", "gold",
                        "Se maneja como un caballo; ya ensillada", "Shift + clic derecho: alforja de 54 casillas",
                    ),
                    precio = 300_000, descripcion = "Montable, rápida y con alforja de 54 casillas", anunciar = false,
                ),
                Oferta(
                    "alpaquita", "Alpaquita",
                    huevo(
                        "llama_spawn_egg",
                        "entity_data={id:\"minecraft:llama\",Variant:1,Age:-24000,AgeLocked:1b,PersistenceRequired:1b," +
                            "CustomName:{text:\"Alpaquita\",color:\"light_purple\"}}",
                        "Huevo de Alpaquita", "light_purple",
                        "Una alpaca cría que nunca crece", "Ponle otro nombre con un nametag",
                    ),
                    precio = 80_000, descripcion = "Alpaca cría que se queda chiquita para siempre", anunciar = false,
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
