package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.texto
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.file.Files

/**
 * Ofrenda al Inti: una barra común para todo el servidor en el altar de la plaza. Cada jugador ofrenda
 * oro, esmeraldas, diamantes o tumbaga (los bloques valen 9 veces más). Cuando entre todos llegan a
 * [META] puntos, el Inti despierta: 1 hora en la que todos los conectados minan más rápido (Prisa II),
 * corren más (Velocidad I) y tienen más suerte (Suerte, mejor pesca). Si se vuelve a llenar mientras
 * está despierto, suma otra hora. En el Inti Raymi cada ofrenda vale el doble.
 * El estado queda en banco_minecraft_per/ofrenda.txt ("puntos fin_en_milisegundos").
 */
object Ofrenda {
    const val META = 1000
    private const val HORA_MS = 60L * 60 * 1000

    private var puntos = 0
    private var hasta = 0L
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("ofrenda.txt")

    private fun cargar() {
        try {
            if (!Files.exists(archivo)) return
            val p = Files.readString(archivo).trim().split(" ")
            puntos = p.getOrNull(0)?.toIntOrNull() ?: 0
            hasta = p.getOrNull(1)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer ofrenda.txt: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.writeString(archivo, "$puntos $hasta")
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar ofrenda.txt: {}", e.message)
        }
    }

    fun despierto() = System.currentTimeMillis() < hasta

    /** Puntos que vale un ítem (0 si no se puede ofrendar). */
    private fun valor(s: ItemStack): Int {
        if (s.isEmpty) return 0
        if (Maquinas.esTumbaga(s)) return 10
        if (s.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return 0
        return VALORES[s.item] ?: 0
    }

    private val VALORES: Map<Item, Int> by lazy {
        mapOf(
            Items.GOLD_INGOT to 1, Items.GOLD_BLOCK to 9,
            Items.EMERALD to 3, Items.EMERALD_BLOCK to 27,
            Items.DIAMOND to 5, Items.DIAMOND_BLOCK to 45,
        )
    }

    private fun multiplicador() = if (Eventos.activo("inti_raymi")) 2 else 1

    fun registrarEventos() {
        cargar()
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 100 == 0 && despierto()) bendecir(servidor)
            if (servidor.tickCount % 10 == 0 && despierto()) brillar(servidor)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { _ -> guardar() }
    }

    /** Mientras el Inti está despierto, todos los conectados tienen sus dones (se renuevan cada 5 s). */
    private fun bendecir(servidor: MinecraftServer) {
        for (j in servidor.playerList.players) {
            j.addEffect(MobEffectInstance(MobEffects.HASTE, 240, 1, true, false, true))
            j.addEffect(MobEffectInstance(MobEffects.SPEED, 240, 0, true, false, true))
            j.addEffect(MobEffectInstance(MobEffects.LUCK, 240, 0, true, false, true))
        }
    }

    /** El sol de la plaza suelta destellos mientras está despierto. */
    private fun brillar(servidor: MinecraftServer) {
        val nivel = servidor.overworld()
        for (d in nivel.getEntities(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY) { "inti_sol" in it.entityTags() }) {
            nivel.sendParticles(ParticleTypes.END_ROD, d.x, d.y, d.z, 6, 2.5, 2.5, 2.5, 0.02)
            nivel.sendParticles(ParticleTypes.FLAME, d.x, d.y, d.z, 4, 2.0, 2.0, 2.0, 0.01)
        }
    }

