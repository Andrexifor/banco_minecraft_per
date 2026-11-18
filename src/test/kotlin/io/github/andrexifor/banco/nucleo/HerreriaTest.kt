package io.github.andrexifor.banco.nucleo

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HerreriaTest {
    private val cfg = ConfigHerrero()

    @Test
    fun `reparar cuesta base mas porcentaje de dano`() {
        // 50 + 5 × 50% = 300
        assertEquals(300, cfg.precioReparacion(dano = 500, danoMaximo = 1000, legendario = false))
    }

    @Test
    fun `un punto de dano cuenta como 1 por ciento`() {
        assertEquals(55, cfg.precioReparacion(dano = 1, danoMaximo = 2031, legendario = false))
    }

    @Test
    fun `legendario multiplica el precio`() {
        assertEquals(900, cfg.precioReparacion(dano = 500, danoMaximo = 1000, legendario = true))
    }

    @Test
    fun `sin dano no cobra`() {
        assertEquals(0, cfg.precioReparacion(dano = 0, danoMaximo = 1000, legendario = true))
    }

    @Test
    fun `combinar niveles iguales sube uno con tope`() {
        assertEquals(4, cfg.nivelCombinado(actual = 3, libro = 3, tope = 5))
        assertEquals(5, cfg.nivelCombinado(actual = 5, libro = 5, tope = 5)) // ya en el tope
        assertEquals(4, cfg.nivelCombinado(actual = 1, libro = 4, tope = 5)) // se queda el mayor
        assertEquals(3, cfg.nivelCombinado(actual = 3, libro = 1, tope = 5)) // no empeora
    }

    @Test
    fun `el herrero no toca niveles legendarios por encima del tope`() {
        assertEquals(6, cfg.nivelCombinado(actual = 6, libro = 5, tope = 5))
    }

    @Test
    fun `clasificacion de kits`() {
        assertTrue(TipoKit.ARMAS.reparaEnMano("netherite_sword"))
        assertTrue(TipoKit.ARMAS.reparaEnMano("diamond_axe"))
        assertTrue(TipoKit.ARMAS.reparaEnMano("mace"))
        assertTrue(TipoKit.HERRAMIENTAS.reparaEnMano("diamond_axe")) // hacha en los dos
        assertTrue(TipoKit.HERRAMIENTAS.reparaEnMano("elytra"))
        assertTrue(TipoKit.HERRAMIENTAS.reparaEnMano("shield"))
        assertFalse(TipoKit.ARMAS.reparaEnMano("diamond_pickaxe"))
        assertFalse(TipoKit.HERRAMIENTAS.reparaEnMano("netherite_sword"))
        assertFalse(TipoKit.ARMADURA.reparaEnMano("diamond_chestplate")) // va por espacios equipados
    }

    @Test
    fun `precios de kits por tipo y usos`() {
        assertEquals(2_500, cfg.precioKit(TipoKit.ARMADURA, 1))
        assertEquals(3_500, cfg.precioKit(TipoKit.HERRAMIENTAS, 3))
        assertNull(cfg.precioKit(TipoKit.ARMAS, 2))
    }

    @Test
    fun `legendarios por defecto son validos y nunca traen reparacion`() {
        val c = CatalogoLegendarios.porDefecto()
        assertEquals(c.legendarios.size, c.normalizado().legendarios.size)
        assertTrue(c.legendarios.none { "minecraft:mending" in it.encantamientos })
        assertTrue(c.legendarios.all { it.precio >= 600_000 })
        assertEquals(c.legendarios.size, c.legendarios.map { it.id }.toSet().size)
    }
}
