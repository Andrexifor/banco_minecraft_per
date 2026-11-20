package io.github.andrexifor.banco.juego

import com.google.gson.GsonBuilder
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.juego.GuiUtil.texto
import io.github.andrexifor.banco.nucleo.Encargo
import io.github.andrexifor.banco.nucleo.Encargos
import io.github.andrexifor.banco.nucleo.Formato
import io.github.andrexifor.banco.nucleo.TipoEncargo
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.stats.Stats
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Kuraka del ayllu: reparte encargos. Cada día, a las 12 del mediodía y a las 12 de la noche (hora de
 * Perú), salen 10 encargos nuevos (5 fáciles, 3 medios, 2 difíciles), elegidos al azar de la lista grande
 * de Encargos. Son los mismos para todos; cada jugador los cumple y cobra por su cuenta. Lo que se mide
 * con estadísticas cuenta desde que empieza la tanda (o desde que entras, si llegas después).
 * El Kuraka también vende la maqueta del tambo (viajes).
 */
object Kuraka {
    private val LIMA = ZoneId.of("America/Lima")
    const val PRECIO_TAMBO = 10_000_000L

    const val MAQUETA_TAMBO = "minecraft:paper[item_model=\"banco_minecraft_per:maqueta_tambo\",max_stack_size=1," +
        "custom_name={text:\"Maqueta: Tambo\",color:\"gold\",italic:false}," +
        "lore=[{text:\"Posada del Qhapaq Ñan: viaja entre tus tambos\",color:\"gray\",italic:false}," +
        "{text:\"y al tambo de la plaza (S/ 1,000 por viaje)\",color:\"gray\",italic:false}," +
        "{text:\"Clic derecho sobre el suelo: se arma (3x3)\",color:\"gray\",italic:false}," +
        "{text:\"Shift + clic derecho al centro, mano vacía: se desarma\",color:\"gray\",italic:false}]," +
        "custom_data={banco_minecraft_per:{maqueta:\"tambo\"}}]"

    data class EstadoJugador(
        var ciclo: String = "",
        var base: MutableMap<String, Int> = mutableMapOf(),
        var hechos: MutableList<String> = mutableListOf(),
        var avisados: MutableList<String> = mutableListOf(),
    )

    private data class Archivo(var jugadores: MutableMap<String, EstadoJugador> = mutableMapOf())

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("encargos.json")
    private var datos = Archivo()
    private var cambios = false

    private fun cargar() {
        try {
            if (Files.exists(archivo)) {
                datos = gson.fromJson(Files.readString(archivo, StandardCharsets.UTF_8), Archivo::class.java) ?: Archivo()
            }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer encargos.json: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.writeString(archivo, gson.toJson(datos), StandardCharsets.UTF_8)
            cambios = false
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar encargos.json: {}", e.message)
        }
    }

    // ---------- tandas ----------
    private fun ahora() = ZonedDateTime.now(LIMA)
    private fun esTarde(t: ZonedDateTime) = t.hour >= 12
    private fun cicloId(t: ZonedDateTime) = "${t.toLocalDate()}-${if (esTarde(t)) "T" else "M"}"

    private var cacheCiclo = ""
    private var cacheLista: List<Encargo> = emptyList()

    fun actuales(): List<Encargo> {
        val t = ahora()
        val id = cicloId(t)
        if (id != cacheCiclo) {
            cacheLista = Encargos.delCiclo(t.toLocalDate(), esTarde(t))
            cacheCiclo = id
        }
        return cacheLista
    }

    private fun proximaRenovacion(): Duration {
        val t = ahora()
        val siguiente = if (esTarde(t)) t.toLocalDate().plusDays(1).atStartOfDay(LIMA) else t.toLocalDate().atTime(12, 0).atZone(LIMA)
        return Duration.between(t, siguiente)
    }

    // ---------- progreso ----------
    private fun valor(j: ServerPlayer, e: Encargo): Int {
        var total = 0
        for (o in e.objetivos) {
            val id = Identifier.tryParse(o) ?: continue
            total += when (e.tipo) {
                TipoEncargo.MATAR -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map { j.stats.getValue(Stats.ENTITY_KILLED, it) }.orElse(0)
                TipoEncargo.PICAR -> BuiltInRegistries.BLOCK.getOptional(id).map { j.stats.getValue(Stats.BLOCK_MINED, it) }.orElse(0)
                TipoEncargo.FABRICAR -> BuiltInRegistries.ITEM.getOptional(id).map { j.stats.getValue(Stats.ITEM_CRAFTED, it) }.orElse(0)
                TipoEncargo.ESTADISTICA -> j.stats.getValue(Stats.CUSTOM, id)
                TipoEncargo.ENTREGAR -> contarEnInventario(j, id)
            }
        }
        return total
    }

