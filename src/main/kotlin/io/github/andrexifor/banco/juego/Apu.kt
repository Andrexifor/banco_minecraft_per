package io.github.andrexifor.banco.juego

import com.google.gson.GsonBuilder
import io.github.andrexifor.banco.BancoMinecraftPer
import io.github.andrexifor.banco.nucleo.Formato
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Apu de la montaña: jefe del servidor. Su santuario se genera UNA sola vez, en la cima más alta que el
 * mod encuentra a 1500–2000 bloques del spawn (apu.json guarda dónde). El Llamero viajero de la plaza
 * cuenta gratis el rumor de dónde está.
 *
 * Mientras duerme es una estatua sobre el altar. Cuando alguien se acerca, despierta: un guerrero de
 * piedra de 6 bloques (modelo propio sobre un esqueleto wither invisible de 800 de vida) que
 *  - lanza rocas a los jugadores,
 *  - hace temblar el suelo (daño, empuje y lentitud a quien está cerca),
 *  - invoca cóndores oscuros,
 *  - con media vida se cubre de nieve: más fuerte y resistente, y congela a quien se queda cerca.
 * Al vencerlo, cada jugador que le hizo al menos el 5 % de su vida recibe su botín COMPLETO (no se
 * reparte): S/ 2,000,000, de 3 a 5 tumbagas y, la primera vez, el Champi del Apu. Cada jugador cobra
 * una vez por semana (de lunes a domingo, hora de Perú); el Apu vuelve a despertar a los 30 minutos,
 * así todos tienen su oportunidad. Si se queda sin nadie cerca un minuto, vuelve a dormir y se cura.
 */
object Apu {
    private val LIMA = ZoneId.of("America/Lima")
    private const val VIDA = 800.0
    const val PREMIO = 2_000_000L
    private const val ESPERA_MS = 30L * 60 * 1000
    private const val RADIO_DESPERTAR = 32.0
    private const val RADIO_PELEA = 64.0

    const val CHAMPI = "minecraft:netherite_axe[item_model=\"banco_minecraft_per:champi_apu\"," +
        "custom_name={text:\"Champi del Apu\",color:\"aqua\",bold:true,italic:false}," +
        "lore=[{text:\"La porra estrellada del guerrero de la montaña\",color:\"gray\",italic:false}," +
        "{text:\"Irrompible\",color:\"gray\",italic:false}]," +
        "enchantments={\"minecraft:sharpness\":5,\"minecraft:efficiency\":5,\"minecraft:unbreaking\":3}," +
        "unbreakable={},rarity=\"epic\"]"

