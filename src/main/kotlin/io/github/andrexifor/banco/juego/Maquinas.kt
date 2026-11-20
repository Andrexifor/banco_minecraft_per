package io.github.andrexifor.banco.juego

import com.google.gson.GsonBuilder
import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

/**
 * Máquinas del Pirqakamayuq. No son bloques nuevos (eso exigiría un mod en el cliente): son estructuras
 * de bloques vanilla de 3x3 que se arman con una maqueta y que el mod hace funcionar.
 *  - Cantera inca: el bloque del centro vuelve a salir al minarlo (10 s; con cinceles hasta 1 s).
 *    Vetas I: carbón · II: carbón, hierro, cobre y tumbaga · III: hierro, cobre y más tumbaga.
 *    Brazo de la cantera (automática): pica sola cada 5 s y deja todo en un cofre vecino; si se llena, para.
 *  - Fuente del Misti: llena su caldero de lava cada 5 min (2,5 con el Corazón del Misti). Si hay un tanque
 *    del mismo dueño a 8 bloques o menos, la lava va al tanque.
 *  - Tanque de vidrio y acero: guarda lava (16 baldes; el Aro de acero lo agranda a 32, 48, 80, 160, 400
 *    y 800). Por dentro se ve el nivel. Clic con balde vacío saca lava; con balde de lava, la guarda.
 *  - Huayrachina (horno inca): funde sin carbón tomando lava de un tanque cercano del mismo dueño.
 * Solo el dueño las usa; nadie las rompe (ni explosiones). El dueño las desarma con Shift + clic derecho
 * al bloque del centro con la mano vacía: recupera la maqueta con sus mejoras (y la lava del tanque).
 */
object Maquinas {
    private const val MARCA = BancoMinecraftPer.MOD_ID
    val TIEMPOS_CANTERA = intArrayOf(10, 7, 5, 3, 1)
    val CAPACIDADES = intArrayOf(16, 32, 48, 80, 160, 400, 800)
    private const val CERCA = 8

    data class Maquina(
        var tipo: String = "",
        var dim: String = "minecraft:overworld",
        var x: Int = 0, var y: Int = 0, var z: Int = 0,
        var dueno: String = "",
        var vel: Int = 0,
        var vetas: Int = 0,
        var auto: Boolean = false,
        var corazon: Boolean = false,
        var nivelTanque: Int = 0,
        var lava: Int = 0,
        var falta: Int = 0,
        var faltaAuto: Int = 5,
        var bloque: String = "piedra",
        var facing: String = "south",
    ) {
        fun origen() = BlockPos(x, y, z)
        fun nucleo() = BlockPos(x, y + 1, z)
        fun alto() = if (tipo == "tanque") 5 else 4
        fun contiene(dim2: String, p: BlockPos) =
            dim2 == dim && p.x in x - 1..x + 1 && p.z in z - 1..z + 1 && p.y in y until y + alto()
    }

    private data class Archivo(var maquinas: MutableList<Maquina> = mutableListOf())

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(MARCA).resolve("maquinas.json")
    private var lista = mutableListOf<Maquina>()
    private var cambios = false

    private fun cargar() {
        try {
            if (Files.exists(archivo)) {
                lista = (gson.fromJson(Files.readString(archivo, StandardCharsets.UTF_8), Archivo::class.java)?.maquinas ?: mutableListOf())
            }
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer maquinas.json: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.writeString(archivo, gson.toJson(Archivo(lista)), StandardCharsets.UTF_8)
            cambios = false
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar maquinas.json: {}", e.message)
        }
    }

    private fun dim(l: Level) = l.dimension().identifier().toString()
    private fun en(l: Level, p: BlockPos) = lista.firstOrNull { it.contiene(dim(l), p) }

    /** Los tambos de un jugador, en el orden en que los armó. */
    fun tambosDe(dueno: String): List<Maquina> = lista.filter { it.tipo == "tambo" && it.dueno == dueno }

