package io.github.andrexifor.banco.nucleo

import java.time.LocalDate
import kotlin.random.Random

/**
 * Encargos del Kuraka. Cada encargo se mide con las estadísticas del juego (matar, picar, fabricar,
 * pescar, caminar...) o pide entregar ítems en mano al Kuraka.
 *  - MATAR / PICAR / FABRICAR: [objetivos] son ids de entidades, bloques o ítems (se suman entre sí).
 *  - ESTADISTICA: [objetivos] tiene un id de estadística propia de Minecraft (minecraft:fish_caught...).
 *  - ENTREGAR: [objetivos] tiene el ítem que se entrega (se quita del inventario al cobrar).
 * [nivel]: 0 fácil, 1 medio, 2 difícil (los difíciles pagan también tumbaga).
 */
enum class TipoEncargo { MATAR, PICAR, FABRICAR, ESTADISTICA, ENTREGAR }

data class Encargo(
    val id: String,
    val tipo: TipoEncargo,
    val objetivos: List<String>,
    val cantidad: Int,
    val nivel: Int,
    val texto: String,
    /** Para mostrar: la estadística de distancia se guarda en centímetros. */
    val divisor: Int = 1,
    val icono: String,
) {
    val pago: Long get() = PAGOS[nivel]
    val tumbaga: Int get() = if (nivel == 2) 1 + (id.hashCode().and(0x7fffffff) % 2) else 0

    companion object {
        val PAGOS = longArrayOf(30_000, 120_000, 300_000)
    }
}

object Encargos {
    /** Cuántos encargos de cada nivel salen en cada renovación (5 fáciles, 3 medios, 2 difíciles = 10). */
    val POR_NIVEL = intArrayOf(5, 3, 2)

    private val NIVELES = arrayOf("fácil", "medio", "difícil")
    fun nombreNivel(n: Int) = NIVELES[n]

    val todos: List<Encargo> by lazy { construir() }
    private val porId: Map<String, Encargo> by lazy { todos.associateBy { it.id } }
    fun buscar(id: String) = porId[id]

    /**
     * Los encargos de una renovación. Se eligen al azar, pero con una semilla por día: así todos ven los
     * mismos y un reinicio del servidor no los cambia. La tanda del mediodía y la de la medianoche salen
     * de la misma mezcla del día, una a continuación de la otra, por eso no se repiten en el mismo día.
     */
    fun delCiclo(dia: LocalDate, tarde: Boolean): List<Encargo> {
        val azar = Random(dia.toEpochDay() * 7919 + 17)
        val r = ArrayList<Encargo>()
        for (nivel in 0..2) {
            val lista = todos.filter { it.nivel == nivel }.shuffled(azar)
            val n = POR_NIVEL[nivel]
            val desde = if (tarde) n else 0
            r += lista.drop(desde).take(n)
        }
        return r
    }

