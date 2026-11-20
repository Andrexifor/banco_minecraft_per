package io.github.andrexifor.banco.juego

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.Formato
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.file.Files

/**
 * Tambos: posadas del Qhapaq Ñan. Cada jugador arma los tambos que quiera (maqueta del Kuraka) y, desde
 * cualquiera de ellos, viaja a otro suyo o al tambo de la plaza pagando S/ 1,000 por viaje. Desde el
 * tambo de la plaza se viaja a los tambos propios. Solo el dueño usa sus tambos.
 * El tambo de la plaza lo registra el datapack (banco npc fijar tambo ...): su lugar queda en tambo_central.txt.
 */
object Tambos {
    const val PRECIO_VIAJE = 1_000L

    private data class Punto(val dim: String, val x: Double, val y: Double, val z: Double, val yaw: Float)

    private var central: Punto? = null
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("tambo_central.txt")

    fun cargar() {
        try {
            if (!Files.exists(archivo)) return
            val p = Files.readString(archivo).trim().split(" ")
            if (p.size == 5) central = Punto(p[0], p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toFloat())
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer tambo_central.txt: {}", e.message)
        }
    }

    /**
     * El datapack registra la entidad del tambo de la plaza (cubre todo el tambo, así cualquier parte abre el
     * menú). Se llega delante de él: a 2.4 bloques hacia donde mira la entidad (el centro de la plaza).
     */
    fun fijarCentral(e: Entity) {
        val dim = e.level().dimension().identifier().toString()
        val yaw = Math.toRadians(e.yRot.toDouble())
        val x = e.x - kotlin.math.sin(yaw) * 2.4
        val z = e.z + kotlin.math.cos(yaw) * 2.4
        // si la entidad empieza a la altura del piso, se llega un bloque más arriba (sobre el piso)
        val nivel = e.level()
        val pie = BlockPos.containing(x, e.y, z)
        val y = if (nivel.getBlockState(pie).getCollisionShape(nivel, pie).isEmpty) e.y else pie.y + 1.0
        central = Punto(dim, x, y, z, e.yRot)
        try {
            Files.createDirectories(archivo.parent)
            Files.writeString(archivo, "$dim $x $y $z ${e.yRot}")
        } catch (ex: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar tambo_central.txt: {}", ex.message)
        }
    }

    private fun nombreDim(dim: String) = when (dim) {
        "minecraft:overworld" -> "Superficie"
        "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "End"
        else -> dim
    }

    /** Dónde se aparece al llegar a un tambo: delante de él (del lado desde el que se armó). */
    private fun llegada(m: Maquinas.Maquina, nivel: ServerLevel?): Punto {
        val dir = runCatching { Direction.valueOf(m.facing.uppercase()) }.getOrDefault(Direction.SOUTH)
        var p = m.origen().relative(dir, 2)
        if (nivel != null) {
            // si delante hay algo sólido, se busca hueco hacia arriba (como mucho 6 bloques)
            var intentos = 0
            while (intentos < 6 && (!nivel.getBlockState(p).getCollisionShape(nivel, p).isEmpty ||
                    !nivel.getBlockState(p.above()).getCollisionShape(nivel, p.above()).isEmpty)) {
                p = p.above(); intentos++
            }
        }
        return Punto(m.dim, p.x + 0.5, p.y.toDouble(), p.z + 0.5, dir.toYRot() + 180f)
    }

    fun abrir(j: ServerPlayer, desde: Maquinas.Maquina?) {
        val servidor = j.level().server ?: return
        val gui = SimpleGui(MenuType.GENERIC_9x4, j, false)
        gui.setTitle(texto(if (desde == null) "Tambo de la plaza" else "Tu tambo", ChatFormatting.DARK_GREEN))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(
            4,
            GuiElementBuilder(Items.MAP).setName(texto("Qhapaq Ñan", ChatFormatting.GOLD)).setLore(
                listOf(
                    texto("Elige a qué tambo viajar", ChatFormatting.GRAY),
                    texto("Cada viaje: ${Formato.soles(PRECIO_VIAJE)}", ChatFormatting.YELLOW),
                    texto("Tus tambos: ${Maquinas.tambosDe(j.uuid.toString()).size} (maqueta: el Kuraka)", ChatFormatting.GRAY),
                ),
            ),
        )
        val destinos = ArrayList<Pair<String, () -> Punto?>>()
        val c = central
        if (desde != null && c != null) destinos += "Tambo de la plaza" to { c }
        Maquinas.tambosDe(j.uuid.toString()).forEachIndexed { i, m ->
            if (m === desde) return@forEachIndexed
            val nombre = "Tambo ${i + 1} · ${nombreDim(m.dim)} (${m.x}, ${m.y}, ${m.z})"
            destinos += nombre to {
                val nivel = servidor.allLevels.firstOrNull { it.dimension().identifier().toString() == m.dim }
                llegada(m, nivel)
            }
        }
        if (destinos.isEmpty()) {
            gui.setSlot(
                13,
                GuiElementBuilder(Items.BARRIER).setName(texto("No hay a dónde viajar", ChatFormatting.RED)).setLore(
                    listOf(texto("Arma tus tambos con la maqueta del Kuraka", ChatFormatting.GRAY)),
                ),
            )
        }
        val ranuras = (9 until 36).toList().toIntArray()
        destinos.take(ranuras.size).forEachIndexed { i, (nombre, punto) ->
            val icono = ItemStack(if (nombre.startsWith("Tambo de la plaza")) Items.BELL else Items.LODESTONE)
            val b = GuiElementBuilder(icono).setName(texto(nombre, ChatFormatting.GREEN)).setLore(
                listOf(texto("Viajar: ${Formato.soles(PRECIO_VIAJE)}", ChatFormatting.YELLOW), texto("Clic para viajar", ChatFormatting.YELLOW)),
            )
            b.setCallback(
                Runnable {
                    GuiUtil.confirmar(
                        j, "¿Viajar?", icono.copy(),
                        listOf(nombre, "Costo: ${Formato.soles(PRECIO_VIAJE)}", "Saldo: ${Formato.soles(Operaciones.saldo(j))}"),
                        alConfirmar = { j.closeContainer(); viajar(j, nombre, punto()) },
                        alCancelar = { abrir(j, desde) },
                    )
                },
            )
            gui.setSlot(ranuras[i], b)
        }
        gui.open()
    }

    private fun viajar(j: ServerPlayer, nombre: String, p: Punto?) {
        val servidor = j.level().server ?: return
        if (p == null || servidor.allLevels.none { it.dimension().identifier().toString() == p.dim }) {
            GuiUtil.avisar(j, Respuesta(false, "Ese tambo ya no existe")); return
        }
        val cobro = Operaciones.cobrar(j, PRECIO_VIAJE, "VIAJE TAMBO")
        if (!cobro.ok) { GuiUtil.avisar(j, cobro); return }
        val origen = j.level() as ServerLevel
        origen.sendParticles(ParticleTypes.CLOUD, j.x, j.y + 1, j.z, 30, 0.4, 0.8, 0.4, 0.02)
        origen.playSound(null, j.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f)
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withSuppressedOutput(),
            "execute in ${p.dim} run tp ${j.uuid} ${p.x} ${p.y} ${p.z} ${p.yaw} 0",
        )
        val destino = j.level() as ServerLevel
        destino.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 30, 0.4, 0.8, 0.4, 0.02)
        destino.playSound(null, BlockPos.containing(p.x, p.y, p.z), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f)
        GuiUtil.avisar(j, Respuesta(true, "Llegaste a: $nombre. ${cobro.mensaje}"))
    }
}
