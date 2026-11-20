package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Cóndor guardián: la Pluma del cóndor (la vende el Criador) lo llama o lo despide con clic derecho.
 * No es una criatura nueva (eso exigiría un mod en el cliente): es un modelo propio del resource pack
 * (item_display "condor") que el mod mueve cada tick. Vuela en círculos a 3–5 bloques del dueño y,
 * si un monstruo se acerca, baja en picada y lo ataca (el golpe cuenta como del jugador). No se puede
 * montar ni recibe daño. Una vez llamado se queda contigo: si te desconectas, mueres o se reinicia el
 * servidor, vuelve solo cuando regresas (la lista de dueños se guarda en banco_minecraft_per/condores.txt).
 * Solo se va si lo despides con la pluma.
 */
object Condor {
    private const val ETIQUETA = "banco_condor"
    private const val RADIO_GUARDIA = 10.0
    private const val DANO = 7f

    private class Estado(val display: UUID, var nivel: ServerLevel) {
        var angulo = 0.0
        var objetivo: LivingEntity? = null
        var espera = 0
        var subir = 0
    }

    /** Cóndores en el mundo ahora mismo (dueño -> display). */
    private val activos = HashMap<UUID, Estado>()

    /** Dueños que tienen su cóndor llamado (persiste entre conexiones y reinicios). */
    private val llamados = HashSet<UUID>()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("condores.txt")

    private fun cargarLlamados() {
        try {
            if (Files.exists(archivo)) {
                Files.readAllLines(archivo).mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.forEach { llamados += it }
            }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer condores.txt: {}", e.message)
        }
    }

    private fun guardarLlamados() {
        try {
            Files.createDirectories(archivo.parent)
            Files.write(archivo, llamados.map { it.toString() })
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar condores.txt: {}", e.message)
        }
    }

    private fun esPluma(s: ItemStack) = !s.isEmpty &&
        s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(BancoMinecraftPer.MOD_ID)?.contains("condor") == true

