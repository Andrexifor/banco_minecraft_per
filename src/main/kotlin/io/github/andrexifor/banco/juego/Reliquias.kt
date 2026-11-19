package io.github.andrexifor.banco.juego

import com.mojang.brigadier.StringReader
import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack

/**
 * Reliquias del Oráculo del Cosmos: los legendarios del catálogo por defecto llevan aspecto propio
 * (item_model del resource pack y, la armadura, la capa "armadura_sol" para verse puesta).
 * Los que se compraron antes se actualizan solos: cada 5 s se revisa el inventario de los jugadores.
 */
object Reliquias {
    private val RANURA_ARMADURA = mapOf("casco" to "head", "pechera" to "chest", "pantalones" to "legs", "botas" to "feet")
    private val CON_ASPECTO = setOf("espada", "hacha", "pico", "arco", "tridente") + RANURA_ARMADURA.keys

    /** Componentes (formato /give) que dan el aspecto de la reliquia [id]; null si no tiene diseño propio. */
    fun componentes(id: String): String? {
        if (id !in CON_ASPECTO) return null
        val modelo = "item_model=\"banco_minecraft_per:reliquia_$id\""
        val ranura = RANURA_ARMADURA[id] ?: return modelo
        return "$modelo,equippable={slot:\"$ranura\",asset_id:\"banco_minecraft_per:armadura_sol\"," +
            "equip_sound:\"minecraft:item.armor.equip_netherite\"}"
    }

    private val plantillas = HashMap<String, ItemStack>()

    private fun plantilla(servidor: MinecraftServer, item: String, id: String): ItemStack? {
        val extra = componentes(id) ?: return null
        return plantillas.getOrPut("$item|$id") {
            try {
                val contexto = CommandBuildContext.simple(servidor.registryAccess(), servidor.worldData.enabledFeatures())
                ItemArgument.item(contexto).parse(StringReader("$item[$extra]")).createItemStack(1)
            } catch (e: Exception) {
                BancoMinecraftPer.LOGGER.warn("Reliquia {}: no se pudo crear el aspecto: {}", id, e.message)
                ItemStack.EMPTY
            }
        }.takeIf { !it.isEmpty }
    }

    /** Copia el aspecto de la reliquia [id] al ítem. Devuelve true si cambió algo. */
    fun aplicar(servidor: MinecraftServer, stack: ItemStack, id: String): Boolean {
        val p = plantilla(servidor, BuiltInRegistries.ITEM.getKey(stack.item).toString(), id) ?: return false
        val modelo = p.get(DataComponents.ITEM_MODEL)
        var cambio = false
        if (modelo != null && stack.get(DataComponents.ITEM_MODEL) != modelo) {
            stack.set(DataComponents.ITEM_MODEL, modelo)
            cambio = true
        }
        if (id in RANURA_ARMADURA) {
            val equipo = p.get(DataComponents.EQUIPPABLE)
            if (equipo != null && stack.get(DataComponents.EQUIPPABLE) != equipo) {
                stack.set(DataComponents.EQUIPPABLE, equipo)
                cambio = true
            }
        }
        return cambio
    }

    /** id del legendario guardado en custom_data, o null si no es legendario. */
    fun idDe(stack: ItemStack): String? {
        if (!Encantamientos.esLegendario(stack)) return null
        val datos = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return datos.copyTag().getCompoundOrEmpty(BancoMinecraftPer.MOD_ID).getStringOr("id", "").ifBlank { null }
    }

    fun registrarEventos() {
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 100 != 50) return@register
            for (j in servidor.playerList.players) {
                val inv = j.inventory
                for (i in 0 until inv.containerSize) {
                    val s = inv.getItem(i)
                    if (s.isEmpty) continue
                    val id = idDe(s) ?: continue
                    if (aplicar(servidor, s, id)) inv.setChanged()
                }
            }
        }
    }
}
