package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Contador
import io.github.andrexifor.banco.nucleo.Denominacion
import io.github.andrexifor.banco.nucleo.DatosJugador
import io.github.andrexifor.banco.nucleo.Desglose
import io.github.andrexifor.banco.nucleo.Logro
import io.github.andrexifor.banco.nucleo.Metas
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import io.github.andrexifor.banco.nucleo.RegistroJugadores
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.level.Level

/**
 * Logros del servidor (data/banco_minecraft_per/advancement/*.json dentro del jar).
 * Todos usan el criterio "minecraft:impossible": los da el mod con /advancement grant,
 * así se ven como logros de Minecraft (aviso en pantalla + chat) sin que el cliente instale nada.
 * Lo que el mod recuerda de cada jugador va en banco_minecraft_per/jugadores.json.
 */
object Logros {
    private lateinit var registro: RegistroJugadores
    private var sucio = false
    private var ticks = 0

    fun cargar() {
        registro = BancoMinecraftPer.almacen.cargarJugadores()
    }

    fun datos(j: ServerPlayer): DatosJugador = registro.de(j.uuid.toString()).also { it.nombre = j.name.string }

    fun guardar() {
        if (!sucio) return
        try {
            BancoMinecraftPer.almacen.guardarJugadores(registro)
            sucio = false
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.error("No se pudo guardar jugadores.json", e)
        }
    }

    fun marcarCambio() {
        sucio = true
    }

    /** Da el logro una sola vez. */
    fun otorgar(j: ServerPlayer, logro: Logro) {
        val d = datos(j)
        if (d.tiene(logro)) return
        d.otorgados().add(logro.id)
        sucio = true
        val servidor = j.level().server ?: return
        servidor.commands.performPrefixedCommand(
            servidor.createCommandSourceStack().withSuppressedOutput(),
            "advancement grant ${j.uuid} only ${BancoMinecraftPer.MOD_ID}:${logro.id}",
        )
        guardar()
    }

    /** Suma a un contador y da el logro si se alcanza la meta. */
    fun sumar(j: ServerPlayer, contador: String, n: Long, meta: Long, logro: Logro) {
        if (n <= 0) return
        val total = datos(j).sumar(contador, n)
        sucio = true
        if (total >= meta) otorgar(j, logro)
    }

    fun registrarEventos() {
        // Revisión periódica: saldo, efectivo, armadura legendaria, ghast y sala del portal
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (++ticks % 40 == 0) revisar(servidor)
            if (ticks % 1200 == 0) guardar()
        }
    }

    private fun revisar(servidor: MinecraftServer) {
        for (j in servidor.playerList.players) {
            val d = datos(j)
            for (l in d.logrosPorSaldo(Operaciones.saldo(j))) otorgar(j, l)
            sucio = true
            if (!d.tiene(Logro.TAHUANTINSUYO) && inventarioLlenoDeBonos(j)) otorgar(j, Logro.TAHUANTINSUYO)
            if (setLegendarioPuesto(j)) MercadoNegro.desbloquear(j)
            if (!d.tiene(Logro.VOLANDO_BAJITO) && montaGhastPeruano(j)) otorgar(j, Logro.VOLANDO_BAJITO)
            if (!d.tiene(Logro.PEAJE_INTI) && enSalaDelPortal(j)) otorgar(j, Logro.PEAJE_INTI)
        }
    }

    private fun inventarioLlenoDeBonos(j: ServerPlayer): Boolean {
        val inv = j.inventory
        return (0 until 36).all { i ->
            val s = inv.getItem(i)
            Efectivo.denominacionDe(s) == Denominacion.BONO_10000 && s.count >= Desglose.MAX_POR_STACK
        }
    }

    fun setLegendarioPuesto(j: ServerPlayer): Boolean =
        listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
            .all { Encantamientos.esLegendario(j.getItemBySlot(it)) }

    private fun montaGhastPeruano(j: ServerPlayer): Boolean =
        j.vehicle?.customName?.string == CatalogoOfertas.NOMBRE_GHAST

    private fun enSalaDelPortal(j: ServerPlayer): Boolean {
        if (j.level().dimension() != Level.NETHER) return false
        val p = j.blockPosition()
        // Sala del portal bajo la roca madre (datapack plaza_inca): x 5..17, y 116..121, z 0..10
        return p.x in 5..17 && p.y in 116..121 && p.z in 0..10
    }

    fun anunciar(servidor: MinecraftServer, texto: String) {
        servidor.playerList.broadcastSystemMessage(Component.literal(texto).withStyle(ChatFormatting.GOLD), false)
    }
}
