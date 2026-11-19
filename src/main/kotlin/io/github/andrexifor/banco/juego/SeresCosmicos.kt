package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Display
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seres cósmicos de los puestos de la plaza (los pone el datapack plaza_inca).
 *
 * Cada jugador los ve según SU distancia (el mod le manda a cada uno sus propios datos):
 *  - a más de 12 bloques: solo la verdadera forma (`ser_lejos`), el orbe gigante sobre el techo;
 *  - de 5 a 12 bloques: el ser trabaja mirando a su puesto (`ser_cerca`): el herrero martilla en el
 *    yunque, el oráculo afila su tumi, el guardián remueve el oro del crisol y el sembrador descansa
 *    sentado; con chispas y sonidos que solo oye ese jugador;
 *  - a menos de 5 bloques: deja de trabajar y se gira hacia el jugador, mirándolo.
 * Las piezas: `ser_cuerpo` (el cuerpo) y `ser_brazo` + `anim_<tipo>` (el brazo con el arma, que gira
 * desde el hombro). Para ocultar algo a un jugador se le manda escala 0.
 */
object SeresCosmicos {
    private const val CERCA = "ser_cerca"
    private const val LEJOS = "ser_lejos"
    private const val CUERPO = "ser_cuerpo"
    private const val BRAZO = "ser_brazo"
    private const val LIMITE_LEJOS = 12.0
    private const val LIMITE_MIRA = 5.0
    private const val ALCANCE = 128.0

    private enum class Estado { LEJOS, TRABAJA, MIRA }

    private enum class Trabajo(val etiqueta: String, val ciclo: Int) {
        MARTILLAR("anim_martillar", 20),
        AFILAR("anim_afilar", 16),
        REMOVER("anim_remover", 24),
        SENTADO("anim_sentado", 40),
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> dato(nombre: String): EntityDataAccessor<T>? = try {
        Display::class.java.getDeclaredField(nombre).apply { isAccessible = true }.get(null) as EntityDataAccessor<T>
    } catch (e: Exception) {
        BancoMinecraftPer.LOGGER.warn("Seres cósmicos: falta el dato {} de los displays ({})", nombre, e.message)
        null
    }

    private val ESCALA = dato<Vector3f>("DATA_SCALE_ID")
    private val GIRO = dato<Quaternionf>("DATA_LEFT_ROTATION_ID")
    private val BILLBOARD = dato<Byte>("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID")
    private val DURACION = dato<Int>("DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID")
    private val INICIO = dato<Int>("DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID")

    private const val FIJO: Byte = 0
    private const val VERTICAL: Byte = 1
    private val CERO = Vector3f(0f, 0f, 0f)

    /** Giro de 180° que pone la cara de los modelos hacia adelante. */
    private val VOLTEO = Quaternionf(0f, 1f, 0f, 0f)

    /** Último estado enviado a cada jugador por display (para mandar solo los cambios). */
    private val enviado = HashMap<String, Estado>()
    private var displays: Map<ServerLevel, List<Display>> = emptyMap()

    fun registrarEventos() {
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (ESCALA == null) return@register
            val tick = servidor.tickCount
            if (tick % 40 == 0) buscar(servidor)
            if (tick % 2 == 0) actualizar(servidor, tick)
        }
    }

    private fun buscar(servidor: MinecraftServer) {
        displays = servidor.allLevels.associateWith { nivel ->
            nivel.allEntities.filterIsInstance<Display>().filter { val t = it.entityTags(); CERCA in t || LEJOS in t }
        }.filterValues { it.isNotEmpty() }
        if (enviado.size > 5000) enviado.clear()
    }

    private fun actualizar(servidor: MinecraftServer, tick: Int) {
        for ((nivel, lista) in displays) {
            val jugadores = nivel.players()
            if (jugadores.isEmpty()) continue
            for (j in jugadores) {
                for (d in lista) {
                    if (d.isRemoved) continue
                    val dx = j.x - d.x
                    val dz = j.z - d.z
                    val dist = kotlin.math.sqrt(dx * dx + dz * dz)
                    if (dist > ALCANCE) continue
                    val estado = when {
                        dist > LIMITE_LEJOS -> Estado.LEJOS
                        dist < LIMITE_MIRA -> Estado.MIRA
                        else -> Estado.TRABAJA
                    }
                    val clave = "${j.uuid}:${d.id}"
                    val cambio = enviado.put(clave, estado) != estado
                    val etiquetas = d.entityTags()
                    val datos = ArrayList<SynchedEntityData.DataValue<*>>()

                    // visibilidad (escala): se reenvía cada segundo por si el jugador recién empezó a ver el display
                    if (cambio || tick % 20 == 0) {
                        val visible = if (CERCA in etiquetas) estado != Estado.LEJOS else estado == Estado.LEJOS
                        datos += SynchedEntityData.DataValue.create(ESCALA!!, if (visible) Vector3f(d.entityData.get(ESCALA)) else CERO)
                        if (BILLBOARD != null && (CUERPO in etiquetas || BRAZO in etiquetas)) {
                            datos += SynchedEntityData.DataValue.create(BILLBOARD, if (estado == Estado.MIRA) VERTICAL else FIJO)
                        }
                    }
                    // brazo: animación de trabajo o en reposo mirando al jugador
                    if (BRAZO in etiquetas && GIRO != null && DURACION != null && INICIO != null) {
                        val trabajo = Trabajo.entries.firstOrNull { it.etiqueta in etiquetas }
                        if (estado == Estado.TRABAJA && trabajo != null) {
                            pose(trabajo, tick)?.let { (giro, dur) ->
                                datos += SynchedEntityData.DataValue.create(GIRO, Quaternionf(VOLTEO).mul(giro))
                                datos += SynchedEntityData.DataValue.create(DURACION, dur)
                                datos += SynchedEntityData.DataValue.create(INICIO, 0)
                            }
                        } else if (cambio) {
                            datos += SynchedEntityData.DataValue.create(GIRO, Quaternionf(VOLTEO))
                            datos += SynchedEntityData.DataValue.create(DURACION, 6)
                            datos += SynchedEntityData.DataValue.create(INICIO, 0)
                        }
                    }
                    if (datos.isNotEmpty()) j.connection.send(ClientboundSetEntityDataPacket(d.id, datos))
                    if (estado == Estado.TRABAJA && CUERPO in etiquetas) efectos(nivel, j, d, tick)
                }
            }
        }
    }

