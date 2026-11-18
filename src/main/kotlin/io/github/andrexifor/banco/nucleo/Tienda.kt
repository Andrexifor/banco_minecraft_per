package io.github.andrexifor.banco.nucleo

/**
 * Catálogo de la tienda del servidor: config/banco_minecraft_per/tienda.json
 *
 * - compra: precio que PAGA el jugador por un lote (0 = la tienda no lo vende)
 * - venta:  lo que RECIBE el jugador por un lote (0 = la tienda no lo compra)
 * - lote:   cuántas unidades son un lote (p. ej. 16 flechas)
 */
data class Categoria(
    var id: String = "",
    var nombre: String = "",
    /** Ítem que se muestra como ícono, p. ej. "minecraft:diamond". */
    var icono: String = "minecraft:chest",
)

data class Producto(
    var item: String = "",
    var categoria: String = "",
    var compra: Long = 0,
    var venta: Long = 0,
    var lote: Int = 1,
)

data class Catalogo(
    var categorias: MutableList<Categoria> = mutableListOf(),
    var productos: MutableList<Producto> = mutableListOf(),
) {
    fun productosDe(categoriaId: String): List<Producto> = productos.filter { it.categoria == categoriaId }

    fun buscar(itemId: String): Producto? = productos.firstOrNull { it.item == itemId }

    /** Quita entradas inválidas y corrige valores fuera de rango. */
    fun normalizado(): Catalogo {
        val idsCategoria = categorias.map { it.id }.toSet()
        val validos = productos
            .filter { it.item.isNotBlank() && it.categoria in idsCategoria && (it.compra > 0 || it.venta > 0) }
            .map { it.copy(compra = it.compra.coerceAtLeast(0), venta = it.venta.coerceAtLeast(0), lote = it.lote.coerceIn(1, 64)) }
            .distinctBy { it.item }
        return Catalogo(categorias.filter { it.id.isNotBlank() }.toMutableList(), validos.toMutableList())
    }

    companion object {
        /**
         * Catálogo inicial. Los ítems que se pueden farmear en masa (esmeralda, perla,
         * pólvora, blaze, oro, caña) se venden barato para que no inflen la economía.
         */
        fun porDefecto(): Catalogo {
            val c = Catalogo(
                categorias = mutableListOf(
                    Categoria("minerales", "Minerales", "minecraft:diamond"),
                    Categoria("cultivos", "Cultivos", "minecraft:wheat"),
                    Categoria("mobs", "Botín de mobs", "minecraft:bone"),
                    Categoria("bloques", "Bloques", "minecraft:oak_log"),
                    Categoria("basicos", "Básicos", "minecraft:bread"),
                ),
            )
            fun p(item: String, cat: String, compra: Long, venta: Long, lote: Int = 1) {
                c.productos.add(Producto("minecraft:$item", cat, compra, venta, lote))
            }
            // Minerales (venta = lo que paga el server)
            p("coal", "minerales", 4, 2)
            p("copper_ingot", "minerales", 6, 3)
            p("iron_ingot", "minerales", 16, 8)
            p("gold_ingot", "minerales", 20, 5) // granjas de piglins: venta baja
            p("redstone", "minerales", 4, 2)
            p("lapis_lazuli", "minerales", 6, 3)
            p("quartz", "minerales", 8, 4)
            p("amethyst_shard", "minerales", 6, 3)
            p("emerald", "minerales", 100, 12) // aldeanos dan esmeraldas casi infinitas
            p("diamond", "minerales", 160, 80)
            p("netherite_scrap", "minerales", 0, 300)
            // Cultivos
            p("wheat", "cultivos", 2, 1)
            p("carrot", "cultivos", 2, 1)
            p("potato", "cultivos", 2, 1)
            p("beetroot", "cultivos", 2, 1)
            p("melon_slice", "cultivos", 2, 1, lote = 2)
            p("pumpkin", "cultivos", 6, 3)
            p("sugar_cane", "cultivos", 8, 1, lote = 4) // se automatiza en masa
            p("cocoa_beans", "cultivos", 4, 2)
            p("nether_wart", "cultivos", 4, 2)
            // Botín de mobs
            p("rotten_flesh", "mobs", 0, 1, lote = 4)
            p("bone", "mobs", 4, 2)
            p("string", "mobs", 4, 2)
            p("gunpowder", "mobs", 10, 2) // granjas de creepers
            p("spider_eye", "mobs", 0, 2)
            p("ender_pearl", "mobs", 40, 5) // granjas de enderman
            p("blaze_rod", "mobs", 40, 8) // granjas de blaze
            // Bloques
            p("oak_log", "bloques", 3, 1)
            p("cobblestone", "bloques", 1, 0, lote = 16)
            p("glass", "bloques", 3, 0)
            p("sand", "bloques", 2, 0)
            // Básicos (solo compra)
            p("bread", "basicos", 5, 0)
            p("cooked_beef", "basicos", 8, 0)
            p("torch", "basicos", 4, 0, lote = 8)
            p("arrow", "basicos", 8, 0, lote = 16)
            p("white_bed", "basicos", 30, 0)
            p("iron_pickaxe", "basicos", 60, 0)
            p("shield", "basicos", 40, 0)
            return c
        }
    }
}

/** Cálculos de precios de la tienda, sin Minecraft. */
object Precios {
    /** Lo que paga el jugador por [lotes] lotes, impuesto incluido. Devuelve (subtotal, impuesto). */
    fun compra(p: Producto, lotes: Int, impuestoPct: Double): Pair<Long, Long> {
        require(p.compra > 0) { "Producto no se vende" }
        val subtotal = Math.multiplyExact(p.compra, lotes.toLong())
        return subtotal to Impuestos.calcular(subtotal, impuestoPct)
    }

    /** Lo que recibe el jugador por [lotes] lotes, ya descontado el impuesto. Devuelve (neto, impuesto). */
    fun venta(p: Producto, lotes: Int, impuestoPct: Double): Pair<Long, Long> {
        require(p.venta > 0) { "Producto no se compra" }
        val bruto = Math.multiplyExact(p.venta, lotes.toLong())
        val impuesto = Impuestos.calcular(bruto, impuestoPct)
        return (bruto - impuesto) to impuesto
    }
}