    /** Para las explosiones: los bloques de una máquina no se rompen. */
    fun esBloque(l: Level, p: BlockPos): Boolean = en(l, p) != null

    private fun datos(s: ItemStack): CompoundTag? =
        s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty(MARCA)?.takeIf { !it.isEmpty }

    private fun aviso(j: ServerPlayer, t: String, c: ChatFormatting = ChatFormatting.GOLD) =
        j.sendSystemMessage(Component.literal(t).withStyle(c))

    // ---------- estructuras ----------
    private fun piezas(m: Maquina): List<Pair<BlockPos, BlockState>> {
        val o = m.origen()
        val r = ArrayList<Pair<BlockPos, BlockState>>()
        fun put(dx: Int, dy: Int, dz: Int, s: BlockState) { r += o.offset(dx, dy, dz) to s }
        fun capa(dy: Int, centro: BlockState?, borde: BlockState?, esquina: BlockState?) {
            for (dx in -1..1) for (dz in -1..1) {
                val s = when {
                    dx == 0 && dz == 0 -> centro
                    dx != 0 && dz != 0 -> esquina
                    else -> borde
                } ?: continue
                put(dx, dy, dz, s)
            }
        }
        when (m.tipo) {
            "cantera" -> {
                val base = Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                val pilar = Blocks.STONE_BRICK_WALL.defaultBlockState()
                capa(0, base, base, base)
                capa(1, bloqueCantera(m.bloque), null, pilar)
                capa(2, null, null, pilar)
                capa(3, null, Blocks.STONE_BRICK_SLAB.defaultBlockState(), base)
            }
            "fuente" -> {
                capa(0, Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState())
                capa(1, Blocks.CAULDRON.defaultBlockState(), null, Blocks.BASALT.defaultBlockState())
                capa(2, null, null, Blocks.BASALT.defaultBlockState())
                capa(3, null, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState())
            }
            "tanque" -> {
                val hierro = Blocks.IRON_BLOCK.defaultBlockState()
                capa(0, hierro, hierro, hierro)
                for (dy in 1..3) capa(dy, Blocks.AIR.defaultBlockState(), Blocks.GLASS.defaultBlockState(), hierro)
                capa(4, hierro, hierro, hierro)
            }
            "horno" -> {
                val ladrillo = Blocks.STONE_BRICKS.defaultBlockState()
                capa(0, ladrillo, ladrillo, ladrillo)
                val dir = runCatching { Direction.valueOf(m.facing.uppercase()) }.getOrDefault(Direction.SOUTH)
                capa(1, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, dir), null, Blocks.STONE_BRICK_WALL.defaultBlockState())
                capa(2, Blocks.CAMPFIRE.defaultBlockState(), null, Blocks.STONE_BRICK_WALL.defaultBlockState())
                capa(3, null, null, Blocks.STONE_BRICK_SLAB.defaultBlockState())
            }
            "tambo" -> {
                // posada del Qhapaq Ñan: piso de andesita, piedra imán al centro, pilares de adobe y techo de paja
                val adobe = Blocks.MUD_BRICK_WALL.defaultBlockState()
                capa(0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState())
                capa(1, Blocks.LODESTONE.defaultBlockState(), null, adobe)
                capa(2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), null, adobe)
                capa(3, Blocks.HAY_BLOCK.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState())
            }
        }
        return r
    }

    private fun bloqueCantera(b: String): BlockState = when (b) {
        "carbon" -> Blocks.COAL_ORE
        "hierro" -> Blocks.IRON_ORE
        "cobre" -> Blocks.COPPER_ORE
        "tumbaga" -> Blocks.RAW_GOLD_BLOCK
        "nada" -> Blocks.AIR
        else -> Blocks.COBBLESTONE
    }.defaultBlockState()

    /** Qué sale de la cantera según sus vetas. */
    private fun sorteo(vetas: Int): String {
        val r = Random.nextDouble()
        return when (vetas) {
            1 -> if (r < 0.12) "carbon" else "piedra"
            2 -> when { r < 0.02 -> "tumbaga"; r < 0.10 -> "hierro"; r < 0.18 -> "cobre"; r < 0.28 -> "carbon"; else -> "piedra" }
            3 -> when { r < 0.06 -> "tumbaga"; r < 0.18 -> "hierro"; r < 0.30 -> "cobre"; else -> "piedra" }
            else -> "piedra"
        }
    }

    private fun itemDe(b: String, servidor: MinecraftServer): ItemStack = when (b) {
        "carbon" -> ItemStack(Items.COAL)
        "hierro" -> ItemStack(Items.RAW_IRON)
        "cobre" -> ItemStack(Items.RAW_COPPER)
        "tumbaga" -> tumbaga(servidor)
        else -> ItemStack(Items.COBBLESTONE)
    }

    private fun tumbaga(servidor: MinecraftServer): ItemStack =
        GuiOfertas.crearTexto(servidor, CATALOGO_TUMBAGA) ?: ItemStack(Items.ECHO_SHARD)

    /** Precio al que el Guardián del Oro Estelar compra cada tumbaga. */
    const val PRECIO_TUMBAGA = 50_000L

    fun esTumbaga(s: ItemStack): Boolean = !s.isEmpty && datos(s)?.contains("tumbaga") == true

    /** Vende al banco toda la tumbaga del inventario. */
    fun venderTumbaga(j: ServerPlayer): Respuesta {
        val inv = j.inventory
        var n = 0
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (esTumbaga(s)) { n += s.count; inv.setItem(i, ItemStack.EMPTY) }
        }
        if (n == 0) return Respuesta(false, "No llevas tumbaga")
        val r = Operaciones.abonar(j, n * PRECIO_TUMBAGA, "TUMBAGA x$n")
        return Respuesta(r.ok, "Vendiste $n tumbaga. ${r.mensaje}")
    }

    const val CATALOGO_TUMBAGA = "minecraft:echo_shard[item_model=\"banco_minecraft_per:tumbaga\"," +
        "custom_name={text:\"Tumbaga\",color:\"gold\",italic:false}," +
        "lore=[{text:\"Aleación sagrada de oro y cobre\",color:\"gray\",italic:false}," +
        "{text:\"Sale de las canteras incas con vetas\",color:\"gray\",italic:false}]," +
        "custom_data={banco_minecraft_per:{tumbaga:1b}}]"

    private fun pintarTanque(nivel: ServerLevel, m: Maquina) {
        val cap = CAPACIDADES[m.nivelTanque.coerceIn(0, CAPACIDADES.size - 1)]
        val llenos = if (m.lava <= 0) 0 else ((m.lava * 3 + cap - 1) / cap).coerceIn(1, 3)
        for (dy in 1..3) {
            val p = m.origen().offset(0, dy, 0)
            val s = if (dy <= llenos) Blocks.LAVA.defaultBlockState() else Blocks.AIR.defaultBlockState()
            if (nivel.getBlockState(p) != s) nivel.setBlock(p, s, 3)
        }
    }

    // ---------- eventos ----------
    fun registrarEventos() {
        cargar()
        UseBlockCallback.EVENT.register { jugador, nivel, mano, golpe ->
            if (jugador !is ServerPlayer || nivel !is ServerLevel) return@register InteractionResult.PASS
            val s = jugador.getItemInHand(mano)
            val d = datos(s)
            // 1) colocar una maqueta
            val maqueta = d?.getStringOr("maqueta", "")?.ifBlank { null }
            if (maqueta != null) {
                if (mano == InteractionHand.MAIN_HAND) colocar(jugador, nivel, s, maqueta, d, golpe.blockPos.relative(golpe.direction))
                return@register InteractionResult.SUCCESS
            }
            val m = en(nivel, golpe.blockPos) ?: return@register InteractionResult.PASS
            if (m.dueno != jugador.uuid.toString()) {
                aviso(jugador, if (m.tipo == "tambo") "Este tambo es de otro jugador." else "Esta máquina es de otro jugador.", ChatFormatting.RED)
                return@register InteractionResult.FAIL
            }
            if (mano != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            // 2) desarmar
            if (jugador.isShiftKeyDown && s.isEmpty && golpe.blockPos == m.nucleo()) {
                desarmar(jugador, nivel, m); return@register InteractionResult.SUCCESS
            }
            // tambo: menú de viajes
            if (m.tipo == "tambo") { Tambos.abrir(jugador, m); return@register InteractionResult.SUCCESS }
            // 3) mejoras
            val mejora = d?.getStringOr("mejora_maquina", "")?.ifBlank { null }
            if (mejora != null) { mejorar(jugador, nivel, m, s, mejora); return@register InteractionResult.SUCCESS }
            // 4) tanque: sacar o guardar lava
            if (m.tipo == "tanque") { tanque(jugador, nivel, m, s, mano); return@register InteractionResult.SUCCESS }
            InteractionResult.PASS
        }
        PlayerBlockBreakEvents.BEFORE.register { nivel, jugador, pos, _, _ ->
            val m = en(nivel, pos) ?: return@register true
            if (jugador !is ServerPlayer || nivel !is ServerLevel) return@register false
            if (m.tipo == "cantera" && pos == m.nucleo() && m.dueno == jugador.uuid.toString()) {
                if (m.bloque == "tumbaga") { // la tumbaga se entrega como ítem propio
                    nivel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
                    nivel.addFreshEntity(ItemEntity(nivel, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, tumbaga(nivel.server)))
                    m.bloque = "nada"; m.falta = TIEMPOS_CANTERA[m.vel]; cambios = true
                    return@register false
                }
                return@register true
            }
            aviso(jugador, if (m.dueno == jugador.uuid.toString()) "Para quitarla: Shift + clic derecho al centro con la mano vacía." else "Esta máquina es de otro jugador.", ChatFormatting.RED)
            false
        }
        PlayerBlockBreakEvents.AFTER.register { nivel, _, pos, _, _ ->
            val m = en(nivel, pos) ?: return@register
            if (m.tipo == "cantera" && pos == m.nucleo()) { m.bloque = "nada"; m.falta = TIEMPOS_CANTERA[m.vel]; cambios = true }
        }
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            if (servidor.tickCount % 20 == 0) segundo(servidor)
            if (cambios && servidor.tickCount % 400 == 0) guardar()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { _ -> guardar() }
    }

    private fun colocar(j: ServerPlayer, nivel: ServerLevel, s: ItemStack, tipo: String, d: CompoundTag, base: BlockPos) {
        val m = Maquina(
            tipo = tipo, dim = dim(nivel), x = base.x, y = base.y, z = base.z, dueno = j.uuid.toString(),
            vel = d.getIntOr("vel", 0), vetas = d.getIntOr("vetas", 0), auto = d.getBooleanOr("auto", false),
            corazon = d.getBooleanOr("corazon", false), nivelTanque = d.getIntOr("nivelTanque", 0), lava = d.getIntOr("lava", 0),
            facing = j.direction.opposite.serializedName,
        )
        if (tipo == "tambo" && tambosDe(m.dueno).size >= Tambos.MAXIMO) {
            aviso(j, "Ya tienes ${Tambos.MAXIMO} tambos. Desarma uno para armar otro.", ChatFormatting.RED); return
        }
        val piezas = piezas(m)
        val zona = (-1..1).flatMap { dx -> (-1..1).flatMap { dz -> (0 until m.alto()).map { dy -> base.offset(dx, dy, dz) } } }
        if (zona.any { !nivel.getBlockState(it).canBeReplaced() } || zona.any { en(nivel, it) != null }) {
            aviso(j, "No hay espacio: necesita 3x3 libre y ${m.alto()} de alto.", ChatFormatting.RED); return
        }
        if (zona.any { Proteccion.zonaEn(nivel, it) != null }) {
            aviso(j, "Aquí no se puede construir.", ChatFormatting.RED); return
        }
        piezas.forEach { (p, st) -> nivel.setBlock(p, st, 3) }
        if (m.tipo == "tanque") pintarTanque(nivel, m)
        if (m.tipo == "fuente") m.falta = tiempoFuente(m)
        lista += m
        guardar()
        s.shrink(1)
        nivel.playSound(null, base, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 0.8f)
        aviso(j, "¡Listo: ${nombre(m.tipo)}! Shift + clic derecho al centro con la mano vacía para desarmarlo.")
    }

    private fun desarmar(j: ServerPlayer, nivel: ServerLevel, m: Maquina) {
        val zona = (-1..1).flatMap { dx -> (-1..1).flatMap { dz -> (0 until m.alto()).map { dy -> m.origen().offset(dx, dy, dz) } } }
        lista.remove(m)
        zona.forEach { nivel.setBlock(it, Blocks.AIR.defaultBlockState(), 3) }
        guardar()
        val servidor = nivel.server
        val texto = if (m.tipo == "tambo") Kuraka.MAQUETA_TAMBO else BancoMinecraftPer.pirqa.buscar(m.tipo)?.item ?: ""
        val maqueta = GuiOfertas.crearTexto(servidor, texto) ?: return
        val raiz = maqueta.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        val interno = raiz.getCompoundOrEmpty(MARCA)
        interno.putInt("vel", m.vel); interno.putInt("vetas", m.vetas); interno.putBoolean("auto", m.auto)
        interno.putBoolean("corazon", m.corazon); interno.putInt("nivelTanque", m.nivelTanque); interno.putInt("lava", m.lava)
        raiz.put(MARCA, interno)
        maqueta.set(DataComponents.CUSTOM_DATA, CustomData.of(raiz))
        Inventarios.dar(j, maqueta)
        aviso(j, "${nombre(m.tipo)}: desarmado. La maqueta guarda sus mejoras.")
    }

    private fun mejorar(j: ServerPlayer, nivel: ServerLevel, m: Maquina, s: ItemStack, mejora: String) {
        val ok = when {
            mejora == "cincel" && m.tipo == "cantera" && m.vel < TIEMPOS_CANTERA.size - 1 -> { m.vel++; true }
            mejora == "veta" && m.tipo == "cantera" && m.vetas < 3 -> { m.vetas++; true }
            mejora == "brazo" && m.tipo == "cantera" && !m.auto -> { m.auto = true; m.faltaAuto = 5; true }
            mejora == "corazon" && m.tipo == "fuente" && !m.corazon -> { m.corazon = true; true }
            mejora == "aro" && m.tipo == "tanque" && m.nivelTanque < CAPACIDADES.size - 1 -> { m.nivelTanque++; pintarTanque(nivel, m); true }
            else -> false
        }
        if (!ok) { aviso(j, "Esa mejora no sirve aquí o ya está al máximo.", ChatFormatting.RED); return }
        s.shrink(1); guardar()
        nivel.playSound(null, m.nucleo(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 1f)
        aviso(j, when (m.tipo) {
            "cantera" -> "Cantera: ${TIEMPOS_CANTERA[m.vel]} s por bloque, vetas ${m.vetas}${if (m.auto) ", automática" else ""}."
            "fuente" -> "Fuente del Misti: un balde cada ${tiempoFuente(m) / 60.0} min."
            else -> "Tanque: ahora guarda ${CAPACIDADES[m.nivelTanque]} baldes."
        })
    }

    private fun tanque(j: ServerPlayer, nivel: ServerLevel, m: Maquina, s: ItemStack, mano: InteractionHand) {
        val cap = CAPACIDADES[m.nivelTanque]
        when {
            s.`is`(Items.BUCKET) && m.lava > 0 -> {
                m.lava--; s.shrink(1); Inventarios.dar(j, ItemStack(Items.LAVA_BUCKET))
                nivel.playSound(null, m.nucleo(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1f, 1f)
            }
            s.`is`(Items.LAVA_BUCKET) && m.lava < cap -> {
                m.lava++; j.setItemInHand(mano, ItemStack(Items.BUCKET))
                nivel.playSound(null, m.nucleo(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1f, 1f)
            }
        }
        cambios = true
        pintarTanque(nivel, m)
        aviso(j, "Tanque: ${m.lava} / $cap baldes de lava.", ChatFormatting.GOLD)
    }

    private fun tiempoFuente(m: Maquina) = if (m.corazon) 150 else 300

    private fun nombre(t: String) = when (t) {
        "cantera" -> "Cantera inca"; "fuente" -> "Fuente del Misti"; "tanque" -> "Tanque de vidrio y acero"; "tambo" -> "Tambo"; else -> "Huayrachina"
    }

    private fun tanqueCerca(m: Maquina, condicion: (Maquina) -> Boolean) = lista.firstOrNull {
        it.tipo == "tanque" && it.dueno == m.dueno && it.dim == m.dim && condicion(it) &&
            kotlin.math.abs(it.x - m.x) <= CERCA && kotlin.math.abs(it.y - m.y) <= CERCA && kotlin.math.abs(it.z - m.z) <= CERCA
    }

    private fun cofreVecino(nivel: ServerLevel, m: Maquina): Container? {
        for (dx in -2..2) for (dz in -2..2) for (dy in 0..2) {
            if (dx in -1..1 && dz in -1..1) continue
            val be = nivel.getBlockEntity(m.origen().offset(dx, dy, dz))
            if (be is Container && be !is AbstractFurnaceBlockEntity) return be
        }
        return null
    }

    /** Una vez por segundo: regenera canteras, picado automático, fuentes y hornos. */
    private fun segundo(servidor: MinecraftServer) {
        for (m in lista.toList()) {
            val nivel = servidor.allLevels.firstOrNull { dim(it) == m.dim } ?: continue
            if (!nivel.isLoaded(m.origen())) continue
            when (m.tipo) {
                "cantera" -> {
                    if (m.bloque == "nada" && --m.falta <= 0) {
                        m.bloque = sorteo(m.vetas)
                        nivel.setBlock(m.nucleo(), bloqueCantera(m.bloque), 3)
                        cambios = true
                    }
                    if (m.auto && --m.faltaAuto <= 0) {
                        m.faltaAuto = 5
                        val cofre = cofreVecino(nivel, m)
                        if (cofre != null) {
                            // si el cofre está lleno, lo que no entra se descarta: nada cae al piso
                            HopperBlockEntity.addItem(null, cofre, itemDe(sorteo(m.vetas), servidor), null)
                            cofre.setChanged()
                        }
                    }
                }
                "fuente" -> {
                    if (--m.falta > 0) continue
                    val caldero = m.nucleo()
                    val tanque = tanqueCerca(m) { it.lava < CAPACIDADES[it.nivelTanque] }
                    when {
                        tanque != null -> { tanque.lava++; pintarTanque(nivel, tanque); m.falta = tiempoFuente(m) }
                        nivel.getBlockState(caldero).`is`(Blocks.CAULDRON) -> {
                            nivel.setBlock(caldero, Blocks.LAVA_CAULDRON.defaultBlockState(), 3); m.falta = tiempoFuente(m)
                        }
                        else -> m.falta = 5 // el caldero sigue lleno: vuelve a mirar en 5 s
                    }
                    cambios = true
                }
                "horno" -> {
                    val horno = nivel.getBlockEntity(m.nucleo()) as? AbstractFurnaceBlockEntity ?: continue
                    val combustible = horno.getItem(1)
                    if (combustible.`is`(Items.BUCKET)) horno.setItem(1, ItemStack.EMPTY)
                    if (!horno.getItem(0).isEmpty && horno.getItem(1).isEmpty) {
                        val tanque = tanqueCerca(m) { it.lava > 0 } ?: continue
                        tanque.lava--; pintarTanque(nivel, tanque)
                        horno.setItem(1, ItemStack(Items.LAVA_BUCKET))
                        cambios = true
                    }
                }
            }
        }
    }
}