    private fun construir(): List<Encargo> {
        val r = ArrayList<Encargo>()
        fun agregar(tipo: TipoEncargo, clave: String, objetivos: List<String>, cantidades: IntArray, icono: String,
                    divisor: Int = 1, texto: (Int) -> String) {
            for (nivel in 0..2) {
                val n = cantidades.getOrElse(nivel) { 0 }
                if (n <= 0) continue
                r += Encargo("${tipo.name.lowercase()}:$clave:$nivel", tipo, objetivos, n, nivel, texto(n / divisor), divisor, icono)
            }
        }

        // ---------- matar ----------
        fun matar(id: String, nombre: String, vararg c: Int) =
            agregar(TipoEncargo.MATAR, id, listOf("minecraft:$id"), c, "minecraft:${id}_spawn_egg") { "Mata $it $nombre" }
        matar("zombie", "zombis", 20, 50)
        matar("skeleton", "esqueletos", 20, 50)
        matar("creeper", "creepers", 10, 30)
        matar("spider", "arañas", 15, 40)
        matar("cave_spider", "arañas de cueva", 10, 30)
        matar("enderman", "endermans", 0, 10, 30)
        matar("witch", "brujas", 0, 5, 15)
        matar("slime", "slimes", 15, 40)
        matar("drowned", "ahogados", 10, 30)
        matar("husk", "zombis momificados", 10, 30)
        matar("stray", "esqueletos glaciales", 10, 30)
        matar("bogged", "esqueletos pantanosos", 10, 30)
        matar("zombie_villager", "aldeanos zombi", 5, 15)
        matar("silverfish", "lepismas", 10, 30)
        matar("phantom", "phantoms", 0, 10, 25)
        matar("pillager", "saqueadores", 0, 15, 40)
        matar("vindicator", "vindicadores", 0, 5, 15)
        matar("evoker", "invocadores", 0, 0, 3)
        matar("vex", "vexes", 0, 5, 15)
        matar("ravager", "devastadores", 0, 0, 3)
        matar("guardian", "guardianes", 0, 10, 30)
        matar("elder_guardian", "guardián anciano", 0, 0, 1)
        matar("blaze", "blazes", 0, 15, 40)
        matar("wither_skeleton", "esqueletos wither", 0, 10, 30)
        matar("ghast", "ghasts", 0, 5, 15)
        matar("magma_cube", "cubos de magma", 0, 15, 40)
        matar("zombified_piglin", "piglins zombificados", 15, 40)
        matar("hoglin", "hoglins", 0, 10, 25)
        matar("zoglin", "zoglins", 0, 0, 5)
        matar("piglin_brute", "piglins brutos", 0, 0, 5)
        matar("shulker", "shulkers", 0, 0, 15)
        matar("breeze", "breezes", 0, 5, 15)
        matar("warden", "warden", 0, 0, 1)
        matar("wither", "wither", 0, 0, 1)
        matar("pig", "cerdos", 10)
        matar("cow", "vacas", 10)
        matar("sheep", "ovejas", 10)
        matar("chicken", "gallinas", 15)
        matar("rabbit", "conejos", 5)
        matar("cod", "bacalaos", 10)
        matar("salmon", "salmones", 10)
        matar("squid", "calamares", 10)

        // ---------- picar ----------
        fun picar(clave: String, nombre: String, bloques: List<String>, icono: String, vararg c: Int) =
            agregar(TipoEncargo.PICAR, clave, bloques.map { "minecraft:$it" }, c, "minecraft:$icono") { "Pica $it de $nombre" }
        fun picar1(id: String, nombre: String, vararg c: Int) = picar(id, nombre, listOf(id), id, *c)
        picar("piedra", "piedra", listOf("stone", "cobblestone"), "stone", 300, 1000, 3000)
        picar1("deepslate", "pizarra profunda", 200, 800)
        picar("carbon", "mena de carbón", listOf("coal_ore", "deepslate_coal_ore"), "coal_ore", 20, 64)
        picar("hierro", "mena de hierro", listOf("iron_ore", "deepslate_iron_ore"), "iron_ore", 15, 48, 128)
        picar("cobre", "mena de cobre", listOf("copper_ore", "deepslate_copper_ore"), "copper_ore", 20, 64)
        picar("oro", "mena de oro", listOf("gold_ore", "deepslate_gold_ore"), "gold_ore", 0, 20, 64)
        picar("redstone", "mena de redstone", listOf("redstone_ore", "deepslate_redstone_ore"), "redstone_ore", 10, 40)
        picar("lapislazuli", "mena de lapislázuli", listOf("lapis_ore", "deepslate_lapis_ore"), "lapis_ore", 0, 15, 40)
        picar("diamante", "mena de diamante", listOf("diamond_ore", "deepslate_diamond_ore"), "diamond_ore", 0, 5, 16)
        picar("esmeralda", "mena de esmeralda", listOf("emerald_ore", "deepslate_emerald_ore"), "emerald_ore", 0, 3, 10)
        picar1("nether_quartz_ore", "cuarzo del Nether", 30, 100)
        picar1("nether_gold_ore", "oro del Nether", 20, 60)
        picar1("ancient_debris", "escombros ancestrales", 0, 0, 8)
        picar1("netherrack", "netherrack", 300, 1000)
        picar1("oak_log", "troncos de roble", 32, 128)
        picar1("birch_log", "troncos de abedul", 32, 128)
        picar1("spruce_log", "troncos de pino", 32, 128)
        picar1("jungle_log", "troncos de jungla", 32, 128)
        picar1("acacia_log", "troncos de acacia", 32, 128)
        picar1("dark_oak_log", "troncos de roble oscuro", 32, 128)
        picar1("mangrove_log", "troncos de mangle", 0, 64)
        picar1("cherry_log", "troncos de cerezo", 0, 64)
        picar1("sand", "arena", 64, 256)
        picar1("red_sand", "arena roja", 0, 128)
        picar1("gravel", "grava", 64, 256)
        picar1("dirt", "tierra", 128, 512)
        picar1("clay", "arcilla", 20, 64)
        picar1("obsidian", "obsidiana", 0, 20, 64)
        picar1("ice", "hielo", 0, 64)
        picar1("packed_ice", "hielo compacto", 0, 0, 64)
        picar1("blackstone", "piedra negra", 0, 128, 512)
        picar1("basalt", "basalto", 0, 128)
        picar1("end_stone", "piedra del End", 0, 0, 512)
        picar1("glowstone", "piedra luminosa", 0, 32, 128)
        picar1("soul_sand", "arena de almas", 0, 64, 256)
        picar1("terracotta", "terracota", 0, 64)
        picar1("wheat", "trigo", 64, 256)
        picar1("carrots", "zanahorias", 64, 256)
        picar1("potatoes", "papas", 64, 256)
        picar1("beetroots", "betarragas", 32, 128)
        picar1("sugar_cane", "caña de azúcar", 64, 256)
        picar1("pumpkin", "zapallos", 16, 64)
        picar1("melon", "sandías", 16, 64)
        picar1("cactus", "cactus", 0, 64)
        picar1("bamboo", "bambú", 64, 256)
        picar1("cocoa", "cacao", 0, 64)
        picar1("nether_wart", "verruga del Nether", 0, 64, 256)

        // ---------- fabricar (también cuenta lo que sacas del horno) ----------
        fun fabricar(id: String, nombre: String, vararg c: Int) =
            agregar(TipoEncargo.FABRICAR, id, listOf("minecraft:$id"), c, "minecraft:$id") { "Fabrica $it $nombre" }
        fabricar("bread", "panes", 16, 64)
        fabricar("torch", "antorchas", 64, 256)
        fabricar("bookshelf", "librerías", 0, 16, 64)
        fabricar("cake", "tortas", 1, 4)
        fabricar("golden_apple", "manzanas de oro", 0, 3, 10)
        fabricar("chest", "cofres", 5, 20)
        fabricar("furnace", "hornos", 3, 10)
        fabricar("glass", "bloques de vidrio", 32, 128)
        fabricar("iron_ingot", "lingotes de hierro", 16, 64, 256)
        fabricar("gold_ingot", "lingotes de oro", 0, 32, 128)
        fabricar("copper_ingot", "lingotes de cobre", 32, 128)
        fabricar("cooked_beef", "filetes cocidos", 16, 64)
        fabricar("cooked_porkchop", "chuletas cocidas", 16, 64)
        fabricar("baked_potato", "papas asadas", 32, 128)
        fabricar("bricks", "ladrillos", 0, 64)
        fabricar("lantern", "faroles", 0, 8)
        fabricar("arrow", "flechas", 32, 128)
        fabricar("paper", "papeles", 0, 64)
        fabricar("book", "libros", 0, 16)
        fabricar("firework_rocket", "cohetes", 0, 32, 128)
        fabricar("tnt", "TNT", 0, 5)
        fabricar("anvil", "yunque", 0, 1)
        fabricar("enchanting_table", "mesa de encantamientos", 0, 0, 1)
        fabricar("beacon", "faro", 0, 0, 1)
        fabricar("brewing_stand", "soporte de pociones", 0, 1)
        fabricar("netherite_ingot", "lingote de netherita", 0, 0, 1)
        fabricar("diamond_pickaxe", "pico de diamante", 0, 1)
        fabricar("golden_carrot", "zanahorias doradas", 0, 16, 64)
        fabricar("pumpkin_pie", "pasteles de zapallo", 0, 16)
        fabricar("cookie", "galletas", 32, 128)
        fabricar("mushroom_stew", "guisos de hongos", 3)
        fabricar("smooth_stone", "piedra lisa", 0, 64)
        fabricar("rail", "rieles", 0, 64, 256)
        fabricar("shield", "escudos", 2)
        fabricar("bucket", "baldes", 3)
        fabricar("crafting_table", "mesas de trabajo", 4)
        fabricar("ladder", "escaleras de mano", 16)
        fabricar("stone_bricks", "ladrillos de piedra", 64, 256)

        // ---------- hazañas medidas con estadísticas ----------
        fun estad(clave: String, stat: String, icono: String, divisor: Int, vararg c: Int, texto: (Int) -> String) =
            agregar(TipoEncargo.ESTADISTICA, clave, listOf("minecraft:$stat"), c, "minecraft:$icono", divisor, texto)
        estad("pescar", "fish_caught", "fishing_rod", 1, 5, 15, 40) { "Pesca $it peces" }
        estad("criar", "animals_bred", "wheat", 1, 5, 15, 40) { "Cría $it animales" }
        estad("caminar", "walk_one_cm", "leather_boots", 100_000, 200_000, 1_000_000) { "Camina $it km" }
        estad("correr", "sprint_one_cm", "golden_boots", 100_000, 0, 500_000) { "Corre $it km" }
        estad("nadar", "swim_one_cm", "heart_of_the_sea", 100, 0, 50_000, 200_000) { "Nada $it metros" }
        estad("volar", "aviate_one_cm", "elytra", 100_000, 0, 500_000, 2_000_000) { "Vuela $it km con élitros" }
        estad("bote", "boat_one_cm", "oak_boat", 100_000, 0, 100_000, 500_000) { "Recorre $it km en bote" }
        estad("cabalgar", "horse_one_cm", "saddle", 100_000, 0, 300_000, 1_000_000) { "Cabalga $it km" }
        estad("saltar", "jump", "rabbit_foot", 1, 200, 1000) { "Salta $it veces" }
        estad("encantar", "enchant_item", "enchanting_table", 1, 0, 3, 10) { "Encanta $it objetos" }
        estad("torta", "eat_cake_slice", "cake", 1, 3, 10) { "Come $it tajadas de torta" }
        estad("macetas", "pot_flower", "flower_pot", 1, 3) { "Pon $it plantas en macetas" }
        estad("dormir", "sleep_in_bed", "red_bed", 1, 1) { "Duerme en una cama" }
        estad("campana", "bell_ring", "bell", 1, 3) { "Toca la campana $it veces" }
        estad("asalto", "raid_win", "ominous_bottle", 1, 0, 0, 1) { "Gana un asalto de saqueadores" }
        estad("comerciar", "traded_with_villager", "emerald", 1, 5, 20) { "Comercia $it veces con aldeanos" }
        estad("trepar", "climb_one_cm", "ladder", 100, 0, 20_000) { "Trepa $it metros" }
        estad("musica", "play_noteblock", "note_block", 1, 10) { "Toca $it notas en bloques musicales" }
        estad("cofres", "open_chest", "chest", 1, 20) { "Abre $it cofres" }
        estad("cazar", "mob_kills", "iron_sword", 1, 30, 100, 300) { "Mata $it criaturas (cualquiera)" }

        // ---------- entregar al Kuraka ----------
        fun entregar(id: String, nombre: String, vararg c: Int) =
            agregar(TipoEncargo.ENTREGAR, id, listOf("minecraft:$id"), c, "minecraft:$id") { "Entrega $it $nombre" }
        entregar("wheat", "de trigo", 64, 192)
        entregar("carrot", "zanahorias", 64, 192)
        entregar("potato", "papas", 64, 192)
        entregar("beetroot", "betarragas", 32, 96)
        entregar("pumpkin", "zapallos", 16, 64)
        entregar("melon_slice", "rodajas de sandía", 64, 256)
        entregar("sugar_cane", "cañas de azúcar", 64, 192)
        entregar("egg", "huevos", 16, 48)
        entregar("leather", "cueros", 10, 32)
        entregar("white_wool", "lanas blancas", 16, 64)
        entregar("bone", "huesos", 32, 96)
        entregar("string", "hilos", 16, 64)
        entregar("gunpowder", "pólvoras", 0, 32, 96)
        entregar("ender_pearl", "perlas de ender", 0, 8, 32)
        entregar("blaze_rod", "varas de blaze", 0, 10, 32)
        entregar("slime_ball", "bolas de slime", 0, 16, 64)
        entregar("honey_bottle", "frascos de miel", 0, 8, 32)
        entregar("cooked_beef", "filetes cocidos", 16, 64)
        entregar("bread", "panes", 16, 64)
        entregar("iron_ingot", "lingotes de hierro", 0, 32, 128)
        entregar("gold_ingot", "lingotes de oro", 0, 16, 64)
        entregar("diamond", "diamantes", 0, 0, 16)
        entregar("emerald", "esmeraldas", 0, 8, 32)
        entregar("redstone", "polvos de redstone", 32, 128)
        entregar("lapis_lazuli", "lapislázulis", 16, 64)
        entregar("coal", "carbones", 32, 128)
        entregar("copper_ingot", "lingotes de cobre", 32, 128)
        entregar("quartz", "cuarzos del Nether", 0, 64)
        entregar("glowstone_dust", "polvos de piedra luminosa", 0, 32)
        entregar("ink_sac", "bolsas de tinta", 8, 24)
        entregar("feather", "plumas", 16, 48)
        entregar("spider_eye", "ojos de araña", 8, 24)
        entregar("rotten_flesh", "carnes podridas", 32, 128)
        entregar("cactus", "cactus", 16, 64)
        entregar("kelp", "algas", 32, 128)
        entregar("bamboo", "bambúes", 64, 192)
        entregar("cocoa_beans", "granos de cacao", 16, 64)
        entregar("nether_wart", "verrugas del Nether", 0, 32, 128)
        entregar("prismarine_shard", "fragmentos de prismarina", 0, 32)
        entregar("phantom_membrane", "membranas de phantom", 0, 4, 16)
        entregar("ghast_tear", "lágrimas de ghast", 0, 2, 8)
        entregar("shulker_shell", "caparazones de shulker", 0, 0, 4)
        entregar("amethyst_shard", "fragmentos de amatista", 0, 16, 64)
        entregar("obsidian", "obsidianas", 0, 16, 64)
        entregar("honeycomb", "panales", 0, 8, 32)
        entregar("nautilus_shell", "caparazones de nautilo", 0, 0, 3)
        entregar("echo_shard", "fragmentos de eco", 0, 0, 2)
        entregar("totem_of_undying", "tótem de la inmortalidad", 0, 0, 1)
        entregar("apple", "manzanas", 8, 32)
        entregar("sweet_berries", "bayas dulces", 32, 96)
        entregar("cod", "bacalaos", 10, 32)
        entregar("salmon", "salmones", 10, 32)
        entregar("pufferfish", "peces globo", 0, 5, 15)
        entregar("tropical_fish", "peces tropicales", 0, 5)
        entregar("rabbit_hide", "pieles de conejo", 0, 8)
        entregar("turtle_scute", "escamas de tortuga", 0, 0, 5)
        entregar("glass_bottle", "frascos de vidrio", 16)
        entregar("paper", "papeles", 24, 64)
        entregar("sea_pickle", "pepinos de mar", 0, 16)
        entregar("crying_obsidian", "obsidianas llorosas", 0, 8, 24)
        return r
    }
}
