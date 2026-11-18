package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.AnvilInputGui
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.boton
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.TipoKit
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment

/**
 * Menú del Herrero (3 filas):
 *   fila 1: [saldo]
 *   fila 2: reparar · combinar libro · renombrar   (sobre el ítem de la mano principal)
 *   fila 3: kits de reparación (armadura, armas, herramientas × 1 y 3 usos)
 */
object GuiHerrero {
    private val cfg get() = BancoMinecraftPer.herrero

    fun abrir(j: ServerPlayer) {
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto("Herrero", ChatFormatting.DARK_GRAY))
        pintar(gui, j)
        gui.open()
    }

    private fun pintar(gui: SimpleGui, j: ServerPlayer) {
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(
            4,
            GuiElementBuilder(Items.GOLD_INGOT)
                .setName(texto("Tu saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD))
                .setLore(listOf(texto("Impuesto de servicios: ${cfg.impuestoServiciosPorcentaje}%", ChatFormatting.DARK_GRAY))),
        )

        fun accion(op: () -> Respuesta): () -> Unit = {
            GuiUtil.avisar(j, op())
            pintar(gui, j)
        }

        val enMano = j.mainHandItem

        // Reparar
        val precioRep = Herrero.precioReparar(enMano)
        val loreRep = when {
            enMano.isEmpty || !enMano.isDamageableItem -> listOf("Ten en la mano lo que quieres reparar")
            !enMano.isDamaged -> listOf("${enMano.hoverName.string} no está dañado")
            else -> listOf(
                "${enMano.hoverName.string}: ${enMano.damageValue}/${enMano.maxDamage} de daño",
                "Precio: ${Formato.soles(Herrero.conImpuesto(precioRep))} (impuesto incluido)",
            ) + (if (Encantamientos.esLegendario(enMano)) listOf("Legendario: ×${cfg.multiplicadorLegendario}") else emptyList())
        }
        gui.setSlot(10, boton(ItemStack(Items.ANVIL), "Reparar", loreRep, ChatFormatting.GREEN, accion { Herrero.reparar(j) }))

        // Combinar
        val sim = Herrero.simularCombinar(enMano, j.offhandItem)
        val loreComb = if (sim == null) {
            listOf("Ítem en la mano principal", "Libro encantado en la secundaria", "Sin límite de \"¡Demasiado caro!\"")
        } else {
            val l = mutableListOf<String>()
            if (sim.aplicar.isEmpty()) l += "No hay nada que aplicar"
            sim.aplicar.forEach { (h, n) -> l += "+ " + Enchantment.getFullname(h, n).string }
            sim.rechazados.forEach { l += "× $it" }
            if (sim.aplicar.isNotEmpty()) l += "Precio: ${Formato.soles(Herrero.conImpuesto(sim.precio))} (impuesto incluido)"
            l
        }
        gui.setSlot(12, boton(ItemStack(Items.ENCHANTED_BOOK), "Combinar libro", loreComb, ChatFormatting.LIGHT_PURPLE, accion { Herrero.combinar(j) }))

        // Renombrar
        gui.setSlot(
            14,
            boton(
                ItemStack(Items.NAME_TAG),
                "Renombrar",
                listOf("El ítem de tu mano principal", "Precio: ${Formato.soles(Herrero.conImpuesto(cfg.renombrar))}"),
                ChatFormatting.YELLOW,
            ) { abrirRenombrar(j) },
        )

        // Reglas
        gui.setSlot(
            16,
            GuiElementBuilder(Items.BOOK)
                .setName(texto("Reglas del herrero", ChatFormatting.WHITE))
                .setLore(
                    listOf(
                        texto("Los ítems legendarios no admiten Reparación (Mending)", ChatFormatting.GRAY),
                        texto("Solo se mantienen con el Herrero o con kits", ChatFormatting.GRAY),
                    ),
                ),
        )

        // Kits
        var slot = 19
        for (tipo in TipoKit.entries) {
            for (usos in listOf(1, 3)) {
                val precio = cfg.precioKit(tipo, usos) ?: continue
                gui.setSlot(
                    slot++,
                    boton(
                        KitsReparacion.crear(tipo, usos),
                        "${tipo.nombre} ($usos ${if (usos == 1) "uso" else "usos"})",
                        listOf("Precio: ${Formato.soles(Herrero.conImpuesto(precio))} (impuesto incluido)"),
                        ChatFormatting.AQUA,
                        accion { Herrero.comprarKit(j, tipo, usos) },
                    ),
                )
            }
        }
    }

    private fun abrirRenombrar(j: ServerPlayer) {
        val enMano = j.mainHandItem
        if (enMano.isEmpty) {
            GuiUtil.avisar(j, Respuesta(false, "Ten en la mano el ítem que quieres renombrar"))
            return
        }
        val gui = AnvilInputGui(j, false)
        gui.setTitle(texto("Nuevo nombre", ChatFormatting.DARK_GRAY))
        gui.setDefaultInputValue(enMano.get(DataComponents.CUSTOM_NAME)?.string ?: enMano.hoverName.string)
        gui.setSlot(
            2,
            boton(
                ItemStack(Items.NAME_TAG),
                "Confirmar nombre",
                listOf("Precio: ${Formato.soles(Herrero.conImpuesto(cfg.renombrar))}"),
                ChatFormatting.GREEN,
            ) {
                GuiUtil.avisar(j, Herrero.renombrar(j, gui.input))
                abrir(j)
            },
        )
        gui.open()
    }
}
