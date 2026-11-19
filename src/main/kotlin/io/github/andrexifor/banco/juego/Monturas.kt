package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.equine.Mule
import net.minecraft.world.entity.animal.equine.SkeletonHorse
import net.minecraft.world.entity.animal.equine.ZombieHorse
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import kotlin.random.Random

/**
 * Monturas especiales del criador:
 *  - Caballo zombi: mientras alguien lo monta, el agua bajo sus patas se vuelve hielo
 *    escarchado (como Paso helado) y se derrite sola; así corre y salta sobre el agua.
 *  - Mula de carga: Shift + clic derecho abre una alforja de 54 casillas guardada en la mula
 *    (si la mula muere, la carga cae al suelo).
 *  - Cada 5 s: los caballos zombi y esqueleto del criador mantienen resistencia al fuego
 *    infinita, y las sillas simples de las tres monturas pasan a su montura con aspecto propio.
 */
object Monturas {
    private const val RADIO_HIELO = 3
    private const val CASILLAS_ALFORJA = 54

    /** Contenido de la alforja, guardado dentro de la propia mula (persiste con el mundo). */
    private val ALFORJA: AttachmentType<List<ItemStack>> = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(BancoMinecraftPer.MOD_ID, "alforja"),
        ItemStack.OPTIONAL_CODEC.listOf(),
    )

    private fun esZombiAcuatico(e: Entity?): Boolean =
        e != null && e is ZombieHorse && e.customName?.string == CatalogoOfertas.NOMBRE_ZOMBI

    private fun esMulaDeCarga(e: Entity): Boolean =
        e is Mule && e.customName?.string == CatalogoOfertas.NOMBRE_MULA

    fun registrarEventos() {
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            congelarBajoZombis(servidor)
            if (servidor.tickCount % 100 == 0) mantenerMonturas(servidor)
        }

        UseEntityCallback.EVENT.register { jugador, _, _, entidad, _ ->
            if (jugador is ServerPlayer && jugador.isShiftKeyDown && esMulaDeCarga(entidad)) {
                abrirAlforja(jugador, entidad)
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { muerto, _ ->
            val carga = muerto.getAttached(ALFORJA)
            if (carga != null) {
                val nivel = muerto.level() as? ServerLevel
                if (nivel != null) {
                    carga.filter { !it.isEmpty }.forEach {
                        nivel.addFreshEntity(ItemEntity(nivel, muerto.x, muerto.y + 0.5, muerto.z, it.copy()))
                    }
                }
                muerto.removeAttached(ALFORJA)
            }
        }
    }

    // ---------- caballo zombi sobre el agua ----------

    private fun congelarBajoZombis(servidor: MinecraftServer) {
        for (j in servidor.playerList.players) {
            val montura = j.vehicle
            if (!esZombiAcuatico(montura)) continue
            val nivel = montura!!.level() as? ServerLevel ?: continue
            val centro = montura.blockPosition()
            for (dx in -RADIO_HIELO..RADIO_HIELO) {
                for (dz in -RADIO_HIELO..RADIO_HIELO) {
                    if (dx * dx + dz * dz > RADIO_HIELO * RADIO_HIELO) continue
                    congelar(nivel, centro.offset(dx, -1, dz))
                }
            }
        }
    }

    private fun congelar(nivel: ServerLevel, pos: BlockPos) {
        val estado = nivel.getBlockState(pos)
        if (!estado.`is`(Blocks.WATER) || !estado.fluidState.isSource) return
        if (!nivel.isEmptyBlock(pos.above())) return
        if (Proteccion.zonaEn(nivel, pos) != null) return
        nivel.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState())
        nivel.scheduleTick(pos, Blocks.FROSTED_ICE, Random.nextInt(60, 120))
    }

    // ---------- fuego y monturas propias ----------

    private val monturas = HashMap<String, ItemStack>()

    private fun montura(servidor: MinecraftServer, texto: String): ItemStack? =
        monturas.getOrPut(texto) {
            try {
                // el texto es un ítem en SNBT {id,count,components}: se lee con el codec de ItemStack
                ItemStack.CODEC.parse(
                    servidor.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                    TagParser.parseCompoundFully(texto),
                ).getOrThrow()
            } catch (e: Exception) {
                BancoMinecraftPer.LOGGER.warn("Montura mal escrita: {}", e.message)
                ItemStack.EMPTY
            }
        }.takeIf { !it.isEmpty }

    private fun mantenerMonturas(servidor: MinecraftServer) {
        for (nivel in servidor.allLevels) {
            for (e in nivel.allEntities) {
                if (e !is AbstractHorse) continue
                val nombre = e.customName?.string ?: continue
                val (sinFuego, textoMontura) = when {
                    e is ZombieHorse && nombre == CatalogoOfertas.NOMBRE_ZOMBI -> true to CatalogoOfertas.MONTURA_ZOMBI
                    e is SkeletonHorse && nombre == CatalogoOfertas.NOMBRE_ESQUELETO -> true to CatalogoOfertas.MONTURA_ESQUELETO
                    e is Mule && nombre == CatalogoOfertas.NOMBRE_MULA -> false to CatalogoOfertas.MONTURA_MULA
                    else -> continue
                }
                if (sinFuego && !e.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    e.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false))
                }
                val silla = e.getItemBySlot(EquipmentSlot.SADDLE)
                if (silla.`is`(Items.SADDLE) && !silla.has(DataComponents.CUSTOM_NAME)) {
                    montura(servidor, textoMontura)?.let { e.setItemSlot(EquipmentSlot.SADDLE, it.copy()) }
                }
            }
        }
    }

    // ---------- alforja de la mula ----------

    /** Abre la alforja de la mula como un cofre doble vanilla; al cerrarlo se guarda en la mula. */
    private fun abrirAlforja(j: ServerPlayer, mula: Entity) {
        val guardado = mula.getAttached(ALFORJA).orEmpty()
        val contenedor = object : SimpleContainer(CASILLAS_ALFORJA) {
            override fun stopOpen(user: ContainerUser) {
                super.stopOpen(user)
                val lista = (0 until CASILLAS_ALFORJA).map { getItem(it).copy() }
                if (lista.all { it.isEmpty }) mula.removeAttached(ALFORJA) else mula.setAttached(ALFORJA, lista)
            }
        }
        guardado.take(CASILLAS_ALFORJA).forEachIndexed { i, s -> contenedor.setItem(i, s.copy()) }
        val titulo = texto("Alforja de ${mula.customName?.string ?: "la mula"}", ChatFormatting.GOLD)
        j.openMenu(SimpleMenuProvider({ id, inventario, _ -> ChestMenu.sixRows(id, inventario, contenedor) }, titulo))
        BancoMinecraftPer.LOGGER.debug("Alforja abierta por {}", j.name.string)
    }
}
