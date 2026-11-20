package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
import java.nio.file.Files
import java.util.UUID

/**
 * Sombreros cosméticos (los vende el Mercader de bromas; algunos solo salen en eventos). No ocupan el
 * casco: el sombrero es un modelo propio (item_display) montado sobre la cabeza del jugador, que el mod
 * gira con la cabeza. Clic derecho con el sombrero: te lo pones; otra vez: te lo quitas (el ítem se queda
 * contigo). El que lo lleva no lo ve en primera persona para que no le tape la vista; los demás sí.
 * Se guarda en sombreros.txt y vuelve solo al entrar, al reaparecer o al cambiar de dimensión.
 */
object Sombreros {
    private const val ETIQUETA = "banco_sombrero"

    val SOMBREROS = CatalogoOfertas.SOMBREROS.associateBy { it.id }

    /** El ítem del sombrero en formato /give. */
    fun item(id: String): String = CatalogoOfertas.sombrero(id)

    @Suppress("UNCHECKED_CAST")
    private val ESCALA: EntityDataAccessor<Vector3f>? = try {
        Display::class.java.getDeclaredField("DATA_SCALE_ID").apply { isAccessible = true }.get(null) as EntityDataAccessor<Vector3f>
    } catch (e: Exception) {
        BancoMinecraftPer.LOGGER.warn("Sombreros: falta DATA_SCALE_ID ({})", e.message)
        null
    }

    private val puestos = HashMap<UUID, String>()
    private val displays = HashMap<UUID, UUID>()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("sombreros.txt")

    private fun cargar() {
        try {
            if (!Files.exists(archivo)) return
            for (l in Files.readAllLines(archivo)) {
                val p = l.trim().split(" ")
                if (p.size != 2 || p[1] !in SOMBREROS) continue
                val u = runCatching { UUID.fromString(p[0]) }.getOrNull() ?: continue
                puestos[u] = p[1]
            }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer sombreros.txt: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.write(archivo, puestos.map { "${it.key} ${it.value}" })
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar sombreros.txt: {}", e.message)
        }
    }

    private fun idDe(s: ItemStack): String? {
        if (s.isEmpty) return null
        val id = s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(BancoMinecraftPer.MOD_ID)?.getStringOr("sombrero", "")
        return id?.takeIf { it in SOMBREROS }
    }

    fun registrarEventos() {
        cargar()
        UseItemCallback.EVENT.register { jugador, _, mano ->
            if (jugador !is ServerPlayer) return@register InteractionResult.PASS
            val id = idDe(jugador.getItemInHand(mano)) ?: return@register InteractionResult.PASS
            quitar(jugador.uuid, jugador.level() as ServerLevel)
            if (puestos[jugador.uuid] == id) {
                puestos.remove(jugador.uuid)
                jugador.sendSystemMessage(Component.literal("Te quitaste: ${SOMBREROS[id]!!.nombre}.").withStyle(ChatFormatting.GRAY))
            } else {
                puestos[jugador.uuid] = id
                poner(jugador, id)
                Efectos.sonar(jugador, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1f, 1f)
                jugador.sendSystemMessage(Component.literal("Te pusiste: ${SOMBREROS[id]!!.nombre}.").withStyle(ChatFormatting.GOLD))
            }
            guardar()
            InteractionResult.SUCCESS
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> quitar(handler.player.uuid, handler.player.level() as ServerLevel) }
        ServerTickEvents.END_SERVER_TICK.register { servidor -> tick(servidor) }
    }

    private fun poner(j: ServerPlayer, id: String) {
        val nivel = j.level() as ServerLevel
        val etiqueta = "sombrero_${j.uuid}"
        val p = j.position()
        val nbt = "{Tags:[\"$ETIQUETA\",\"$etiqueta\"],teleport_duration:1,view_range:1f,item_display:\"fixed\"," +
            "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"banco_minecraft_per:sombrero_$id\"}}," +
            "transformation:{left_rotation:[0f,1f,0f,0f],right_rotation:[0f,0f,0f,1f],translation:[0f,0.45f,0f],scale:[1f,1f,1f]}}"
        val servidor = nivel.server
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withLevel(nivel).withPosition(p).withSuppressedOutput(),
            "summon minecraft:item_display ${p.x} ${p.y + 2} ${p.z} $nbt",
        )
        val d = nivel.getEntitiesOfClass(Display::class.java, AABB(p, p).inflate(3.0)) { etiqueta in it.entityTags() }.firstOrNull() ?: return
        d.startRiding(j, true, true)
        displays[j.uuid] = d.uuid
    }

    private fun quitar(dueno: UUID, nivel: ServerLevel) {
        val id = displays.remove(dueno) ?: return
        val servidor = nivel.server
        for (l in servidor.allLevels) l.getEntity(id)?.let { it.stopRiding(); it.discard() }
    }

    private fun tick(servidor: MinecraftServer) {
        if (puestos.isEmpty()) return
        val revisar = servidor.tickCount % 20 == 0
        val ocultar = servidor.tickCount % 10 == 0
        for (j in servidor.playerList.players) {
            val id = puestos[j.uuid] ?: continue
            val nivel = j.level() as ServerLevel
            val d = displays[j.uuid]?.let { nivel.getEntity(it) as? Display }
            if (d == null || d.vehicle !== j) {
                if (revisar && !j.isDeadOrDying) {
                    quitar(j.uuid, nivel)
                    poner(j, id)
                }
                continue
            }
            d.setYRot(j.yHeadRot)
            // el que lo lleva no lo ve (si no, el ala le tapa la vista en primera persona)
            val escala = ESCALA
            if (ocultar && escala != null) {
                j.connection.send(ClientboundSetEntityDataPacket(d.id, listOf(SynchedEntityData.DataValue.create(escala, Vector3f(0f, 0f, 0f)))))
            }
        }
        if (servidor.tickCount % 100 == 0) limpiar(servidor)
    }

    private fun limpiar(servidor: MinecraftServer) {
        val vivos = displays.values.toSet()
        for (nivel in servidor.allLevels) {
            nivel.allEntities.filterIsInstance<Display>()
                .filter { ETIQUETA in it.entityTags() && it.uuid !in vivos }
                .forEach { it.stopRiding(); it.discard() }
        }
    }
}
