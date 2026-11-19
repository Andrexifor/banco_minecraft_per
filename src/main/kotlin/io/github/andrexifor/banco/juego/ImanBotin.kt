package io.github.andrexifor.banco.juego

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity

/**
 * Imán de botín (Tridente de Pachacámac y cualquier legendario con "iman": true):
 * cuando el ítem mata a un enemigo, cuerpo a cuerpo o lanzado, lo que suelta va
 * directo al inventario del jugador. Si el inventario está lleno, cae a sus pies.
 */
object ImanBotin {
    fun registrarEventos() {
        ServerLivingEntityEvents.AFTER_DEATH.register { muerto, fuente -> alMorir(muerto, fuente) }
    }

    private fun alMorir(muerto: LivingEntity, fuente: DamageSource) {
        val jugador = fuente.entity as? ServerPlayer ?: return
        if (muerto == jugador) return
        // Lanzado: el arma es el tridente que viaja. Cuerpo a cuerpo: lo que tiene en la mano.
        val arma = fuente.weaponItem ?: jugador.mainHandItem
        if (!Encantamientos.esIman(arma)) return

        val nivel = muerto.level() as? ServerLevel ?: return
        // El botín recién soltado está alrededor del cuerpo y tiene edad 0
        val botin = nivel.getEntitiesOfClass(ItemEntity::class.java, muerto.boundingBox.inflate(1.5)) {
            it.age == 0 && !it.isRemoved
        }
        for (item in botin) {
            Inventarios.dar(jugador, item.item.copy())
            item.discard()
        }
    }
}
