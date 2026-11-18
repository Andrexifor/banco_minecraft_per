package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.TipoKit
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Kits de reparación portátiles: clic derecho y reparan sin ir al Herrero.
 * Armas/herramientas: el kit en una mano y el ítem a reparar en la otra.
 * Ítem base: reloj (no se usa en recetas ni hace nada al usarlo), tamaño de stack 1.
 * custom_data {banco_minecraft_per:{kit:"armas", usos:3}}
 */
object KitsReparacion {
    private const val CLAVE = BancoMinecraftPer.MOD_ID

    fun registrarEventos() {
        UseItemCallback.EVENT.register { jugador, _, mano ->
            val stack = jugador.getItemInHand(mano)
            val kit = leer(stack)
            if (kit == null || jugador !is ServerPlayer) {
                InteractionResult.PASS
            } else {
                usar(jugador, mano, stack, kit.first, kit.second)
                InteractionResult.SUCCESS
            }
        }
    }

    fun crear(tipo: TipoKit, usos: Int): ItemStack {
        val stack = ItemStack(Items.CLOCK)
        stack.set(DataComponents.MAX_STACK_SIZE, 1)
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(CLAVE, "kit_${tipo.id}"))
        stack.set(DataComponents.ITEM_NAME, Component.literal(tipo.nombre).withStyle(ChatFormatting.AQUA))
        escribirUsos(stack, tipo, usos)
        return stack
    }

    private fun escribirUsos(stack: ItemStack, tipo: TipoKit, usos: Int) {
        val interno = CompoundTag()
        interno.putString("kit", tipo.id)
        interno.putInt("usos", usos)
        val raiz = CompoundTag()
        raiz.put(CLAVE, interno)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(raiz))
        val repara = when (tipo) {
            TipoKit.ARMADURA -> "Armadura puesta + escudo"
            TipoKit.ARMAS -> "Espada, hacha, arco, ballesta, tridente o maza"
            TipoKit.HERRAMIENTAS -> "Pico, pala, azada, hacha, tijeras, caña, mechero, élitros o escudo"
        }
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(
                    GuiUtil.texto("Usos: $usos", ChatFormatting.YELLOW),
                    GuiUtil.texto("Repara: $repara", ChatFormatting.GRAY),
                    GuiUtil.texto(
                        if (tipo == TipoKit.ARMADURA) "Clic derecho para usar" else "Kit en una mano, ítem en la otra, clic derecho",
                        ChatFormatting.DARK_GRAY,
                    ),
                ),
            ),
        )
    }

    /** (tipo, usos) si el stack es un kit; null si no. */
    fun leer(stack: ItemStack): Pair<TipoKit, Int>? {
        if (stack.isEmpty || !stack.`is`(Items.CLOCK)) return null
        val datos = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        for (tipo in TipoKit.entries) {
            for (usos in 1..MAX_USOS) {
                if (datos.matchedBy(marca(tipo, usos))) return tipo to usos
            }
        }
        return null
    }

    private const val MAX_USOS = 9

    private fun marca(tipo: TipoKit, usos: Int): CompoundTag {
        val interno = CompoundTag()
        interno.putString("kit", tipo.id)
        interno.putInt("usos", usos)
        val raiz = CompoundTag()
        raiz.put(CLAVE, interno)
        return raiz
    }

    private fun usar(j: ServerPlayer, mano: InteractionHand, kit: ItemStack, tipo: TipoKit, usos: Int) {
        val reparados = when (tipo) {
            TipoKit.ARMADURA -> repararArmadura(j)
            TipoKit.ARMAS, TipoKit.HERRAMIENTAS -> repararEnMano(j, mano, tipo)
        }
        if (reparados.isEmpty()) {
            avisar(j, "No hay nada que este kit pueda reparar (el uso no se gastó)", ChatFormatting.RED)
            return
        }
        val restantes = usos - 1
        if (restantes <= 0) {
            kit.shrink(1)
        } else {
            escribirUsos(kit, tipo, restantes)
        }
        BancoMinecraftPer.almacen.registrar("KIT_${tipo.id.uppercase()}", j.name.string, "-", 0, detalle = reparados.joinToString("|"))
        avisar(j, "Reparado: ${reparados.joinToString(", ")}. Usos restantes: $restantes", ChatFormatting.GREEN)
    }

    private fun repararArmadura(j: ServerPlayer): List<String> {
        val reparados = mutableListOf<String>()
        for (slot in listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val pieza = j.getItemBySlot(slot)
            if (pieza.`is`(Items.ELYTRA)) continue // los élitros van con el kit de herramientas
            if (reparar(pieza)) reparados += pieza.hoverName.string
        }
        // Escudo: primero la mano secundaria, si no el primero dañado del inventario
        val escudo = if (j.offhandItem.`is`(Items.SHIELD) && j.offhandItem.isDamaged) {
            j.offhandItem
        } else {
            (0 until j.inventory.containerSize).map { j.inventory.getItem(it) }
                .firstOrNull { it.`is`(Items.SHIELD) && it.isDamaged }
        }
        if (escudo != null && reparar(escudo)) reparados += escudo.hoverName.string
        return reparados
    }

    private fun repararEnMano(j: ServerPlayer, manoKit: InteractionHand, tipo: TipoKit): List<String> {
        // El ítem a reparar es el de la OTRA mano si el kit está en la principal, o el de la principal
        val objetivo = if (manoKit == InteractionHand.MAIN_HAND) j.offhandItem else j.mainHandItem
        if (!objetivo.isEmpty && tipo.reparaEnMano(ruta(objetivo)) && reparar(objetivo)) {
            return listOf(objetivo.hoverName.string)
        }
        // Kit de herramientas: también repara los élitros equipados
        if (tipo == TipoKit.HERRAMIENTAS) {
            val pecho = j.getItemBySlot(EquipmentSlot.CHEST)
            if (pecho.`is`(Items.ELYTRA) && reparar(pecho)) return listOf(pecho.hoverName.string)
        }
        return emptyList()
    }

    private fun reparar(stack: ItemStack): Boolean {
        if (stack.isEmpty || !stack.isDamageableItem || !stack.isDamaged) return false
        stack.damageValue = 0
        return true
    }

    fun ruta(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).path

    private fun avisar(j: ServerPlayer, texto: String, color: ChatFormatting) {
        j.sendSystemMessage(Component.literal(texto).withStyle(color))
    }
}
