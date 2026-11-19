package io.github.andrexifor.banco

import io.github.andrexifor.banco.juego.Almacen
import io.github.andrexifor.banco.juego.Comandos
import io.github.andrexifor.banco.juego.Bromas
import io.github.andrexifor.banco.juego.ImanBotin
import io.github.andrexifor.banco.juego.Logros
import io.github.andrexifor.banco.juego.KitsReparacion
import io.github.andrexifor.banco.juego.Npcs
import io.github.andrexifor.banco.juego.Proteccion
import io.github.andrexifor.banco.nucleo.Banco
import io.github.andrexifor.banco.nucleo.Catalogo
import io.github.andrexifor.banco.nucleo.CatalogoLegendarios
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import io.github.andrexifor.banco.nucleo.ConfigHerrero
import io.github.andrexifor.banco.nucleo.ConfigMercadoNegro
import io.github.andrexifor.banco.nucleo.ConfigZonas
import io.github.andrexifor.banco.nucleo.Configuracion
import io.github.andrexifor.banco.nucleo.Formato
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object BancoMinecraftPer : DedicatedServerModInitializer {
    const val MOD_ID = "banco_minecraft_per"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var almacen: Almacen
        private set
    lateinit var config: Configuracion
        private set
    lateinit var banco: Banco
        private set
    lateinit var catalogo: Catalogo
        private set
    lateinit var legendarios: CatalogoLegendarios
        private set
    lateinit var herrero: ConfigHerrero
        private set
    lateinit var zonas: ConfigZonas
        private set
    lateinit var bromas: CatalogoOfertas
        private set
    lateinit var contrabando: CatalogoOfertas
        private set
    lateinit var mercadoNegro: ConfigMercadoNegro
        private set

    override fun onInitializeServer() {
        val loader = FabricLoader.getInstance()
        almacen = Almacen(
            dirConfig = loader.configDir.resolve(MOD_ID),
            dirDatos = loader.gameDir.resolve(MOD_ID),
        )
        config = almacen.cargarConfiguracion()
        banco = almacen.cargarBanco()
        catalogo = almacen.cargarCatalogo()
        legendarios = almacen.cargarLegendarios()
        herrero = almacen.cargarHerrero()
        zonas = almacen.cargarZonas()
        bromas = almacen.cargarBromas()
        contrabando = almacen.cargarContrabando()
        mercadoNegro = almacen.cargarMercadoNegro()
        Npcs.cargar()
        Logros.cargar()
        Npcs.registrarEventos()
        KitsReparacion.registrarEventos()
        ImanBotin.registrarEventos()
        Proteccion.registrarEventos()
        Bromas.registrarEventos()
        Logros.registrarEventos()
        LOGGER.info(
            "Banco Minecraft PER listo: {} cuentas, {} productos en la tienda, {} legendarios, {} zonas protegidas",
            banco.todas().size,
            catalogo.productos.size,
            legendarios.legendarios.size,
            zonas.zonas.orEmpty().count { it.activa },
        )

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> Comandos.registrar(dispatcher) }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val jugador = handler.player
            val nueva = banco.abrirCuenta(jugador.uuid, jugador.name.string, config.saldoInicial)
            guardar()
            if (nueva) {
                almacen.registrar("APERTURA", "-", jugador.name.string, config.saldoInicial)
                jugador.sendSystemMessage(
                    Component.literal(
                        "¡Bienvenido al Banco Minecraft PER! Tu cuenta empieza con " +
                            "${Formato.soles(config.saldoInicial)}. Escribe /banco para ver los comandos.",
                    ).withStyle(ChatFormatting.GOLD),
                )
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            guardar()
            Logros.marcarCambio()
            Logros.guardar()
        }
    }

    fun guardar() {
        try {
            almacen.guardarBanco(banco)
        } catch (e: Exception) {
            LOGGER.error("No se pudo guardar cuentas.json", e)
        }
    }

    fun recargarConfiguracion() {
        config = almacen.cargarConfiguracion()
        catalogo = almacen.cargarCatalogo()
        legendarios = almacen.cargarLegendarios()
        herrero = almacen.cargarHerrero()
        zonas = almacen.cargarZonas()
        bromas = almacen.cargarBromas()
        contrabando = almacen.cargarContrabando()
        mercadoNegro = almacen.cargarMercadoNegro()
    }
}
