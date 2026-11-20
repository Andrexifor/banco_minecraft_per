package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Mascotas de compañía que caminan a tu lado, igual que el cóndor vuela: modelos propios del resource
 * pack (item_display) que el mod mueve cada tick. Se llaman o despiden con su ítem (clic derecho), se
 * quedan contigo aunque te desconectes, mueras o se reinicie el servidor (mascotas.txt) y no reciben daño.
 *  - Perro peruano sin pelo: pelea. Corre hacia los monstruos que se acercan a su dueño y los muerde.
 *  - Cuy y vizcacha: solo acompañan (el cuy trota pegado a ti; la vizcacha va dando saltitos).
 * Cada jugador puede tener las tres a la vez (una de cada una).
 */
object Mascotas {
    private const val ETIQUETA = "banco_mascota"

    enum class Tipo(
        val id: String, val nombre: String, val escala: Float, val lado: Double, val atras: Double,
        val salto: Double, val pelea: Boolean, val sonido: SoundEvent,
    ) {
        PERRO("perro", "perro peruano sin pelo", 1.0f, 1.6, 0.6, 0.06, true, SoundEvents.FOX_AMBIENT),
        CUY("cuy", "cuy", 0.8f, -1.4, 0.4, 0.08, false, SoundEvents.RABBIT_AMBIENT),
        VIZCACHA("vizcacha", "vizcacha", 0.9f, 0.0, 1.8, 0.35, false, SoundEvents.RABBIT_JUMP),
        ;

        companion object { fun porId(id: String) = entries.firstOrNull { it.id == id } }
    }

    private const val RADIO_GUARDIA = 8.0
    private const val DANO_PERRO = 5f

    private class Estado(val tipo: Tipo, val display: UUID, var nivel: ServerLevel) {
        var fase = 0.0
        var objetivo: LivingEntity? = null
        var espera = 0
    }

    private data class Clave(val dueno: UUID, val tipo: Tipo)

    private val activas = HashMap<Clave, Estado>()
    private val llamadas = HashSet<Clave>()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("mascotas.txt")

    private fun cargar() {
        try {
            if (!Files.exists(archivo)) return
            for (linea in Files.readAllLines(archivo)) {
                val p = linea.trim().split(" ")
                if (p.size != 2) continue
                val u = runCatching { UUID.fromString(p[0]) }.getOrNull() ?: continue
                val t = Tipo.porId(p[1]) ?: continue
                llamadas += Clave(u, t)
            }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer mascotas.txt: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.write(archivo, llamadas.map { "${it.dueno} ${it.tipo.id}" })
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar mascotas.txt: {}", e.message)
        }
    }

    private fun tipoDe(s: ItemStack): Tipo? {
        if (s.isEmpty) return null
        val id = s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(BancoMinecraftPer.MOD_ID)
            ?.getStringOr("mascota", "") ?: return null
        return Tipo.porId(id)
    }

