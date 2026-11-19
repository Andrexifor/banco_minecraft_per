package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Zona
import me.lucko.fabric.api.permissions.v0.Permissions
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
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BucketItem
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Zonas protegidas (config/banco_minecraft_per/zonas.json): dentro, solo los op
 * (nivel 2+) o quien tenga banco_minecraft_per.zonas.editar pueden romper, poner
 * o usar bloques, usar cubetas, ni tocar soportes de armadura. Los aldeanos del
 * banco y el portal se siguen usando normal. Las explosiones no rompen bloques ahí
 * (ExplosionMixin).
 */
object Proteccion {
    const val PERM_EDITAR = "${BancoMinecraftPer.MOD_ID}.zonas.editar"
    private const val MARGEN_CUBETAS = 6
    private val ultimoAviso = HashMap<UUID, Long>()

    fun nombreDimension(level: Level): String = when (level.dimension()) {
        Level.NETHER -> "the_nether"
        Level.END -> "the_end"
        Level.OVERWORLD -> "overworld"
        else -> "otra"
    }

    fun zonaEn(level: Level, pos: BlockPos, margen: Int = 0): Zona? =
        BancoMinecraftPer.zonas.buscar(nombreDimension(level), pos.x, pos.y, pos.z, margen)

    private fun puedeEditar(jugador: Player): Boolean = Permissions.check(jugador, PERM_EDITAR, 2)

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

        AttackEntityCallback.EVENT.register { jugador, level, _, entidad, _ ->
            if (entidadProtegida(entidad) && bloqueado(jugador, level, entidad.blockPosition())) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }
    }

    /** Para el mixin de explosiones: true si la posición está en alguna zona. */
    @JvmStatic
    fun protegido(level: Level, pos: BlockPos): Boolean = zonaEn(level, pos) != null
}
