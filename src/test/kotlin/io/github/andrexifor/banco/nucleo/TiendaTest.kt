package io.github.andrexifor.banco.nucleo

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiendaTest {

    private val diamante = Producto("minecraft:diamond", "minerales", compra = 160, venta = 80)

    @Test
    fun `compra suma el impuesto aparte`() {
        val (subtotal, impuesto) = Precios.compra(diamante, 3, 5.0)
        assertEquals(480, subtotal)
        assertEquals(24, impuesto)
    }

    @Test
    fun `venta descuenta el impuesto`() {
        val (neto, impuesto) = Precios.venta(diamante, 10, 10.0)
        assertEquals(720, neto)
        assertEquals(80, impuesto)
    }

    @Test
    fun `venta sin impuesto paga completo`() {
        assertEquals(800L to 0L, Precios.venta(diamante, 10, 0.0))
    }

    @Test
    fun `catalogo por defecto es valido y no tiene repetidos`() {
        val c = Catalogo.porDefecto()
        val n = c.normalizado()
        assertEquals(c.productos.size, n.productos.size)
        assertEquals(n.productos.size, n.productos.map { it.item }.toSet().size)
        assertTrue(n.productos.all { it.categoria in n.categorias.map { cat -> cat.id } })
    }

    @Test
    fun `normalizar quita productos sin precio, con categoria inexistente o repetidos`() {
        val c = Catalogo(
            categorias = mutableListOf(Categoria("a", "A")),
            productos = mutableListOf(
                Producto("minecraft:stone", "a", compra = 1),
                Producto("minecraft:stone", "a", compra = 5), // repetido
                Producto("minecraft:dirt", "a"), // sin precio
                Producto("minecraft:sand", "no-existe", compra = 1),
                Producto("minecraft:arrow", "a", compra = 8, lote = 500), // lote fuera de rango
            ),
        ).normalizado()
        assertEquals(listOf("minecraft:stone", "minecraft:arrow"), c.productos.map { it.item })
        assertEquals(64, c.buscar("minecraft:arrow")!!.lote)
    }

    @Test
    fun `cambiar a sencillo nunca usa la misma denominacion`() {
        for (d in Denominacion.entries) {
            val cambio = Desglose.desglosar(d.valor, menorQue = d.valor)
            if (d == Denominacion.MONEDA_1) {
                assertTrue(cambio.isEmpty())
            } else {
                assertTrue(cambio.keys.all { it.valor < d.valor }, "cambio de ${d.modelo}")
                assertEquals(d.valor, Desglose.total(cambio))
            }
        }
    }

    @Test
    fun `cambio de 100 es 50 + 50`() {
        assertEquals(mapOf(Denominacion.BILLETE_50 to 2L), Desglose.desglosar(100, menorQue = 100))
    }
}
