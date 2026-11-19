package io.github.andrexifor.banco.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogrosTest {
    @Test
    fun `logros de saldo se dan por umbral y una sola vez`() {
        val d = DatosJugador()
        assertTrue(d.logrosPorSaldo(500).isEmpty())
        assertEquals(listOf(Logro.PRIMER_MILLON), d.logrosPorSaldo(1_000_000))
        d.otorgados().add(Logro.PRIMER_MILLON.id)
        assertEquals(listOf(Logro.RICO_MCPATO), d.logrosPorSaldo(70_000_000))
    }

    @Test
    fun `bancarrota solo si antes tuvo mas de cien mil`() {
        val rico = DatosJugador()
        rico.logrosPorSaldo(150_000)
        assertEquals(listOf(Logro.BANCARROTA), rico.logrosPorSaldo(0))
        val pobre = DatosJugador()
        pobre.logrosPorSaldo(50_000)
        assertTrue(pobre.logrosPorSaldo(0).isEmpty())
    }

    @Test
    fun `contadores acumulan`() {
        val d = DatosJugador(contadores = null)
        assertEquals(5, d.sumar(Contador.VENDIDOS, 5))
        assertEquals(12, d.sumar(Contador.VENDIDOS, 7))
    }

    @Test
    fun `catalogos por defecto`() {
        val bromas = CatalogoOfertas.bromasPorDefecto()
        assertNotNull(bromas.buscar(CatalogoOfertas.VARA_VERDAD))
        assertTrue(bromas.buscar("puyu_inti")!!.item.contains("flying_speed"))
        assertEquals(6, CatalogoOfertas(null).normalizado(CatalogoOfertas::contrabandoPorDefecto).ofertas!!.size)
        assertEquals(-5.5, ConfigMercadoNegro(null, null).normalizada().entrada!!.z)
    }
}
