package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import java.nio.file.Files
import java.util.UUID

/**
 * Pirqakamayuq, maestro constructor (vende las máquinas). Solo lo ven y le pueden hablar quienes compraron
 * el artículo más caro del mercado negro; para el resto es invisible (el mod le manda a cada jugador su
 * propia versión: cuerpo invisible y sin nombre). Los desbloqueados quedan en banco_minecraft_per/pirqa.txt.
 */
object Pirqa {
    private val desbloqueados = HashSet<UUID>()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("pirqa.txt")

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> dato(nombre: String): EntityDataAccessor<T>? = try {
        Entity::class.java.getDeclaredField(nombre).apply { isAccessible = true }.get(null) as EntityDataAccessor<T>
    } catch (e: Exception) {
        BancoMinecraftPer.LOGGER.warn("Pirqakamayuq: falta el dato {} ({})", nombre, e.message)
        null
    }

    private val BANDERAS = dato<Byte>("DATA_SHARED_FLAGS_ID")
    private val NOMBRE_VISIBLE = dato<Boolean>("DATA_CUSTOM_NAME_VISIBLE")
    private const val INVISIBLE = 0x20

    fun desbloqueado(j: ServerPlayer) = j.uuid in desbloqueados

    fun desbloquear(j: ServerPlayer) {
        if (!desbloqueados.add(j.uuid)) return
        guardar()
        j.sendSystemMessage(
            Component.literal("Alguien en el mercado negro empieza a verte… El Pirqakamayuq, maestro constructor, ya trata contigo.")
                .withStyle(ChatFormatting.DARK_RED),
        )
    }

    fun rechazar(j: ServerPlayer) =
        j.sendSystemMessage(Component.literal("…").withStyle(ChatFormatting.DARK_GRAY))

    private fun cargar() {
        try {
            if (Files.exists(archivo)) Files.readAllLines(archivo).mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.forEach { desbloqueados += it }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer pirqa.txt: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.write(archivo, desbloqueados.map { it.toString() })
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar pirqa.txt: {}", e.message)
        }
    }

    fun registrarEventos() {
        cargar()
        ServerTickEvents.END_SERVER_TICK.register { servidor -> if (servidor.tickCount % 10 == 0) ocultar(servidor) }
    }

    private fun ocultar(servidor: MinecraftServer) {
        val banderas = BANDERAS ?: return
        val nombre = NOMBRE_VISIBLE ?: return
        val ids = Npcs.entidadesDe(TipoNpc.PIRQA)
        if (ids.isEmpty()) return
        for (nivel in servidor.allLevels) {
            val jugadores = nivel.players()
            if (jugadores.isEmpty()) continue
            for (id in ids) {
                val e = nivel.getEntity(id) ?: continue
                val real = e.entityData.get(banderas)
                for (j in jugadores) {
                    if (j.distanceToSqr(e) > 96.0 * 96.0) continue
                    val ve = desbloqueado(j)
                    val valores = listOf(
                        SynchedEntityData.DataValue.create(banderas, if (ve) real else (real.toInt() or INVISIBLE).toByte()),
                        SynchedEntityData.DataValue.create(nombre, ve && e.isCustomNameVisible),
                    )
                    j.connection.send(ClientboundSetEntityDataPacket(e.id, valores))
                }
            }
        }
    }
}
