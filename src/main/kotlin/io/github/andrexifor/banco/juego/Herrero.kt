package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.Impuestos
import io.github.andrexifor.banco.nucleo.Legendario
import io.github.andrexifor.banco.nucleo.TipoKit
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * Servicios del Herrero y compras del Mercader. Cobran soles (con impuesto de servicios)
 * a través de Operaciones.cobrar: banco primero, efectivo del inventario si falta.
 */
object Herrero {
    private val cfg get() = BancoMinecraftPer.herrero

    private fun impuesto(monto: Long) = Impuestos.calcular(monto, cfg.impuestoServiciosPorcentaje)

    fun conImpuesto(monto: Long): Long = monto + impuesto(monto)

    // ---------- reparar ----------

    fun precioReparar(stack: ItemStack): Long =
        if (stack.isEmpty || !stack.isDamageableItem) 0
        else cfg.precioReparacion(stack.damageValue, stack.maxDamage, Encantamientos.esLegendario(stack))

    fun reparar(j: ServerPlayer): Respuesta {
        val stack = j.mainHandItem
        if (stack.isEmpty || !stack.isDamageableItem) return Respuesta(false, "Ten en la mano el ítem que quieres reparar")
        if (!stack.isDamaged) return Respuesta(false, "${stack.hoverName.string} no está dañado")
        val precio = precioReparar(stack)
        val cobro = Operaciones.cobrar(j, precio, "HERRERO_REPARAR ${KitsReparacion.ruta(stack)}", impuesto(precio))
        if (!cobro.ok) return cobro
        stack.damageValue = 0
        if (Encantamientos.esLegendario(stack)) Logros.otorgar(j, io.github.andrexifor.banco.nucleo.Logro.COMO_NUEVO)
        return Respuesta(true, "${stack.hoverName.string} reparado. ${cobro.mensaje}")
    }

    // ---------- combinar libro ----------

    /** Resultado de simular la combinación: qué se aplicaría, qué no y cuánto cuesta. */
    data class Combinacion(
        val aplicar: Map<Holder<Enchantment>, Int>,
        val rechazados: List<String>,
        val precio: Long,
    )

    fun simularCombinar(item: ItemStack, libro: ItemStack): Combinacion? {
        if (item.isEmpty || !libro.`is`(Items.ENCHANTED_BOOK)) return null
        val delLibro = libro.get(DataComponents.STORED_ENCHANTMENTS) ?: return null
        val actuales = Encantamientos.encantamientosDe(item)
        val prohibidaReparacion = Encantamientos.reparacionProhibida(item)
        val aplicar = linkedMapOf<Holder<Enchantment>, Int>()
        val rechazados = mutableListOf<String>()
        var precio = 0L

        for (entrada in delLibro.entrySet()) {
            val h = entrada.key
            val nivelLibro = entrada.intValue
            val nombre = Enchantment.getFullname(h, nivelLibro).string
            when {
                h.registeredName in cfg.noCombinables -> rechazados += "$nombre (el herrero no lo aplica)"
                Encantamientos.esReparacion(h) && prohibidaReparacion -> rechazados += "$nombre (no permitido en ítems legendarios)"
                !h.value().canEnchant(item) -> rechazados += "$nombre (no sirve para este ítem)"
                actuales.keySet().any { it != h && !Enchantment.areCompatible(it, h) } -> rechazados += "$nombre (incompatible)"
                else -> {
                    val actual = actuales.getLevel(h)
                    val tope = if (cfg.nivelMaximoHerrero > 0) cfg.nivelMaximoHerrero else h.value().maxLevel
                    val nuevo = cfg.nivelCombinado(actual, nivelLibro, tope)
                    if (nuevo > actual) {
                        aplicar[h] = nuevo
                        precio += cfg.combinarPorNivel * nuevo
                    } else {
                        rechazados += "$nombre (no mejora el nivel actual)"
                    }
                }
            }
        }
        return Combinacion(aplicar, rechazados, precio)
    }

