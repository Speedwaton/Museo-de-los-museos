package cl.triskeledu.museo.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cl.triskeledu.museo.model.Rol

/*
 * Tablas de Room (cada clase @Entity es una tabla SQLite, como Persona.kt del ejemplo).
 * Todas las tablas "personales" llevan usuarioId: así cada perfil del teléfono
 * tiene sus propios favoritos, carrito, solicitudes, historial y avisos.
 */

@Entity(tableName = "usuarios", indices = [Index(value = ["correo"], unique = true)])
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val correo: String,
    val claveHash: String,
    val rol: Rol,
    val avatar: String,
    val activo: Boolean = true,
    val notificaciones: Boolean = true,
    val textoGrande: Boolean = false,
    val idioma: String = "es",
)

enum class EstadoFicha(val etiqueta: String) { EN_REVISION("En revisión"), PUBLICADO("Publicado"), OBSERVADO("Observado") }

/** Ficha de museo: separa la UBICACIÓN (latitud/longitud) del ENLACE a la visita virtual. */
@Entity(tableName = "museos", indices = [Index(value = ["codigo"], unique = true)])
data class Museo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val pais: String,
    val ciudad: String,
    val descripcion: String,
    val clasificacion: String,
    val proyecto: String,
    val latitud: Double,
    val longitud: Double,
    val urlVisita: String,
    val imagenUrl: String? = null,  // Foto real (Wikimedia Commons)
    val codigo: String,             // Código del QR de la ficha
    val estado: EstadoFicha = EstadoFicha.EN_REVISION,
    val observacion: String? = null,
    val autorId: Long? = null,      // Investigador que registró la ficha
    val fotoRuta: String? = null,   // Versión 2: foto tomada con la cámara (ver MIGRACION_1_2)
)

@Entity(tableName = "favoritos", primaryKeys = ["usuarioId", "museoId"])
data class Favorito(val usuarioId: Long, val museoId: Long, val fecha: Long = System.currentTimeMillis())

enum class Categoria(val etiqueta: String) {
    CURSO("Cursos"), PUBLICACION("Publicaciones"), ARTICULO("Artículos museológicos"), CONSERVACION("Materiales de conservación")
}

@Entity(tableName = "productos", indices = [Index(value = ["codigo"], unique = true)])
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val categoria: Categoria,
    val descripcion: String,
    val precio: Int,                // CLP ficticios
    val stock: Int,
    val pesoGramos: Int,            // 0 = digital (cursos)
    val activo: Boolean = true,
    val codigo: String,
) {
    val disponible get() = activo && stock > 0
    val esCurso get() = categoria == Categoria.CURSO
}

@Entity(tableName = "carrito", primaryKeys = ["usuarioId", "productoId"])
data class ItemCarrito(val usuarioId: Long, val productoId: Long, val cantidad: Int)

/** Fila de consulta (no es tabla): ítem del carrito unido con su producto. */
data class LineaCarrito(
    val productoId: Long, val nombre: String, val precio: Int, val cantidad: Int,
    val stock: Int, val pesoGramos: Int, val activo: Boolean,
) {
    val subtotal get() = precio * cantidad
}

enum class EstadoSolicitud(val etiqueta: String) {
    PENDIENTE("Pendiente"), CONFIRMADO("Confirmado"), ENVIADO("Enviado"), ENTREGADO("Entregado")
}

@Entity(tableName = "solicitudes", indices = [Index("usuarioId")])
data class Solicitud(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: Long,
    val tipo: String,               // "Compra" o "Información"
    val resumen: String,
    val mensaje: String = "",
    val subtotal: Int = 0,
    val envio: Int = 0,
    val total: Int = 0,
    val destino: String = "",
    val estado: EstadoSolicitud = EstadoSolicitud.PENDIENTE,
    val fecha: Long = System.currentTimeMillis(),
)

data class SolicitudConCliente(@Embedded val solicitud: Solicitud, val cliente: String)

/** Historial QR: el índice único (usuario, código) evita lecturas duplicadas. */
@Entity(tableName = "lecturas_qr", indices = [Index(value = ["usuarioId", "codigo"], unique = true)])
data class LecturaQr(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: Long,
    val codigo: String,
    val titulo: String,
    val destino: String,            // "museo:3" o "producto:5"
    val fecha: Long = System.currentTimeMillis(),
    val veces: Int = 1,
)

@Entity(tableName = "notificaciones", indices = [Index("usuarioId")])
data class Notificacion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: Long,
    val titulo: String,
    val mensaje: String,
    val destino: String? = null,    // Pantalla relacionada
    val leida: Boolean = false,
    val fecha: Long = System.currentTimeMillis(),
)
