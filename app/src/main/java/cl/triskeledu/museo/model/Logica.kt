package cl.triskeledu.museo.model

import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.ceil
import kotlin.math.roundToInt

/* Lógica pura (sin Android): roles, navegación, validaciones, envío, QR y seguridad. */

// ---------------------------------------------------------------- Roles y permisos
enum class Rol(val etiqueta: String) {
    ADMINISTRADOR("Administrador"), INVESTIGADOR("Investigador"), CLIENTE("Cliente");

    val veCatalogo get() = this != INVESTIGADOR      // Pantalla 9
    val compra get() = this == CLIENTE               // Pantallas 10, 11 y 12
    val gestiona get() = this == ADMINISTRADOR       // Catálogo, solicitudes, publicación, avisos, cuentas
    val escanea get() = this != ADMINISTRADOR        // Pantallas 7 y 8
    val editaFichas get() = this != CLIENTE          // Registrar o corregir fichas
    val veNoPublicadas get() = this != CLIENTE
}

// ---------------------------------------------------------------- Navegación
/** Destinos de la app (como el enum Pantalla del ejemplo de formularios, pero con parámetros). */
sealed interface Pantalla {
    data object Bienvenida : Pantalla
    data object Acceso : Pantalla
    data object Registro : Pantalla
    data object Inicio : Pantalla
    data object Mapa : Pantalla
    data class Museo(val id: Long) : Pantalla
    data class FichaMuseo(val id: Long?) : Pantalla
    data object Escaner : Pantalla
    data object ResultadoQr : Pantalla
    data object Catalogo : Pantalla
    data class Producto(val id: Long) : Pantalla
    data class FichaProducto(val id: Long?) : Pantalla
    data object Favoritos : Pantalla
    data object Carrito : Pantalla
    data object Solicitudes : Pantalla
    data object Avisos : Pantalla
    data object Perfil : Pantalla

    fun permitida(rol: Rol) = when (this) {
        Catalogo -> rol.veCatalogo
        is FichaProducto -> rol.gestiona
        is FichaMuseo -> rol.editaFichas
        Favoritos, Carrito -> rol.compra
        Solicitudes -> rol != Rol.INVESTIGADOR
        Escaner, ResultadoQr -> rol.escanea
        else -> true
    }

    companion object {
        /** "museo:3", "producto:5", "solicitudes"... → pantalla (notificaciones e historial QR). */
        fun desde(destino: String?): Pantalla? {
            val partes = destino?.split(":") ?: return null
            val id = partes.getOrNull(1)?.toLongOrNull()
            return when (partes[0]) {
                "museo" -> id?.let(::Museo)
                "producto" -> id?.let(::Producto)
                "solicitudes" -> Solicitudes
                "catalogo" -> Catalogo
                "mapa" -> Mapa
                else -> null
            }
        }
    }
}

// ---------------------------------------------------------------- Formularios
/** Igual que el ejemplo: texto escrito + mensaje de error (null = válido). */
data class CampoFormulario(val valor: String = "", val error: String? = null) {
    val esValido get() = error == null
}

fun campo(valor: String, regla: (String) -> String?) = CampoFormulario(valor, regla(valor))

object Validar {
    fun texto(v: String, min: Int = 3) = when {
        v.isBlank() -> "Campo obligatorio"
        v.trim().length < min -> "Escribe al menos $min caracteres"
        else -> null
    }
    fun correo(v: String) = if (Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(v.trim())) null else "Ingresa un correo válido"
    fun clave(v: String) = when {
        v.length < 8 -> "Usa al menos 8 caracteres"
        v.none(Char::isLetter) || v.none(Char::isDigit) -> "Incluye letras y números"
        else -> null
    }
    fun url(v: String) = if (Regex("^https?://\\S+\\.\\S+$").matches(v.trim())) null else "Debe comenzar con https://"
    fun numero(v: String, rango: ClosedFloatingPointRange<Double>): String? {
        val n = v.replace(',', '.').trim().toDoubleOrNull() ?: return "Debe ser un número"
        return if (n in rango) null else "Fuera de rango"
    }
    fun entero(v: String): String? {
        val n = v.trim().toIntOrNull() ?: return "Debe ser un número entero"
        return if (n >= 0) null else "No puede ser negativo"
    }
    fun codigo(v: String) = if (Regex("^[A-Za-z0-9-]{3,20}$").matches(v.trim())) null else "3 a 20 letras, números o guiones"
}

