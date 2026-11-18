package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.UUID

/** Tipos de NPC del banco y qué menú abren. */
enum class TipoNpc(val id: String, val nombre: String, val profesion: String) {
    BANQUERO("banquero", "Banquero", "minecraft:librarian"),
    TENDERO("tendero", "Tendero", "minecraft:farmer"),
    HERRERO("herrero", "Herrero", "minecraft:armorer"),
    MERCADER("mercader", "Mercader legendario", "minecraft:weaponsmith"),
    ;

    companion object {
        fun porId(id: String): TipoNpc? = entries.firstOrNull { it.id == id }
    }
}

/**
 * NPC = aldeano vanilla sin IA, invulnerable y persistente. Se reconoce por su UUID,
 * guardado en banco_minecraft_per/npcs.json.
 *
 * Para registrar o quitar un NPC, el admin usa un comando y luego hace clic derecho
 * al aldeano: así el mod obtiene la entidad directamente del clic, sin buscarla.
 */
object Npcs {
    private const val SEGUNDOS_PARA_CLIC = 60

    private sealed interface Pendiente {
        val vence: Long
        data class Asignar(val tipo: TipoNpc, override val vence: Long) : Pendiente
        data class Quitar(override val vence: Long) : Pendiente
    }

    private lateinit var registro: MutableMap<UUID, String>
    private val pendientes = mutableMapOf<UUID, Pendiente>()

    fun cargar() {
        registro = BancoMinecraftPer.almacen.cargarNpcs()
    }

    fun registrarEventos() {
        UseEntityCallback.EVENT.register { jugador, _, mano, entidad, _ ->
            if (jugador !is ServerPlayer || entidad is Player) {
                InteractionResult.PASS
            } else {
                manejarClic(jugador, mano, entidad)
            }
        }
    }

    private fun manejarClic(jugador: ServerPlayer, mano: InteractionHand, entidad: Entity): InteractionResult {
        val pendiente = pendientes[jugador.uuid]?.takeIf { it.vence > System.currentTimeMillis() }
        if (pendiente != null) {
            if (mano != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS
            pendientes.remove(jugador.uuid)
            when (pendiente) {
                is Pendiente.Asignar -> asignar(jugador, entidad, pendiente.tipo)
                is Pendiente.Quitar -> quitar(jugador, entidad)
            }
            return InteractionResult.SUCCESS
        }

        val tipo = registro[entidad.uuid]?.let { TipoNpc.porId(it) } ?: return InteractionResult.PASS
        // Siempre se consume el clic para que no se abra el comercio vanilla
        if (mano == InteractionHand.MAIN_HAND) abrirMenu(jugador, tipo)
        return InteractionResult.SUCCESS
    }

    fun abrirMenu(jugador: ServerPlayer, tipo: TipoNpc) {
        when (tipo) {
            TipoNpc.BANQUERO -> GuiBanquero.abrir(jugador)
            TipoNpc.TENDERO -> GuiTienda.abrir(jugador)
            TipoNpc.HERRERO -> GuiHerrero.abrir(jugador)
            TipoNpc.MERCADER -> GuiMercader.abrir(jugador)
        }
    }

    /** Crea un aldeano en la posición del jugador y deja pendiente el clic para registrarlo. */
    fun crear(jugador: ServerPlayer, tipo: TipoNpc) {
        val servidor = (jugador.level() as ServerLevel).server
        val comando = "execute as ${jugador.uuid} at @s run summon minecraft:villager ~ ~ ~ {${nbtNpc(tipo)}}"
        servidor.commands.performPrefixedCommand(servidor.createCommandSourceStack(), comando)
        esperarAsignacion(jugador, tipo)
    }

    /** El próximo aldeano (o entidad) al que el jugador haga clic se convierte en este NPC. */
    fun esperarAsignacion(jugador: ServerPlayer, tipo: TipoNpc) {
        pendientes[jugador.uuid] = Pendiente.Asignar(tipo, vence())
    }

    /** El próximo NPC del banco al que el jugador haga clic se elimina. */
    fun esperarQuitar(jugador: ServerPlayer) {
        pendientes[jugador.uuid] = Pendiente.Quitar(vence())
    }

    private fun asignar(jugador: ServerPlayer, entidad: Entity, tipo: TipoNpc) {
        registro[entidad.uuid] = tipo.id
        BancoMinecraftPer.almacen.guardarNpcs(registro)
        // Lo deja quieto, invulnerable, persistente y sin comercios vanilla
        val servidor = (jugador.level() as ServerLevel).server
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack(),
            "data merge entity ${entidad.uuid} {${nbtNpc(tipo)}}",
        )
        avisar(jugador, "${tipo.nombre} listo. Clic derecho para abrir su menú.", ChatFormatting.GREEN)
    }

    private fun quitar(jugador: ServerPlayer, entidad: Entity) {
        if (registro.remove(entidad.uuid) == null) {
            avisar(jugador, "Esa entidad no es un NPC del banco", ChatFormatting.RED)
            return
        }
        BancoMinecraftPer.almacen.guardarNpcs(registro)
        entidad.discard()
        avisar(jugador, "NPC eliminado", ChatFormatting.GREEN)
    }

    private fun nbtNpc(tipo: TipoNpc): String =
        "NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b," +
            "CustomName:\"${tipo.nombre}\",CustomNameVisible:1b,Offers:{Recipes:[]}," +
            "VillagerData:{profession:\"${tipo.profesion}\",level:5,type:\"minecraft:plains\"}"

    private fun vence(): Long = System.currentTimeMillis() + SEGUNDOS_PARA_CLIC * 1000L

    private fun avisar(j: ServerPlayer, texto: String, color: ChatFormatting) {
        j.sendSystemMessage(Component.literal(texto).withStyle(color))
    }
}
