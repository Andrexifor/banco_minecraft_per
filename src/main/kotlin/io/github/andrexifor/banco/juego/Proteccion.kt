package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Zona
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BucketItem
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.util.UUID

/**
 * Zonas protegidas (config/banco_minecraft_per/zonas.json): dentro, nadie en
 * supervivencia puede romper, poner o usar bloques, usar cubetas ni tocar soportes
 * de armadura. Solo se puede editar en modo creativo y siendo op (nivel 2+) o con
 * banco_minecraft_per.zonas.editar. Los aldeanos del
 * banco y el portal se siguen usando normal. Las explosiones no rompen bloques ahí
 * (ExplosionMixin).
 */
object Proteccion {
    const val PERM_EDITAR = "${BancoMinecraftPer.MOD_ID}.zonas.editar"
    private const val MARGEN_CUBETAS = 6
    private const val MARGEN_EXPLOSIVOS = 4.0
    private var ticks = 0
    private val ultimoAviso = HashMap<UUID, Long>()

    fun nombreDimension(level: Level): String = when (level.dimension()) {
        Level.NETHER -> "the_nether"
        Level.END -> "the_end"
        Level.OVERWORLD -> "overworld"
        else -> "otra"
    }

    fun zonaEn(level: Level, pos: BlockPos, margen: Int = 0): Zona? =
        BancoMinecraftPer.zonas.buscar(nombreDimension(level), pos.x, pos.y, pos.z, margen)

    private fun puedeEditar(jugador: Player): Boolean =
        jugador.isCreative && Permissions.check(jugador, PERM_EDITAR, 2)

    private fun bloqueado(jugador: Player, level: Level, pos: BlockPos, margen: Int = 0): Boolean {
        if (jugador !is ServerPlayer) return false
        if (zonaEn(level, pos, margen) == null) return false
        if (puedeEditar(jugador)) return false
        avisar(jugador)
        return true
    }

    private fun avisar(jugador: ServerPlayer) {
        val ahora = System.currentTimeMillis()
        if (ahora - (ultimoAviso[jugador.uuid] ?: 0L) < 3000) return
        ultimoAviso[jugador.uuid] = ahora
        jugador.sendSystemMessage(Component.literal("Zona protegida del servidor.").withStyle(ChatFormatting.RED))
    }

    private fun entidadProtegida(e: Entity): Boolean = e is ArmorStand

    fun registrarEventos() {
        PlayerBlockBreakEvents.BEFORE.register { level, jugador, pos, _, _ ->
            !bloqueado(jugador, level, pos)
        }

        UseBlockCallback.EVENT.register { jugador, level, _, hit ->
            val pos = hit.blockPos
            if (bloqueado(jugador, level, pos) || bloqueado(jugador, level, pos.relative(hit.direction))) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }

        UseItemCallback.EVENT.register { jugador, level, mano ->
            if (jugador.getItemInHand(mano).item is BucketItem &&
                bloqueado(jugador, level, jugador.blockPosition(), MARGEN_CUBETAS)
            ) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }

        UseEntityCallback.EVENT.register { jugador, level, _, entidad, _ ->
            if (entidadProtegida(entidad) && bloqueado(jugador, level, entidad.blockPosition())) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }

        // Segunda capa contra explosiones: creepers, TNT y bolas de fuego que entran
        // a una zona desaparecen antes de explotar (no depende del mixin).
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (++ticks % 10 == 0) quitarExplosivos(servidor)
        }

        AttackEntityCallback.EVENT.register { jugador, level, _, entidad, _ ->
            if (entidadProtegida(entidad) && bloqueado(jugador, level, entidad.blockPosition())) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }
    }

    private fun nivelDe(servidor: MinecraftServer, dimension: String) = when (dimension) {
        "the_nether" -> servidor.getLevel(Level.NETHER)
        "the_end" -> servidor.getLevel(Level.END)
        "overworld" -> servidor.getLevel(Level.OVERWORLD)
        else -> null
    }

    private fun esExplosivo(e: Entity): Boolean =
        e is Creeper || e is PrimedTnt || e is MinecartTNT || e is LargeFireball || e is WitherSkull

    private fun quitarExplosivos(servidor: MinecraftServer) {
        for (z in BancoMinecraftPer.zonas.zonas.orEmpty()) {
            if (!z.activa) continue
            val nivel = nivelDe(servidor, z.dimension) ?: continue
            val caja = AABB(
                minOf(z.x1, z.x2).toDouble(), minOf(z.y1, z.y2).toDouble(), minOf(z.z1, z.z2).toDouble(),
                maxOf(z.x1, z.x2) + 1.0, maxOf(z.y1, z.y2) + 1.0, maxOf(z.z1, z.z2) + 1.0,
            ).inflate(MARGEN_EXPLOSIVOS)
            for (e in nivel.getEntitiesOfClass(Entity::class.java, caja) { esExplosivo(it) }) {
                e.discard()
            }
        }
    }

    /** Lo llama el mixin cuando una explosión iba a romper bloques de una zona. */
    @JvmStatic
    fun explosionFrenada(salvados: Int) {
        BancoMinecraftPer.LOGGER.info("Explosión en zona protegida: {} bloques no se rompieron", salvados)
    }

    /** Para el mixin de explosiones: true si la posición está en alguna zona. */
    @JvmStatic
    fun protegido(level: Level, pos: BlockPos): Boolean = zonaEn(level, pos) != null
}
