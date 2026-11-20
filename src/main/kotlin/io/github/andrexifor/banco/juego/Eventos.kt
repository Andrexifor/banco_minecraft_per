package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.CatalogoOfertas
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId

/**
 * Eventos por fecha (hora de Perú). Mientras dura cada uno:
 *  - la plaza se decora sola (el datapack trae plaza_inca:evento/<id>_poner y _quitar; el mod las corre
 *    al empezar y al terminar; al arrancar el servidor corre evento/limpiar y pone las de los activos);
 *  - cada jugador recibe una vez su regalo del evento al entrar (eventos.txt guarda quién ya lo recibió);
 *  - hay partículas propias alrededor de quien está en la plaza (y fuegos artificiales en Año Nuevo y
 *    Fiestas Patrias, de noche).
 * Inti Raymi además duplica los puntos de la Ofrenda al Inti.
 */
object Eventos {
    private val LIMA = ZoneId.of("America/Lima")

    class Evento(
        val id: String,
        val nombre: String,
        val color: ChatFormatting,
        val desde: MonthDay,
        val hasta: MonthDay,
        val saludo: String,
        val particula: ParticleOptions?,
        val fuegos: List<Int> = emptyList(),
        val regalos: () -> List<String>,
    ) {
        fun dentro(d: LocalDate): Boolean {
            val md = MonthDay.from(d)
            return if (!desde.isAfter(hasta)) !md.isBefore(desde) && !md.isAfter(hasta) else !md.isBefore(desde) || !md.isAfter(hasta)
        }

        /** Clave del año del evento (Año Nuevo cuenta desde diciembre). */
        fun clave(d: LocalDate): String {
            val anio = if (desde.isAfter(hasta) && MonthDay.from(d).isBefore(desde)) d.year - 1 else d.year
            return "$id-$anio"
        }
    }

    private fun nombre(t: String, color: String) = "custom_name={text:\"$t\",color:\"$color\",italic:false}"
    private fun lore(vararg l: String) = "lore=[" + l.joinToString(",") { "{text:\"$it\",color:\"gray\",italic:false}" } + "]"
    private fun cohetes(vararg colores: Int) =
        "minecraft:firework_rocket[fireworks={flight_duration:2,explosions:[{shape:\"large_ball\",has_twinkle:true," +
            "colors:[I;${colores.joinToString(",")}]}]}]"

