package io.github.andrexifor.banco.juego

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.andrexifor.banco.nucleo.Banco
import io.github.andrexifor.banco.nucleo.Configuracion
import io.github.andrexifor.banco.nucleo.Cuenta
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Persistencia en archivos JSON:
 *  - config/banco_minecraft_per/banco.json     -> configuración (editable a mano)
 *  - banco_minecraft_per/cuentas.json           -> saldos (dentro de /data: entra en los backups)
 *  - banco_minecraft_per/transacciones.log      -> registro de cada movimiento
 */
class Almacen(private val dirConfig: Path, private val dirDatos: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private val archivoConfig: Path get() = dirConfig.resolve("banco.json")
    private val archivoCuentas: Path get() = dirDatos.resolve("cuentas.json")
    private val archivoLog: Path get() = dirDatos.resolve("transacciones.log")

    fun cargarConfiguracion(): Configuracion {
        Files.createDirectories(dirConfig)
        if (!Files.exists(archivoConfig)) {
            val porDefecto = Configuracion()
            escribirAtomico(archivoConfig, gson.toJson(porDefecto))
            return porDefecto
        }
        val texto = Files.readString(archivoConfig, StandardCharsets.UTF_8)
        val leida = gson.fromJson(texto, Configuracion::class.java) ?: Configuracion()
        return leida.normalizada()
    }

    fun cargarBanco(): Banco {
        Files.createDirectories(dirDatos)
        if (!Files.exists(archivoCuentas)) return Banco()
        val raiz = JsonParser.parseString(Files.readString(archivoCuentas, StandardCharsets.UTF_8)).asJsonObject
        val cuentas = linkedMapOf<UUID, Cuenta>()
        raiz.getAsJsonObject("cuentas")?.entrySet()?.forEach { (uuid, valor) ->
            val obj = valor.asJsonObject
            cuentas[UUID.fromString(uuid)] = Cuenta(
                nombre = obj.get("nombre")?.asString ?: "?",
                saldo = obj.get("saldo")?.asLong ?: 0,
            )
        }
        return Banco(cuentas)
    }

    fun guardarBanco(banco: Banco) {
        Files.createDirectories(dirDatos)
        val cuentas = JsonObject()
        banco.todas().forEach { (uuid, c) ->
            cuentas.add(
                uuid.toString(),
                JsonObject().apply {
                    addProperty("nombre", c.nombre)
                    addProperty("saldo", c.saldo)
                },
            )
        }
        val raiz = JsonObject().apply {
            addProperty("version", 1)
            add("cuentas", cuentas)
        }
        escribirAtomico(archivoCuentas, gson.toJson(raiz))
    }

    /** Una línea por movimiento: fecha;tipo;origen;destino;monto;impuesto;detalle */
    fun registrar(tipo: String, origen: String, destino: String, monto: Long, impuesto: Long = 0, detalle: String = "") {
        Files.createDirectories(dirDatos)
        val linea = listOf(OffsetDateTime.now().toString(), tipo, origen, destino, monto, impuesto, detalle)
            .joinToString(";") + System.lineSeparator()
        Files.writeString(
            archivoLog,
            linea,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    /** Escribe a un temporal y lo mueve: si el servidor se cae a mitad, no se corrompe el archivo. */
    private fun escribirAtomico(destino: Path, contenido: String) {
        val tmp = destino.resolveSibling(destino.fileName.toString() + ".tmp")
        Files.writeString(tmp, contenido, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            // Algunos montajes de Docker en Windows no soportan el movimiento atómico
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