// ---------------------------------------------------------------- Envío ficticio
enum class Destino(val etiqueta: String) { NACIONAL("Nacional (Chile)"), INTERNACIONAL("Internacional") }
enum class Modalidad(val etiqueta: String, val factor: Double) { ESTANDAR("Estándar", 1.0), EXPRESS("Express", 1.6) }

/**
 * Reglas SIMULADAS (sin courier real): sin peso = sin envío; nacional $3.500 + $1.200/kg;
 * internacional $14.000 + $6.500/kg; express ×1,6; nacional estándar gratis desde $80.000.
 */
fun calcularEnvio(gramos: Int, subtotal: Int, destino: Destino, modalidad: Modalidad): Int {
    if (gramos <= 0) return 0
    if (destino == Destino.NACIONAL && modalidad == Modalidad.ESTANDAR && subtotal >= 80_000) return 0
    val kilos = ceil(gramos / 1000.0).toInt()
    val (base, porKilo) = if (destino == Destino.NACIONAL) 3_500 to 1_200 else 14_000 to 6_500
    return ((base + porKilo * kilos) * modalidad.factor / 100).roundToInt() * 100
}

// ---------------------------------------------------------------- QR (JSON, como el ejemplo)
data class ContenidoQr(val tipo: String, val codigo: String, val campos: List<Pair<String, String>>)

object LectorQr {
    private val TIPOS = setOf("museo", "exposicion", "publicacion", "producto", "curso")

    /** Devuelve el contenido o un mensaje de error si el JSON no sirve. */
    fun leer(texto: String): Result<ContenidoQr> = try {
        val json = JSONObject(texto.trim())
        val tipo = json.optString("tipo").lowercase()
        val codigo = json.optString("codigo").uppercase()
        when {
            tipo !in TIPOS -> Result.failure(Exception("Tipo «$tipo» desconocido. Usa: ${TIPOS.joinToString()}."))
            codigo.isBlank() -> Result.failure(Exception("Falta el campo \"codigo\"."))
            else -> Result.success(ContenidoQr(tipo, codigo, json.keys().asSequence().map { it to json.optString(it) }.toList()))
        }
    } catch (e: JSONException) {
        Result.failure(Exception("El QR no contiene un JSON válido."))
    }

    fun generar(tipo: String, codigo: String, titulo: String, url: String = ""): String =
        JSONObject().put("tipo", tipo).put("codigo", codigo).put("titulo", titulo).apply { if (url.isNotBlank()) put("url", url) }.toString()
}

// ---------------------------------------------------------------- Seguridad y formato
/** Contraseñas con PBKDF2 + sal: en SQLite solo queda "sal$hash". */
object Seguridad {
    private fun derivar(clave: String, sal: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(clave.toCharArray(), sal, 10_000, 256)).encoded
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.bytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun hash(clave: String): String {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "${sal.hex()}$${derivar(clave, sal).hex()}"
    }

    fun verificar(clave: String, guardado: String) = runCatching {
        val (sal, hash) = guardado.split("$")
        MessageDigest.isEqual(derivar(clave, sal.bytes()), hash.bytes())
    }.getOrDefault(false)
}

private val CHILE = Locale.forLanguageTag("es-CL")
fun clp(monto: Int): String = "$" + NumberFormat.getIntegerInstance(CHILE).format(monto)
fun fecha(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", CHILE).format(Date(ms))

val AVATARES = listOf("🏛️", "🔍", "🎨", "🏺", "📜", "🎭", "⭐", "🌎")
