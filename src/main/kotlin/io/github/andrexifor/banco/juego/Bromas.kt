package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import io.github.andrexifor.banco.nucleo.Logro
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

/** Efectos de los ítems del mercader de bromas. */
object Bromas {
    /** Probabilidad de rayo de la Vara de la Verdad por golpe. */
    private const val PROBABILIDAD_RAYO = 0.02

    private fun marca(id: String): CompoundTag {
        val interno = CompoundTag()
        interno.putString("broma", id)
        val raiz = CompoundTag()
        raiz.put(BancoMinecraftPer.MOD_ID, interno)
        return raiz
    }

    fun esBroma(stack: ItemStack, id: String): Boolean {
        if (stack.isEmpty) return false
        val datos = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return datos.matchedBy(marca(id))
    }

    fun registrarEventos() {
        AttackEntityCallback.EVENT.register { jugador, level, _, entidad, _ ->
            if (jugador is ServerPlayer &&
                esBroma(jugador.mainHandItem, CatalogoOfertas.VARA_VERDAD) &&
                Random.nextDouble() < PROBABILIDAD_RAYO
            ) {
                val servidor = jugador.level().server
                if (servidor != null) {
                    val dim = Proteccion.nombreDimension(level)
                    servidor.commands.performPrefixedCommand(
                        servidor.createCommandSourceStack().withSuppressedOutput(),
                        "execute in minecraft:$dim run summon minecraft:lightning_bolt ${entidad.x} ${entidad.y} ${entidad.z}",
                    )
                    Logros.otorgar(jugador, Logro.VERDAD_DUELE)
                }
            }
            InteractionResult.PASS
        }
    }
}
