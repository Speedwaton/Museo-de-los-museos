package cl.triskeledu.museo.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cl.triskeledu.museo.model.Rol

// Tablas de la base de datos. Cada clase con @Entity es una tabla.

// Usuarios de la app, con su rol y sus preferencias. El correo no se puede repetir.
@Entity(tableName = "usuarios", indices = [Index(value = ["correo"], unique = true)])
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val correo: String,
    val claveHash: String,          // Contraseña cifrada (nunca la real)
    val rol: Rol,
    val avatar: String,
    val activo: Boolean = true,
    val notificaciones: Boolean = true,
    val textoGrande: Boolean = false,
    val idioma: String = "es",
)

// Estados de una ficha de museo.
enum class EstadoFicha(val etiqueta: String) { EN_REVISION("En revisión"), PUBLICADO("Publicado"), OBSERVADO("Observado") }

// Museos: datos de la ficha, su ubicación en el mapa y su enlace a la visita virtual.
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
    val imagenUrl: String? = null,  // Foto de internet
    val codigo: String,             // Código de su QR
    val estado: EstadoFicha = EstadoFicha.EN_REVISION,
    val observacion: String? = null,
    val autorId: Long? = null,      // Investigador que la creó
    val fotoRuta: String? = null,   // Foto tomada con la cámara (se agregó en la versión 2)
)

// Museos que guardó cada usuario como favoritos.
@Entity(tableName = "favoritos", primaryKeys = ["usuarioId", "museoId"])
data class Favorito(val usuarioId: Long, val museoId: Long, val fecha: Long = System.currentTimeMillis())

// Tipos de producto del catálogo.
enum class Categoria(val etiqueta: String) {
    CURSO("Cursos"), PUBLICACION("Publicaciones"), ARTICULO("Artículos museológicos"), CONSERVACION("Materiales de conservación")
}

// Cursos y productos del catálogo.
@Entity(tableName = "productos", indices = [Index(value = ["codigo"], unique = true)])
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val categoria: Categoria,
    val descripcion: String,
    val precio: Int,                // Pesos chilenos (de prueba)
    val stock: Int,
    val pesoGramos: Int,            // 0 = curso, no se envía
    val activo: Boolean = true,
    val codigo: String,
) {
    val disponible get() = activo && stock > 0
    val esCurso get() = categoria == Categoria.CURSO
}

// Productos que cada usuario tiene en su carrito.
@Entity(tableName = "carrito", primaryKeys = ["usuarioId", "productoId"])
data class ItemCarrito(val usuarioId: Long, val productoId: Long, val cantidad: Int)

// Un producto del carrito con su precio y su stock (resultado de una consulta, no es tabla).
data class LineaCarrito(
    val productoId: Long, val nombre: String, val precio: Int, val cantidad: Int,
    val stock: Int, val pesoGramos: Int, val activo: Boolean,
) {
    val subtotal get() = precio * cantidad
}

// Estados de una solicitud.
enum class EstadoSolicitud(val etiqueta: String) {
    PENDIENTE("Pendiente"), CONFIRMADO("Confirmado"), ENVIADO("Enviado"), ENTREGADO("Entregado")
}

// Compras y consultas de los clientes.
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

// Una solicitud junto con el nombre del cliente (para el Administrador).
data class SolicitudConCliente(@Embedded val solicitud: Solicitud, val cliente: String)

// Códigos QR que leyó cada usuario. El mismo código no se guarda dos veces.
@Entity(tableName = "lecturas_qr", indices = [Index(value = ["usuarioId", "codigo"], unique = true)])
data class LecturaQr(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: Long,
    val codigo: String,
    val titulo: String,
    val destino: String,            // Pantalla a abrir, por ejemplo "museo:3"
    val fecha: Long = System.currentTimeMillis(),
    val veces: Int = 1,
)

// Avisos de cada usuario.
@Entity(tableName = "notificaciones", indices = [Index("usuarioId")])
data class Notificacion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: Long,
    val titulo: String,
    val mensaje: String,
    val destino: String? = null,    // Pantalla que se abre al tocarlo
    val leida: Boolean = false,
    val fecha: Long = System.currentTimeMillis(),
)
