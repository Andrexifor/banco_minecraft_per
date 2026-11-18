package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.world.inventory.MenuType
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

    /**
     * Pantalla de confirmación: [muestra] en el centro, "Confirmar" a la izquierda y
     * "Cancelar" a la derecha. [alCancelar] suele reabrir el menú anterior.
     */
    fun confirmar(
        j: ServerPlayer,
        titulo: String,
        muestra: ItemStack,
        detalle: List<String>,
        alConfirmar: () -> Unit,
        alCancelar: () -> Unit,
    ) {
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto(titulo, ChatFormatting.DARK_RED))
        for (i in 0 until gui.size) gui.setSlot(i, relleno())
        gui.setSlot(13, GuiElementBuilder(muestra).setLore(detalle.map { texto(it, ChatFormatting.GRAY) }))
        gui.setSlot(11, boton(ItemStack(Items.EMERALD_BLOCK), "Confirmar", detalle, ChatFormatting.GREEN) { alConfirmar() })
        gui.setSlot(15, boton(ItemStack(Items.BARRIER), "Cancelar", listOf("Volver sin pagar"), ChatFormatting.RED) { alCancelar() })
        gui.open()
    }

    fun avisar(j: ServerPlayer, r: Respuesta) {
        j.sendSystemMessage(Component.literal(r.mensaje).withStyle(if (r.ok) ChatFormatting.GREEN else ChatFormatting.RED))
    }
}
