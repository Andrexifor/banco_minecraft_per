package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.boton
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.Categoria
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.Precios
import io.github.andrexifor.banco.nucleo.Producto
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Tienda del servidor.
 *  - Menú principal: categorías.
 *  - Categoría: hasta 45 productos por página.
 *      clic izquierdo = comprar 1 lote · shift + izquierdo = comprar 8 lotes
 *      clic derecho   = vender 1 lote  · shift + derecho   = vender todo lo que tengas
 * Las compras se cobran del banco (y si falta, del efectivo del inventario).
 */
object GuiTienda {
    private const val POR_PAGINA = 45
    private const val LOTES_SHIFT = 8

    private val catalogo get() = BancoMinecraftPer.catalogo
    private val config get() = BancoMinecraftPer.config

    // ---------- menús ----------

    fun abrir(j: ServerPlayer) {
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto("Tienda del servidor", ChatFormatting.DARK_BLUE))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(
            4,
            GuiElementBuilder(Items.GOLD_INGOT)
                .setName(texto("Tu saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD)),
        )
        catalogo.categorias.take(14).forEachIndexed { i, cat ->
            val slot = if (i < 7) 10 + i else 19 + (i - 7)
            val cantidad = catalogo.productosDe(cat.id).size
            gui.setSlot(slot, boton(ItemStack(item(cat.icono) ?: Items.CHEST), cat.nombre, listOf("$cantidad productos"), ChatFormatting.AQUA) { abrirCategoria(j, cat, 0) })
        }
        gui.open()
    }

    fun abrirCategoria(j: ServerPlayer, cat: Categoria, pagina: Int) {
        val gui = SimpleGui(MenuType.GENERIC_9x6, j, false)
        gui.setTitle(texto("Tienda — ${cat.nombre}", ChatFormatting.DARK_BLUE))
        pintarCategoria(gui, j, cat, pagina)
        gui.open()
    }

    private fun pintarCategoria(gui: SimpleGui, j: ServerPlayer, cat: Categoria, pagina: Int) {
        val productos = catalogo.productosDe(cat.id)
        val paginas = maxOf(1, (productos.size + POR_PAGINA - 1) / POR_PAGINA)
        val p = pagina.coerceIn(0, paginas - 1)
        for (i in 0 until gui.size) gui.clearSlot(i)

        productos.drop(p * POR_PAGINA).take(POR_PAGINA).forEachIndexed { i, prod ->
            val itm = item(prod.item) ?: return@forEachIndexed
            gui.setSlot(i, elementoProducto(gui, j, cat, p, prod, itm))
        }

        for (i in 45 until 54) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(45, boton(ItemStack(Items.ARROW), "Volver", listOf("A las categorías"), ChatFormatting.WHITE) { abrir(j) })
        if (p > 0) {
            gui.setSlot(48, boton(ItemStack(Items.PAPER), "Página anterior", listOf("${p}/$paginas"), ChatFormatting.WHITE) { pintarCategoria(gui, j, cat, p - 1) })
        }
        gui.setSlot(
            49,
            GuiElementBuilder(Items.GOLD_INGOT)
                .setName(texto("Saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD))
                .setLore(
                    listOf(
                        texto("Efectivo: ${Formato.soles(Efectivo.valorEnInventario(j))}", ChatFormatting.GRAY),
                        texto("Impuesto de compra: ${config.impuestoComprasPorcentaje}%", ChatFormatting.DARK_GRAY),
                    ),
                ),
        )
        if (p < paginas - 1) {
            gui.setSlot(50, boton(ItemStack(Items.PAPER), "Página siguiente", listOf("${p + 2}/$paginas"), ChatFormatting.WHITE) { pintarCategoria(gui, j, cat, p + 1) })
        }
    }

    private fun elementoProducto(gui: SimpleGui, j: ServerPlayer, cat: Categoria, pagina: Int, prod: Producto, item: Item): GuiElementBuilder {
        val lore = mutableListOf<String>()
        if (prod.compra > 0) {
            lore += "Comprar: ${Formato.soles(prod.compra)}  (clic izquierdo)"
            lore += "Shift + izq.: comprar ${LOTES_SHIFT} lotes"
        } else {
            lore += "No está a la venta"
        }
        if (prod.venta > 0) {
            lore += "Vender: ${Formato.soles(prod.venta)}  (clic derecho)"
            lore += "Shift + der.: vender todo"
        } else {
            lore += "La tienda no lo compra"
        }
        if (prod.lote > 1) lore += "Precio por lote de ${prod.lote}"
        lore += "Tienes: ${contar(j, item)}"

        return GuiElementBuilder(item, prod.lote)
            .setLore(lore.map { texto(it, ChatFormatting.GRAY) })
            .setCallback { _, tipo, _, _ ->
                val r = when {
                    tipo.isLeft -> comprar(j, prod, if (tipo.shift) LOTES_SHIFT else 1)
                    tipo.isRight -> vender(j, prod, if (tipo.shift) null else 1)
                    else -> null
                }
                if (r != null) {
                    GuiUtil.avisar(j, r)
                    pintarCategoria(gui, j, cat, pagina)
                }
            }
    }

    // ---------- compra y venta (también usadas por /vender) ----------

    fun comprar(j: ServerPlayer, prod: Producto, lotes: Int): Respuesta {
        if (prod.compra <= 0) return Respuesta(false, "Ese producto no está a la venta")
        val item = item(prod.item) ?: return Respuesta(false, "Ítem desconocido: ${prod.item}")
        val cantidad = prod.lote * lotes
        val ejemplo = ItemStack(item)
        if (!Operaciones.cabe(j, ejemplo, cantidad)) return Respuesta(false, "No tienes espacio en el inventario")

        val (subtotal, impuesto) = Precios.compra(prod, lotes, config.impuestoComprasPorcentaje)
        val cobro = Operaciones.cobrar(j, subtotal, "COMPRA ${prod.item} x$cantidad", impuesto)
        if (!cobro.ok) return cobro

        var restante = cantidad
        while (restante > 0) {
            val n = minOf(restante, ejemplo.maxStackSize)
            j.inventory.placeItemBackInInventory(ItemStack(item, n))
            restante -= n
        }
        val extra = if (impuesto > 0) " (+${Formato.soles(impuesto)} de impuesto)" else ""
        return Respuesta(true, "Compraste $cantidad × ${ejemplo.hoverName.string} por ${Formato.soles(subtotal)}$extra. Saldo: ${Formato.soles(Operaciones.saldo(j))}")
    }

    /** [lotes] = null vende todo lo que tenga el jugador. */
    fun vender(j: ServerPlayer, prod: Producto, lotes: Int?): Respuesta {
        if (prod.venta <= 0) return Respuesta(false, "La tienda no compra ese producto")
        val item = item(prod.item) ?: return Respuesta(false, "Ítem desconocido: ${prod.item}")
        val maxLotes = contar(j, item) / prod.lote
        val aVender = if (lotes == null) maxLotes else minOf(lotes, maxLotes)
        if (aVender <= 0) {
            return Respuesta(false, "Necesitas al menos ${prod.lote} × ${ItemStack(item).hoverName.string} (sin encantar ni renombrar)")
        }
        quitar(j, item, aVender * prod.lote)
        val (neto, impuesto) = Precios.venta(prod, aVender, config.impuestoVentasPorcentaje)
        val r = Operaciones.abonar(j, neto, "VENTA ${prod.item} x${aVender * prod.lote}", impuesto)
        if (!r.ok) return r
        return Respuesta(true, "Vendiste ${aVender * prod.lote} × ${ItemStack(item).hoverName.string} por ${Formato.soles(neto)}. Saldo: ${Formato.soles(Operaciones.saldo(j))}")
    }

    /** /vender mano */
    fun venderMano(j: ServerPlayer): Respuesta {
        val enMano = j.mainHandItem
        if (enMano.isEmpty) return Respuesta(false, "No tienes nada en la mano")
        val id = BuiltInRegistries.ITEM.getKey(enMano.item).toString()
        val prod = catalogo.buscar(id)
        if (prod == null || prod.venta <= 0) return Respuesta(false, "La tienda no compra ${enMano.hoverName.string}")
        if (!enMano.componentsPatch.isEmpty) return Respuesta(false, "No se compran ítems encantados, renombrados o dañados")
        val lotes = enMano.count / prod.lote
        if (lotes <= 0) return Respuesta(false, "Necesitas al menos ${prod.lote} en la mano")
        val cantidad = lotes * prod.lote
        enMano.shrink(cantidad)
        val (neto, impuesto) = Precios.venta(prod, lotes, config.impuestoVentasPorcentaje)
        val r = Operaciones.abonar(j, neto, "VENTA $id x$cantidad", impuesto)
        if (!r.ok) return r
        return Respuesta(true, "Vendiste $cantidad por ${Formato.soles(neto)}. Saldo: ${Formato.soles(Operaciones.saldo(j))}")
    }

    /** /vender todo: vende todo lo vendible del inventario. */
    fun venderTodo(j: ServerPlayer): Respuesta {
        var total = 0L
        var piezas = 0
        for (prod in catalogo.productos.filter { it.venta > 0 }) {
            val item = item(prod.item) ?: continue
            val lotes = contar(j, item) / prod.lote
            if (lotes <= 0) continue
            quitar(j, item, lotes * prod.lote)
            val (neto, impuesto) = Precios.venta(prod, lotes, config.impuestoVentasPorcentaje)
            val r = Operaciones.abonar(j, neto, "VENTA ${prod.item} x${lotes * prod.lote}", impuesto)
            if (r.ok) {
                total += neto
                piezas += lotes * prod.lote
            }
        }
        if (piezas == 0) return Respuesta(false, "No tienes nada que la tienda compre")
        return Respuesta(true, "Vendiste $piezas ítems por ${Formato.soles(total)}. Saldo: ${Formato.soles(Operaciones.saldo(j))}")
    }

    // ---------- inventario ----------

    /** Cuenta solo ítems "limpios" (sin encantar, renombrar ni dañar) del inventario principal. */
    private fun contar(j: ServerPlayer, item: Item): Int {
        val inv = j.inventory
        var n = 0
        for (i in 0 until 36) {
            val s = inv.getItem(i)
            if (s.`is`(item) && s.componentsPatch.isEmpty) n += s.count
        }
        return n
    }

    private fun quitar(j: ServerPlayer, item: Item, cantidad: Int) {
        val inv = j.inventory
        var restante = cantidad
        for (i in 0 until 36) {
            if (restante <= 0) break
            val s = inv.getItem(i)
            if (s.`is`(item) && s.componentsPatch.isEmpty) {
                val n = minOf(restante, s.count)
                s.shrink(n)
                restante -= n
            }
        }
    }

    fun item(id: String): Item? {
        val ident = try {
            Identifier.parse(id)
        } catch (e: Exception) {
            return null
        }
        val item = BuiltInRegistries.ITEM.getValue(ident)
        return if (item == Items.AIR) null else item
    }
}
