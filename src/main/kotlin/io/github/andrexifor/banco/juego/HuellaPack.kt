package io.github.andrexifor.banco.juego

import io.github.andrexifor.banco.BancoMinecraftPer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Huella (SHA1) del resource pack, calculada sola. Al arrancar, el mod descarga el pack de la dirección
 * configurada en el servidor (resource-pack) y calcula su SHA1; DedicatedServerMixin se la entrega a cada
 * jugador al entrar, con un id nuevo por huella. Así, cuando el pack cambia, todos bajan el nuevo sin
 * tener que escribir RESOURCE_PACK_SHA1 a mano. Mientras se calcula (unos segundos) se usa lo del
 * server.properties.
 */
object HuellaPack {
    private val huellas = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun huella(url: String): String? = huellas[url]

    /** id del pack para el cliente: cambia con la huella, así nunca reutiliza una copia vieja. */
    @JvmStatic
    fun id(huella: String): UUID = UUID.nameUUIDFromBytes("banco_minecraft_per:$huella".toByteArray())

    fun registrarEventos() {
        ServerLifecycleEvents.SERVER_STARTED.register { servidor ->
            val url = servidor.serverResourcePack.map { it.url() }.orElse(null)
            if (url.isNullOrBlank()) return@register
            Thread({ calcular(url) }, "banco-huella-pack").apply { isDaemon = true }.start()
        }
    }

    private fun calcular(url: String) {
        try {
            val cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build()
            val pedido = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build()
            val respuesta = cliente.send(pedido, HttpResponse.BodyHandlers.ofByteArray())
            if (respuesta.statusCode() != 200) {
                BancoMinecraftPer.LOGGER.warn("No se pudo bajar el resource pack para su huella (HTTP {}): {}", respuesta.statusCode(), url)
                return
            }
            val sha1 = MessageDigest.getInstance("SHA-1").digest(respuesta.body()).joinToString("") { "%02x".format(it) }
            huellas[url] = sha1
            BancoMinecraftPer.LOGGER.info("Huella del resource pack calculada: {} ({} KB)", sha1, respuesta.body().size / 1024)
        } catch (e: Exception) {
            BancoMinecraftPer.LOGGER.warn("No se pudo calcular la huella del resource pack: {}", e.message)
        }
    }
}
