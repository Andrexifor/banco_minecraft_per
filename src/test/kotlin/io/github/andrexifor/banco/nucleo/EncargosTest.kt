package io.github.andrexifor.banco.nucleo

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncargosTest {
    @Test
    fun `la lista grande tiene al menos 200 encargos con ids unicos`() {
        val todos = Encargos.todos
        assertTrue(todos.size >= 200, "hay ${todos.size}")
        assertEquals(todos.size, todos.map { it.id }.toSet().size)
        assertTrue(todos.all { it.cantidad > 0 && it.nivel in 0..2 })
    }

    @Test
    fun `cada tanda trae 10 encargos y las dos del dia no se repiten`() {
        var dia = LocalDate.of(2026, 11, 19)
        repeat(60) {
            val manana = Encargos.delCiclo(dia, tarde = false)
            val tarde = Encargos.delCiclo(dia, tarde = true)
            assertEquals(10, manana.size)
            assertEquals(10, tarde.size)
            assertTrue(manana.map { it.id }.intersect(tarde.map { it.id }.toSet()).isEmpty())
            assertEquals(listOf(5, 3, 2), (0..2).map { n -> manana.count { it.nivel == n } })
            dia = dia.plusDays(1)
        }
    }

    @Test
    fun `la tanda es la misma para todos y no cambia al reiniciar`() {
        val d = LocalDate.of(2026, 12, 24)
        assertEquals(Encargos.delCiclo(d, true), Encargos.delCiclo(d, true))
    }

    @Test
    fun `solo los dificiles pagan tumbaga`() {
        assertTrue(Encargos.todos.filter { it.nivel < 2 }.all { it.tumbaga == 0 })
        assertTrue(Encargos.todos.filter { it.nivel == 2 }.all { it.tumbaga in 1..2 })
    }
}
