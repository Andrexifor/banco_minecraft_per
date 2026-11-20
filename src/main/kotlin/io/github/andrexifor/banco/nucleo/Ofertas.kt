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
                Oferta("sombrero_chullo", "Chullo", sombrero("chullo"), precio = 150_000,
                    descripcion = "Cosmético: no ocupa el casco", anunciar = false),
                Oferta("sombrero_chotano", "Sombrero chotano", sombrero("chotano"), precio = 200_000,
                    descripcion = "Cosmético: no ocupa el casco", anunciar = false),
                Oferta("sombrero_montera", "Montera cusqueña", sombrero("montera"), precio = 250_000,
                    descripcion = "Cosmético: no ocupa el casco", anunciar = false),
                Oferta("sombrero_mascaipacha", "Mascaipacha", sombrero("mascaipacha"), precio = 2_000_000,
                    descripcion = "Cosmético: la borla real del Sapa Inca"),
            ),
        )

        // ---------- sombreros cosméticos (Sombreros.kt los pone sobre la cabeza) ----------
        class InfoSombrero(val id: String, val nombre: String, val color: String, val lore: String)

        val SOMBREROS = listOf(
            InfoSombrero("chullo", "Chullo", "red", "Tejido con lana de alpaca"),
            InfoSombrero("montera", "Montera cusqueña", "dark_red", "La de las fiestas del Cusco"),
            InfoSombrero("chotano", "Sombrero chotano", "yellow", "Paja toquilla de Chota"),
            InfoSombrero("mascaipacha", "Mascaipacha", "gold", "La borla real del Sapa Inca"),
            InfoSombrero("chullo_navideno", "Chullo navideño", "red", "Regalo de Navidad"),
            InfoSombrero("sombrero_fiesta", "Sombrero de Año Nuevo", "gold", "Regalo de Año Nuevo"),
            InfoSombrero("sombrero_patrio", "Sombrero bicolor", "red", "Regalo de Fiestas Patrias"),
        )

        /** El ítem de un sombrero en formato /give. */
        fun sombrero(id: String): String {
            val s = SOMBREROS.firstOrNull { it.id == id } ?: return ""
            return "minecraft:paper[item_model=\"banco_minecraft_per:sombrero_$id\",max_stack_size=1," +
                nombre(s.nombre, s.color) + "," +
                lore(s.lore, "Cosmético: no ocupa el casco", "Clic derecho: te lo pones o te lo quitas") + "," +
                "custom_data={$MARCA:{sombrero:\"$id\"}}]"
        }

        /** Cuerpo de entity_data para huevos: animal domesticado, persistente y con nombre. */
        private fun animal(id: String, nombre: String, color: String, extra: String) =
            "entity_data={id:\"minecraft:$id\",Tame:1b,PersistenceRequired:1b," +
                "CustomName:{text:\"$nombre\",color:\"$color\"},$extra}"

        private fun huevo(egg: String, entityData: String, nombreHuevo: String, color: String, vararg l: String) =
            "minecraft:$egg[" + listOf(entityData, nombre(nombreHuevo, color), lore(*l)).joinToString(",") + "]"

        private const val SILLA = "saddle:{id:\"minecraft:saddle\",count:1}"
        const val NOMBRE_ZOMBI = "Caballo zombi"
        const val NOMBRE_ESQUELETO = "Caballo esqueleto"
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

        /**
         * Montura (silla) con aspecto propio: el resource pack la dibuja con las capas
         * <entidad>_saddle de assets/banco_minecraft_per/equipment/<asset>.json.
         */
        private fun montura(asset: String, entidad: String, nombre: String, color: String) =
            "{id:\"minecraft:saddle\",count:1,components:{" +
                "\"minecraft:equippable\":{slot:\"saddle\",asset_id:\"banco_minecraft_per:$asset\"," +
                "allowed_entities:\"minecraft:$entidad\",equip_sound:\"minecraft:entity.horse.saddle\",can_be_sheared:true}," +
                "\"minecraft:custom_name\":{text:\"$nombre\",color:\"$color\",italic:false}}}"

        /** Monturas propias en formato /give (Monturas.kt las usa para cambiar las sillas simples). */
        val MONTURA_ESQUELETO = montura("montura_ukhu_pacha", "skeleton_horse", "Montura del Ukhu Pacha", "dark_purple")
        val MONTURA_ZOMBI = montura("montura_titicaca", "zombie_horse", "Montura del Titicaca", "aqua")
        val MONTURA_MULA = montura("aguayo_de_carga", "mule", "Aguayo de carga", "gold")

        /** Resistencia al fuego infinita: los caballos no muertos no se queman con el sol. */
        private const val SIN_FUEGO =
            "active_effects:[{id:\"minecraft:fire_resistance\",duration:-1,amplifier:0b,show_particles:0b,show_icon:0b}]"

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
                            "skeleton_horse", NOMBRE_ESQUELETO, "gray",
                            "Health:40f,attributes:[{id:\"minecraft:max_health\",base:40d}," +
                                "{id:\"minecraft:movement_speed\",base:${VELOCIDAD_ESPECIAL}d}],$SIN_FUEGO," +
                                "equipment:{saddle:$MONTURA_ESQUELETO}",
                        ),
                        "Huevo de caballo esqueleto", "gray",
                        "40 de vida (el doble de un caballo)", "Más rápido que cualquier caballo",
                        "Con la Montura del Ukhu Pacha; no le afecta el fuego",
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
                                "{id:\"minecraft:water_movement_efficiency\",base:1d}],$SIN_FUEGO," +
                                "equipment:{saddle:$MONTURA_ZOMBI}",
                        ),
                        "Huevo de caballo zombi", "dark_green",
                        "Camina sobre el agua (la congela al pasar)", "Más rápido que cualquier caballo",
                        "Con la Montura del Titicaca; el sol no lo quema",
                    ),
                    precio = 350_000, descripcion = "Camina sobre el agua y es más rápido que cualquier caballo", anunciar = false,
                ),
                Oferta(
                    "mula", NOMBRE_MULA,
                    huevo(
                        "mule_spawn_egg",
                        animal(
                            "mule", NOMBRE_MULA, "gold",
                            "ChestedHorse:1b,attributes:[{id:\"minecraft:movement_speed\",base:0.3d}],equipment:{saddle:$MONTURA_MULA}",
                        ),
                        "Huevo de mula de carga", "gold",
                        "Se maneja como un caballo; con su aguayo de carga", "Shift + clic derecho: alforja de 54 casillas",
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
                Oferta(
                    "flor_traslado", "Flor de traslado",
                    especial("glowstone_dust", "flor_traslado", "flor", "1b", "Flor de traslado", "yellow", null,
                        "Clic derecho a un animal: lo guarda en la flor", "Clic derecho al suelo: lo libera donde estés",
                        "Se gasta al liberar al animal"),
                    cantidad = 4, precio = 40_000, descripcion = "4 flores; cada una lleva un animal una vez", anunciar = false,
                ),
                Oferta(
                    "condor", "Cóndor guardián",
                    especial("feather", "pluma_condor", "condor", "1b", "Pluma del cóndor guardián", "gold", 1,
                        "Clic derecho: llama o despide a tu cóndor", "Vuela a tu alrededor y ataca a los monstruos",
                        "No se puede montar; se queda contigo aunque te desconectes"),
                    precio = 5_000_000, descripcion = "Un cóndor andino que te protege volando a tu alrededor",
                ),
                Oferta("perro", "Perro peruano sin pelo", MASCOTA_PERRO, precio = 3_000_000,
                    descripcion = "Te acompaña y muerde a los monstruos que se te acercan"),
                Oferta("cuy", "Cuy", MASCOTA_CUY, precio = 300_000,
                    descripcion = "Un cuy que trota a tu lado (no pelea)", anunciar = false),
                Oferta("vizcacha", "Vizcacha", MASCOTA_VIZCACHA, precio = 400_000,
                    descripcion = "Una vizcacha que te sigue a saltitos (no pelea)", anunciar = false),
            ),
        )

        // ---------- mascotas de compañía (Mascotas.kt) ----------
        val MASCOTA_PERRO get() = especial("paper", "silbato_perro", "mascota", "\"perro\"", "Silbato del perro peruano", "gold", 1,
            "Clic derecho: llama o despide a tu perro", "Muerde a los monstruos que se te acercan",
            "Te acompaña siempre, aunque te desconectes")
        val MASCOTA_CUY get() = especial("paper", "mazorca_cuy", "mascota", "\"cuy\"", "Alfalfa del cuy", "green", 1,
            "Clic derecho: llama o despide a tu cuy", "Trota a tu lado (no pelea)")
        val MASCOTA_VIZCACHA get() = especial("paper", "pasto_vizcacha", "mascota", "\"vizcacha\"", "Ichu de la vizcacha", "yellow", 1,
            "Clic derecho: llama o despide a tu vizcacha", "Te sigue a saltitos (no pelea)")

        // ---------- artículos del Sembrador del Vacío: mochilas ----------

        /** Ítem especial: base vanilla con textura propia y una marca en custom_data. */
        private fun especial(
            base: String, modelo: String, clave: String, valor: String, nombre: String, color: String,
            pila: Int?, vararg lineas: String,
        ) = "minecraft:$base[" + listOfNotNull(
            "item_model=\"banco_minecraft_per:$modelo\"",
            pila?.let { "max_stack_size=$it" },
            nombre(nombre, color),
            lore(*lineas),
            "custom_data={$MARCA:{$clave:$valor}}",
        ).joinToString(",") + "]"

        fun sembradorPorDefecto() = CatalogoOfertas(
            mutableListOf(
                Oferta(
                    "chuspa", "Chuspa",
                    especial("leather", "chuspa", "mochila", "\"chuspa\"", "Chuspa", "green", 1,
                        "Bolsita andina de 5 casillas", "Clic derecho para abrirla",
                        "Con hilo de alpaca en la otra mano: se agranda"),
                    precio = 60_000, descripcion = "Mochila de 5 casillas",
                ),
                Oferta(
                    "qepi", "Q'epi",
                    especial("leather", "qepi", "mochila", "\"qepi\"", "Q'epi", "green", 1,
                        "Atado andino de 18 casillas", "Clic derecho para abrirlo",
                        "Con hilo de alpaca en la otra mano: se agranda"),
                    precio = 250_000, descripcion = "Mochila de 18 casillas",
                ),
                Oferta(
                    "gran_qepi", "Gran q'epi",
                    especial("leather", "gran_qepi", "mochila", "\"gran_qepi\"", "Gran q'epi", "green", 1,
                        "Gran atado andino de 27 casillas", "Clic derecho para abrirlo"),
                    precio = 700_000, descripcion = "Mochila de 27 casillas",
                ),
                Oferta(
                    "hilo_alpaca", "Hilo de alpaca",
                    especial("string", "hilo_alpaca", "mejora", "1b", "Hilo de alpaca", "aqua", null,
                        "Agranda una mochila sin perder lo que lleva:", "chuspa -> q'epi -> gran q'epi",
                        "Tenlo en la otra mano y abre la mochila"),
                    precio = 200_000, descripcion = "Agranda una mochila un tamaño",
                ),
                Oferta(
                    "chuspa_ukhu", "Chuspa del Ukhu Pacha",
                    especial("leather", "chuspa_ukhu", "mochila", "\"ukhu\"", "Chuspa del Ukhu Pacha", "dark_purple", 1,
                        "Abre tu cofre de Ender desde donde estés", "Clic derecho para abrirla"),
                    precio = 1_500_000, descripcion = "Acceso a tu cofre de Ender en cualquier lugar",
                ),
                Oferta(
                    "chuspa_enlazada", "Chuspa enlazada",
                    especial("leather", "chuspa_enlazada", "mochila", "\"enlazada\"", "Chuspa enlazada", "gold", 1,
                        "Se enlaza a un cofre simple (27 casillas):", "tenla en la otra mano y abre el cofre",
                        "Luego, clic derecho: abre ese cofre desde lejos", "Shift + clic derecho: la desenlaza"),
                    precio = 800_000, descripcion = "Abre un cofre simple tuyo desde cualquier lugar",
                ),
                Oferta(
                    "chuspa_enlazada_doble", "Chuspa enlazada doble",
                    especial("leather", "chuspa_enlazada_doble", "mochila", "\"enlazada_doble\"", "Chuspa enlazada doble", "gold", 1,
                        "Se enlaza a un cofre doble (54 casillas):", "tenla en la otra mano y abre el cofre",
                        "Luego, clic derecho: abre ese cofre desde lejos", "Shift + clic derecho: la desenlaza"),
                    precio = 1_600_000, descripcion = "Abre un cofre doble tuyo desde cualquier lugar",
                ),
            ),
        )

        // ---------- máquinas del Pirqakamayuq ----------

        private fun maqueta(tipo: String, nombre: String, vararg l: String) =
            especial("paper", "maqueta_$tipo", "maqueta", "\"$tipo\"", nombre, "gold", 1,
                *l, "Clic derecho sobre el suelo: se arma (3x3)", "Shift + clic derecho al centro, mano vacía: se desarma")

        private fun mejora(id: String, modelo: String, nombre: String, vararg l: String) =
            especial("paper", modelo, "mejora_maquina", "\"$id\"", nombre, "aqua", 16, *l, "Clic derecho a la máquina para usarla")

        fun pirqaPorDefecto() = CatalogoOfertas(
            mutableListOf(
                Oferta("cantera", "Cantera inca", maqueta("cantera", "Maqueta: Cantera inca",
                    "La piedra del centro vuelve a salir al minarla", "Cada 10 s (con cinceles, hasta 1 s)"),
                    precio = 1_500_000, descripcion = "Piedra infinita"),
                Oferta("cincel", "Cincel del Apu", mejora("cincel", "cincel_apu", "Cincel del Apu",
                    "Cantera más rápida: 10 -> 7 -> 5 -> 3 -> 1 s"),
                    precio = 750_000, descripcion = "Acelera la cantera un nivel"),
                Oferta("veta", "Veta de la montaña", mejora("veta", "veta_montana", "Veta de la montaña",
                    "Vetas I: carbón · II: hierro, cobre y tumbaga", "III: sin carbón y más tumbaga"),
                    precio = 2_500_000, descripcion = "Sube las vetas de la cantera un nivel"),
                Oferta("brazo", "Brazo de la cantera", mejora("brazo", "brazo_cantera", "Brazo de la cantera",
                    "La cantera pica sola cada 5 s", "y lo deja en un cofre pegado a ella"),
                    precio = 30_000_000, descripcion = "Cantera automática"),
                Oferta("fuente", "Fuente del Misti", maqueta("fuente", "Maqueta: Fuente del Misti",
                    "Un balde de lava cada 5 minutos", "Con un tanque cerca, la lava va al tanque"),
                    precio = 6_000_000, descripcion = "Lava cada 5 minutos"),
                Oferta("corazon", "Corazón del Misti", mejora("corazon", "corazon_misti", "Corazón del Misti",
                    "La fuente da lava en la mitad del tiempo", "Solo una por fuente"),
                    precio = 8_000_000, descripcion = "Fuente del Misti al doble de rápido"),
                Oferta("tanque", "Tanque de vidrio y acero", maqueta("tanque", "Maqueta: Tanque de vidrio y acero",
                    "Guarda 16 baldes de lava", "Clic con balde: saca o guarda lava"),
                    precio = 2_500_000, descripcion = "Guarda lava (16 baldes)"),
                Oferta("aro", "Aro de acero", mejora("aro", "aro_acero", "Aro de acero",
                    "Agranda un tanque: 16 -> 32 -> 48 -> 80", "-> 160 -> 400 -> 800 baldes"),
                    precio = 3_000_000, descripcion = "Agranda el tanque un tamaño"),
                Oferta("horno", "Huayrachina", maqueta("horno", "Maqueta: Huayrachina (horno inca)",
                    "Funde sin carbón: toma lava de un tanque", "tuyo a 8 bloques o menos"),
                    precio = 4_000_000, descripcion = "Horno que funde con la lava del tanque"),
            ),
        )

        // ---------- pociones del Kallawaya ----------

        private fun pocion(id: String, nombre: String, color: String, vararg lineas: String) =
            "minecraft:potion[" + listOf(
                "item_model=\"banco_minecraft_per:pocion_$id\"",
                "max_stack_size=16",
                "tooltip_display={hidden_components:[\"minecraft:potion_contents\"]}",
                nombre(nombre, color),
                lore(*lineas),
                "custom_data={$MARCA:{pocion:\"$id\"}}",
            ).joinToString(",") + "]"

        fun pocionesPorDefecto() = CatalogoOfertas(
            mutableListOf(
                Oferta(
                    "amparo", "Poción de amparo",
                    pocion("amparo", "Poción de amparo", "gold",
                        "Hasta tu próxima muerte, al morir conservas:", "armadura, armas, mochilas y flores con animales",
                        "Lo demás cae como siempre"),
                    precio = 400_000, descripcion = "Al morir conservas armadura, armas y mochilas",
                ),
                Oferta(
                    "regreso", "Poción de regreso",
                    pocion("regreso", "Poción de regreso", "aqua",
                        "Te lleva a tu cama a los 2 segundos", "Si no tienes cama, no se gasta"),
                    precio = 120_000, descripcion = "Te lleva a tu cama",
                ),
                Oferta(
                    "muerte", "Poción del último aliento",
                    pocion("muerte", "Poción del último aliento", "dark_purple",
                        "Te lleva a donde moriste por última vez", "Llegas con resistencia al fuego y caída lenta",
                        "Si no hay lugar al que volver, no se gasta"),
                    precio = 250_000, descripcion = "Te lleva a donde moriste",
                ),
                Oferta(
                    "buscador", "Poción del buscador",
                    pocion("buscador", "Poción del buscador", "green",
                        "20 segundos viendo a través de las paredes", "los minerales del chunk donde estás",
                        "Solo tú los ves"),
                    precio = 80_000, descripcion = "Muestra los minerales del chunk por 20 s",
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
