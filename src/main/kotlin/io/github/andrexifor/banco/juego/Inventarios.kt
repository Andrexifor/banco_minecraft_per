package io.github.andrexifor.banco.juego

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * Entrega ítems al jugador sin depender de Inventory.placeItemBackInInventory,
 * cuya firma cambió en 26.3. Lo que no entra en el inventario cae a sus pies.
 */
object Inventarios {
    fun dar(jugador: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        val resto = stack.copy()
        jugador.inventory.add(resto) // deja en 'resto' lo que no cupo
        if (resto.isEmpty) return
        val nivel = jugador.level() as? ServerLevel ?: return
        nivel.addFreshEntity(ItemEntity(nivel, jugador.x, jugador.y, jugador.z, resto))
    }
}