    val EVENTOS = listOf(
        Evento(
            "navidad", "Navidad", ChatFormatting.RED, MonthDay.of(12, 20), MonthDay.of(12, 26),
            "¡Feliz Navidad! La plaza tiene su árbol y el Kuraka te dejó un regalo.", ParticleTypes.SNOWFLAKE,
        ) {
            listOf(
                Sombreros.item("chullo_navideno"),
                "minecraft:bread[item_model=\"banco_minecraft_per:paneton\",${nombre("Panetón", "gold")}," +
                    "${lore("Con su chocolate caliente", "Navidad en la plaza")}]",
            )
        },
        Evento(
            "anio_nuevo", "Año Nuevo", ChatFormatting.GOLD, MonthDay.of(12, 31), MonthDay.of(1, 2),
            "¡Feliz Año Nuevo! Que no falten las uvas de la suerte.", ParticleTypes.FIREWORK, listOf(0xFFD23F, 0xFFFFFF),
        ) {
            listOf(
                Sombreros.item("sombrero_fiesta"),
                "minecraft:sweet_berries[${nombre("Uvas de la suerte", "light_purple")},${lore("Doce, una por cada campanada")}," +
                    "enchantment_glint_override=true]",
                cohetes(0xFFD23F, 0xFFFFFF),
            )
        },
        Evento(
            "san_valentin", "San Valentín", ChatFormatting.LIGHT_PURPLE, MonthDay.of(2, 13), MonthDay.of(2, 15),
            "¡Feliz San Valentín! Regala una rosa a quien quieras.", ParticleTypes.HEART,
        ) {
            listOf(
                "minecraft:poppy[${nombre("Rosa del Inti", "red")},${lore("Para regalar")},enchantment_glint_override=true]",
                "minecraft:cookie[${nombre("Chocolate cusqueño", "gold")},${lore("Hecho con cacao de la selva")}]",
            )
        },
        Evento(
            "inti_raymi", "Inti Raymi", ChatFormatting.GOLD, MonthDay.of(6, 20), MonthDay.of(6, 25),
            "¡Inti Raymi, la fiesta del Sol! Las ofrendas al Inti valen el doble.", ParticleTypes.END_ROD,
        ) {
            listOf("minecraft:honey_bottle[${nombre("Chicha de jora", "gold")},${lore("Para brindar con el Inti")}]")
        },
        Evento(
            "fiestas_patrias", "Fiestas Patrias", ChatFormatting.RED, MonthDay.of(7, 27), MonthDay.of(7, 30),
            "¡Felices Fiestas Patrias! ¡Viva el Perú!", null, listOf(0xD7263D, 0xFFFFFF),
        ) {
            listOf(Sombreros.item("sombrero_patrio"), cohetes(0xD7263D, 0xFFFFFF))
        },
        Evento(
            "pachamama", "Día de la Pachamama", ChatFormatting.DARK_GREEN, MonthDay.of(8, 1), MonthDay.of(8, 3),
            "Hoy se le agradece a la Pachamama: la tierra te devuelve lo que siembras.", ParticleTypes.HAPPY_VILLAGER,
        ) {
            listOf(
                "minecraft:bone_meal[${nombre("Abono de la Pachamama", "green")},${lore("La tierra agradece tu ofrenda")}]",
                "minecraft:golden_carrot[${nombre("Ofrenda de la chacra", "gold")}]",
            )
        },
        Evento(
            "mes_cuy", "Mes del Cuy", ChatFormatting.GOLD, MonthDay.of(10, 1), MonthDay.of(10, 31),
            "¡Es el mes del cuy! Llévate uno de regalo para que te acompañe.", null,
        ) {
            listOf(CatalogoOfertas.MASCOTA_CUY)
        },
        Evento(
            "cancion_criolla", "Día de la Canción Criolla", ChatFormatting.YELLOW, MonthDay.of(10, 30), MonthDay.of(11, 1),
            "¡Día de la Canción Criolla! Que suene el cajón.", ParticleTypes.NOTE,
        ) {
            listOf(
                "minecraft:note_block[${nombre("Cajón peruano", "gold")},${lore("Patrimonio cultural del Perú")}]",
                "minecraft:music_disc_cat[${nombre("Disco criollo", "yellow")},${lore("Para la jarana")}]",
            )
        },
    )

    /** cantidades de cada regalo (por posición): por defecto 1. */
    private val CANTIDADES = mapOf("navidad" to listOf(1, 3), "anio_nuevo" to listOf(1, 12, 16), "san_valentin" to listOf(1, 8),
        "inti_raymi" to listOf(3), "fiestas_patrias" to listOf(1, 16), "pachamama" to listOf(32, 8),
        "cancion_criolla" to listOf(1, 1), "mes_cuy" to listOf(1))

    private var activos: Set<String> = emptySet()
    private val recibidos = HashSet<String>()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("eventos.txt")

    fun activo(id: String) = id in activos
    private fun hoy(): LocalDate = LocalDate.now(LIMA)

    private fun cargar() {
        try {
            if (Files.exists(archivo)) recibidos += Files.readAllLines(archivo).map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer eventos.txt: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.write(archivo, recibidos.toList())
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar eventos.txt: {}", e.message)
        }
    }