    data class Datos(
        var generado: Boolean = false,
        var dim: String = "minecraft:overworld",
        var x: Int = 0, var y: Int = 0, var z: Int = 0,
        var proximo: Long = 0,
        var cobros: MutableMap<String, String> = mutableMapOf(),
        var trofeo: MutableList<String> = mutableListOf(),
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val archivo = FabricLoader.getInstance().gameDir.resolve(BancoMinecraftPer.MOD_ID).resolve("apu.json")
    private var datos = Datos()

    // ---------- estado de la pelea (en memoria) ----------
    private var jefe: UUID? = null
    private var modelo: UUID? = null
    private val dano = HashMap<UUID, Float>()
    private var fase2 = false
    private var soledad = 0
    private var tRocas = 100
    private var tTemblor = 200
    private var tCondores = 300
    private val barra = ServerBossEvent(UUID.randomUUID(), Component.literal("Apu de la montaña").withStyle(ChatFormatting.GOLD),
        BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10)

    private class Roca(val display: UUID, val desde: Vec3, val hasta: Vec3, var t: Int = 0)
    private val rocas = ArrayList<Roca>()

    private fun cargar() {
        try {
            if (Files.exists(archivo)) datos = gson.fromJson(Files.readString(archivo, StandardCharsets.UTF_8), Datos::class.java) ?: Datos()
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo leer apu.json: {}", e.message)
        }
    }

    private fun guardar() {
        try {
            Files.createDirectories(archivo.parent)
            Files.writeString(archivo, gson.toJson(datos), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo guardar apu.json: {}", e.message)
        }
    }

    private fun centro() = BlockPos(datos.x, datos.y, datos.z)
    private fun nivel(servidor: MinecraftServer): ServerLevel? =
        servidor.allLevels.firstOrNull { it.dimension().identifier().toString() == datos.dim }

    private fun semana(): String = LocalDate.now(LIMA).with(DayOfWeek.MONDAY).toString()

    fun registrarEventos() {
        cargar()
        ServerLifecycleEvents.SERVER_STARTED.register { servidor ->
            // la v0.7.5 inicial lo dejó en el fondo del mundo (no generaba los chunks): se vuelve a buscar
            if (!datos.generado || datos.y <= servidor.overworld().minY + 5) {
                try {
                    generar(servidor)
                } catch (e: Exception) {
                    BancoMinecraftPer.LOGGER.error("No se pudo generar el santuario del Apu", e)
                }
            }
        }
        ServerTickEvents.END_SERVER_TICK.register { servidor -> tick(servidor) }
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entidad, fuente, _, recibido, _ ->
            if (entidad.uuid != jefe) return@register
            val atacante = fuente.entity as? ServerPlayer ?: return@register
            dano.merge(atacante.uuid, recibido) { a, b -> a + b }
        }
        ServerLivingEntityEvents.AFTER_DEATH.register { entidad, _ ->
            if (entidad.uuid == jefe) vencido(entidad.level().server ?: return@register, entidad)
        }
        PlayerBlockBreakEvents.BEFORE.register { nivel, jugador, pos, _, _ ->
            if (!datos.generado || nivel.dimension().identifier().toString() != datos.dim) return@register true
            val c = centro()
            val dentro = hypot((pos.x - c.x).toDouble(), (pos.z - c.z).toDouble()) <= 10.5 && pos.y in c.y - 13..c.y + 14
            if (dentro && jugador is ServerPlayer && !jugador.isCreative) {
                jugador.sendSystemMessage(Component.literal("El santuario del Apu no se puede romper.").withStyle(ChatFormatting.RED))
                return@register false
            }
            true
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { servidor ->
            retirar(servidor)
            guardar()
        }
    }

    // ---------- generación del santuario ----------
    private fun generar(servidor: MinecraftServer) {
        val nivel = servidor.overworld()
        val spawn = nivel.respawnData.pos()
        var mejor: BlockPos? = null
        for (radio in intArrayOf(1500, 2000)) {
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 + radio / 100.0)
                val x = spawn.x + (cos(a) * radio).toInt()
                val z = spawn.z + (sin(a) * radio).toInt()
                // getChunk genera el chunk si hace falta (getHeight solo mira chunks ya cargados)
                val chunk = nivel.getChunk(x shr 4, z shr 4)
                val y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x and 15, z and 15) + 1
                if (y <= nivel.minY + 5) continue
                val suelo = BlockPos(x, y - 1, z)
                if (!nivel.getBlockState(suelo).fluidState.isEmpty) continue
                if (mejor == null || y > mejor.y) mejor = BlockPos(x, y, z)
            }
        }
        val c = mejor ?: return
        construir(nivel, c)
        datos.generado = true
        datos.dim = nivel.dimension().identifier().toString()
        datos.x = c.x; datos.y = c.y; datos.z = c.z
        guardar()
        estatua(servidor, true)
        BancoMinecraftPer.LOGGER.info("Santuario del Apu generado en {} {} {}", c.x, c.y, c.z)
    }

    private fun construir(nivel: ServerLevel, c: BlockPos) {
        val azar = Random(c.x * 31L + c.z)
        fun pon(p: BlockPos, b: net.minecraft.world.level.block.state.BlockState) { nivel.setBlock(p, b, 2) }
        for (dx in -10..10) for (dz in -10..10) {
            val r = hypot(dx.toDouble(), dz.toDouble())
            if (r > 10.5) continue
            // aire encima
            for (dy in 0..15) pon(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState())
            if (r > 9.5) continue
            // cimientos hasta tocar suelo firme
            for (dy in 2..14) {
                val p = c.offset(dx, -dy, dz)
                val s = nivel.getBlockState(p)
                if (!s.canBeReplaced() && s.fluidState.isEmpty && !s.isAir) break
                pon(p, Blocks.STONE.defaultBlockState())
            }
            // piso
            val piso = when {
                r <= 2.5 -> Blocks.CHISELED_STONE_BRICKS
                r >= 7.5 -> if (azar.nextInt(3) == 0) Blocks.MOSSY_STONE_BRICKS else Blocks.STONE_BRICKS
                (dx == 0 || dz == 0) -> Blocks.POLISHED_DIORITE
                else -> if (azar.nextInt(4) == 0) Blocks.CRACKED_STONE_BRICKS else Blocks.POLISHED_ANDESITE
            }
            pon(c.offset(dx, -1, dz), piso.defaultBlockState())
            if (r in 3.0..7.0 && azar.nextInt(5) == 0) pon(c.offset(dx, 0, dz), Blocks.SNOW.defaultBlockState())
        }
        // ocho pilares con nieve y faroles de almas
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val px = (cos(a) * 8).toInt()
            val pz = (sin(a) * 8).toInt()
            for (dy in 0..4) pon(c.offset(px, dy, pz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState())
            pon(c.offset(px, 5, pz), Blocks.SNOW_BLOCK.defaultBlockState())
            pon(c.offset(px, 6, pz), Blocks.SOUL_LANTERN.defaultBlockState())
        }
        // altar
        for (dx in -1..1) for (dz in -1..1) pon(c.offset(dx, 0, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())
        pon(c.offset(0, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState())
        for ((dx, dz) in listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2)) pon(c.offset(dx, 0, dz), Blocks.SOUL_CAMPFIRE.defaultBlockState())
    }

    // ---------- estatua (mientras duerme) ----------
    private fun estatua(servidor: MinecraftServer, poner: Boolean) {
        val nivel = nivel(servidor) ?: return
        val c = centro()
        if (!nivel.isLoaded(c)) return
        nivel.getEntitiesOfClass(Display::class.java, AABB(c).inflate(6.0)) { "apu_estatua" in it.entityTags() }.forEach { it.discard() }
        if (!poner) return
        comando(servidor, nivel, Vec3(c.x + 0.5, c.y + 1.0, c.z + 0.5),
            "summon minecraft:item_display ${c.x + 0.5} ${c.y + 1.0} ${c.z + 0.5} {Tags:[\"apu_estatua\"],Rotation:[0f,0f]," +
                "item:{id:\"minecraft:stone\",count:1,components:{\"minecraft:item_model\":\"banco_minecraft_per:apu\"}},item_display:\"fixed\"," +
                "view_range:4f,transformation:{left_rotation:[0f,1f,0f,0f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.25f,0f],scale:[2.5f,2.5f,2.5f]}}")
    }

    private fun comando(servidor: MinecraftServer, nivel: ServerLevel, p: Vec3, cmd: String) {
        servidor.commands.performPrefixedCommand(servidor.createCommandSourceStack().withLevel(nivel).withPosition(p).withSuppressedOutput(), cmd)
    }

    // ---------- pelea ----------
    private fun jefeVivo(nivel: ServerLevel): LivingEntity? = jefe?.let { nivel.getEntity(it) as? LivingEntity }?.takeIf { it.isAlive }

    private fun tick(servidor: MinecraftServer) {
        if (!datos.generado) return
        val nivel = nivel(servidor) ?: return
        val c = centro()
        val centroV = Vec3(c.x + 0.5, c.y.toDouble(), c.z + 0.5)
        moverRocas(nivel)
        val boss = jefeVivo(nivel)
        if (boss != null) {
            // el modelo sigue al cuerpo
            (modelo?.let { nivel.getEntity(it) } as? Display)?.snapTo(boss.x, boss.y, boss.z, boss.yBodyRot, 0f)
        }
        if (servidor.tickCount % 20 != 0) return
        if (!nivel.isLoaded(c)) return
        val cerca = nivel.players().filter { it.position().distanceTo(centroV) < RADIO_PELEA && !it.isSpectator }
        if (boss == null) {
            if (jefe != null) retirar(servidor) // desapareció sin morir (comando, etc.)
            if (servidor.tickCount % 100 == 0) huerfanos(nivel, c)
            if (System.currentTimeMillis() >= datos.proximo &&
                cerca.any { it.position().distanceTo(centroV) < RADIO_DESPERTAR && !it.isCreative }) {
                despertar(servidor, nivel, c)
            } else if (servidor.tickCount % 200 == 0 && nivel.getEntitiesOfClass(Display::class.java, AABB(c).inflate(6.0)) { "apu_estatua" in it.entityTags() }.isEmpty()) {
                estatua(servidor, true)
            }
            return
        }
        // barra de jefe para quienes están cerca
        val enBarra = barra.players.toSet()
        for (p in cerca) if (p !in enBarra) barra.addPlayer(p)
        for (p in enBarra) if (p !in cerca) barra.removePlayer(p)
        barra.setProgress((boss.health / boss.maxHealth).coerceIn(0f, 1f))
        // sin nadie cerca un minuto: vuelve a dormir
        if (cerca.isEmpty()) {
            if (++soledad >= 60) { retirar(servidor); estatua(servidor, true) }
            return
        }
        soledad = 0
        // no se aleja de su santuario
        if (boss.position().distanceTo(centroV) > 22) boss.teleportTo(centroV.x, centroV.y + 1, centroV.z)
        if (!fase2 && boss.health < boss.maxHealth / 2) entrarFase2(nivel, boss, cerca)
        val peleando = cerca.filter { !it.isCreative }
        if (peleando.isEmpty()) return
        tRocas -= 20; tTemblor -= 20; tCondores -= 20
        if (tRocas <= 0) { lanzarRoca(servidor, nivel, boss, peleando.random()); tRocas = if (fase2) 60 else 120 }
        if (tTemblor <= 0 && peleando.any { it.distanceTo(boss) < 10 }) { temblor(nivel, boss, peleando); tTemblor = if (fase2) 160 else 240 }
        if (tCondores <= 0) { condores(servidor, nivel, boss); tCondores = if (fase2) 300 else 400 }
        if (fase2) helada(nivel, boss, peleando)
    }

    private fun despertar(servidor: MinecraftServer, nivel: ServerLevel, c: BlockPos) {
        estatua(servidor, false)
        huerfanos(nivel, c)
        val p = Vec3(c.x + 0.5, c.y + 1.0, c.z + 0.5)
        comando(servidor, nivel, p,
            "summon minecraft:wither_skeleton ${p.x} ${p.y} ${p.z} {Tags:[\"apu_jefe\"],PersistenceRequired:1b," +
                "CustomName:{text:\"Apu de la montaña\",color:\"gold\"},DeathLootTable:\"minecraft:empty\",Health:${VIDA}f," +
                "attributes:[{id:\"minecraft:max_health\",base:${VIDA}d},{id:\"minecraft:scale\",base:2.5d}," +
                "{id:\"minecraft:attack_damage\",base:14d},{id:\"minecraft:movement_speed\",base:0.3d}," +
                "{id:\"minecraft:knockback_resistance\",base:1d},{id:\"minecraft:follow_range\",base:48d},{id:\"minecraft:armor\",base:10d}]," +
                "active_effects:[{id:\"minecraft:invisibility\",duration:-1,amplifier:0b,show_particles:0b,show_icon:0b}]}")
        comando(servidor, nivel, p,
            "summon minecraft:item_display ${p.x} ${p.y} ${p.z} {Tags:[\"apu_modelo\"],teleport_duration:2,view_range:4f," +
                "item:{id:\"minecraft:stone\",count:1,components:{\"minecraft:item_model\":\"banco_minecraft_per:apu\"}},item_display:\"fixed\"," +
                "transformation:{left_rotation:[0f,1f,0f,0f],right_rotation:[0f,0f,0f,1f],translation:[0f,3f,0f],scale:[6f,6f,6f]}}")
        val caja = AABB(c).inflate(4.0)
        jefe = nivel.getEntitiesOfClass(LivingEntity::class.java, caja) { "apu_jefe" in it.entityTags() }.firstOrNull()?.uuid
        modelo = nivel.getEntitiesOfClass(Display::class.java, caja) { "apu_modelo" in it.entityTags() }.firstOrNull()?.uuid
        if (jefe == null) return
        dano.clear(); fase2 = false; soledad = 0; tRocas = 100; tTemblor = 200; tCondores = 300
        barra.name = Component.literal("Apu de la montaña").withStyle(ChatFormatting.GOLD)
        barra.color = BossEvent.BossBarColor.RED
        nivel.playSound(null, c, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2f, 0.6f)
        nivel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 2, p.z, 2, 1.0, 1.0, 1.0, 0.0)
        for (j in nivel.players()) if (j.position().distanceTo(p) < RADIO_PELEA) {
            j.sendSystemMessage(Component.literal("¡El Apu de la montaña despierta!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
        }
    }

    private fun entrarFase2(nivel: ServerLevel, boss: LivingEntity, cerca: List<ServerPlayer>) {
        fase2 = true
        boss.addEffect(MobEffectInstance(MobEffects.RESISTANCE, -1, 0, false, false))
        boss.addEffect(MobEffectInstance(MobEffects.STRENGTH, -1, 0, false, false))
        barra.name = Component.literal("Apu de la montaña · Nevado").withStyle(ChatFormatting.AQUA)
        barra.color = BossEvent.BossBarColor.WHITE
        nivel.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2f, 0.5f)
        nivel.sendParticles(ParticleTypes.SNOWFLAKE, boss.x, boss.y + 3, boss.z, 200, 3.0, 3.0, 3.0, 0.05)
        for (j in cerca) j.sendSystemMessage(Component.literal("El Apu se cubre de nieve… ¡el frío quema!").withStyle(ChatFormatting.AQUA))
    }

    private fun lanzarRoca(servidor: MinecraftServer, nivel: ServerLevel, boss: LivingEntity, objetivo: ServerPlayer) {
        val desde = boss.position().add(0.0, boss.bbHeight * 0.85, 0.0)
        val hasta = objetivo.position()
        val etiqueta = "apu_roca_${UUID.randomUUID().toString().take(8)}"
        comando(servidor, nivel, desde,
            "summon minecraft:block_display ${desde.x} ${desde.y} ${desde.z} {Tags:[\"apu_roca\",\"$etiqueta\"],teleport_duration:1," +
                "block_state:{Name:\"minecraft:cobbled_deepslate\"},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]," +
                "translation:[-0.7f,-0.7f,-0.7f],scale:[1.4f,1.4f,1.4f]}}")
        val d = nivel.getEntitiesOfClass(Display::class.java, AABB.ofSize(desde, 2.0, 2.0, 2.0)) { etiqueta in it.entityTags() }.firstOrNull() ?: return
        rocas += Roca(d.uuid, desde, hasta)
        nivel.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, 1.4f)
    }

    private fun moverRocas(nivel: ServerLevel) {
        if (rocas.isEmpty()) return
        val boss = jefeVivo(nivel)
        for (r in rocas.toList()) {
            val d = nivel.getEntity(r.display)
            if (d == null) { rocas.remove(r); continue }
            r.t++
            val k = r.t / 20.0
            val p = r.desde.lerp(r.hasta, k).add(0.0, sin(k * Math.PI) * 4.0, 0.0)
            d.snapTo(p.x, p.y, p.z, r.t * 18f, 0f)
            if (r.t >= 20) {
                rocas.remove(r)
                d.discard()
                nivel.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState()), p.x, p.y + 0.5, p.z, 40, 0.8, 0.5, 0.8, 0.1)
                nivel.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0.0, 0.0, 0.0, 0.0)
                nivel.playSound(null, BlockPos.containing(p), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.3f)
                for (j in nivel.getEntitiesOfClass(ServerPlayer::class.java, AABB.ofSize(p, 5.0, 4.0, 5.0))) {
                    val fuente = if (boss != null) nivel.damageSources().mobAttack(boss) else nivel.damageSources().generic()
                    j.hurtServer(nivel, fuente, 9f)
                }
            }
        }
    }

    private fun temblor(nivel: ServerLevel, boss: LivingEntity, jugadores: List<ServerPlayer>) {
        nivel.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 0.6f)
        val suelo = BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState())
        for (i in 0 until 24) {
            val a = Math.toRadians(i * 15.0)
            for (r in intArrayOf(3, 6, 9)) {
                nivel.sendParticles(suelo, boss.x + cos(a) * r, boss.y + 0.2, boss.z + sin(a) * r, 4, 0.3, 0.1, 0.3, 0.05)
            }
        }
        for (j in jugadores) {
            if (j.distanceTo(boss) > 10 || !j.onGround()) continue
            j.hurtServer(nivel, nivel.damageSources().mobAttack(boss), 6f)
            j.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 80, 1))
            val fuera = j.position().subtract(boss.position()).let { Vec3(it.x, 0.0, it.z) }.normalize().scale(0.9)
            j.setDeltaMovement(fuera.x, 0.7, fuera.z)
            j.syncVelocity = true
        }
    }

    private fun condores(servidor: MinecraftServer, nivel: ServerLevel, boss: LivingEntity) {
        val vivos = nivel.getEntitiesOfClass(LivingEntity::class.java, boss.boundingBox.inflate(48.0)) { "apu_esbirro" in it.entityTags() }.size
        if (vivos >= 6) return
        repeat(2) {
            val p = boss.position().add(Random.nextDouble(-4.0, 4.0), 7.0, Random.nextDouble(-4.0, 4.0))
            comando(servidor, nivel, p,
                "summon minecraft:phantom ${p.x} ${p.y} ${p.z} {Tags:[\"apu_esbirro\"],PersistenceRequired:1b,size:2," +
                    "CustomName:{text:\"Cóndor oscuro\",color:\"dark_gray\"},DeathLootTable:\"minecraft:empty\"," +
                    "active_effects:[{id:\"minecraft:fire_resistance\",duration:-1,amplifier:0b,show_particles:0b}]}")
        }
        nivel.playSound(null, boss.blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.5f)
    }

    /** Fase 2: quien se queda a menos de 12 bloques se va congelando (como en nieve en polvo). */
    private fun helada(nivel: ServerLevel, boss: LivingEntity, jugadores: List<ServerPlayer>) {
        nivel.sendParticles(ParticleTypes.SNOWFLAKE, boss.x, boss.y + 2, boss.z, 60, 8.0, 2.0, 8.0, 0.02)
        for (j in jugadores) {
            if (j.distanceTo(boss) > 12) continue
            j.ticksFrozen = minOf(j.ticksFrozen + 60, 220)
        }
    }

    private fun vencido(servidor: MinecraftServer, boss: Entity) {
        val nivel = boss.level() as ServerLevel
        val semana = semana()
        val minimo = VIDA * 0.05
        val ganadores = dano.filter { it.value >= minimo }.keys.mapNotNull { servidor.playerList.getPlayer(it) }
        val nombres = ArrayList<String>()
        for (j in ganadores) {
            nombres += j.name.string
            if (datos.cobros[j.uuid.toString()] == semana) {
                j.sendSystemMessage(Component.literal("Ya recibiste el botín del Apu esta semana. ¡Gracias por ayudar!").withStyle(ChatFormatting.GRAY))
                continue
            }
            datos.cobros[j.uuid.toString()] = semana
            val r = Operaciones.abonar(j, PREMIO, "APU")
            val tumbaga = GuiOfertas.crearTexto(servidor, Maquinas.CATALOGO_TUMBAGA)
            val cuantas = 3 + Random.nextInt(3)
            if (tumbaga != null) { tumbaga.count = cuantas; Inventarios.dar(j, tumbaga) }
            var extra = ""
            if (j.uuid.toString() !in datos.trofeo) {
                GuiOfertas.crearTexto(servidor, CHAMPI)?.let { Inventarios.dar(j, it); datos.trofeo += j.uuid.toString(); extra = " y el Champi del Apu" }
            }
            Efectos.sonar(j, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            j.sendSystemMessage(Component.literal("Botín del Apu: ${Formato.soles(PREMIO)}, $cuantas tumbaga$extra. ${r.mensaje}").withStyle(ChatFormatting.GOLD))
        }
        if (nombres.isNotEmpty()) {
            Logros.anunciar(servidor, "¡${nombres.joinToString(", ")} vencieron al Apu de la montaña! Despertará otra vez en 30 minutos.")
        }
        datos.proximo = System.currentTimeMillis() + ESPERA_MS
        guardar()
        nivel.sendParticles(ParticleTypes.SNOWFLAKE, boss.x, boss.y + 2, boss.z, 300, 3.0, 3.0, 3.0, 0.1)
        retirar(servidor)
        estatua(servidor, true)
    }

    /** Quita al jefe, su modelo, sus cóndores y la barra (sin premio). */
    private fun retirar(servidor: MinecraftServer) {
        val nivel = nivel(servidor)
        if (nivel != null) {
            jefe?.let { nivel.getEntity(it) }?.discard()
            modelo?.let { nivel.getEntity(it) }?.discard()
            if (datos.generado && nivel.isLoaded(centro())) huerfanos(nivel, centro())
            rocas.forEach { nivel.getEntity(it.display)?.discard() }
        }
        rocas.clear()
        jefe = null; modelo = null; dano.clear(); fase2 = false; soledad = 0
        barra.removeAllPlayers()
    }

    /** Restos de una pelea anterior (por ejemplo, tras reiniciar el servidor). */
    private fun huerfanos(nivel: ServerLevel, c: BlockPos) {
        val caja = AABB(c).inflate(64.0)
        nivel.getEntitiesOfClass(Entity::class.java, caja) {
            val t = it.entityTags()
            ("apu_jefe" in t && it.uuid != jefe) || ("apu_modelo" in t && it.uuid != modelo) ||
                ("apu_esbirro" in t && jefe == null) || ("apu_roca" in t && rocas.none { r -> r.display == it.uuid })
        }.forEach { it.discard() }
    }

    /** Lo que cuenta el Llamero viajero. */
    fun rumor(j: ServerPlayer) {
        val servidor = j.level().server ?: return
        if (!datos.generado) {
            j.sendSystemMessage(Component.literal("Llamero viajero: «Aún no sé de ningún Apu despierto por estas tierras…»").withStyle(ChatFormatting.GRAY))
            return
        }
        val nivel = nivel(servidor)
        val estado = when {
            nivel != null && jefeVivo(nivel) != null -> "Dicen que ahora mismo está despierto y peleando."
            System.currentTimeMillis() < datos.proximo -> {
                val min = (datos.proximo - System.currentTimeMillis()) / 60000 + 1
                "Lo vencieron hace poco; volverá a despertar en unos $min minutos."
            }
            else -> "Duerme convertido en piedra, esperando a quien se acerque."
        }
        val donde = if (datos.dim == "minecraft:overworld") "" else " (${datos.dim})"
        j.sendSystemMessage(
            Component.literal("Llamero viajero: «Dicen que en lo alto de la montaña, cerca de ${datos.x}, ${datos.y}, ${datos.z}$donde, " +
                "duerme el Apu de la montaña. $estado»").withStyle(ChatFormatting.YELLOW),
        )
        if (datos.cobros[j.uuid.toString()] == semana()) {
            j.sendSystemMessage(Component.literal("«Tú ya recibiste su botín esta semana; el lunes podrás volver a cobrarlo.»").withStyle(ChatFormatting.GRAY))
        } else {
            j.sendSystemMessage(Component.literal("«Quien lo vence se lleva ${Formato.soles(PREMIO)} y tumbaga. Mejor ve con amigos.»").withStyle(ChatFormatting.GRAY))
        }
    }
}
