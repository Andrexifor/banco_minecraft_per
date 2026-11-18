package io.github.andrexifor.banco.nucleo

import java.util.Locale

object Formato {
    /** 1234567 -> "S/ 1,234,567" (separador de miles con coma, como en Perú). */
    fun soles(monto: Long): String = "S/ " + String.format(Locale.US, "%,d", monto)
}
