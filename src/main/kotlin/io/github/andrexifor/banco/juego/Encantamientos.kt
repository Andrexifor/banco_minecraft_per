package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Legendario
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * Ítems legendarios y reglas de encantamientos.
 *  - Legendario = ítem con custom_data {banco_minecraft_per:{legendario:1b}}.
 *  - Regla: un ítem legendario o con algún encantamiento por encima del máximo vanilla
 *    NO puede tener Reparación (Mending). La usa el yunque (AnvilMenuMixin) y el herrero.
 */
object Encantamientos {
    private const val CLAVE = BancoMinecraftPer.MOD_ID

    private fun marca(clave: String): CompoundTag {
        val interno = CompoundTag()
        interno.putBoolean(clave, true)
        val raiz = CompoundTag()
        raiz.put(CLAVE, interno)
        return raiz
    }

    private fun marcaLegendario(): CompoundTag = marca("legendario")

    /** ¿Tiene el poder "imán de botín"? */
    fun esIman(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val datos = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return datos.matchedBy(marca("iman"))
    }

    fun esLegendario(stack: ItemStack): Boolean {
        val datos = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return datos.matchedBy(marcaLegendario())
    }

    fun encantamientosDe(stack: ItemStack): ItemEnchantments =
        stack.get(DataComponents.ENCHANTMENTS) ?: ItemEnchantments.EMPTY

    /** ¿Tiene algún encantamiento por encima del máximo vanilla? */
    fun superaMaximo(stack: ItemStack): Boolean =
        encantamientosDe(stack).entrySet().any { it.intValue > it.key.value().maxLevel }

    fun tieneReparacion(stack: ItemStack): Boolean =
        encantamientosDe(stack).keySet().any { it.`is`(Enchantments.MENDING) }

    fun esReparacion(h: Holder<Enchantment>): Boolean = h.`is`(Enchantments.MENDING)

    /** Regla del servidor: legendarios y "sobre el máximo" no pueden llevar Reparación. */
    fun reparacionProhibida(stack: ItemStack): Boolean = esLegendario(stack) || superaMaximo(stack)

    /** Usado por el mixin del yunque: ¿hay que bloquear este resultado? */
    @JvmStatic
    fun bloquearResultadoYunque(resultado: ItemStack): Boolean =
        !resultado.isEmpty && tieneReparacion(resultado) && reparacionProhibida(resultado)

    /** Busca un encantamiento por id ("minecraft:sharpness"). */
    fun buscar(server: MinecraftServer, id: String): Holder<Enchantment>? =
        server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
            .filter { it.registeredName == id }
            .findFirst()
            .orElse(null)

    /** Crea el ítem legendario listo para entregar. Devuelve null si el ítem no existe. */
    fun crearLegendario(server: MinecraftServer, l: Legendario): ItemStack? {
        val item = GuiTienda.item(l.item) ?: return null
        val stack = ItemStack(item)
        val mutable = ItemEnchantments.Mutable(ItemEnchantments.EMPTY)
        for ((id, nivel) in l.encantamientos) {
            val h = buscar(server, id)
            if (h == null) {
                BancoMinecraftPer.LOGGER.warn("Legendario {}: encantamiento desconocido {}", l.id, id)
                continue
            }
            if (esReparacion(h)) continue // nunca Reparación en un legendario
            mutable.set(h, nivel)
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable())
        val interno = CompoundTag()
        interno.putBoolean("legendario", true)
        if (l.iman) interno.putBoolean("iman", true)
        val datos = CompoundTag()
        datos.put(CLAVE, interno)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(datos))
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(l.nombre).withStyle(ChatFormatting.GOLD).withStyle { it.withItalic(false) })
        val lore = mutableListOf<Component>(
            Component.literal("Legendario").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle { it.withItalic(false) },
        )
        if (l.descripcion.isNotBlank()) {
            lore += Component.literal(l.descripcion).withStyle(ChatFormatting.GRAY).withStyle { it.withItalic(false) }
        }
        if (l.iman) {
            lore += Component.literal("Imán de botín: lo que suelta el enemigo va a tu inventario")
                .withStyle(ChatFormatting.AQUA).withStyle { it.withItalic(false) }
        }
        lore += Component.literal("No admite Reparación: llévalo al Herrero").withStyle(ChatFormatting.DARK_GRAY).withStyle { it.withItalic(false) }
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }
}
