package io.github.andrexifor.banco.nucleo

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BancoTest {
    private val andre = UUID.randomUUID()
    private val amigo = UUID.randomUUID()
    private lateinit var banco: Banco

    @BeforeEach
    fun preparar() {
        banco = Banco()
        banco.abrirCuenta(andre, "Andrexifor", 500)
        banco.abrirCuenta(amigo, "Amigo", 100)
    }

    @Test
    fun `abrir cuenta dos veces no reinicia el saldo`() {
        banco.depositar(andre, 1_000)
        assertFalse(banco.abrirCuenta(andre, "Andrexifor", 500))
        assertEquals(1_500, banco.saldo(andre))
    }

    @Test
    fun `retirar mas de lo que hay falla y no cambia el saldo`() {
        val r = banco.retirar(andre, 501)
        assertIs<Resultado.SaldoInsuficiente>(r)
        assertEquals(500, banco.saldo(andre))
    }

    @Test
    fun `retirar exacto deja en cero`() {
        assertIs<Resultado.Ok>(banco.retirar(andre, 500))
        assertEquals(0, banco.saldo(andre))
    }

    @Test
    fun `montos cero o negativos son invalidos`() {
        assertIs<Resultado.MontoInvalido>(banco.depositar(andre, 0))
        assertIs<Resultado.MontoInvalido>(banco.retirar(andre, -5))
        assertIs<Resultado.MontoInvalido>(banco.transferir(andre, amigo, 0, 0.0))
    }

    @Test
    fun `transferencia sin impuesto conserva el dinero total`() {
        val r = banco.transferir(andre, amigo, 200, 0.0)
        assertIs<Resultado.Ok>(r)
        assertEquals(300, banco.saldo(andre))
        assertEquals(300, banco.saldo(amigo))
    }

    @Test
    fun `con impuesto paga el emisor y el impuesto desaparece`() {
        val r = banco.transferir(andre, amigo, 200, 5.0)
        assertIs<Resultado.Ok>(r)
        assertEquals(10, r.impuesto)
        assertEquals(290, banco.saldo(andre)) // 500 - 200 - 10
        assertEquals(300, banco.saldo(amigo))
    }

    @Test
    fun `si no alcanza para monto mas impuesto falla`() {
        val r = banco.transferir(andre, amigo, 490, 5.0) // 490 + 24 = 514 > 500
        assertIs<Resultado.SaldoInsuficiente>(r)
        assertEquals(500, banco.saldo(andre))
        assertEquals(100, banco.saldo(amigo))
    }

    @Test
    fun `no puedes pagarte a ti mismo`() {
        assertIs<Resultado.MontoInvalido>(banco.transferir(andre, andre, 10, 0.0))
    }

    @Test
    fun `pagar a una cuenta inexistente falla`() {
        assertIs<Resultado.CuentaInexistente>(banco.transferir(andre, UUID.randomUUID(), 10, 0.0))
    }

    @Test
    fun `no se puede superar el saldo maximo`() {
        banco.fijar(andre, Banco.SALDO_MAXIMO)
        assertIs<Resultado.MontoInvalido>(banco.depositar(andre, 1))
        assertEquals(Banco.SALDO_MAXIMO, banco.saldo(andre))
    }

    @Test
    fun `ranking ordena de mayor a menor`() {
        banco.depositar(amigo, 10_000)
        val top = banco.ranking(10)
        assertEquals(listOf(amigo, andre), top.map { it.first })
    }

    @Test
    fun `impuesto redondea hacia abajo`() {
        assertEquals(0, banco.calcularImpuesto(19, 5.0))  // 0.95 -> 0
        assertEquals(1, banco.calcularImpuesto(20, 5.0))  // 1.00 -> 1
        assertEquals(0, banco.calcularImpuesto(1_000, 0.0))
        assertTrue(banco.calcularImpuesto(1_000_000, 10.0) == 100_000L)
    }
}
