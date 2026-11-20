package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.HopperMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.level.block.ChestBlock
import java.util.UUID

/**
 * Mochilas del Sembrador del Vacío (todas se abren con clic derecho):
 *  - chuspa (5 casillas), q'epi (18) y gran q'epi (27): lo que llevan viaja dentro del ítem
 *    (componente container). Con hilo de alpaca en la otra mano, se agrandan un tamaño.
 *  - chuspa del Ukhu Pacha: abre el cofre de Ender del jugador.
 *  - chuspa enlazada: se enlaza al abrir un cofre (simple o doble) teniéndola en la otra mano;
 *    luego abre ese cofre desde cualquier lugar. Shift + clic derecho la desenlaza.
 *
 * Anti-duplicado: al abrir una mochila su contenido sale del ítem al menú y vuelve al cerrar,
 * buscando la mochila por un id propio. Si la mochila ya no está (se tiró), el contenido se le da
 * al jugador. Una mochila nunca se guarda dentro de otra: al cerrar, las mochilas salen.
 */
object Mochilas {
    private const val MARCA = BancoMinecraftPer.MOD_ID

    private enum class Tipo(val id: String, val casillas: Int, val nombre: String, val siguiente: String?) {
        CHUSPA("chuspa", 5, "Chuspa", "qepi"),
        QEPI("qepi", 18, "Q'epi", "gran_qepi"),
        GRAN_QEPI("gran_qepi", 27, "Gran q'epi", null),
        UKHU("ukhu", 0, "Chuspa del Ukhu Pacha", null),
        ENLAZADA("enlazada", 0, "Chuspa enlazada", null),
        ;

        companion object {
            fun de(id: String) = entries.firstOrNull { it.id == id }
        }
    }

    private fun datos(s: ItemStack): CompoundTag? =
        s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(MARCA)?.takeIf { !it.isEmpty }

    private fun tipo(s: ItemStack): Tipo? = datos(s)?.getStringOr("mochila", "")?.let { Tipo.de(it) }

    fun esMochila(s: ItemStack) = !s.isEmpty && tipo(s) != null

    private fun esHilo(s: ItemStack) = !s.isEmpty && datos(s)?.contains("mejora") == true