    fun abrir(j: ServerPlayer) {
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(texto("Ofrenda al Inti", ChatFormatting.GOLD))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        val estado = if (despierto()) {
            val min = (hasta - System.currentTimeMillis()) / 60000
            texto("¡El Inti está despierto! Quedan $min min", ChatFormatting.YELLOW)
        } else {
            texto("El Inti duerme", ChatFormatting.GRAY)
        }
        gui.setSlot(
            4,
            GuiElementBuilder(Items.SUNFLOWER).setName(texto("Ofrenda: $puntos / $META puntos", ChatFormatting.GOLD)).setLore(
                listOfNotNull(
                    estado,
                    texto("Al llegar a $META, 1 hora para todos los conectados:", ChatFormatting.GRAY),
                    texto("Prisa II, Velocidad I y Suerte", ChatFormatting.GRAY),
                    if (multiplicador() > 1) texto("¡Inti Raymi! Cada ofrenda vale el doble", ChatFormatting.LIGHT_PURPLE) else null,
                ),
            ),
        )
        // barra de 9 casillas
        val llenas = (puntos * 9 / META).coerceIn(0, 9)
        for (i in 0 until 9) {
            gui.setSlot(
                9 + i,
                GuiElementBuilder(if (i < llenas) Items.YELLOW_STAINED_GLASS_PANE else Items.GRAY_STAINED_GLASS_PANE)
                    .setName(texto("$puntos / $META", ChatFormatting.GOLD)),
            )
        }
        gui.setSlot(
            19,
            GuiElementBuilder(Items.GOLD_INGOT).setName(texto("Cuánto vale cada ofrenda", ChatFormatting.AQUA)).setLore(
                listOf(
                    "Lingote de oro: 1 · Bloque de oro: 9",
                    "Esmeralda: 3 · Bloque de esmeralda: 27",
                    "Diamante: 5 · Bloque de diamante: 45",
                    "Tumbaga: 10",
                ).map { texto(it, ChatFormatting.GRAY) },
            ),
        )
        val mano = GuiUtil.boton(ItemStack(Items.GOLD_NUGGET), "Ofrendar lo que tengo en la mano", listOf("Solo la pila de tu mano principal"), ChatFormatting.YELLOW) {
            ofrendar(j, soloMano = true); abrir(j)
        }
        gui.setSlot(22, mano)
        val todo = GuiUtil.boton(ItemStack(Items.GOLD_BLOCK), "Ofrendar todo", listOf("Todo el oro, esmeraldas, diamantes", "y tumbaga de tu inventario"), ChatFormatting.GOLD) {
            ofrendar(j, soloMano = false); abrir(j)
        }
        gui.setSlot(25, todo)
        gui.open()
    }

    private fun ofrendar(j: ServerPlayer, soloMano: Boolean) {
        val servidor = j.level().server ?: return
        val inv = j.inventory
        var suma = 0
        val ranuras = if (soloMano) listOf(inv.selectedSlot) else (0 until inv.containerSize).toList()
        for (i in ranuras) {
            val s = inv.getItem(i)
            val v = valor(s)
            if (v <= 0) continue
            suma += v * s.count
            inv.setItem(i, ItemStack.EMPTY)
        }
        if (suma == 0) {
            GuiUtil.avisar(j, Respuesta(false, if (soloMano) "Eso no se puede ofrendar al Inti" else "No llevas nada que ofrendar"))
            return
        }
        suma *= multiplicador()
        puntos += suma
        Efectos.sonar(j, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.8f)
        GuiUtil.avisar(j, Respuesta(true, "Ofrendaste $suma puntos al Inti. Van $puntos / $META."))
        var despertó = false
        while (puntos >= META) {
            puntos -= META
            hasta = maxOf(hasta, System.currentTimeMillis()) + HORA_MS
            despertó = true
        }
        guardar()
        if (despertó) {
            Logros.anunciar(servidor, "¡El Inti despertó gracias a la ofrenda de ${j.name.string}! Durante 1 hora todos minan más rápido, corren más y tienen más suerte.")
            for (p in servidor.playerList.players) Efectos.sonar(p, SoundEvents.BEACON_ACTIVATE, 1f, 1f)
            bendecir(servidor)
        }
    }

    /** Para el comando de admin: estado actual. */
    fun estadoTexto(): Component = Component.literal("Ofrenda: $puntos / $META · despierto: ${despierto()}")
}
