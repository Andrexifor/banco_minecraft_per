package io.github.andrexifor.banco.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZonasTest {
    private val plaza = Zona("plaza", "overworld", 116, 320, 84, 76, -64, 44) // esquinas al revés

    @Test
    fun `contiene respeta limites y dimension`() {
        assertTrue(plaza.contiene("overworld", 96, 69, 64))
        assertTrue(plaza.contiene("overworld", 76, 68, 44))
        assertTrue(plaza.contiene("overworld", 116, 68, 84))
        assertFalse(plaza.contiene("overworld", 117, 68, 64))
        assertFalse(plaza.contiene("the_nether", 96, 69, 64))
    }

    @Test
    fun `margen amplia la zona`() {
        assertFalse(plaza.contiene("overworld", 120, 68, 64))
        assertTrue(plaza.contiene("overworld", 120, 68, 64, margen = 5))
    }

    @Test
    fun `zona inactiva no protege`() {
        assertFalse(plaza.copy(activa = false).contiene("overworld", 96, 69, 64))
    }

    @Test
    fun `normalizada quita el prefijo minecraft y usa defectos`() {
        val c = ConfigZonas(listOf(Zona(nombre = "", dimension = "minecraft:the_nether"))).normalizada()
        assertEquals("the_nether", c.zonas!![0].dimension)
        assertEquals("zona", c.zonas!![0].nombre)
        assertEquals(2, ConfigZonas(null).normalizada().zonas!!.size)
    }

    @Test
    fun `por defecto vienen apagadas`() {
        val c = ConfigZonas().normalizada()
        assertNull(c.buscar("overworld", 96, 69, 64))
        assertFalse(c.cambiar("no_existe", true))
    }

    @Test
    fun `al activarlas protegen la plaza y la sala del nether`() {
        val c = ConfigZonas().normalizada()
        assertTrue(c.cambiar("plaza_spawn", true))
        assertTrue(c.cambiar("sala_portal_nether", true))
        assertEquals("plaza_spawn", c.buscar("overworld", 96, 69, 64)?.nombre)
        assertEquals("sala_portal_nether", c.buscar("the_nether", 11, 117, 5)?.nombre)
        assertEquals("sala_portal_nether", c.buscar("the_nether", 40, 100, 5)?.nombre) // túnel
        assertNull(c.buscar("overworld", 0, 69, 0))
    }
}
