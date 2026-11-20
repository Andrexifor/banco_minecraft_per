package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
import java.nio.file.Files
import java.util.UUID

/**
 * Pociones del Kallawaya (el mixin de Consumable llama a [alBeber] al terminar de tomarlas):
 *  - amparo: hasta tu próxima muerte, al morir conservas armadura, armas, mochilas y flores con animales;
 *  - regreso: a los 2 s te lleva a tu cama (o punto de reaparición). Si no hay, no se gasta;
 *  - muerte: a los 2 s te lleva a donde moriste por última vez (con resistencia al fuego y caída lenta);
 *  - buscador: 20 s viendo a través de las paredes los minerales del chunk donde estás (solo tú).
 */
object Pociones {
    private const val MARCA = BancoMinecraftPer.MOD_ID
    private const val ESPERA = 40
    private const val DURA_BUSCADOR = 400
    private const val MAX_MINERALES = 250

    fun tipo(s: ItemStack): String? =
        s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(MARCA)?.getStringOr("pocion", "")?.ifBlank { null }

    private fun aviso(j: ServerPlayer, t: String, c: ChatFormatting) = j.sendSystemMessage(Component.literal(t).withStyle(c))

    // ---------- amparo ----------
    private val amparados = HashSet<UUID>()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(MARCA).resolve("amparo.txt")
    private val guardados = HashMap<UUID, MutableList<ItemStack>>()

    private fun cargar() {
        try {
            if (Files.exists(archivo)) Files.readAllLines(archivo).mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.forEach { amparados += it }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer amparo.txt: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.write(archivo, amparados.map { it.toString() })
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar amparo.txt: {}", e.message)
        }
    }

    private fun protegido(s: ItemStack): Boolean {
        if (s.isEmpty) return false
        if (Mochilas.esMochila(s) || FlorTraslado.llena(s)) return true
        val eq = s.get(DataComponents.EQUIPPABLE)
        if (eq != null && eq.slot().type == EquipmentSlot.Type.HUMANOID_ARMOR) return true
        val ruta = BuiltInRegistries.ITEM.getKey(s.item).path
        return ruta.endsWith("_sword") || ruta.endsWith("_axe") || ruta.endsWith("_spear") ||
            ruta in setOf("bow", "crossbow", "trident", "mace", "shield")
    }

    // ---------- teletransportes con espera ----------
    private data class Viaje(val nivel: ServerLevel, val pos: BlockPos, val tick: Int, val muerte: Boolean)
    private val viajes = HashMap<UUID, Viaje>()

    // ---------- buscador ----------
    private data class Busqueda(val dueno: UUID, val nivel: ServerLevel, val displays: List<UUID>, val fin: Int)
    private val busquedas = ArrayList<Busqueda>()

    @Suppress("UNCHECKED_CAST")
    private val ESCALA: EntityDataAccessor<Vector3f>? = try {
        Display::class.java.getDeclaredField("DATA_SCALE_ID").apply { isAccessible = true }.get(null) as EntityDataAccessor<Vector3f>
    } catch (e: Exception) {
        null
    }

    fun registrarEventos() {
        cargar()
        ServerLivingEntityEvents.ALLOW_DEATH.register { entidad, _, _ ->
            if (entidad is ServerPlayer && entidad.uuid in amparados &&
                !entidad.mainHandItem.`is`(Items.TOTEM_OF_UNDYING) && !entidad.offhandItem.`is`(Items.TOTEM_OF_UNDYING)
            ) {
                val lista = ArrayList<ItemStack>()
                val inv = entidad.inventory
                for (i in 0 until inv.containerSize) {
                    if (protegido(inv.getItem(i))) lista += inv.removeItemNoUpdate(i)
                }
                guardados.getOrPut(entidad.uuid) { ArrayList() } += lista
                amparados -= entidad.uuid
                guardar()
            }
            true
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, nuevo, _ -> devolver(nuevo) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> if (!handler.player.isDeadOrDying) devolver(handler.player) }
        ServerTickEvents.END_SERVER_TICK.register { servidor -> tick(servidor) }
    }

    private fun devolver(j: ServerPlayer) {
        val lista = guardados.remove(j.uuid) ?: return
        lista.forEach { Inventarios.dar(j, it) }
        if (lista.isNotEmpty()) aviso(j, "La poción de amparo protegió tu armadura, armas y mochilas.", ChatFormatting.GOLD)
    }

    /** Lo llama el mixin al terminar de tomar la poción. false = no se gasta (no se pudo usar). */
    @JvmStatic
    fun alBeber(j: ServerPlayer, s: ItemStack): Boolean {
        val t = tipo(s) ?: return true
        return when (t) {
            "amparo" -> {
                amparados += j.uuid
                guardar()
                aviso(j, "Estás amparado: si mueres, conservas armadura, armas, mochilas y flores con animales.", ChatFormatting.GOLD)
                true
            }
            "regreso" -> regreso(j)
            "muerte" -> muerte(j)
            "buscador" -> { buscador(j); true }
            else -> true
        }
    }

    private fun regreso(j: ServerPlayer): Boolean {
        val servidor = j.level().server ?: return false
        val cfg = j.respawnConfig
        val gp: GlobalPos? = cfg?.respawnData()?.globalPos()
        val nivel = gp?.let { servidor.getLevel(it.dimension()) }
        if (cfg == null || gp == null || nivel == null) {
            aviso(j, "No tienes cama ni punto de reaparición. La poción no se gastó.", ChatFormatting.RED); return false
        }
        val bloque = nivel.getBlockState(gp.pos()).block
        if (!cfg.forced() && bloque !is BedBlock && bloque !is RespawnAnchorBlock) {
            aviso(j, "Tu cama ya no está. La poción no se gastó.", ChatFormatting.RED); return false
        }
        viajes[j.uuid] = Viaje(nivel, gp.pos().above(), servidor.tickCount + ESPERA, false)
        aviso(j, "Volviendo a tu cama…", ChatFormatting.AQUA)
        return true
    }

