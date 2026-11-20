package io.github.andrexifor.banco.juego

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/** Pequeñas ayudas comunes. */
object Efectos {
    /** Un sonido que solo oye este jugador. */
    fun sonar(j: ServerPlayer, ev: SoundEvent, vol: Float = 1f, tono: Float = 1f) {
        j.connection.send(
            ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev), SoundSource.MASTER, j.x, j.y, j.z, vol, tono, j.random.nextLong()),
        )
    }
}
