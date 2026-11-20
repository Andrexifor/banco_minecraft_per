package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.util.UUID
import kotlin.math.atan2
import kotlin.random.Random

/**
 * El Llamero viajero pasea por la plaza: sale de su lugar junto a la entrada este, recorre los cuatro
 * caminos entre las tiendas, se detiene un rato en cada uno y vuelve. Cada tanto dice algo en un globo
 * sobre su cabeza. Si alguien se le acerca, se detiene y lo mira (el clic derecho sigue contando el rumor
 * del Apu). Su lugar de partida se guarda la primera vez que se le ve (llamero_casa.txt); el recorrido está
 * en bloques relativos a ese lugar, que en la plaza es (15, 5) desde el centro.
 */
object Llamero {
    private const val VELOCIDAD = 0.11
    private const val CERCA = 3.5
    private val CASA_EN_PLAZA = 15.0 to 5.0

    /** Paradas del recorrido, desde el centro de la plaza (x, z). */
    private val RUTA = listOf(15.0 to 5.0, 11.0 to 0.5, 0.5 to -11.0, -9.0 to 0.5, 0.5 to 11.0, 11.0 to 0.5)

    private val FRASES = listOf(
        "Dicen que en lo alto de la montaña duerme un Apu…",
        "¿Un rumor? Háblame, que es gratis.",
        "El Kuraka tiene encargos nuevos, ¡apúrate!",
        "Ofrenden al Inti, que cuando despierta todo es más fácil.",
        "Mi llama se quedó cuidando el ichu.",
        "Del Cusco al Titicaca, todo lo he caminado.",
        "Con un tambo propio no se camina tanto…",
        "¿Ya probaste las pociones del Kallawaya?",
        "Quien vence al Apu se lleva tumbaga.",
    )

    private var casa: Pair<UUID, Vec3>? = null
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("llamero_casa.txt")

    private var parada = 0
    private var espera = 100
    private var globo: UUID? = null
    private var globoHasta = 0
    private var proximoGlobo = 200

    private fun cargar() {
        try {
            if (!Files.exists(archivo)) return
            val p = Files.readString(archivo).trim().split(" ")
            if (p.size == 4) casa = UUID.fromString(p[0]) to Vec3(p[1].toDouble(), p[2].toDouble(), p[3].toDouble())
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer llamero_casa.txt: {}", e.message)
        }
    }

    private fun guardar() {
        val (u, v) = casa ?: return
        try {
            Files.createDirectories(archivo.parent)
            Files.writeString(archivo, "$u ${v.x} ${v.y} ${v.z}")
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar llamero_casa.txt: {}", e.message)
        }
    }

    fun registrarEventos() {
        cargar()
        ServerTickEvents.END_SERVER_TICK.register { servidor -> tick(servidor) }
    }

    private fun buscar(servidor: MinecraftServer): Pair<ServerLevel, LivingEntity>? {
        for (id in Npcs.entidadesDe(TipoNpc.LLAMERO)) {
            for (nivel in servidor.allLevels) {
                val e = nivel.getEntity(id) as? LivingEntity ?: continue
                return nivel to e
            }
        }
        return null
    }

    private fun tick(servidor: MinecraftServer) {
        val (nivel, e) = buscar(servidor) ?: return
        // su casa: donde estaba la primera vez que se le vio (si la plaza se rehace, es otra entidad)
        val c = casa
        if (c == null || c.first != e.uuid) {
            casa = e.uuid to e.position()
            parada = 0; espera = 100
            guardar()
        }
        val base = casa!!.second
        val centro = Vec3(base.x - CASA_EN_PLAZA.first, base.y, base.z - CASA_EN_PLAZA.second)
        val tick = servidor.tickCount

        val cercano = nivel.players().filter { !it.isSpectator && it.distanceTo(e) < CERCA }.minByOrNull { it.distanceTo(e) }
        if (cercano != null) {
            mirar(e, Math.toDegrees(atan2(-(cercano.x - e.x), cercano.z - e.z)).toFloat())
            if (globo == null) decir(servidor, nivel, e, "¿Un rumor del Apu? Clic derecho.", 80)
        } else if (espera > 0) {
            espera--
        } else {
            val (px, pz) = RUTA[parada]
            val destino = Vec3(centro.x + px, base.y, centro.z + pz)
            val delta = Vec3(destino.x - e.x, 0.0, destino.z - e.z)
            val dist = delta.length()
            if (dist < 0.15) {
                parada = (parada + 1) % RUTA.size
                espera = 60 + Random.nextInt(80)
            } else {
                val paso = delta.normalize().scale(minOf(VELOCIDAD, dist))
                val yaw = Math.toDegrees(atan2(-paso.x, paso.z)).toFloat()
                e.snapTo(e.x + paso.x, base.y, e.z + paso.z, yaw, 0f)
                mirar(e, yaw)
            }
        }

        // globo de diálogo: aparece cada tanto unos segundos y sigue su cabeza
        val g = globo?.let { nivel.getEntity(it) as? Display }
        if (g != null) {
            if (tick >= globoHasta) { g.discard(); globo = null; proximoGlobo = tick + 160 + Random.nextInt(200) }
            else g.snapTo(e.x, e.y + e.bbHeight + 0.65, e.z)
        } else {
            globo = null
            if (tick >= proximoGlobo && nivel.players().any { it.distanceTo(e) < 24 }) {
                decir(servidor, nivel, e, FRASES.random(), 120)
            }
        }
        if (tick % 200 == 0) huerfanos(nivel, e)
    }

    private fun mirar(e: LivingEntity, yaw: Float) {
        e.yRot = yaw
        e.setYBodyRot(yaw)
        e.setYHeadRot(yaw)
    }

    private fun decir(servidor: MinecraftServer, nivel: ServerLevel, e: Entity, frase: String, duracion: Int) {
        globo?.let { nivel.getEntity(it)?.discard() }
        val y = e.y + e.bbHeight + 0.65
        val etiqueta = "llamero_globo_${servidor.tickCount}"
        val texto = frase.replace("\\", "").replace("\"", "'")
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withLevel(nivel).withPosition(e.position()).withSuppressedOutput(),
            "summon minecraft:text_display ${e.x} $y ${e.z} {Tags:[\"llamero_globo\",\"$etiqueta\"],billboard:\"center\"," +
                "teleport_duration:2,line_width:160,background:-1056964609,shadow:0b,see_through:0b," +
                "brightness:{block:15,sky:15},text:{text:\"$texto\",color:\"black\"}}",
        )
        globo = nivel.getEntitiesOfClass(Display::class.java, AABB.ofSize(Vec3(e.x, y, e.z), 2.0, 2.0, 2.0)) { etiqueta in it.entityTags() }
            .firstOrNull()?.uuid
        globoHasta = servidor.tickCount + duracion
    }

    /** Globos que quedaron sueltos (por ejemplo, tras reiniciar el servidor). */
    private fun huerfanos(nivel: ServerLevel, e: Entity) {
        nivel.getEntitiesOfClass(Display::class.java, e.boundingBox.inflate(48.0)) { "llamero_globo" in it.entityTags() && it.uuid != globo }
            .forEach { it.discard() }
    }
}