    /** Ítems normales (sin marcas del banco) de ese tipo en el inventario. */
    private fun contarEnInventario(j: ServerPlayer, id: Identifier): Int {
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return 0
        val inv = j.inventory
        var n = 0
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (s.`is`(item) && s.get(DataComponents.CUSTOM_DATA) == null) n += s.count
        }
        return n
    }

    private fun quitarDelInventario(j: ServerPlayer, id: Identifier, cuantos: Int) {
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return
        val inv = j.inventory
        var falta = cuantos
        for (i in 0 until inv.containerSize) {
            if (falta <= 0) break
            val s = inv.getItem(i)
            if (s.`is`(item) && s.get(DataComponents.CUSTOM_DATA) == null) {
                val q = minOf(falta, s.count)
                s.shrink(q)
                falta -= q
            }
        }
        inv.setChanged()
    }

    /** Estado del jugador en la tanda actual (si cambió la tanda, empieza de cero). */
    private fun estado(j: ServerPlayer): EstadoJugador {
        val id = cicloId(ahora())
        val e = datos.jugadores.getOrPut(j.uuid.toString()) { EstadoJugador() }
        if (e.ciclo != id) {
            e.ciclo = id
            e.hechos.clear()
            e.avisados.clear()
            e.base.clear()
            for (enc in actuales()) if (enc.tipo != TipoEncargo.ENTREGAR) e.base[enc.id] = valor(j, enc)
            cambios = true
        }
        return e
    }

    fun progreso(j: ServerPlayer, enc: Encargo): Int {
        val e = estado(j)
        val v = valor(j, enc)
        return if (enc.tipo == TipoEncargo.ENTREGAR) v else (v - (e.base[enc.id] ?: v)).coerceAtLeast(0)
    }

    fun registrarEventos() {
        cargar()
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 100 == 0) revisar(servidor)
            if (cambios && servidor.tickCount % 1200 == 0) guardar()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { _ -> guardar() }
    }

    /** Cada 5 s: avisa a quien terminó un encargo (los de entregar se avisan al tener todo). */
    private fun revisar(servidor: MinecraftServer) {
        for (j in servidor.playerList.players) {
            val e = estado(j)
            for (enc in actuales()) {
                if (enc.id in e.hechos || enc.id in e.avisados) continue
                if (progreso(j, enc) >= enc.cantidad) {
                    e.avisados += enc.id
                    cambios = true
                    j.sendSystemMessage(
                        Component.literal("¡Encargo listo: ${enc.texto}! Ve donde el Kuraka a cobrar.").withStyle(ChatFormatting.GOLD),
                    )
                    Efectos.sonar(j, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 1.4f)
                }
            }
        }
    }

    // ---------- menú ----------
    private val RANURAS = intArrayOf(11, 12, 13, 14, 15, 19, 20, 21, 23, 24)
    private val COLOR = arrayOf(ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.RED)

    private fun icono(enc: Encargo): ItemStack {
        val id = Identifier.tryParse(enc.icono)
        val item = id?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) } ?: Items.PAPER
        return ItemStack(item)
    }

    fun abrir(j: ServerPlayer) {
        val servidor = j.level().server ?: return
        val e = estado(j)
        val gui = SimpleGui(MenuType.GENERIC_9x4, j, false)
        gui.setTitle(texto("Encargos del Kuraka", ChatFormatting.DARK_GREEN))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        val falta = proximaRenovacion()
        gui.setSlot(
            4,
            GuiElementBuilder(Items.CLOCK).setName(texto("Encargos de hoy", ChatFormatting.GOLD)).setLore(
                listOf(
                    texto("Se renuevan a las 12:00 y a las 00:00 (hora de Perú)", ChatFormatting.GRAY),
                    texto("Próxima tanda en ${falta.toHours()} h ${falta.toMinutesPart()} min", ChatFormatting.YELLOW),
                    texto("Son los mismos para todos; cada uno cobra los suyos", ChatFormatting.GRAY),
                ),
            ),
        )
        gui.setSlot(0, GuiElementBuilder(Items.GOLD_INGOT).setName(texto("Tu saldo: ${Formato.soles(Operaciones.saldo(j))}", ChatFormatting.GOLD)))
        actuales().forEachIndexed { i, enc ->
            if (i >= RANURAS.size) return@forEachIndexed
            val hecho = enc.id in e.hechos
            val prog = progreso(j, enc).coerceAtMost(enc.cantidad)
            val listo = prog >= enc.cantidad
            val pago = buildString {
                append(Formato.soles(enc.pago))
                if (enc.tumbaga > 0) append(" + ${enc.tumbaga} tumbaga")
            }
            val mostrar = { v: Int -> if (enc.divisor > 1) String.format("%.1f", v.toDouble() / enc.divisor) else v.toString() }
            val lore = mutableListOf(
                texto("Nivel: ${Encargos.nombreNivel(enc.nivel)}", COLOR[enc.nivel]),
                texto("Progreso: ${mostrar(prog)} / ${mostrar(enc.cantidad)}", ChatFormatting.GRAY),
                texto("Pago: $pago", ChatFormatting.YELLOW),
                when {
                    hecho -> texto("✔ Cobrado", ChatFormatting.DARK_GREEN)
                    enc.tipo == TipoEncargo.ENTREGAR && listo -> texto("Clic para entregar y cobrar", ChatFormatting.GREEN)
                    enc.tipo == TipoEncargo.ENTREGAR -> texto("Junta los ítems y vuelve", ChatFormatting.DARK_GRAY)
                    listo -> texto("Clic para cobrar", ChatFormatting.GREEN)
                    else -> texto("En progreso…", ChatFormatting.DARK_GRAY)
                },
            )
            val b = GuiElementBuilder(if (hecho) ItemStack(Items.DYE.lime()) else icono(enc))
                .setName(texto(enc.texto, if (hecho) ChatFormatting.DARK_GREEN else COLOR[enc.nivel]))
                .setLore(lore)
            if (listo && !hecho) b.glow()
            b.setCallback(Runnable { cobrar(j, enc); abrir(j) })
            gui.setSlot(RANURAS[i], b)
        }
        gui.setSlot(
            27,
            GuiElementBuilder(Items.BOOK).setName(texto("¿Cómo funciona?", ChatFormatting.AQUA)).setLore(
                listOf(
                    "Fáciles: ${Formato.soles(Encargo.PAGOS[0])} · Medios: ${Formato.soles(Encargo.PAGOS[1])}",
                    "Difíciles: ${Formato.soles(Encargo.PAGOS[2])} y tumbaga",
                    "Lo que se mide (matar, picar, fabricar, pescar…)",
                    "cuenta desde que empezó la tanda o desde que entraste.",
                    "Los de entregar: lleva los ítems y haz clic.",
                ).map { texto(it, ChatFormatting.GRAY) },
            ),
        )
        val maqueta = GuiOfertas.crearTexto(servidor, MAQUETA_TAMBO)
        if (maqueta != null) {
            val total = Herrero.conImpuesto(PRECIO_TAMBO)
            val b = GuiElementBuilder(maqueta.copy()).setLore(
                listOf(
                    texto("Un tambo tuyo: viaja entre tus tambos", ChatFormatting.GRAY),
                    texto("y al tambo de la plaza", ChatFormatting.GRAY),
                    texto("Cada viaje: ${Formato.soles(Tambos.PRECIO_VIAJE)}", ChatFormatting.GRAY),
                    texto("Precio: ${Formato.soles(PRECIO_TAMBO)} (con impuesto ${Formato.soles(total)})", ChatFormatting.YELLOW),
                    texto("Clic para comprar", ChatFormatting.YELLOW),
                ),
            )
            b.setCallback(
                Runnable {
                    GuiUtil.confirmar(
                        j, "¿Comprar un tambo?", maqueta.copy(),
                        listOf("Total: ${Formato.soles(total)}", "Saldo: ${Formato.soles(Operaciones.saldo(j))}"),
                        alConfirmar = { GuiUtil.avisar(j, comprarTambo(j)); abrir(j) },
                        alCancelar = { abrir(j) },
                    )
                },
            )
            gui.setSlot(31, b)
        }
        gui.open()
    }

    private fun comprarTambo(j: ServerPlayer): Respuesta {
        val servidor = j.level().server ?: return Respuesta(false, "Servidor no disponible")
        val maqueta = GuiOfertas.crearTexto(servidor, MAQUETA_TAMBO) ?: return Respuesta(false, "La maqueta está mal configurada; avisa a un admin")
        if (!Operaciones.cabe(j, maqueta, 1)) return Respuesta(false, "No tienes espacio en el inventario")
        val impuesto = Herrero.conImpuesto(PRECIO_TAMBO) - PRECIO_TAMBO
        val cobro = Operaciones.cobrar(j, PRECIO_TAMBO, "TAMBO", impuesto)
        if (!cobro.ok) return cobro
        Inventarios.dar(j, maqueta)
        return Respuesta(true, "¡La maqueta del tambo es tuya! ${cobro.mensaje}")
    }

    private fun cobrar(j: ServerPlayer, enc: Encargo) {
        val servidor = j.level().server ?: return
        val e = estado(j)
        if (enc.id in e.hechos || enc !in actuales()) return
        val prog = progreso(j, enc)
        if (prog < enc.cantidad) {
            GuiUtil.avisar(j, Respuesta(false, if (enc.tipo == TipoEncargo.ENTREGAR) "Te faltan ${enc.cantidad - prog}." else "Aún no terminas este encargo."))
            return
        }
        if (enc.tipo == TipoEncargo.ENTREGAR) quitarDelInventario(j, Identifier.parse(enc.objetivos.first()), enc.cantidad)
        e.hechos += enc.id
        cambios = true
        val r = Operaciones.abonar(j, enc.pago, "ENCARGO ${enc.id}")
        if (enc.tumbaga > 0) {
            val t = GuiOfertas.crearTexto(servidor, Maquinas.CATALOGO_TUMBAGA)
            if (t != null) { t.count = enc.tumbaga; Inventarios.dar(j, t) }
        }
        Efectos.sonar(j, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f)
        GuiUtil.avisar(j, Respuesta(true, "Encargo cumplido: ${enc.texto}. ${r.mensaje}" + if (enc.tumbaga > 0) " (+${enc.tumbaga} tumbaga)" else ""))
        guardar()
    }
}
