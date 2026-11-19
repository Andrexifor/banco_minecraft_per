package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Destino
import io.github.andrexifor.banco.nucleo.Logro
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Mercado negro del Nether. Se desbloquea para siempre la primera vez que el jugador
 * lleva puesto el set completo de armadura legendaria (logro "Los dioses te ven de más cerca").
 * La puerta sellada es un NPC (entidad de interacción) que teletransporta adentro si ya está desbloqueado.
 */
object MercadoNegro {
    fun desbloqueado(j: ServerPlayer): Boolean = Logros.datos(j).mercadoNegro

    fun desbloquear(j: ServerPlayer) {
        val d = Logros.datos(j)
        if (d.mercadoNegro) return
        d.mercadoNegro = true
        Logros.marcarCambio()
        Logros.otorgar(j, Logro.DIOSES)
        j.sendSystemMessage(
            Component.literal("Algo se mueve en lo profundo del Nether… el mercado negro ahora te reconoce.")
                .withStyle(ChatFormatting.DARK_PURPLE),
        )
    }

    fun puertaEntrada(j: ServerPlayer) {
        if (!desbloqueado(j)) {
            j.sendSystemMessage(
                Component.literal("La puerta no se mueve. El mercado negro no te reconoce…")
                    .withStyle(ChatFormatting.DARK_GRAY),
            )
            return
        }
        llevar(j, BancoMinecraftPer.mercadoNegro.entrada ?: return)
        j.sendSystemMessage(Component.literal("Bienvenido al mercado negro.").withStyle(ChatFormatting.DARK_RED))
    }

    fun puertaSalida(j: ServerPlayer) {
        llevar(j, BancoMinecraftPer.mercadoNegro.salida ?: return)
    }

    private fun llevar(j: ServerPlayer, d: Destino) {
        val servidor = j.level().server ?: return
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withSuppressedOutput(),
            "execute in minecraft:${d.dimension} run tp ${j.uuid} ${d.x} ${d.y} ${d.z} ${d.yaw} 0",
        )
    }
}