    /** Ítem en la mano principal + libro encantado en la mano secundaria. */
    fun combinar(j: ServerPlayer): Respuesta {
        val item = j.mainHandItem
        val libro = j.offhandItem
        val sim = simularCombinar(item, libro)
            ?: return Respuesta(false, "Pon el ítem en la mano principal y el libro encantado en la secundaria")
        if (sim.aplicar.isEmpty()) {
            return Respuesta(false, "Nada que aplicar: " + sim.rechazados.joinToString("; "))
        }
        val cobro = Operaciones.cobrar(j, sim.precio, "HERRERO_COMBINAR ${KitsReparacion.ruta(item)}", impuesto(sim.precio))
        if (!cobro.ok) return cobro
        val mutable = ItemEnchantments.Mutable(Encantamientos.encantamientosDe(item))
        sim.aplicar.forEach { (h, nivel) -> mutable.set(h, nivel) }
        item.set(DataComponents.ENCHANTMENTS, mutable.toImmutable())
        libro.shrink(1)
        val aplicados = sim.aplicar.entries.joinToString(", ") { (h, n) -> Enchantment.getFullname(h, n).string }
        val extra = if (sim.rechazados.isEmpty()) "" else " No aplicados: " + sim.rechazados.joinToString("; ")
        return Respuesta(true, "Aplicado: $aplicados. ${cobro.mensaje}.$extra")
    }

    // ---------- renombrar ----------

    fun renombrar(j: ServerPlayer, nombre: String): Respuesta {
        val stack = j.mainHandItem
        val limpio = nombre.trim()
        if (stack.isEmpty) return Respuesta(false, "Ten en la mano el ítem que quieres renombrar")
        if (Efectivo.denominacionDe(stack) != null || KitsReparacion.leer(stack) != null) {
            return Respuesta(false, "Ese ítem no se puede renombrar")
        }
        if (limpio.isEmpty() || limpio.length > 50) return Respuesta(false, "El nombre debe tener entre 1 y 50 caracteres")
        val cobro = Operaciones.cobrar(j, cfg.renombrar, "HERRERO_RENOMBRAR", impuesto(cfg.renombrar))
        if (!cobro.ok) return cobro
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(limpio).withStyle { it.withItalic(false) })
        return Respuesta(true, "Ahora se llama \"$limpio\". ${cobro.mensaje}")
    }

    // ---------- kits ----------

    fun comprarKit(j: ServerPlayer, tipo: TipoKit, usos: Int): Respuesta {
        val precio = cfg.precioKit(tipo, usos) ?: return Respuesta(false, "Ese kit no está a la venta")
        if (Efectivo.casillasLibres(j) < 1) return Respuesta(false, "Necesitas una casilla libre en el inventario")
        val cobro = Operaciones.cobrar(j, precio, "KIT_COMPRA ${tipo.id}_$usos", impuesto(precio))
        if (!cobro.ok) return cobro
        Inventarios.dar(j, KitsReparacion.crear(tipo, usos))
        return Respuesta(true, "Compraste ${tipo.nombre} ($usos ${if (usos == 1) "uso" else "usos"}). ${cobro.mensaje}")
    }

    // ---------- mercader ----------

    fun comprarLegendario(j: ServerPlayer, l: Legendario): Respuesta {
        val server = j.level().server ?: return Respuesta(false, "Servidor no disponible")
        val stack = Encantamientos.crearLegendario(server, l) ?: return Respuesta(false, "Ítem desconocido: ${l.item}")
        if (Efectivo.casillasLibres(j) < 1) return Respuesta(false, "Necesitas una casilla libre en el inventario")
        val cobro = Operaciones.cobrar(j, l.precio, "LEGENDARIO ${l.id}", impuesto(l.precio))
        if (!cobro.ok) return cobro
        Inventarios.dar(j, stack)
        Logros.otorgar(j, io.github.andrexifor.banco.nucleo.Logro.NO_ARRIESGA)
        val datos = Logros.datos(j)
        datos.legendarios().add(l.id)
        Logros.marcarCambio()
        val todos = BancoMinecraftPer.legendarios.legendarios.map { it.id }
        if (todos.isNotEmpty() && datos.legendarios().containsAll(todos)) {
            Logros.otorgar(j, io.github.andrexifor.banco.nucleo.Logro.COLECCIONISTA)
        }
        server.playerList.broadcastSystemMessage(
            Component.literal("${j.name.string} compró ${l.nombre} al Oráculo del Cosmos").withStyle(ChatFormatting.GOLD),
            false,
        )
        return Respuesta(true, "¡${l.nombre} es tuyo! ${cobro.mensaje}")
    }
}