    private fun editar(s: ItemStack, f: (CompoundTag) -> Unit) {
        val raiz = s.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        val interno = raiz.getCompoundOrEmpty(MARCA)
        f(interno)
        raiz.put(MARCA, interno)
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(raiz))
    }

    /** Último cofre al que cada jugador hizo clic (para enlazar la chuspa si el cofre se abre). */
    private data class Clic(val dim: String, val pos: BlockPos, val tick: Int)
    private val ultimoCofre = HashMap<UUID, Clic>()

    fun registrarEventos() {
        UseItemCallback.EVENT.register { jugador, _, mano ->
            val s = jugador.getItemInHand(mano)
            if (jugador !is ServerPlayer || !esMochila(s)) return@register InteractionResult.PASS
            usar(jugador, mano, s)
            InteractionResult.SUCCESS
        }
        UseBlockCallback.EVENT.register { jugador, nivel, _, golpe ->
            if (jugador is ServerPlayer && nivel.getBlockState(golpe.blockPos).block is ChestBlock) {
                val servidor = jugador.level().server
                if (servidor != null) {
                    ultimoCofre[jugador.uuid] = Clic(nivel.dimension().identifier().toString(), golpe.blockPos.immutable(), servidor.tickCount)
                }
            }
            InteractionResult.PASS
        }
        // si el cofre se abrió de verdad (las protecciones lo dejaron) y hay una chuspa enlazada sin
        // destino en la otra mano, queda enlazada a ese cofre
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (ultimoCofre.isEmpty()) return@register
            val it = ultimoCofre.entries.iterator()
            while (it.hasNext()) {
                val (uuid, clic) = it.next()
                if (servidor.tickCount - clic.tick > 10) { it.remove(); continue }
                val j = servidor.playerList.getPlayer(uuid) ?: continue
                if (j.containerMenu !is ChestMenu) continue
                it.remove()
                for (mano in InteractionHand.entries) {
                    val s = j.getItemInHand(mano)
                    if (tipo(s) == Tipo.ENLAZADA && datos(s)?.contains("enlace") != true) {
                        editar(s) { d ->
                            val e = CompoundTag()
                            e.putString("dim", clic.dim)
                            e.putInt("x", clic.pos.x); e.putInt("y", clic.pos.y); e.putInt("z", clic.pos.z)
                            d.put("enlace", e)
                        }
                        aviso(j, "Chuspa enlazada al cofre en ${clic.pos.x}, ${clic.pos.y}, ${clic.pos.z}", ChatFormatting.GOLD)
                        break
                    }
                }
            }
        }
    }

    private fun aviso(j: ServerPlayer, t: String, color: ChatFormatting) =
        j.sendSystemMessage(Component.literal(t).withStyle(color))

    private fun usar(j: ServerPlayer, mano: InteractionHand, s: ItemStack) {
        when (val t = tipo(s)!!) {
            Tipo.UKHU -> {
                j.openMenu(SimpleMenuProvider({ id, inv, _ -> ChestMenu.threeRows(id, inv, j.enderChestInventory) },
                    Component.literal(t.nombre).withStyle(ChatFormatting.DARK_PURPLE)))
                j.level().playSound(null, j.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.2f)
            }
            Tipo.ENLAZADA -> enlazada(j, s)
            else -> {
                val otra = j.getItemInHand(if (mano == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND)
                if (esHilo(otra) && t.siguiente != null) mejorar(j, s, t, otra) else abrir(j, s, t)
            }
        }
    }

    private fun mejorar(j: ServerPlayer, s: ItemStack, t: Tipo, hilo: ItemStack) {
        val nueva = Tipo.de(t.siguiente!!)!!
        editar(s) { it.putString("mochila", nueva.id) }
        s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(MARCA, nueva.id))
        s.set(DataComponents.CUSTOM_NAME, Component.literal(nueva.nombre).withStyle(ChatFormatting.GREEN).withStyle { it.withItalic(false) })
        hilo.shrink(1)
        j.level().playSound(null, j.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f)
        aviso(j, "Tu ${t.nombre} ahora es un ${nueva.nombre} de ${nueva.casillas} casillas", ChatFormatting.GREEN)
    }

    private fun abrir(j: ServerPlayer, bolsa: ItemStack, t: Tipo) {
        var id = datos(bolsa)?.getStringOr("id", "").orEmpty()
        if (id.isBlank()) {
            id = UUID.randomUUID().toString()
            val nuevoId = id
            editar(bolsa) { it.putString("id", nuevoId) }
        }
        val idFinal = id
        val contenedor = object : SimpleContainer(t.casillas) {
            override fun stopOpen(user: ContainerUser) {
                super.stopOpen(user)
                guardar(j, idFinal, this)
            }
        }
        // el contenido pasa del ítem al menú (el ítem queda vacío mientras está abierto)
        val lista = NonNullList.withSize(t.casillas, ItemStack.EMPTY)
        bolsa.get(DataComponents.CONTAINER)?.copyInto(lista)
        lista.forEachIndexed { i, it -> contenedor.setItem(i, it) }
        bolsa.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
        val titulo = Component.literal(t.nombre).withStyle(ChatFormatting.DARK_GREEN)
        j.openMenu(SimpleMenuProvider({ mid, inv, _ ->
            when (t.casillas) {
                5 -> HopperMenu(mid, inv, contenedor)
                18 -> ChestMenu(MenuType.GENERIC_9x2, mid, inv, contenedor, 2)
                else -> ChestMenu.threeRows(mid, inv, contenedor)
            }
        }, titulo))
        j.level().playSound(null, j.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6f, 1.0f)
    }

    private fun guardar(j: ServerPlayer, id: String, c: Container) {
        val guardados = ArrayList<ItemStack>()
        for (i in 0 until c.containerSize) {
            val it = c.getItem(i)
            if (esMochila(it)) {
                Inventarios.dar(j, it.copy()) // una mochila no viaja dentro de otra
                guardados += ItemStack.EMPTY
            } else {
                guardados += it.copy()
            }
        }
        c.clearContent()
        val inv = j.inventory
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (esMochila(s) && datos(s)?.getStringOr("id", "") == id) {
                s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(guardados))
                return
            }
        }
        // la mochila ya no está en el inventario: el contenido vuelve al jugador
        guardados.filter { !it.isEmpty }.forEach { Inventarios.dar(j, it) }
    }

    // ---------- chuspa enlazada ----------

    private class Remoto(private val base: Container, private val sigue: () -> Boolean) : Container by base {
        override fun stillValid(player: Player): Boolean = sigue()
    }

    private fun enlazada(j: ServerPlayer, s: ItemStack) {
        val enlace = datos(s)?.getCompoundOrEmpty("enlace")?.takeIf { !it.isEmpty }
        if (j.isShiftKeyDown && enlace != null) {
            editar(s) { it.remove("enlace") }
            aviso(j, "Chuspa desenlazada. Ábrela junto a otro cofre (tenla en la otra mano) para enlazarla.", ChatFormatting.YELLOW)
            return
        }
        if (enlace == null) {
            aviso(j, "Para enlazarla: tenla en la otra mano y abre un cofre.", ChatFormatting.YELLOW)
            return
        }
        val servidor = j.level().server ?: return
        val clave = ResourceKey.create(Registries.DIMENSION, Identifier.parse(enlace.getStringOr("dim", "minecraft:overworld")))
        val nivel = servidor.getLevel(clave) ?: return aviso(j, "No encuentro el mundo de ese cofre.", ChatFormatting.RED)
        val pos = BlockPos(enlace.getIntOr("x", 0), enlace.getIntOr("y", 0), enlace.getIntOr("z", 0))
        val estado = nivel.getBlockState(pos)
        val bloque = estado.block as? ChestBlock
            ?: return aviso(j, "El cofre enlazado ya no está. Shift + clic derecho para desenlazarla.", ChatFormatting.RED)
        val base = ChestBlock.getContainer(bloque, estado, nivel, pos, true)
            ?: return aviso(j, "No se puede abrir ese cofre ahora.", ChatFormatting.RED)
        val remoto = Remoto(base) { nivel.getBlockState(pos).block is ChestBlock }
        val titulo = Component.literal("Cofre en ${pos.x}, ${pos.y}, ${pos.z}").withStyle(ChatFormatting.GOLD)
        j.openMenu(SimpleMenuProvider({ mid, inv, _ ->
            if (base.containerSize >= 54) ChestMenu.sixRows(mid, inv, remoto) else ChestMenu.threeRows(mid, inv, remoto)
        }, titulo))
        j.level().playSound(null, j.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.1f)
    }
}
