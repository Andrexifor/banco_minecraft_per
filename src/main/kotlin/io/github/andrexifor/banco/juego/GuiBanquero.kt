package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.juego.GuiUtil.boton
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.Denominacion
import io.github.andrexifor.banco.nucleo.Formato
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Menú del Banquero (3 filas):
 *   fila 1: [saldo]
 *   fila 2: depositar todo · depositar mano · ordenar efectivo · cambiar a sencillo
 *   fila 3: retirar S/ 10 · 50 · 100 · 500 · 1,000 · 5,000 · 10,000
 */
object GuiBanquero {
    private val RETIROS = listOf(
        Denominacion.BILLETE_10, Denominacion.BILLETE_50, Denominacion.BILLETE_100,
        Denominacion.BONO_500, Denominacion.BONO_1000, Denominacion.BONO_5000, Denominacion.BONO_10000,
    )

    fun abrir(j: ServerPlayer) {
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto("Banco Minecraft PER — Banquero", ChatFormatting.DARK_GREEN))
        pintar(gui, j)
        gui.open()
    }

    private fun pintar(gui: SimpleGui, j: ServerPlayer) {
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())

        gui.setSlot(
            4,
            GuiElementBuilder(Items.GOLD_INGOT)
                .setName(texto("Tu saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD))
                .setLore(listOf(texto("Efectivo en tu inventario: ${Formato.soles(Efectivo.valorEnInventario(j))}", ChatFormatting.GRAY))),
        )

        fun accion(op: () -> Respuesta): () -> Unit = {
            GuiUtil.avisar(j, op())
            pintar(gui, j)
        }

        gui.setSlot(10, boton(ItemStack(Items.CHEST), "Depositar todo", listOf("Guarda en el banco todo el", "efectivo de tu inventario"), ChatFormatting.GREEN, accion { Operaciones.depositar(j, soloMano = false) }))
        gui.setSlot(11, boton(Efectivo.crear(Denominacion.BILLETE_100, 1), "Depositar lo de la mano", listOf("Solo el stack que tienes", "en la mano principal"), ChatFormatting.GREEN, accion { Operaciones.depositar(j, soloMano = true) }))
        gui.setSlot(14, boton(ItemStack(Items.HOPPER), "Ordenar mi efectivo", listOf("Junta tus billetes y monedas", "en la menor cantidad de piezas"), ChatFormatting.AQUA, accion { Operaciones.ordenarEfectivo(j) }))
        gui.setSlot(16, boton(Efectivo.crear(Denominacion.MONEDA_5, 1), "Cambiar a sencillo", listOf("Cambia UNA pieza de tu mano", "por piezas menores (100 → 50 + 50)"), ChatFormatting.AQUA, accion { Operaciones.cambiarASencillo(j) }))

        RETIROS.forEachIndexed { i, d ->
            gui.setSlot(
                19 + i,
                boton(Efectivo.crear(d, 1), "Retirar ${Formato.soles(d.valor)}", listOf("Saca ${Formato.soles(d.valor)} del banco"), ChatFormatting.YELLOW, accion { Operaciones.retirar(j, d.valor) }),
            )
        }
    }
}
