package cl.triskeledu.museo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import cl.triskeledu.museo.model.Rol
import kotlinx.coroutines.flow.Flow

// Todas las consultas a la base de datos.
@Dao
interface MuseoDao {

    // --- Datos de demostración (IGNORE: si ya existen, no los repite) ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertarUsuarios(lista: List<Usuario>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertarMuseos(lista: List<Museo>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertarProductos(lista: List<Producto>)

    // --- Usuarios ---
    @Insert suspend fun insertar(usuario: Usuario): Long
    @Update suspend fun actualizar(usuario: Usuario)
    @Query("SELECT * FROM usuarios WHERE correo = :correo") suspend fun usuarioPorCorreo(correo: String): Usuario?
    @Query("SELECT * FROM usuarios WHERE id = :id") fun usuario(id: Long): Flow<Usuario?>
    @Query("SELECT * FROM usuarios ORDER BY rol, nombre") fun usuarios(): Flow<List<Usuario>>
    @Query("SELECT id FROM usuarios WHERE activo = 1 AND (:rol IS NULL OR rol = :rol)") suspend fun idsActivos(rol: Rol?): List<Long>
    @Query("UPDATE usuarios SET activo = :activo WHERE id = :id") suspend fun cambiarActivo(id: Long, activo: Boolean)

    // --- Museos y favoritos ---
    @Insert suspend fun insertar(museo: Museo): Long
    @Update suspend fun actualizar(museo: Museo)
    @Query("SELECT * FROM museos ORDER BY pais, nombre") fun museos(): Flow<List<Museo>>
    @Query("SELECT * FROM museos WHERE id = :id") suspend fun museo(id: Long): Museo?
    @Query("SELECT * FROM museos WHERE codigo = :codigo") suspend fun museoPorCodigo(codigo: String): Museo?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun guardarFavorito(favorito: Favorito)
    @Query("DELETE FROM favoritos WHERE usuarioId = :u AND museoId = :m") suspend fun quitarFavorito(u: Long, m: Long)
    @Query("SELECT museoId FROM favoritos WHERE usuarioId = :u") fun favoritos(u: Long): Flow<List<Long>>

    // --- Catálogo y carrito ---
    @Insert suspend fun insertar(producto: Producto): Long
    @Update suspend fun actualizar(producto: Producto)
    @Query("SELECT * FROM productos ORDER BY categoria, nombre") fun productos(): Flow<List<Producto>>
    @Query("SELECT * FROM productos WHERE codigo = :codigo") suspend fun productoPorCodigo(codigo: String): Producto?
    @Upsert suspend fun guardarItem(item: ItemCarrito)
    @Query("DELETE FROM carrito WHERE usuarioId = :u AND productoId = :p") suspend fun quitarItem(u: Long, p: Long)
    @Query(
        "SELECT c.productoId, p.nombre, p.precio, c.cantidad, p.stock, p.pesoGramos, p.activo " +
            "FROM carrito c JOIN productos p ON p.id = c.productoId WHERE c.usuarioId = :u ORDER BY p.nombre"
    )
    fun carrito(u: Long): Flow<List<LineaCarrito>>

    @Query("SELECT c.productoId, p.nombre, p.precio, c.cantidad, p.stock, p.pesoGramos, p.activo " +
        "FROM carrito c JOIN productos p ON p.id = c.productoId WHERE c.usuarioId = :u")
    suspend fun carritoActual(u: Long): List<LineaCarrito>

    // Resta stock solo si alcanza. Devuelve 0 si no hay suficiente.
    @Query("UPDATE productos SET stock = stock - :n WHERE id = :id AND activo = 1 AND stock >= :n")
    suspend fun descontarStock(id: Long, n: Int): Int

    @Query("DELETE FROM carrito WHERE usuarioId = :u") suspend fun vaciarCarrito(u: Long)

    // Compra: resta el stock, crea la solicitud y vacía el carrito.
    // Si algo falla, se deshace todo (@Transaction).
    @Transaction
    suspend fun comprar(usuarioId: Long, crear: (List<LineaCarrito>) -> Solicitud): Solicitud {
        val lineas = carritoActual(usuarioId)
        require(lineas.isNotEmpty()) { "El carrito está vacío." }
        lineas.forEach { require(descontarStock(it.productoId, it.cantidad) == 1) { "«${it.nombre}» ya no tiene stock suficiente." } }
        val solicitud = crear(lineas)
        val id = insertar(solicitud)
        vaciarCarrito(usuarioId)
        return solicitud.copy(id = id)
    }

    // --- Solicitudes ---
    @Insert suspend fun insertar(solicitud: Solicitud): Long
    @Query("SELECT s.*, u.nombre AS cliente FROM solicitudes s JOIN usuarios u ON u.id = s.usuarioId " +
        "WHERE (:u IS NULL OR s.usuarioId = :u) ORDER BY s.fecha DESC")
    fun solicitudes(u: Long?): Flow<List<SolicitudConCliente>>
    @Query("UPDATE solicitudes SET estado = :estado WHERE id = :id") suspend fun cambiarEstado(id: Long, estado: EstadoSolicitud)

    // --- Historial de códigos QR ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertar(lectura: LecturaQr): Long
    @Query("UPDATE lecturas_qr SET veces = veces + 1, fecha = :fecha WHERE usuarioId = :u AND codigo = :codigo")
    suspend fun repetirLectura(u: Long, codigo: String, fecha: Long): Int
    @Query("SELECT * FROM lecturas_qr WHERE usuarioId = :u ORDER BY fecha DESC") fun lecturas(u: Long): Flow<List<LecturaQr>>

    // Guarda una lectura QR. Si ya existía, solo suma una vez más. Devuelve true si es nueva.
    @Transaction
    suspend fun registrarLectura(lectura: LecturaQr): Boolean =
        if (repetirLectura(lectura.usuarioId, lectura.codigo, lectura.fecha) > 0) false
        else insertar(lectura).let { true }

    // --- Notificaciones ---
    @Insert suspend fun insertarNotificaciones(lista: List<Notificacion>)
    @Query("SELECT * FROM notificaciones WHERE usuarioId = :u ORDER BY fecha DESC, id DESC") fun notificaciones(u: Long): Flow<List<Notificacion>>
    @Query("UPDATE notificaciones SET leida = 1 WHERE usuarioId = :u AND (:id IS NULL OR id = :id)")
    suspend fun marcarLeida(u: Long, id: Long?)
}
