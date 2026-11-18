package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.Formato
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items

/** Menú del Mercader legendario: muestra los ítems de legendarios.json y pide confirmación. */
object GuiMercader {
    fun abrir(j: ServerPlayer) {
        val server = j.level().server ?: return
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto("Mercader legendario", ChatFormatting.DARK_PURPLE))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(
            4,
            GuiElementBuilder(Items.GOLD_INGOT)
                .setName(texto("Tu saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD)),
        )

        val ranuras = (9 until 27).toList()
        BancoMinecraftPer.legendarios.legendarios.take(ranuras.size).forEachIndexed { i, l ->
            val muestra = Encantamientos.crearLegendario(server, l) ?: return@forEachIndexed
            val total = Herrero.conImpuesto(l.precio)
            val detalle = listOf(
                "Precio: ${Formato.soles(l.precio)}",
                "Con impuesto: ${Formato.soles(total)}",
                "Clic para comprar",
            )
            val elemento = GuiElementBuilder(muestra.copy())
            val loreOriginal = muestra.get(DataComponents.LORE)?.lines() ?: emptyList()
            elemento.setLore(loreOriginal + detalle.map { texto(it, ChatFormatting.YELLOW) })
            elemento.setCallback(
                Runnable {
                    GuiUtil.confirmar(
                        j,
                        "¿Comprar ${l.nombre}?",
                        muestra.copy(),
                        listOf("Total: ${Formato.soles(total)}", "Saldo: ${Formato.soles(Operaciones.saldo(j))}"),
                        alConfirmar = {
                            GuiUtil.avisar(j, Herrero.comprarLegendario(j, l))
                            abrir(j)
                        },
                        alCancelar = { abrir(j) },
                    )
                },
            )
            gui.setSlot(ranuras[i], elemento)
        }
        gui.open()
    }
}
