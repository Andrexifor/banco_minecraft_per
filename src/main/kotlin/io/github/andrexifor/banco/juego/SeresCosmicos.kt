package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Display
import org.joml.Vector3f

/**
 * Seres cósmicos de los puestos de la plaza (los pone el datapack plaza_inca).
 *
 * Cada ser tiene displays con la etiqueta `ser_cerca` (máscara, unku, arma y nombre) y uno con
 * `ser_lejos` (su verdadera forma: el orbe gigante sobre el techo). Cada medio segundo el mod le dice
 * a CADA jugador qué ver según SU distancia: cerca de ese puesto ve la máscara; lejos, la forma real.
 * Para ocultar un display solo a un jugador se le envía su escala en 0 (los demás no se enteran).
 */
object SeresCosmicos {
    private const val CERCA = "ser_cerca"
    private const val LEJOS = "ser_lejos"
    private const val DISTANCIA = 12.0
    private const val ALCANCE = 128.0

    @Suppress("UNCHECKED_CAST")
    private val ESCALA: EntityDataAccessor<Vector3f>? = try {
        Display::class.java.getDeclaredField("DATA_SCALE_ID").apply { isAccessible = true }.get(null)
            as EntityDataAccessor<Vector3f>
    } catch (e: Exception) {
        BancoMinecraftPer.LOGGER.warn("Seres cósmicos: no se encontró la escala de los displays ({})", e.message)
        null
    }

    private val CERO = Vector3f(0f, 0f, 0f)

    fun registrarEventos() {
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 10 == 0) actualizar(servidor)
        }
    }

    private fun actualizar(servidor: MinecraftServer) {
        val escala = ESCALA ?: return
        for (nivel in servidor.allLevels) {
            val jugadores = nivel.players()
            if (jugadores.isEmpty()) continue
            val displays = nivel.allEntities.filterIsInstance<Display>().filter {
                val t = it.entityTags()
                CERCA in t || LEJOS in t
            }
            if (displays.isEmpty()) continue
            for (j in jugadores) {
                for (d in displays) {
                    val dx = j.x - d.x
                    val dz = j.z - d.z
                    val distancia2 = dx * dx + dz * dz
                    if (distancia2 > ALCANCE * ALCANCE) continue
                    val cerca = distancia2 <= DISTANCIA * DISTANCIA
                    val visible = if (CERCA in d.entityTags()) cerca else !cerca
                    val valor = if (visible) Vector3f(d.entityData.get(escala)) else CERO
                    j.connection.send(
                        ClientboundSetEntityDataPacket(d.id, listOf(SynchedEntityData.DataValue.create(escala, valor))),
                    )
                }
            }
        }
    }
}