    private fun muerte(j: ServerPlayer): Boolean {
        val servidor = j.level().server ?: return false
        val gp = j.lastDeathLocation.orElse(null)
        val nivel = gp?.let { servidor.getLevel(it.dimension()) }
        if (gp == null || nivel == null || gp.pos().y < nivel.minY) {
            aviso(j, "No hay un lugar de muerte al que volver. La poción no se gastó.", ChatFormatting.RED); return false
        }
        viajes[j.uuid] = Viaje(nivel, gp.pos(), servidor.tickCount + ESPERA, true)
        aviso(j, "Volviendo al lugar donde caíste…", ChatFormatting.DARK_PURPLE)
        return true
    }

    private val COLORES = linkedMapOf(
        "diamond" to 0x5FE8FF, "emerald" to 0x34D058, "gold" to 0xFFD23F, "iron" to 0xF2E6DA,
        "redstone" to 0xFF3030, "lapis" to 0x3060FF, "copper" to 0xE07A3C, "coal" to 0x505050,
        "quartz" to 0xFFFFFF, "ancient_debris" to 0xB06BFF,
    )

    private fun buscador(j: ServerPlayer) {
        val nivel = j.level() as ServerLevel
        val servidor = nivel.server
        val cx = j.blockX shr 4
        val cz = j.blockZ shr 4
        val etiqueta = "buscador_${j.uuid}"
        var n = 0
        val fuente = servidor.createCommandSourceStack().withLevel(nivel).withSuppressedOutput()
        loop@ for (y in nivel.minY until nivel.maxY) {
            for (x in (cx shl 4) until (cx shl 4) + 16) {
                for (z in (cz shl 4) until (cz shl 4) + 16) {
                    val estado = nivel.getBlockState(BlockPos(x, y, z))
                    val id = BuiltInRegistries.BLOCK.getKey(estado.block)
                    val ruta = id.path
                    if (!ruta.endsWith("_ore") && ruta != "ancient_debris") continue
                    val color = COLORES.entries.firstOrNull { it.key in ruta }?.value ?: 0xFFFFFF
                    servidor.commands.performPrefixedCommand(
                        fuente,
                        "summon minecraft:block_display $x $y $z {Tags:[\"banco_buscador\",\"$etiqueta\"],Glowing:1b," +
                            "glow_color_override:$color,block_state:{Name:\"$id\"}," +
                            "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]," +
                            "translation:[0.02f,0.02f,0.02f],scale:[0.96f,0.96f,0.96f]}}",
                    )
                    if (++n >= MAX_MINERALES) break@loop
                }
            }
        }
        val caja = AABB(((cx shl 4) - 1).toDouble(), nivel.minY.toDouble(), ((cz shl 4) - 1).toDouble(),
            ((cx shl 4) + 17).toDouble(), nivel.maxY.toDouble(), ((cz shl 4) + 17).toDouble())
        val ids = nivel.getEntitiesOfClass(Display::class.java, caja) { etiqueta in it.entityTags() }.map { it.uuid }
        busquedas += Busqueda(j.uuid, nivel, ids, servidor.tickCount + DURA_BUSCADOR)
        aviso(j, if (n == 0) "No hay minerales en este chunk." else "Ves $n minerales de este chunk durante 20 segundos.", ChatFormatting.GREEN)
    }

    private fun tick(servidor: MinecraftServer) {
        val ahora = servidor.tickCount
        // viajes pendientes: partículas mientras esperan, luego el teletransporte
        val it = viajes.entries.iterator()
        while (it.hasNext()) {
            val (uuid, v) = it.next()
            val j = servidor.playerList.getPlayer(uuid)
            if (j == null) { it.remove(); continue }
            val nivelJ = j.level() as ServerLevel
            if (ahora < v.tick) {
                if (ahora % 4 == 0) nivelJ.sendParticles(if (v.muerte) ParticleTypes.SOUL else ParticleTypes.PORTAL, j.x, j.y + 1, j.z, 8, 0.4, 0.8, 0.4, 0.05)
                continue
            }
            it.remove()
            val dim = v.nivel.dimension().identifier()
            servidor.commands.performPrefixedCommand(
                servidor.createCommandSourceStack().withSuppressedOutput(),
                "execute in $dim run tp ${j.uuid} ${v.pos.x + 0.5} ${v.pos.y} ${v.pos.z + 0.5}",
            )
            if (v.muerte) {
                j.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0))
                j.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0))
            }
            v.nivel.playSound(null, v.pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1f)
        }
        // buscador: los contornos solo los ve quien tomó la poción; se borran a los 20 s
        if (busquedas.isEmpty()) return
        val bi = busquedas.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            if (ahora >= b.fin) {
                b.displays.forEach { b.nivel.getEntity(it)?.discard() }
                bi.remove()
                continue
            }
            val escala = ESCALA ?: continue
            if (ahora % 5 != 0) continue
            for (otro in b.nivel.players()) {
                if (otro.uuid == b.dueno) continue
                for (u in b.displays) {
                    val d = b.nivel.getEntity(u) ?: continue
                    if (otro.distanceToSqr(d) > 128.0 * 128.0) continue
                    otro.connection.send(ClientboundSetEntityDataPacket(d.id, listOf(SynchedEntityData.DataValue.create(escala, Vector3f(0f, 0f, 0f)))))
                }
            }
        }
    }
}
