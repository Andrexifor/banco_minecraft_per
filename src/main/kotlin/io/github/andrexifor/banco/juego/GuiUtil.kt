package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Utilidades comunes para los menús (SGUI). */
object GuiUtil {
    fun texto(t: String, color: ChatFormatting): Component =
        Component.literal(t).withStyle(color).withStyle { it.withItalic(false) }

    /** Botón con nombre, descripción y acción al hacer clic. */
    fun boton(icono: ItemStack, nombre: String, lore: List<String>, color: ChatFormatting, accion: () -> Unit): GuiElementBuilder {
        val b = GuiElementBuilder(icono)
            .setName(texto(nombre, color))
            .setLore(lore.map { texto(it, ChatFormatting.GRAY) })
        b.setCallback(Runnable { accion() })
        return b
    }

    /** Relleno decorativo sin acción. */
    fun relleno(): GuiElementBuilder = GuiElementBuilder(Items.GLASS_PANE).setName(Component.literal(" ")).hideTooltip()

    fun avisar(j: ServerPlayer, r: Respuesta) {
        j.sendSystemMessage(Component.literal(r.mensaje).withStyle(if (r.ok) ChatFormatting.GREEN else ChatFormatting.RED))
    }
}
