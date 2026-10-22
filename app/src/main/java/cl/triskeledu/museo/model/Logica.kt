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

// Reglas de la app: roles, pantallas, validaciones, envío, QR y contraseñas.

// ---------------------------------------------------------------- Roles y permisos
// Qué puede hacer cada rol.
enum class Rol(val etiqueta: String) {
    ADMINISTRADOR("Administrador"), INVESTIGADOR("Investigador"), CLIENTE("Cliente");

    val veCatalogo get() = this != INVESTIGADOR      // Ve el catálogo
    val compra get() = this == CLIENTE               // Compra, guarda favoritos y usa el carrito
    val gestiona get() = this == ADMINISTRADOR       // Administra catálogo, solicitudes, fichas, avisos y cuentas
    val escanea get() = this != ADMINISTRADOR        // Lee códigos QR
    val editaFichas get() = this != CLIENTE          // Crea o corrige fichas de museos
    val veNoPublicadas get() = this != CLIENTE       // Ve fichas que aún no se publican
}

// ---------------------------------------------------------------- Navegación
// Todas las pantallas de la app. Algunas llevan el id de lo que muestran.
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

    // Dice si un rol puede entrar a esta pantalla.
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
        // Convierte un texto como "museo:3" en la pantalla que hay que abrir.
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
// Un campo de formulario: lo escrito y su error (null = sin error).
data class CampoFormulario(val valor: String = "", val error: String? = null) {
    val esValido get() = error == null
}

// Crea un campo y lo valida con la regla indicada.
fun campo(valor: String, regla: (String) -> String?) = CampoFormulario(valor, regla(valor))

// Reglas de validación. Cada una devuelve el mensaje de error o null si está bien.
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

// ---------------------------------------------------------------- Cálculo de envío
enum class Destino(val etiqueta: String) { NACIONAL("Nacional (Chile)"), INTERNACIONAL("Internacional") }
enum class Modalidad(val etiqueta: String, val factor: Double) { ESTANDAR("Estándar", 1.0), EXPRESS("Express", 1.6) }

// Calcula el envío con reglas inventadas (no hay courier real):
// nacional $3.500 + $1.200 por kg, internacional $14.000 + $6.500 por kg,
// express cuesta 1,6 veces más y el nacional estándar es gratis desde $80.000.
fun calcularEnvio(gramos: Int, subtotal: Int, destino: Destino, modalidad: Modalidad): Int {
    if (gramos <= 0) return 0
    if (destino == Destino.NACIONAL && modalidad == Modalidad.ESTANDAR && subtotal >= 80_000) return 0
    val kilos = ceil(gramos / 1000.0).toInt()
    val (base, porKilo) = if (destino == Destino.NACIONAL) 3_500 to 1_200 else 14_000 to 6_500
    return ((base + porKilo * kilos) * modalidad.factor / 100).roundToInt() * 100
}

// ---------------------------------------------------------------- Códigos QR
// Lo que trae un QR: su tipo, su código y todos sus datos.
data class ContenidoQr(val tipo: String, val codigo: String, val campos: List<Pair<String, String>>)

object LectorQr {
    private val TIPOS = setOf("museo", "exposicion", "publicacion", "producto", "curso")

    // Lee el texto del QR (en formato JSON) y revisa que tenga tipo y código.
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

    // Arma el texto JSON para crear el QR de una ficha.
    fun generar(tipo: String, codigo: String, titulo: String, url: String = ""): String =
        JSONObject().put("tipo", tipo).put("codigo", codigo).put("titulo", titulo).apply { if (url.isNotBlank()) put("url", url) }.toString()
}

// ---------------------------------------------------------------- Contraseñas y formatos
// Cifra las contraseñas: en la base solo se guarda el resultado cifrado.
object Seguridad {
    private fun derivar(clave: String, sal: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(clave.toCharArray(), sal, 10_000, 256)).encoded
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.bytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // Cifra una contraseña nueva.
    fun hash(clave: String): String {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "${sal.hex()}$${derivar(clave, sal).hex()}"
    }

    // Compara la contraseña escrita con la guardada.
    fun verificar(clave: String, guardado: String) = runCatching {
        val (sal, hash) = guardado.split("$")
        MessageDigest.isEqual(derivar(clave, sal.bytes()), hash.bytes())
    }.getOrDefault(false)
}

private val CHILE = Locale.forLanguageTag("es-CL")
// 12500 → "$12.500"
fun clp(monto: Int): String = "$" + NumberFormat.getIntegerInstance(CHILE).format(monto)
// Fecha y hora legibles, por ejemplo "21/10/2026 22:06".
fun fecha(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", CHILE).format(Date(ms))

// Avatares que se pueden elegir.
val AVATARES = listOf("🏛️", "🔍", "🎨", "🏺", "📜", "🎭", "⭐", "🌎")
