package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import java.util.UUID

/** Tipos de NPC del banco y qué menú abren. */
enum class TipoNpc(val id: String, val nombre: String, val profesion: String) {
    BANQUERO("banquero", "Banquero", "minecraft:librarian"),
    TENDERO("tendero", "Tendero", "minecraft:farmer"),
    ;

    companion object {
        fun porId(id: String): TipoNpc? = entries.firstOrNull { it.id == id }
    }
}

/**
 * NPC = aldeano vanilla sin IA, invulnerable y persistente. Se reconoce por su UUID,
 * que se guarda en banco_minecraft_per/npcs.json (así no depende de nombres o etiquetas
 * que un jugador podría cambiar con una etiqueta de nombre).
 */
object Npcs {
    private lateinit var registro: MutableMap<UUID, String>

    fun cargar() {
        registro = BancoMinecraftPer.almacen.cargarNpcs()
    }

    fun registrarEventos() {
        UseEntityCallback.EVENT.register { jugador, _, mano, entidad, _ ->
            val tipo = registro[entidad.uuid]?.let { TipoNpc.porId(it) }
            if (tipo == null || jugador !is ServerPlayer) {
                InteractionResult.PASS
            } else {
                // Siempre se consume el clic para que no se abra el comercio vanilla
                if (mano == InteractionHand.MAIN_HAND) abrirMenu(jugador, tipo)
                InteractionResult.SUCCESS
            }
        }
    }

    fun abrirMenu(jugador: ServerPlayer, tipo: TipoNpc) {
        when (tipo) {
            TipoNpc.BANQUERO -> GuiBanquero.abrir(jugador)
            TipoNpc.TENDERO -> GuiTienda.abrir(jugador)
        }
    }

    /** Crea el NPC donde está parado el jugador. Devuelve true si apareció. */
    fun crear(jugador: ServerPlayer, tipo: TipoNpc): Boolean {
        val nivel = jugador.level() as ServerLevel
        val servidor = nivel.server
        val id = UUID.randomUUID()

        val comun = "UUID:${uuidComoArreglo(id)},NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b," +
            "CustomName:\"${tipo.nombre}\",CustomNameVisible:1b"
        val conProfesion = "$comun,VillagerData:{profession:\"${tipo.profesion}\",level:5,type:\"minecraft:plains\"}"

        // Se ejecuta como consola (permisos completos) pero en la posición del jugador
        for (nbt in listOf(conProfesion, comun)) {
            val comando = "execute as ${jugador.uuid} at @s run summon minecraft:villager ~ ~ ~ {$nbt}"
            servidor.commands.performPrefixedCommand(servidor.createCommandSourceStack(), comando)
            if (nivel.getEntity(id) != null) {
                registro[id] = tipo.id
                BancoMinecraftPer.almacen.guardarNpcs(registro)
                return true
            }
        }
        return false
    }

    /** Quita el NPC del banco más cercano (hasta 6 bloques). */
    fun quitarCercano(jugador: ServerPlayer): Boolean {
        val nivel = jugador.level() as ServerLevel
        val cercano = registro.keys
            .mapNotNull { nivel.getEntity(it) }
            .filter { it.distanceTo(jugador) <= 6f }
            .minByOrNull { it.distanceTo(jugador) }
            ?: return false
        registro.remove(cercano.uuid)
        cercano.discard()
        BancoMinecraftPer.almacen.guardarNpcs(registro)
        return true
    }

    private fun uuidComoArreglo(u: UUID): String {
        val a = (u.mostSignificantBits shr 32).toInt()
        val b = u.mostSignificantBits.toInt()
        val c = (u.leastSignificantBits shr 32).toInt()
        val d = u.leastSignificantBits.toInt()
        return "[I;$a,$b,$c,$d]"
    }
}