    private fun rot(x: Float = 0f, y: Float = 0f, z: Float = 0f): Quaternionf =
        Quaternionf().rotateY(Math.toRadians(y.toDouble()).toFloat())
            .rotateX(Math.toRadians(x.toDouble()).toFloat())
            .rotateZ(Math.toRadians(z.toDouble()).toFloat())

    /** Pose del brazo en este tick (solo en los cuadros clave) y cuántos ticks tarda en llegar. */
    private fun pose(t: Trabajo, tick: Int): Pair<Quaternionf, Int>? {
        val fase = tick % t.ciclo
        return when (t) {
            Trabajo.MARTILLAR -> when (fase) {
                0 -> rot(x = -80f) to 12          // levanta el champi
                12 -> rot(x = 20f) to 3           // golpe en el yunque
                else -> null
            }
            Trabajo.AFILAR -> when (fase) {
                0 -> rot(x = 25f, y = 28f) to 8   // pasa el tumi por la piedra hacia un lado
                8 -> rot(x = 25f, y = -28f) to 8  // y de vuelta
                else -> null
            }
            Trabajo.REMOVER -> when (fase) {
                0 -> rot(x = 35f, z = 18f) to 6   // remueve el crisol en círculo
                6 -> rot(x = 50f) to 6
                12 -> rot(x = 35f, z = -18f) to 6
                18 -> rot(x = 20f) to 6
                else -> null
            }
            Trabajo.SENTADO -> null
        }
    }

    /** Chispas y sonidos del trabajo, solo para ese jugador, frente al ser (donde está su puesto de trabajo). */
    private fun efectos(nivel: ServerLevel, j: ServerPlayer, cuerpo: Display, tick: Int) {
        val etiquetas = cuerpo.entityTags()
        val trabajo = when {
            "ser_banquero" in etiquetas -> Trabajo.REMOVER
            "ser_herrero" in etiquetas -> Trabajo.MARTILLAR
            "ser_mercader" in etiquetas -> Trabajo.AFILAR
            else -> Trabajo.SENTADO
        }
        val fase = tick % trabajo.ciclo
        val yaw = Math.toRadians(cuerpo.yRot.toDouble())
        val x = cuerpo.x - sin(yaw) * 1.35
        val z = cuerpo.z + cos(yaw) * 1.35
        val y = cuerpo.y - 0.25
        when (trabajo) {
            Trabajo.MARTILLAR -> if (fase == 14) {
                particulas(nivel, j, ParticleTypes.LAVA, x, y, z, 4, 0.15)
                particulas(nivel, j, ParticleTypes.SMALL_FLAME, x, y, z, 6, 0.25)
                sonido(j, SoundEvents.ANVIL_USE, x, y, z, 0.25f, 1.3f)
            }
            Trabajo.AFILAR -> if (fase == 0 || fase == 8) {
                particulas(nivel, j, ParticleTypes.ELECTRIC_SPARK, x, y, z, 5, 0.2)
                if (fase == 0) sonido(j, SoundEvents.GRINDSTONE_USE, x, y, z, 0.25f, 1.2f)
            }
            Trabajo.REMOVER -> if (fase % 6 == 0) {
                particulas(nivel, j, ParticleTypes.WAX_ON, x, y + 0.3, z, 4, 0.3)
                particulas(nivel, j, ParticleTypes.FLAME, x, y + 0.1, z, 1, 0.15)
                if (fase == 0) sonido(j, SoundEvents.LAVA_POP, x, y, z, 0.4f, 0.9f)
            }
            Trabajo.SENTADO -> if (fase == 0) {
                particulas(nivel, j, ParticleTypes.HAPPY_VILLAGER, cuerpo.x, cuerpo.y + 0.5, cuerpo.z, 3, 0.7)
            }
        }
    }

    private fun particulas(n: ServerLevel, j: ServerPlayer, tipo: ParticleOptions, x: Double, y: Double, z: Double, cantidad: Int, ancho: Double) {
        n.sendParticles(j, tipo, false, false, x, y, z, cantidad, ancho, ancho, ancho, 0.02)
    }

    private fun sonido(j: ServerPlayer, ev: SoundEvent, x: Double, y: Double, z: Double, vol: Float, tono: Float) {
        j.connection.send(
            ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev), SoundSource.NEUTRAL, x, y, z, vol, tono, j.random.nextLong()),
        )
    }
}
