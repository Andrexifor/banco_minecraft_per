package io.github.andrexifor.banco.juego

import com.mojang.brigadier.StringReader
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import io.github.andrexifor.banco.nucleo.Contador
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.Logro
import io.github.andrexifor.banco.nucleo.Metas
import io.github.andrexifor.banco.nucleo.Oferta
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Los dos mercaderes especiales: el de bromas (plaza) y el del mercado negro (Nether). */
enum class Tienda(val titulo: String, val color: ChatFormatting, val concepto: String) {
    BROMAS("Mercader de bromas", ChatFormatting.LIGHT_PURPLE, "BROMA"),
    ANIMALES("Criador de animales", ChatFormatting.GREEN, "ANIMAL"),
    CONTRABANDO("Mercado negro", ChatFormatting.DARK_RED, "CONTRABANDO"),
    SEMBRADOR("Artículos del Sembrador", ChatFormatting.DARK_GREEN, "ARTICULO"),
    POCIONES("Pociones del Kallawaya", ChatFormatting.DARK_AQUA, "POCION"),
    ;

    val catalogo: CatalogoOfertas
        get() = when (this) {
            BROMAS -> BancoMinecraftPer.bromas
            ANIMALES -> BancoMinecraftPer.animales
            CONTRABANDO -> BancoMinecraftPer.contrabando
            SEMBRADOR -> BancoMinecraftPer.sembrador
            POCIONES -> BancoMinecraftPer.pociones
        }
}

object GuiOfertas {
    /** Convierte el texto del JSON (formato /give) en un ítem. null si está mal escrito. */
    fun crear(servidor: MinecraftServer, o: Oferta): ItemStack? = try {
        val contexto = CommandBuildContext.simple(servidor.registryAccess(), servidor.worldData.enabledFeatures())
        ItemArgument.item(contexto).parse(StringReader(o.item)).createItemStack(o.cantidad)
    } catch (e: Exception) {
        BancoMinecraftPer.LOGGER.warn("Oferta {}: ítem mal escrito '{}': {}", o.id, o.item, e.message)
        null
    }

    fun abrir(j: ServerPlayer, tienda: Tienda) {
        val servidor = j.level().server ?: return
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto(tienda.titulo, tienda.color))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(
            4,
            GuiElementBuilder(Items.GOLD_INGOT).setName(texto("Tu saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD)),
        )
        val ranuras = (9 until 27).toList()
        tienda.catalogo.ofertas.orEmpty().take(ranuras.size).forEachIndexed { i, o ->
            val muestra = crear(servidor, o) ?: return@forEachIndexed
            val total = Herrero.conImpuesto(o.precio)
            val lore = (muestra.get(DataComponents.LORE)?.lines() ?: emptyList()) +
                listOfNotNull(
                    o.descripcion.takeIf { it.isNotBlank() }?.let { texto(it, ChatFormatting.GRAY) },
                    texto("Precio: ${Formato.soles(o.precio)}", ChatFormatting.YELLOW),
                    texto("Con impuesto: ${Formato.soles(total)}", ChatFormatting.YELLOW),
                    texto("Clic para comprar", ChatFormatting.YELLOW),
                )
            val elemento = GuiElementBuilder(muestra.copy()).setLore(lore)
            elemento.setCallback(
                Runnable {
                    GuiUtil.confirmar(
                        j, "¿Comprar ${o.nombre}?", muestra.copy(),
                        listOf("Total: ${Formato.soles(total)}", "Saldo: ${Formato.soles(Operaciones.saldo(j))}"),
                        alConfirmar = {
                            GuiUtil.avisar(j, comprar(j, tienda, o))
                            abrir(j, tienda)
                        },
                        alCancelar = { abrir(j, tienda) },
                    )
                },
            )
            gui.setSlot(ranuras[i], elemento)
        }
        gui.open()
    }

    fun comprar(j: ServerPlayer, tienda: Tienda, o: Oferta): Respuesta {
        val servidor = j.level().server ?: return Respuesta(false, "Servidor no disponible")
        if (tienda == Tienda.CONTRABANDO && !MercadoNegro.desbloqueado(j)) {
            return Respuesta(false, "El mercado negro no te reconoce…")
        }
        val stack = crear(servidor, o) ?: return Respuesta(false, "Este artículo está mal configurado; avisa a un admin")
        if (!Operaciones.cabe(j, stack, stack.count)) return Respuesta(false, "No tienes espacio en el inventario")
        val impuesto = Herrero.conImpuesto(o.precio) - o.precio
        val cobro = Operaciones.cobrar(j, o.precio, "${tienda.concepto} ${o.id}", impuesto)
        if (!cobro.ok) return cobro
        Inventarios.dar(j, stack)
        if (o.anunciar) Logros.anunciar(servidor, "${j.name.string} compró ${o.nombre} en el ${tienda.titulo.lowercase()}")
        when (tienda) {
            Tienda.BROMAS -> Logros.sumar(j, Contador.GASTO_BROMAS, o.precio + impuesto, Metas.GASTO_BROMAS, Logro.PLATA_COMO_CANCHA)
            Tienda.CONTRABANDO -> Logros.sumar(j, Contador.COMPRAS_NEGRO, 1, Metas.COMPRAS_NEGRO, Logro.VIP_MERCADO_NEGRO)
            Tienda.ANIMALES, Tienda.SEMBRADOR, Tienda.POCIONES -> Unit
        }
        return Respuesta(true, "¡${o.nombre} es tuyo! ${cobro.mensaje}")
    }
}