    fun registrarEventos() {
        cargar()
        UseItemCallback.EVENT.register { jugador, _, mano ->
            if (jugador !is ServerPlayer) return@register InteractionResult.PASS
            val tipo = tipoDe(jugador.getItemInHand(mano)) ?: return@register InteractionResult.PASS
            val clave = Clave(jugador.uuid, tipo)
            if (clave in llamadas) {
                llamadas -= clave
                guardar()
                despedir(clave)
                jugador.sendSystemMessage(Component.literal("Tu ${tipo.nombre} se va a descansar.").withStyle(ChatFormatting.GRAY))
            } else {
                llamadas += clave
                guardar()
                llamar(jugador, tipo)
                jugador.sendSystemMessage(
                    Component.literal("¡Tu ${tipo.nombre} llegó! Te acompaña hasta que lo despidas.").withStyle(ChatFormatting.GOLD),
                )
            }
            InteractionResult.SUCCESS
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            Tipo.entries.forEach { despedir(Clave(handler.player.uuid, it)) }
        }
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 20 == 0) reponer(servidor)
            mover(servidor)
            if (servidor.tickCount % 100 == 0) limpiar(servidor)
        }
    }

    private fun reponer(servidor: MinecraftServer) {
        for (c in llamadas) {
            if (activas.containsKey(c)) continue
            val j = servidor.playerList.getPlayer(c.dueno) ?: continue
            if (!j.isDeadOrDying) llamar(j, c.tipo)
        }
    }

    private fun llamar(j: ServerPlayer, tipo: Tipo) {
        val nivel = j.level() as ServerLevel
        val etiqueta = "mascota_${j.uuid}_${tipo.id}"
        val p = j.position()
        val e = tipo.escala
        val nbt = "{Tags:[\"$ETIQUETA\",\"$etiqueta\"],teleport_duration:3,view_range:2f,item_display:\"fixed\"," +
            "item:{id:\"minecraft:bone\",count:1,components:{\"minecraft:item_model\":\"banco_minecraft_per:mascota_${tipo.id}\"}}," +
            "transformation:{left_rotation:[0f,1f,0f,0f],right_rotation:[0f,0f,0f,1f],translation:[0f,${0.5f * e}f,0f]," +
            "scale:[${e}f,${e}f,${e}f]}}"
        val servidor = nivel.server
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withLevel(nivel).withPosition(p).withSuppressedOutput(),
            "summon minecraft:item_display ${p.x} ${p.y} ${p.z} $nbt",
        )
        val d = nivel.getEntitiesOfClass(Display::class.java, AABB(p, p).inflate(2.0)) { etiqueta in it.entityTags() }.firstOrNull()
            ?: return
        activas[Clave(j.uuid, tipo)] = Estado(tipo, d.uuid, nivel)
        nivel.playSound(null, j.blockPosition(), tipo.sonido, SoundSource.NEUTRAL, 0.7f, 1.1f)
    }

    private fun despedir(c: Clave) {
        val e = activas.remove(c) ?: return
        e.nivel.getEntity(e.display)?.discard()
    }

    /** Altura del suelo más cercano a [yRef] en (x, z); si no lo encuentra, [yRef]. */
    private fun suelo(nivel: ServerLevel, x: Double, z: Double, yRef: Double): Double {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val y0 = floor(yRef).toInt()
        for (dy in 2 downTo -4) {
            val p = BlockPos(bx, y0 + dy, bz)
            val abajo = nivel.getBlockState(p.below())
            val aqui = nivel.getBlockState(p)
            if (!abajo.getCollisionShape(nivel, p.below()).isEmpty && aqui.getCollisionShape(nivel, p).isEmpty) {
                val tope = abajo.getCollisionShape(nivel, p.below()).max(net.minecraft.core.Direction.Axis.Y)
                return p.y - 1 + tope
            }
        }
        return yRef
    }

    private fun mover(servidor: MinecraftServer) {
        if (activas.isEmpty()) return
        val tick = servidor.tickCount
        for ((c, e) in activas.entries.toList()) {
            val j = servidor.playerList.getPlayer(c.dueno)
            if (j == null || j.isDeadOrDying) { despedir(c); continue }
            val nivel = j.level() as ServerLevel
            if (nivel != e.nivel) { despedir(c); llamar(j, c.tipo); continue }
            val d = nivel.getEntity(e.display) as? Display ?: run { activas.remove(c); null } ?: continue

            if (c.tipo.pelea) {
                if (tick % 10 == 0 && (e.objetivo?.isAlive != true || e.objetivo!!.distanceTo(j) > RADIO_GUARDIA + 4)) {
                    e.objetivo = nivel.getEntitiesOfClass(LivingEntity::class.java, j.boundingBox.inflate(RADIO_GUARDIA)) {
                        it is Enemy && it.isAlive
                    }.minByOrNull { it.distanceToSqr(j) }
                }
                if (e.espera > 0) e.espera--
            }

            val actual = d.position()
            val presa = e.objetivo?.takeIf { c.tipo.pelea && it.isAlive }
            val destino: Vec3
            val velocidad: Double
            if (presa != null) {
                // corre hacia la presa y se queda pegado a ella
                val haciaMi = actual.subtract(presa.position()).let { if (it.horizontalDistanceSqr() < 1e-4) Vec3(1.0, 0.0, 0.0) else it.normalize() }
                destino = presa.position().add(haciaMi.scale(presa.bbWidth / 2.0 + 0.5))
                velocidad = 0.55
            } else {
                // su lugar junto al dueño: a un costado y un poco atrás, según hacia dónde mira el dueño
                val yaw = Math.toRadians(j.yRot.toDouble())
                val adelante = Vec3(-sin(yaw), 0.0, cos(yaw))
                val derecha = Vec3(-cos(yaw), 0.0, -sin(yaw))
                destino = j.position().add(derecha.scale(c.tipo.lado)).subtract(adelante.scale(c.tipo.atras))
                velocidad = if (j.isSprinting) 0.42 else 0.3
            }
            val plano = Vec3(destino.x - actual.x, 0.0, destino.z - actual.z)
            val dist = plano.length()
            val lejos = actual.distanceTo(destino) > 16.0
            val paso = when {
                lejos -> plano
                dist < 0.25 -> Vec3.ZERO
                else -> plano.normalize().scale(min(dist, velocidad))
            }
            val nx = actual.x + paso.x
            val nz = actual.z + paso.z
            val yRef = if (lejos) j.y else actual.y
            var ny = suelo(nivel, nx, nz, yRef)
            if (abs(ny - j.y) > 4.0) ny = j.y
            val caminando = paso.horizontalDistanceSqr() > 1.0e-3
            if (caminando) e.fase += 0.55 else e.fase = 0.0
            val brinco = if (caminando) abs(sin(e.fase)) * c.tipo.salto else 0.0
            val yaw = if (caminando) {
                Math.toDegrees(atan2(-paso.x, paso.z)).toFloat()
            } else if (presa == null) {
                // quieto: mira hacia donde mira su dueño
                j.yRot
            } else d.yRot
            d.snapTo(nx, ny + brinco, nz, yaw, 0f)

            if (c.tipo == Tipo.VIZCACHA && caminando && (e.fase % Math.PI) < 0.55) {
                nivel.playSound(null, d.blockPosition(), SoundEvents.RABBIT_JUMP, SoundSource.NEUTRAL, 0.25f, 1.2f)
            }

            if (presa != null && e.espera == 0 && d.position().distanceTo(presa.position()) < presa.bbWidth / 2.0 + 1.3) {
                val golpe = j.damageSources().playerAttack(j)
                presa.hurtServer(nivel, golpe, DANO_PERRO)
                presa.knockback(0.4, d.x - presa.x, d.z - presa.z, golpe, DANO_PERRO)
                nivel.playSound(null, presa.blockPosition(), SoundEvents.FOX_BITE, SoundSource.NEUTRAL, 0.9f, 0.8f)
                e.espera = 15
            }
        }
    }

    private fun limpiar(servidor: MinecraftServer) {
        val vivos = activas.values.map { it.display }.toSet()
        for (nivel in servidor.allLevels) {
            nivel.allEntities.filterIsInstance<Display>()
                .filter { ETIQUETA in it.entityTags() && it.uuid !in vivos }
                .forEach { it.discard() }
        }
    }
}
