package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.ProblemReporter
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.OwnableEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.phys.Vec3

/**
 * Flor de traslado (la vende el Criador): clic derecho a un animal lo guarda dentro de la flor
 * (con nombre, vida, montura, alforja… todo); clic derecho al suelo lo libera ahí y la flor se gasta.
 * Las flores vacías se apilan; las que llevan un animal no (cada una guarda uno distinto).
 * Solo animales (no aldeanos ni monstruos), y no los domesticados por otro jugador.
 */
object FlorTraslado {
    private const val MARCA = BancoMinecraftPer.MOD_ID

    private fun datos(s: ItemStack): CompoundTag? =
        s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(MARCA)?.takeIf { it.contains("flor") }

    /** ¿Es una flor que lleva un animal adentro? */
    fun llena(s: ItemStack): Boolean = !s.isEmpty && datos(s)?.contains("animal") == true

    private fun aviso(j: ServerPlayer, t: String, c: ChatFormatting) = j.sendSystemMessage(Component.literal(t).withStyle(c))

    fun registrarEventos() {
        UseEntityCallback.EVENT.register { jugador, nivel, mano, entidad, _ ->
            if (jugador !is ServerPlayer || mano != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            val s = jugador.getItemInHand(mano)
            val d = datos(s) ?: return@register InteractionResult.PASS
            if (d.contains("animal")) {
                aviso(jugador, "Esta flor ya lleva un animal: clic derecho al suelo para liberarlo.", ChatFormatting.YELLOW)
                return@register InteractionResult.SUCCESS
            }
            guardar(jugador, nivel as ServerLevel, s, entidad)
            InteractionResult.SUCCESS
        }
        UseBlockCallback.EVENT.register { jugador, nivel, mano, golpe ->
            if (jugador !is ServerPlayer) return@register InteractionResult.PASS
            val s = jugador.getItemInHand(mano)
            val d = datos(s) ?: return@register InteractionResult.PASS
            val animal = d.getCompoundOrEmpty("animal").takeIf { !it.isEmpty } ?: return@register InteractionResult.PASS
            val pos = golpe.blockPos.relative(golpe.direction)
            liberar(jugador, nivel as ServerLevel, s, animal, d.getStringOr("tipo", ""), Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5))
            InteractionResult.SUCCESS
        }
    }

    private fun guardar(j: ServerPlayer, nivel: ServerLevel, flor: ItemStack, e: Entity) {
        if (e !is Animal || e.isInvulnerable || "plaza_inca" in e.entityTags()) {
            return aviso(j, "La flor solo puede llevar animales.", ChatFormatting.RED)
        }
        if (e.isVehicle || e.isPassenger) return aviso(j, "Bájate (o baja a quien lo monta) antes de guardarlo.", ChatFormatting.RED)
        val dueno = (e as? OwnableEntity)?.ownerReference?.uuid
        if (dueno != null && dueno != j.uuid) return aviso(j, "Ese animal tiene otro dueño.", ChatFormatting.RED)

        val salida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, nivel.registryAccess())
        e.saveWithoutId(salida)
        val nbt = salida.buildResult()
        for (k in listOf("UUID", "Pos", "Motion", "Leash", "leash", "Passengers")) nbt.remove(k)
        val tipo = BuiltInRegistries.ENTITY_TYPE.getKey(e.type).toString()
        val nombre = e.customName?.string ?: e.type.description.string

        val llena = flor.copyWithCount(1)
        val raiz = llena.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        val interno = raiz.getCompoundOrEmpty(MARCA)
        interno.put("animal", nbt)
        interno.putString("tipo", tipo)
        raiz.put(MARCA, interno)
        llena.set(DataComponents.CUSTOM_DATA, CustomData.of(raiz))
        llena.set(DataComponents.MAX_STACK_SIZE, 1)
        llena.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(MARCA, "flor_llena"))
        llena.set(DataComponents.CUSTOM_NAME, Component.literal("Flor con $nombre").withStyle(ChatFormatting.YELLOW).withStyle { it.withItalic(false) })
        flor.shrink(1)
        e.discard()
        Inventarios.dar(j, llena)
        nivel.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f)
        aviso(j, "$nombre está ahora en la flor. Clic derecho al suelo para liberarlo.", ChatFormatting.GREEN)
    }

    private fun liberar(j: ServerPlayer, nivel: ServerLevel, flor: ItemStack, animal: CompoundTag, tipo: String, pos: Vec3) {
        if (tipo.isBlank()) return
        val servidor = nivel.server
        val fuente = servidor.createCommandSourceStack().withLevel(nivel).withPosition(pos).withSuppressedOutput()
        servidor.commands.performPrefixedCommand(fuente, "summon $tipo ${pos.x} ${pos.y} ${pos.z} $animal")
        flor.shrink(1)
        nivel.playSound(null, j.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f)
        aviso(j, "Animal liberado. La flor se marchitó.", ChatFormatting.GREEN)
    }
}