    fun registrarEventos() {
        cargarLlamados()
        UseItemCallback.EVENT.register { jugador, _, mano ->
            if (jugador !is ServerPlayer || !esPluma(jugador.getItemInHand(mano))) return@register InteractionResult.PASS
            if (jugador.uuid in llamados) {
                llamados -= jugador.uuid
                guardarLlamados()
                despedir(jugador.uuid)
                jugador.sendSystemMessage(Component.literal("Tu cóndor vuelve a las montañas.").withStyle(ChatFormatting.GRAY))
            } else {
                llamados += jugador.uuid
                guardarLlamados()
                llamar(jugador)
                jugador.sendSystemMessage(Component.literal("¡Tu cóndor guardián llegó! Se queda contigo hasta que lo despidas.").withStyle(ChatFormatting.GOLD))
            }
            InteractionResult.SUCCESS
        }
        // al desconectarse el cóndor se retira del mundo, pero sigue llamado: vuelve al reconectar
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> despedir(handler.player.uuid) }
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 20 == 0) reponer(servidor)
            mover(servidor)
            if (servidor.tickCount % 100 == 0) limpiar(servidor)
        }
    }

    /** Los dueños conectados y vivos que tienen su cóndor llamado lo tienen en el mundo. */
    private fun reponer(servidor: MinecraftServer) {
        for (uuid in llamados) {
            if (activos.containsKey(uuid)) continue
            val j = servidor.playerList.getPlayer(uuid) ?: continue
            if (!j.isDeadOrDying) llamar(j)
        }
    }

    private fun llamar(j: ServerPlayer) {
        val nivel = j.level() as ServerLevel
        val etiqueta = "condor_${j.uuid}"
        val p = j.position().add(0.0, 3.0, 0.0)
        val nbt = "{Tags:[\"$ETIQUETA\",\"$etiqueta\"],teleport_duration:3,view_range:3f,item_display:\"fixed\"," +
            "item:{id:\"minecraft:feather\",count:1,components:{\"minecraft:item_model\":\"banco_minecraft_per:condor\"}}," +
            "transformation:{left_rotation:[0f,1f,0f,0f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.8f,1.8f,1.8f]}}"
        val servidor = nivel.server
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withLevel(nivel).withPosition(p).withSuppressedOutput(),
            "summon minecraft:item_display ${p.x} ${p.y} ${p.z} $nbt",
        )
        val d = nivel.getEntitiesOfClass(Display::class.java, AABB(p, p).inflate(2.0)) { etiqueta in it.entityTags() }.firstOrNull()
            ?: return
        activos[j.uuid] = Estado(d.uuid, nivel)
        nivel.playSound(null, j.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.6f, 0.7f)
    }

    private fun despedir(dueno: UUID) {
        val e = activos.remove(dueno) ?: return
        e.nivel.getEntity(e.display)?.discard()
    }

    private fun mover(servidor: MinecraftServer) {
        if (activos.isEmpty()) return
        val tick = servidor.tickCount
        for ((dueno, e) in activos.entries.toList()) {
            val j = servidor.playerList.getPlayer(dueno)
            // sin dueño conectado o muerto: se retira y vuelve solo cuando el dueño regresa (reponer)
            if (j == null || j.isDeadOrDying) { despedir(dueno); continue }
            val nivel = j.level() as ServerLevel
            if (nivel != e.nivel) { // cambió de dimensión: el cóndor lo sigue
                despedir(dueno); llamar(j); continue
            }
            val d = nivel.getEntity(e.display) as? Display ?: run { activos.remove(dueno); null } ?: continue

            // elegir presa cada medio segundo: el monstruo vivo más cercano al dueño
            if (tick % 10 == 0 && (e.objetivo?.isAlive != true || e.objetivo!!.distanceTo(j) > RADIO_GUARDIA + 4)) {
                e.objetivo = nivel.getEntitiesOfClass(LivingEntity::class.java, j.boundingBox.inflate(RADIO_GUARDIA)) {
                    it is Enemy && it.isAlive
                }.minByOrNull { it.distanceToSqr(j) }
            }
            if (e.espera > 0) e.espera--
            if (e.subir > 0) e.subir--

            val actual = d.position()
            val presa = e.objetivo?.takeIf { it.isAlive && e.subir == 0 }
            val destino: Vec3
            val velocidad: Double
            if (presa != null) {
                destino = presa.position().add(0.0, presa.bbHeight * 0.6, 0.0)
                velocidad = 0.7
            } else {
                // vuela en círculos a 4 bloques del dueño, un poco por encima, subiendo y bajando suave
                e.angulo += 0.05
                destino = j.position().add(cos(e.angulo) * 4.0, 3.0 + sin(e.angulo * 2.0) * 0.6, sin(e.angulo) * 4.0)
                velocidad = 0.45
            }
            val delta = destino.subtract(actual)
            val dist = delta.length()
            val paso = if (dist > 24) delta else delta.normalize().scale(min(dist, velocidad))
            val nueva = actual.add(paso)
            val yaw = if (paso.horizontalDistanceSqr() > 1.0e-4) Math.toDegrees(atan2(-paso.x, paso.z)).toFloat() else d.yRot
            d.snapTo(nueva.x, nueva.y, nueva.z, yaw, 0f)

            if (presa != null && e.espera == 0 && nueva.distanceTo(destino) < 1.6) {
                val golpe = j.damageSources().playerAttack(j)
                presa.hurtServer(nivel, golpe, DANO)
                presa.knockback(0.5, nueva.x - presa.x, nueva.z - presa.z, golpe, DANO)
                nivel.playSound(null, presa.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.NEUTRAL, 0.8f, 0.8f)
                e.espera = 20
                e.subir = 12 // después del picotazo vuelve a subir antes de la siguiente picada
            }
        }
    }

    /** Borra cóndores sin dueño activo (por ejemplo, tras reiniciar el servidor). */
    private fun limpiar(servidor: MinecraftServer) {
        val vivos = activos.values.map { it.display }.toSet()
        for (nivel in servidor.allLevels) {
            nivel.allEntities.filterIsInstance<Display>()
                .filter { ETIQUETA in it.entityTags() && it.uuid !in vivos }
                .forEach { it.discard() }
        }
    }
}
