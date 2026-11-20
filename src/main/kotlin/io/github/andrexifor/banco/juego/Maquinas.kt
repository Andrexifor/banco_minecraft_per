package io.github.andrexifor.banco.juego

import com.google.gson.GsonBuilder
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
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
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
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
 * Solo el dueño las usa; nadie las rompe (ni explosiones) ni pone nada dentro: cada 2 s la máquina se repara
 * sola (quita lo que se coló, como agua o bloques empujados, y repone sus piezas).
 * Clic derecho a la máquina (con cualquier cosa en la mano, salvo bloques, mejoras o baldes en el tanque):
 * abre su menú, con su estado y sus mejoras; desde ahí se saca una mejora (vuelve completa al inventario)
 * o se desarma. Atajo para desarmar: Shift + clic derecho al bloque del centro con la mano vacía.
 * Las mejoras se ven: cinceles en el techo, vetas de mineral, el brazo que pica, el corazón y los aros.
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
                if (mano == InteractionHand.MAIN_HAND) colocar(jugador, nivel, s, maqueta, d, baseDe(nivel, golpe.blockPos, golpe.direction))
                usoMaquina[jugador.uuid] = nivel.server.tickCount
                return@register InteractionResult.SUCCESS
            }
            // nadie pone bloques dentro de una máquina (al desarmarla se perderían); encima o al lado, sí
            if (s.item is BlockItem) {
                val clic = golpe.blockPos
                val enClic = en(nivel, clic)
                val esHorno = enClic?.tipo == "horno" && clic == enClic.nucleo()
                val abreMenu = !jugador.isShiftKeyDown && nivel.getBlockState(clic).getMenuProvider(nivel, clic) != null
                val destino = if (nivel.getBlockState(clic).canBeReplaced()) clic else clic.relative(golpe.direction)
                if (!esHorno && !abreMenu && en(nivel, destino) != null) {
                    if (mano == InteractionHand.MAIN_HAND) aviso(jugador, "No se pueden poner bloques dentro de una máquina.", ChatFormatting.RED)
                    return@register InteractionResult.FAIL
                }
            }
            val m = en(nivel, golpe.blockPos) ?: return@register InteractionResult.PASS
            if (m.dueno != jugador.uuid.toString()) {
                aviso(jugador, if (m.tipo == "tambo") "Este tambo es de otro jugador." else "Esta máquina es de otro jugador.", ChatFormatting.RED)
                return@register InteractionResult.FAIL
            }
            if (mano != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            // lo que se haga aquí no debe seguir como uso del ítem (un balde vaciaría lava al lado, por ejemplo)
            if (s.item !is BlockItem) usoMaquina[jugador.uuid] = nivel.server.tickCount
            // 2) desarmar: Shift + clic derecho con la mano vacía a cualquier parte (el centro del tanque no se alcanza)
            if (jugador.isShiftKeyDown && s.isEmpty) {
                desarmar(jugador, nivel, m); return@register InteractionResult.SUCCESS
            }
            // tambo: menú de viajes
            if (m.tipo == "tambo") { Tambos.abrir(jugador, m); return@register InteractionResult.SUCCESS }
            // 3) mejoras
            val mejora = d?.getStringOr("mejora_maquina", "")?.ifBlank { null }
            if (mejora != null) { mejorar(jugador, nivel, m, s, mejora); return@register InteractionResult.SUCCESS }
            // 4) tanque con balde: sacar o guardar lava
            if (m.tipo == "tanque" && (s.`is`(Items.BUCKET) || s.`is`(Items.LAVA_BUCKET))) {
                tanque(jugador, nivel, m, s, mano); return@register InteractionResult.SUCCESS
            }
            // 5) con un bloque en la mano se coloca al lado (así se ponen los cofres); dentro no, eso se frenó arriba
            if (s.item is BlockItem) return@register InteractionResult.PASS
            // 6) el horno de la huayrachina se abre normal
            if (m.tipo == "horno" && golpe.blockPos == m.nucleo()) return@register InteractionResult.PASS
            // 7) con cualquier otra cosa (o nada): el menú de la máquina
            menu(jugador, nivel, m)
            InteractionResult.SUCCESS
        }
        // el cliente, tras el clic a una máquina, también intenta usar el ítem (p. ej. vaciar el balde): se frena
        UseItemCallback.EVENT.register { jugador, nivel, _ ->
            val servidor = (nivel as? ServerLevel)?.server ?: return@register InteractionResult.PASS
            val t = usoMaquina[jugador.uuid] ?: return@register InteractionResult.PASS
            if (servidor.tickCount - t <= 2) InteractionResult.FAIL else InteractionResult.PASS
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
            aviso(jugador, if (m.dueno == jugador.uuid.toString()) "Para quitarla: clic derecho y Desarmar (o Shift + clic derecho con la mano vacía)." else "Esta máquina es de otro jugador.", ChatFormatting.RED)
            false
        }
        PlayerBlockBreakEvents.AFTER.register { nivel, _, pos, _, _ ->
            val m = en(nivel, pos) ?: return@register
            if (m.tipo == "cantera" && pos == m.nucleo()) { m.bloque = "nada"; m.falta = TIEMPOS_CANTERA[m.vel]; cambios = true }
        }
        ServerTickEvents.END_SERVER_TICK.register { servidor ->
            animar()
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
        val piezas = piezas(m)
        val zona = (-1..1).flatMap { dx -> (-1..1).flatMap { dz -> (0 until m.alto()).map { dy -> base.offset(dx, dy, dz) } } }
        if (zona.any { !nivel.getBlockState(it).canBeReplaced() } || zona.any { en(nivel, it) != null }) {
            aviso(j, "No hay espacio: necesita 3x3 libre y ${m.alto()} de alto.", ChatFormatting.RED); return
        }
        if (zona.any { Proteccion.zonaEn(nivel, it) != null }) {
            aviso(j, "Aquí no se puede construir.", ChatFormatting.RED); return
        }
        zona.forEach { if (!nivel.getBlockState(it).isAir) nivel.setBlock(it, Blocks.AIR.defaultBlockState(), 3) }
        piezas.forEach { (p, st) -> nivel.setBlock(p, st, 3) }
        if (m.tipo == "tanque") pintarTanque(nivel, m)
        if (m.tipo == "fuente") m.falta = tiempoFuente(m)
        lista += m
        guardar()
        decorar(nivel, m)
        s.shrink(1)
        nivel.playSound(null, base, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 0.8f)
        aviso(j, "¡Listo: ${nombre(m.tipo)}! Clic derecho: su menú (mejoras y desarmar).")
    }

    private fun desarmar(j: ServerPlayer, nivel: ServerLevel, m: Maquina) {
        val zona = (-1..1).flatMap { dx -> (-1..1).flatMap { dz -> (0 until m.alto()).map { dy -> m.origen().offset(dx, dy, dz) } } }
        lista.remove(m)
        quitarAdornos(nivel, m)
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
        // la descripción de la maqueta muestra las mejoras que guarda
        val previas = maqueta.get(DataComponents.LORE)?.lines() ?: emptyList()
        val extra = mejoras(m).map { Component.literal(it).withStyle { st -> st.withColor(ChatFormatting.AQUA).withItalic(false) } }
        if (extra.isNotEmpty()) maqueta.set(DataComponents.LORE, ItemLore(previas + extra))
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
        s.shrink(1); guardar(); decorar(nivel, m)
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

    /**
     * Cofres (o barriles) pegados a la máquina: alrededor, a la altura de la estructura, y encima de ella.
     * Un cofre doble cuenta como uno solo de 54 casillas.
     */
    private fun cofres(nivel: ServerLevel, m: Maquina): List<Pair<BlockPos, Container>> {
        val r = ArrayList<Pair<BlockPos, Container>>()
        val vistos = HashSet<BlockPos>()
        val posiciones = ArrayList<BlockPos>()
        for (dy in 0 until m.alto()) for (dx in -2..2) for (dz in -2..2) {
            if (dx in -1..1 && dz in -1..1) continue
            posiciones += m.origen().offset(dx, dy, dz)
        }
        for (dx in -1..1) for (dz in -1..1) posiciones += m.origen().offset(dx, m.alto(), dz)
        for (p in posiciones) {
            if (p in vistos) continue
            vistos += p
            val st = nivel.getBlockState(p)
            val bloque = st.block
            if (bloque is ChestBlock) {
                if (st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) vistos += p.relative(ChestBlock.getConnectedDirection(st))
                ChestBlock.getContainer(bloque, st, nivel, p, true)?.let { r += p to it }
                continue
            }
            val be = nivel.getBlockEntity(p)
            if (be is Container && be !is AbstractFurnaceBlockEntity) r += p to be
        }
        return r
    }

    /** Las mejoras de una máquina, en palabras. */
    private fun mejoras(m: Maquina): List<String> = when (m.tipo) {
        "cantera" -> listOf(
            "Velocidad: ${TIEMPOS_CANTERA[m.vel]} s por bloque (cinceles: ${m.vel} de ${TIEMPOS_CANTERA.size - 1})",
            "Vetas: ${if (m.vetas == 0) "ninguna (solo piedra)" else "nivel ${m.vetas} de 3"}",
            "Brazo de la cantera: ${if (m.auto) "sí (pica sola cada 5 s)" else "no"}",
        )
        "fuente" -> listOf("Corazón del Misti: ${if (m.corazon) "sí (lava cada 2.5 min)" else "no (lava cada 5 min)"}")
        "tanque" -> listOf("Tamaño: ${CAPACIDADES[m.nivelTanque]} baldes (aros: ${m.nivelTanque} de ${CAPACIDADES.size - 1})", "Lava: ${m.lava} baldes")
        else -> emptyList()
    }

    /** El estado de una máquina, en palabras (para su menú). */
    private fun lineasEstado(nivel: ServerLevel, m: Maquina): List<String> {
        val r = ArrayList(mejoras(m))
        when (m.tipo) {
            "cantera" -> if (m.auto) {
                val n = cofres(nivel, m).size
                r += if (n == 0) "Sin cofre: pon cofres pegados a la cantera (al lado o encima)" else "Cofres conectados: $n (llena uno y pasa al siguiente)"
            }
            "fuente" -> r += "Próxima lava en ${m.falta} s" + if (tanqueCerca(m) { true } != null) " (va al tanque)" else " (al caldero)"
            "horno" -> {
                val t = tanqueCerca(m) { true }
                val f = fuenteCerca(m)
                r += when {
                    t != null -> "Lava: tanque cerca con ${t.lava} baldes"
                    f != null -> "Lava: Fuente del Misti cerca"
                    else -> "Sin lava: pon un tanque o una Fuente del Misti a 8 bloques o menos"
                }
                r += "Funde lo que pongas dentro; lo listo va a los cofres pegados"
                r += "Una cantera automática cerca le manda el hierro y el cobre"
            }
        }
        return r
    }

    /** Una vez por segundo: regenera canteras, picado automático, fuentes y hornos. */
    private fun segundo(servidor: MinecraftServer) {
        segundos++
        for (m in lista.toList()) {
            val nivel = servidor.allLevels.firstOrNull { dim(it) == m.dim } ?: continue
            if (!nivel.isLoaded(m.origen())) continue
            if (segundos % 2 == 0) reparar(nivel, m)
            if (segundos % 10 == 0 || clave(m) !in revisadas) revisarAdornos(nivel, m)
            when (m.tipo) {
                "cantera" -> {
                    if (m.bloque == "nada" && --m.falta <= 0) {
                        m.bloque = sorteo(m.vetas)
                        nivel.setBlock(m.nucleo(), bloqueCantera(m.bloque), 3)
                        cambios = true
                    }
                    if (m.auto && --m.faltaAuto <= 0) {
                        m.faltaAuto = 5
                        // llena los cofres en orden; si todos están llenos, lo que no entra se descarta (nada cae al piso)
                        val sale = sorteo(m.vetas)
                        var resto = itemDe(sale, servidor)
                        var destino: BlockPos? = null
                        if (sale == "hierro" || sale == "cobre") hornoCerca(nivel, m)?.let { (h, horno) ->
                            val entrada = horno.getItem(0)
                            if (entrada.isEmpty) { horno.setItem(0, resto); resto = ItemStack.EMPTY }
                            else if (ItemStack.isSameItemSameComponents(entrada, resto) && entrada.count < 64) { entrada.grow(1); resto = ItemStack.EMPTY }
                            if (resto.isEmpty) { horno.setChanged(); destino = h.nucleo() }
                        }
                        if (!resto.isEmpty) for ((pos, cofre) in cofres(nivel, m)) {
                            val antes = resto.count
                            resto = HopperBlockEntity.addItem(null, cofre, resto, null)
                            cofre.setChanged()
                            if (destino == null && (resto.isEmpty || resto.count < antes)) destino = pos
                            if (resto.isEmpty) break
                        }
                        picar(nivel, m, sale, destino)
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
                    if (!horno.getItem(0).isEmpty && horno.getItem(1).isEmpty && tomarLava(nivel, m)) {
                        horno.setItem(1, ItemStack(Items.LAVA_BUCKET))
                        cambios = true
                    }
                    // lo fundido pasa a los cofres pegados al horno
                    val listo = horno.getItem(2)
                    if (!listo.isEmpty) {
                        var resto = listo.copy()
                        for ((_, cofre) in cofres(nivel, m)) {
                            resto = HopperBlockEntity.addItem(null, cofre, resto, null)
                            cofre.setChanged()
                            if (resto.isEmpty) break
                        }
                        if (resto.count != listo.count) { horno.setItem(2, resto); horno.setChanged() }
                    }
                }
            }
        }
    }

    // ---------- menú: estado, sacar mejoras, desarmar ----------

    private data class Ranura(val id: String, val puestas: Int, val maximo: Int)

    private fun ranuras(m: Maquina): List<Ranura> = when (m.tipo) {
        "cantera" -> listOf(Ranura("cincel", m.vel, TIEMPOS_CANTERA.size - 1), Ranura("veta", m.vetas, 3), Ranura("brazo", if (m.auto) 1 else 0, 1))
        "fuente" -> listOf(Ranura("corazon", if (m.corazon) 1 else 0, 1))
        "tanque" -> listOf(Ranura("aro", m.nivelTanque, CAPACIDADES.size - 1))
        else -> emptyList()
    }

    private fun itemMejora(servidor: MinecraftServer, id: String): ItemStack? =
        BancoMinecraftPer.pirqa.buscar(id)?.item?.let { GuiOfertas.crearTexto(servidor, it) }

    private fun icono(m: Maquina) = ItemStack(when (m.tipo) {
        "cantera" -> Items.CHISELED_STONE_BRICKS; "fuente" -> Items.MAGMA_BLOCK; "tanque" -> Items.IRON_BLOCK; else -> Items.FURNACE
    })

    private fun menu(j: ServerPlayer, nivel: ServerLevel, m: Maquina) {
        if (m !in lista) return
        val gui = SimpleGui(MenuType.GENERIC_9x3, j, false)
        gui.setTitle(GuiUtil.texto(nombre(m.tipo), ChatFormatting.DARK_GREEN))
        for (i in 0 until gui.size) gui.setSlot(i, GuiUtil.relleno())
        gui.setSlot(4, GuiElementBuilder(icono(m)).setName(GuiUtil.texto(nombre(m.tipo), ChatFormatting.GOLD))
            .setLore(lineasEstado(nivel, m).map { GuiUtil.texto(it, ChatFormatting.GRAY) }))
        val lugares = intArrayOf(11, 13, 15)
        ranuras(m).forEachIndexed { i, r ->
            val base = itemMejora(nivel.server, r.id) ?: ItemStack(Items.PAPER)
            base.count = r.puestas.coerceIn(1, 64)
            val nombreMejora = BancoMinecraftPer.pirqa.buscar(r.id)?.nombre ?: r.id
            val lore = ArrayList<net.minecraft.network.chat.Component>()
            lore += GuiUtil.texto("Puestas: ${r.puestas} de ${r.maximo}", if (r.puestas > 0) ChatFormatting.AQUA else ChatFormatting.GRAY)
            if (r.puestas > 0) lore += GuiUtil.texto("Clic: sacar una (vuelve a tu inventario)", ChatFormatting.YELLOW)
            else lore += GuiUtil.texto("Para ponerla: clic derecho a la máquina con la mejora", ChatFormatting.DARK_GRAY)
            val b = GuiElementBuilder(base).setName(GuiUtil.texto(nombreMejora, ChatFormatting.AQUA)).setLore(lore)
            if (r.puestas > 0) b.setCallback(Runnable { quitar(j, nivel, m, r.id); menu(j, nivel, m) })
            gui.setSlot(lugares[i], b)
        }
        val guarda = if (m.tipo == "tanque") "sus mejoras y su lava" else "sus mejoras"
        gui.setSlot(22, GuiUtil.boton(ItemStack(Items.BARRIER), "Desarmar", listOf("Vuelve a ser maqueta", "La maqueta guarda $guarda"), ChatFormatting.RED) {
            GuiUtil.confirmar(j, "¿Desarmar?", icono(m), listOf(nombre(m.tipo), "La maqueta guarda $guarda"),
                alConfirmar = { j.closeContainer(); if (m in lista) desarmar(j, nivel, m) },
                alCancelar = { menu(j, nivel, m) })
        })
        gui.open()
    }

    /** Saca una mejora: la máquina baja un nivel y la mejora vuelve completa al inventario. */
    private fun quitar(j: ServerPlayer, nivel: ServerLevel, m: Maquina, id: String) {
        if (m !in lista) return
        val ok = when (id) {
            "cincel" -> if (m.vel > 0) { m.vel--; true } else false
            "veta" -> if (m.vetas > 0) { m.vetas--; true } else false
            "brazo" -> if (m.auto) { m.auto = false; true } else false
            "corazon" -> if (m.corazon) { m.corazon = false; true } else false
            "aro" -> when {
                m.nivelTanque == 0 -> false
                m.lava > CAPACIDADES[m.nivelTanque - 1] -> {
                    aviso(j, "Primero saca lava: con un aro menos caben ${CAPACIDADES[m.nivelTanque - 1]} baldes (tiene ${m.lava}).", ChatFormatting.RED)
                    return
                }
                else -> { m.nivelTanque--; pintarTanque(nivel, m); true }
            }
            else -> false
        }
        if (!ok) return
        if (m.tipo == "cantera" && m.falta > TIEMPOS_CANTERA[m.vel]) m.falta = TIEMPOS_CANTERA[m.vel]
        itemMejora(nivel.server, id)?.let { Inventarios.dar(j, it) }
        guardar()
        decorar(nivel, m)
        nivel.playSound(null, m.nucleo(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8f, 0.8f)
        aviso(j, "Mejora sacada. " + mejoras(m).joinToString(" · "), ChatFormatting.AQUA)
    }

    // ---------- se repara sola: nada se queda dentro ----------

    private fun reparar(nivel: ServerLevel, m: Maquina) {
        val esperado = HashMap<BlockPos, BlockState>()
        piezas(m).forEach { (p, st) -> esperado[p] = st }
        for (dy in 0 until m.alto()) for (dx in -1..1) for (dz in -1..1) {
            val p = m.origen().offset(dx, dy, dz)
            if (p == m.nucleo() || !nivel.isLoaded(p)) continue
            if (dx == 0 && dz == 0 && (m.tipo == "horno" || (m.tipo == "tanque" && dy in 1..3))) continue
            val st = nivel.getBlockState(p)
            val e = esperado[p]
            if (e == null) {
                if (st.isAir) continue
                if (st.canBeReplaced() || !st.fluidState.isEmpty) nivel.setBlock(p, Blocks.AIR.defaultBlockState(), 3)
                else nivel.destroyBlock(p, true)
            } else if (st.block != e.block) {
                nivel.setBlock(p, e, 3)
            }
        }
    }

    // ---------- adornos: las mejoras se ven ----------

    private const val ADORNO = "maq_adorno"
    private var segundos = 0
    private val revisadas = HashSet<String>()

    private fun clave(m: Maquina) = "maq_${m.dim.substringAfter(':')}_${m.x}_${m.y}_${m.z}"
    private fun caja(m: Maquina) = AABB(m.x - 2.0, m.y - 1.0, m.z - 2.0, m.x + 3.0, m.y + m.alto() + 3.0, m.z + 3.0)
    private fun modelo(id: String) = "{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"banco_minecraft_per:$id\"}}"

    private data class Adorno(
        val item: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val escala: Float,
        val modo: String = "fixed", val giro: String = "[0f,1f,0f,0f]", val extra: String = "", val brazo: Boolean = false,
    )

    private fun adornos(m: Maquina): List<Adorno> {
        val cx = m.x + 0.5
        val cz = m.z + 0.5
        val y = m.y.toDouble()
        val r = ArrayList<Adorno>()
        when (m.tipo) {
            "cantera" -> {
                // un cincel parado sobre cada esquina del techo
                val esquinas = listOf(Triple(-1, -1, 45f), Triple(1, -1, -45f), Triple(1, 1, -135f), Triple(-1, 1, 135f))
                for (i in 0 until m.vel.coerceAtMost(4)) {
                    val (dx, dz, yaw) = esquinas[i]
                    r += Adorno(modelo("cincel_apu"), cx + dx, y + 4.45, cz + dz, yaw, 0.9f)
                }
                // vetas: trozos de mineral en las aberturas del primer piso
                val vetas = listOf(Triple(0, -1, "coal_ore"), Triple(1, 0, "iron_ore"), Triple(-1, 0, "copper_ore"), Triple(0, 1, "raw_gold_block"))
                val cuantas = when (m.vetas) { 1 -> 1; 2 -> 3; 3 -> 4; else -> 0 }
                for (i in 0 until cuantas) {
                    val (dx, dz, b) = vetas[i]
                    r += Adorno("{id:\"minecraft:$b\",count:1}", cx + dx * 1.2, y + 1.2, cz + dz * 1.2, i * 37f, 0.4f, "none", "[0f,0f,0f,1f]")
                }
                if (m.auto) {
                    val yaw = runCatching { Direction.valueOf(m.facing.uppercase()).toYRot() }.getOrDefault(0f)
                    r += Adorno(modelo("brazo_cantera"), cx, y + BRAZO_ARRIBA, cz, yaw, 1.1f, brazo = true)
                }
            }
            "fuente" -> if (m.corazon) r += Adorno(modelo("corazon_misti"), cx, y + 4.7, cz, 0f, 0.8f, extra = "billboard:\"vertical\",")
            "tanque" -> for (i in 0 until m.nivelTanque) {
                // los aros, apilados sobre la tapa
                r += Adorno(modelo("aro_acero"), cx, y + 5.03 + i * 0.07, cz, i * 25f, 1.6f, giro = "[0.7071f,0f,0f,0.7071f]")
            }
        }
        return r
    }

    private const val BRAZO_ARRIBA = 2.75

    private fun comando(nivel: ServerLevel, p: Vec3, cmd: String) {
        val servidor = nivel.server
        servidor.commands.performPrefixedCommand(servidor.createCommandSourceStack().withLevel(nivel).withPosition(p).withSuppressedOutput(), cmd)
    }

    private fun quitarAdornos(nivel: ServerLevel, m: Maquina) {
        val c = clave(m)
        nivel.getEntitiesOfClass(Display::class.java, caja(m)) { c in it.entityTags() }.forEach { it.discard() }
    }

    private fun decorar(nivel: ServerLevel, m: Maquina) {
        quitarAdornos(nivel, m)
        val c = clave(m)
        for (a in adornos(m)) {
            val tags = "\"$ADORNO\",\"$c\"" + if (a.brazo) ",\"${c}_brazo\"" else ""
            val nbt = "{Tags:[$tags],Rotation:[${a.yaw}f,0f],teleport_duration:3,view_range:1.5f,item_display:\"${a.modo}\",${a.extra}" +
                "item:${a.item},transformation:{left_rotation:${a.giro},right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f]," +
                "scale:[${a.escala}f,${a.escala}f,${a.escala}f]}}"
            comando(nivel, Vec3(a.x, a.y, a.z), "summon minecraft:item_display ${a.x} ${a.y} ${a.z} $nbt")
        }
        revisadas += c
    }

    /** Si faltan o sobran adornos (por ejemplo, tras reiniciar el servidor), se vuelven a poner. */
    private fun revisarAdornos(nivel: ServerLevel, m: Maquina) {
        if (!nivel.areEntitiesLoaded(ChunkPos.pack(m.x shr 4, m.z shr 4))) return
        val c = clave(m)
        val hay = nivel.getEntitiesOfClass(Display::class.java, caja(m)) { c in it.entityTags() }.size
        if (hay != adornos(m).size) decorar(nivel, m) else revisadas += c
    }

    // ---------- el brazo pica y el mineral vuela al cofre ----------

    private class Animacion(val nivel: ServerLevel, val id: UUID, val hasta: Vec3?, val y: Double, var t: Int = 0)
    private val golpes = ArrayList<Animacion>()
    private val vuelos = ArrayList<Animacion>()

    private fun idItem(b: String) = when (b) {
        "carbon" -> "coal"; "hierro" -> "raw_iron"; "cobre" -> "raw_copper"; "tumbaga" -> "raw_gold"; else -> "cobblestone"
    }

    private fun picar(nivel: ServerLevel, m: Maquina, sale: String, destino: BlockPos?) {
        val n = m.nucleo()
        val c = clave(m)
        nivel.getEntitiesOfClass(Display::class.java, caja(m)) { "${c}_brazo" in it.entityTags() }.firstOrNull()?.let { d ->
            d.snapTo(d.x, m.y + BRAZO_ARRIBA - 0.55, d.z, d.yRot, 0f)
            golpes += Animacion(nivel, d.uuid, null, m.y + BRAZO_ARRIBA)
        }
        val estado = nivel.getBlockState(n).takeIf { !it.isAir } ?: bloqueCantera(sale)
        nivel.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, estado), n.x + 0.5, n.y + 1.0, n.z + 0.5, 12, 0.25, 0.05, 0.25, 0.05)
        nivel.playSound(null, n, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6f, 0.9f)
        if (destino == null) {
            // sin cofre o con todos llenos: un poco de humo
            nivel.sendParticles(ParticleTypes.SMOKE, n.x + 0.5, n.y + 1.3, n.z + 0.5, 6, 0.2, 0.1, 0.2, 0.01)
            return
        }
        val desde = Vec3(n.x + 0.5, n.y + 1.3, n.z + 0.5)
        val etiqueta = "maq_vuelo_${UUID.randomUUID().toString().take(8)}"
        comando(nivel, desde, "summon minecraft:item_display ${desde.x} ${desde.y} ${desde.z} {Tags:[\"$ADORNO\",\"$etiqueta\"]," +
            "teleport_duration:8,billboard:\"center\",item:{id:\"minecraft:${idItem(sale)}\",count:1}," +
            "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.45f,0.45f,0.45f]}}")
        val d = nivel.getEntitiesOfClass(Display::class.java, AABB.ofSize(desde, 1.0, 1.0, 1.0)) { etiqueta in it.entityTags() }.firstOrNull() ?: return
        vuelos += Animacion(nivel, d.uuid, Vec3(destino.x + 0.5, destino.y + 1.1, destino.z + 0.5), 0.0)
    }

    /** Cada tick: el brazo vuelve arriba y los minerales llegan al cofre. */
    private fun animar() {
        golpes.toList().forEach { a ->
            if (++a.t < 4) return@forEach
            golpes.remove(a)
            a.nivel.getEntity(a.id)?.let { it.snapTo(it.x, a.y, it.z, it.yRot, 0f) }
        }
        vuelos.toList().forEach { a ->
            val d = a.nivel.getEntity(a.id)
            if (d == null) { vuelos.remove(a); return@forEach }
            a.t++
            if (a.t == 2) a.hasta?.let { d.snapTo(it.x, it.y, it.z, 0f, 0f) }
            if (a.t >= 12) { vuelos.remove(a); d.discard() }
        }
    }

    // ---------- detalles ----------

    /** Quién usó una máquina y en qué tick: ese mismo clic no debe seguir como uso del ítem. */
    private val usoMaquina = HashMap<UUID, Int>()

    /**
     * Dónde se arma una maqueta: sobre el bloque clicado; si es pasto, flores o nieve (se reemplazan),
     * en su lugar y no encima (y si es pasto alto, desde su mitad de abajo).
     */
    private fun baseDe(nivel: ServerLevel, clic: BlockPos, cara: Direction): BlockPos {
        val st = nivel.getBlockState(clic)
        var b = if (!st.isAir && st.canBeReplaced() && st.fluidState.isEmpty) clic else clic.relative(cara)
        repeat(2) {
            val abajo = nivel.getBlockState(b.below())
            if (!abajo.isAir && abajo.canBeReplaced() && abajo.fluidState.isEmpty) b = b.below()
        }
        return b
    }

    private fun cerca(a: Maquina, b: Maquina) = a.dueno == b.dueno && a.dim == b.dim &&
        kotlin.math.abs(a.x - b.x) <= CERCA && kotlin.math.abs(a.y - b.y) <= CERCA && kotlin.math.abs(a.z - b.z) <= CERCA

    private fun fuenteCerca(m: Maquina) = lista.firstOrNull { it.tipo == "fuente" && it !== m && cerca(it, m) }

    /** Un balde de lava para el horno: de un tanque cercano o, si no, del caldero lleno de una fuente cercana. */
    private fun tomarLava(nivel: ServerLevel, m: Maquina): Boolean {
        tanqueCerca(m) { it.lava > 0 }?.let { t -> t.lava--; pintarTanque(nivel, t); return true }
        val f = lista.firstOrNull { it.tipo == "fuente" && cerca(it, m) && nivel.getBlockState(it.nucleo()).`is`(Blocks.LAVA_CAULDRON) } ?: return false
        nivel.setBlock(f.nucleo(), Blocks.CAULDRON.defaultBlockState(), 3)
        f.falta = tiempoFuente(f)
        return true
    }

    private fun hornoCerca(nivel: ServerLevel, m: Maquina): Pair<Maquina, AbstractFurnaceBlockEntity>? {
        val h = lista.firstOrNull { it.tipo == "horno" && cerca(it, m) } ?: return null
        val be = nivel.getBlockEntity(h.nucleo()) as? AbstractFurnaceBlockEntity ?: return null
        return h to be
    }
}