    fun registrarEventos() {
        cargar()
        ServerLifecycleEvents.SERVER_STARTED.register { servidor ->
            activos = EVENTOS.filter { it.dentro(hoy()) }.map { it.id }.toSet()
            // borra cualquier decoración vieja y pone la de los eventos de hoy (después de borrar)
            comando(servidor, "function plaza_inca:evento/limpiar")
            for (e in EVENTOS) if (e.id in activos) funcion(servidor, e.id, true)
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, servidor ->
            val j = handler.player
            servidor.execute { for (e in EVENTOS) if (e.id in activos) bienvenida(j, e) }
        }
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 1200 == 0) revisar(servidor)
            if (servidor.tickCount % 20 == 0 && activos.isNotEmpty()) ambiente(servidor)
        }
    }

    private fun revisar(servidor: MinecraftServer) {
        val ahora = EVENTOS.filter { it.dentro(hoy()) }.map { it.id }.toSet()
        if (ahora == activos) return
        val empiezan = ahora - activos
        val terminan = activos - ahora
        activos = ahora
        for (e in EVENTOS) {
            if (e.id in terminan) funcion(servidor, e.id, false)
            if (e.id in empiezan) {
                funcion(servidor, e.id, true)
                Logros.anunciar(servidor, "Empieza ${e.nombre}: ${e.saludo}")
                for (j in servidor.playerList.players) bienvenida(j, e)
            }
        }
    }

    private fun funcion(servidor: MinecraftServer, id: String, poner: Boolean) =
        comando(servidor, "function plaza_inca:evento/${id}_${if (poner) "poner" else "quitar"}")

    private fun comando(servidor: MinecraftServer, cmd: String) {
        servidor.commands.performPrefixedCommand(servidor.createCommandSourceStack().withSuppressedOutput(), cmd)
    }

    private fun bienvenida(j: ServerPlayer, e: Evento) {
        j.sendSystemMessage(Component.literal("✦ ${e.nombre}: ${e.saludo}").withStyle(e.color))
        val clave = "${j.uuid} ${e.clave(hoy())}"
        if (clave in recibidos) return
        val servidor = j.level().server ?: return
        val cantidades = CANTIDADES[e.id].orEmpty()
        var dio = false
        e.regalos().forEachIndexed { i, texto ->
            val s = GuiOfertas.crearTexto(servidor, texto) ?: return@forEachIndexed
            s.count = cantidades.getOrElse(i) { 1 }.coerceAtMost(s.maxStackSize)
            Inventarios.dar(j, s)
            dio = true
        }
        if (!dio) return
        recibidos += clave
        guardar()
        j.sendSystemMessage(Component.literal("Recibiste tu regalo de ${e.nombre}.").withStyle(ChatFormatting.GREEN))
    }

    /** Partículas del evento alrededor de quien está cerca de la plaza (el punto de aparición). */
    private fun ambiente(servidor: MinecraftServer) {
        val nivel = servidor.overworld()
        val centro = nivel.respawnData.pos()
        val noche = nivel.isDarkOutside
        for (e in EVENTOS) {
            if (e.id !in activos) continue
            val cerca = nivel.players().filter { it.blockPosition().distSqr(centro) < 48.0 * 48.0 }
            if (cerca.isEmpty()) continue
            for (j in cerca) {
                e.particula?.let { nivel.sendParticles(it, j.x, j.y + 4, j.z, 12, 8.0, 3.0, 8.0, 0.01) }
            }
            if (e.fuegos.isNotEmpty() && noche && servidor.tickCount % 200 == 0) {
                val x = centro.x + nivel.random.nextInt(31) - 15
                val z = centro.z + nivel.random.nextInt(31) - 15
                val colores = e.fuegos.joinToString(",")
                servidor.commands.performPrefixedCommand(
                    servidor.createCommandSourceStack().withLevel(nivel).withSuppressedOutput(),
                    "summon minecraft:firework_rocket $x ${centro.y + 2} $z {LifeTime:35,FireworksItem:{id:\"minecraft:firework_rocket\"," +
                        "count:1,components:{\"minecraft:fireworks\":{flight_duration:2,explosions:[{shape:\"large_ball\"," +
                        "has_twinkle:true,colors:[I;$colores]}]}}}}",
                )
            }
        }
    }
}
