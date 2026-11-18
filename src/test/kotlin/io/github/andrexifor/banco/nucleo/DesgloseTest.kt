package io.github.andrexifor.banco.nucleo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class DesgloseTest {

    @Test
    fun `87 soles se desglosa en 50+20+10+5+2`() {
        val d = Desglose.desglosar(87)
        assertEquals(
            mapOf(
                Denominacion.BILLETE_50 to 1L,
                Denominacion.BILLETE_20 to 1L,
                Denominacion.BILLETE_10 to 1L,
                Denominacion.MONEDA_5 to 1L,
                Denominacion.MONEDA_2 to 1L,
            ),
            d,
        )
    }

    @Test
    fun `un millon usa solo bonos de 10000`() {
        assertEquals(mapOf(Denominacion.BONO_10000 to 100L), Desglose.desglosar(1_000_000))
    }

    @Test
    fun `cero no genera piezas`() {
        assertEquals(emptyMap(), Desglose.desglosar(0))
    }

    @Test
    fun `monto negativo es rechazado`() {
        assertThrows<IllegalArgumentException> { Desglose.desglosar(-1) }
    }

    @ParameterizedTest
    @ValueSource(longs = [1, 3, 4, 7, 9, 99, 388, 499, 1_234, 9_999, 10_001, 123_456_789])
    fun `el total del desglose siempre es igual al monto`(monto: Long) {
        assertEquals(monto, Desglose.total(Desglose.desglosar(monto)))
    }

    @Test
    fun `el desglose es minimo en piezas (comparado con fuerza bruta)`() {
        // Programación dinámica: mínimo de piezas para cada monto hasta 2000
        val limite = 2_000
        val minimo = IntArray(limite + 1) { Int.MAX_VALUE }
        minimo[0] = 0
        for (m in 1..limite) {
            for (den in Denominacion.entries) {
                val v = den.valor.toInt()
                if (v <= m && minimo[m - v] != Int.MAX_VALUE) {
                    minimo[m] = minOf(minimo[m], minimo[m - v] + 1)
                }
            }
        }
        for (m in 1..limite) {
            val piezas = Desglose.desglosar(m.toLong()).values.sum()
            assertEquals(minimo[m].toLong(), piezas, "monto $m")
        }
    }

    @Test
    fun `casillas necesarias redondea hacia arriba por stack de 64`() {
        val d = mapOf(Denominacion.BONO_10000 to 130L, Denominacion.MONEDA_1 to 1L)
        // 130 bonos = 3 casillas (64+64+2), 1 moneda = 1 casilla
        assertEquals(4, Desglose.casillasNecesarias(d))
    }

    @Test
    fun `formato con separador de miles`() {
        assertEquals("S/ 1,234,567", Formato.soles(1_234_567))
        assertEquals("S/ 0", Formato.soles(0))
        assertEquals("Bono S/ 10,000", Denominacion.BONO_10000.nombreCorto)
    }

    @Test
    fun `hay 12 denominaciones con modelos unicos`() {
        assertEquals(12, Denominacion.entries.size)
        assertEquals(12, Denominacion.entries.map { it.modelo }.toSet().size)
    }
}
