package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Denominacion
import io.github.andrexifor.banco.nucleo.Desglose
import io.github.andrexifor.banco.nucleo.TipoEfectivo
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Billetes, monedas y bonos como ítems vanilla con componentes:
 *  - custom_data {banco_minecraft_per:{valor:N}}  -> lo que el mod lee (no se puede añadir en supervivencia)
 *  - item_model banco_minecraft_per:<modelo>       -> textura del resource pack
 *  - item_name / lore                              -> lo que ve el jugador
 */
object Efectivo {
    private const val CLAVE = BancoMinecraftPer.MOD_ID
    private const val CLAVE_VALOR = "valor"

    /** Casillas del inventario principal (hotbar + 27). */
    private const val CASILLAS_PRINCIPALES = 36

    fun itemBase(d: Denominacion): Item = when (d.tipo) {
        TipoEfectivo.MONEDA -> Items.GOLD_NUGGET
        TipoEfectivo.BILLETE, TipoEfectivo.BONO -> Items.PAPER
    }

    private fun marca(d: Denominacion): CompoundTag {
        val interno = CompoundTag()
        interno.putLong(CLAVE_VALOR, d.valor)
        val raiz = CompoundTag()
        raiz.put(CLAVE, interno)
        return raiz
    }

    fun crear(d: Denominacion, cantidad: Int): ItemStack {
        val stack = ItemStack(itemBase(d), cantidad)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marca(d)))
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(CLAVE, d.modelo))

        val color = when (d.tipo) {
            TipoEfectivo.MONEDA -> ChatFormatting.GOLD
            TipoEfectivo.BILLETE -> ChatFormatting.GREEN
            TipoEfectivo.BONO -> ChatFormatting.LIGHT_PURPLE
        }
        stack.set(DataComponents.ITEM_NAME, Component.literal(d.nombreCorto).withStyle(color))
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(
                    linea(d.nombreLargo, ChatFormatting.GRAY),
                    linea("Banco Minecraft PER", ChatFormatting.DARK_GRAY),
                ),
            ),
        )
        if (d.tipo == TipoEfectivo.BONO) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        }
        return stack
    }

    private fun linea(texto: String, color: ChatFormatting): Component =
        Component.literal(texto).withStyle(color).withStyle { it.withItalic(false) }

    /** Qué denominación es este stack, o null si no es efectivo del banco. */
    fun denominacionDe(stack: ItemStack): Denominacion? {
        if (stack.isEmpty) return null
        val datos = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return Denominacion.entries.firstOrNull { d ->
            stack.`is`(itemBase(d)) && datos.matchedBy(marca(d))
        }
    }

    fun valorDe(stack: ItemStack): Long {
        val d = denominacionDe(stack) ?: return 0
        return Math.multiplyExact(d.valor, stack.count.toLong())
    }

    /** Casillas vacías en el inventario principal (sin contar armadura ni mano secundaria). */
    fun casillasLibres(jugador: ServerPlayer): Int {
        val inv = jugador.inventory
        return (0 until CASILLAS_PRINCIPALES).count { inv.getItem(it).isEmpty }
    }

    /** Entrega el desglose en stacks de 64. Asume que ya se verificó el espacio. */
    fun entregar(jugador: ServerPlayer, desglose: Map<Denominacion, Long>) {
        val inv = jugador.inventory
        for ((d, cantidad) in desglose) {
            var restante = cantidad
            while (restante > 0) {
                val n = minOf(restante, Desglose.MAX_POR_STACK.toLong()).toInt()
                inv.placeItemBackInInventory(crear(d, n))
                restante -= n
            }
        }
    }

    /** Quita todo el efectivo del inventario y devuelve cuánto valía. */
    fun recogerTodo(jugador: ServerPlayer): Long {
        val inv = jugador.inventory
        var total = 0L
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            val valor = valorDe(stack)
            if (valor > 0) {
                total = Math.addExact(total, valor)
                inv.setItem(i, ItemStack.EMPTY)
            }
        }
        return total
    }

    /** Quita solo el stack de la mano principal, si es efectivo. */
    fun recogerMano(jugador: ServerPlayer): Long {
        val stack = jugador.mainHandItem
        val valor = valorDe(stack)
        if (valor > 0) {
            stack.count = 0
        }
        return valor
    }
}
