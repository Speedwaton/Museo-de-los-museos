package cl.triskeledu.museo.data

import android.content.Context
import cl.triskeledu.museo.model.Rol
import cl.triskeledu.museo.model.Seguridad
import cl.triskeledu.museo.util.notificar

/**
 * Repositorio: único punto de acceso a los datos para el ViewModel.
 * Hoy usa Room; con un backend REST (Retrofit) aquí se descargarían los datos
 * y Room quedaría como caché local, sin tocar las pantallas.
 */
class Repositorio(private val context: Context) {
    val dao = BaseDatos.obtener(context).dao()

    suspend fun prepararDatos() = DatosDemo.poblar(dao)

    suspend fun iniciarSesion(correo: String, clave: String, rol: Rol): Result<Usuario> {
        val u = dao.usuarioPorCorreo(correo.trim().lowercase())
        return when {
            u == null || !Seguridad.verificar(clave, u.claveHash) -> Result.failure(Exception("Correo o contraseña incorrectos."))
            !u.activo -> Result.failure(Exception("El Administrador desactivó esta cuenta."))
            u.rol != rol -> Result.failure(Exception("Esta cuenta es de rol ${u.rol.etiqueta}."))
            else -> Result.success(u)
        }
    }

    /** El registro público siempre crea Clientes; Admin e Investigador son perfiles de prueba. */
    suspend fun registrar(nombre: String, correo: String, clave: String, avatar: String): Result<Usuario> {
        val c = correo.trim().lowercase()
        if (dao.usuarioPorCorreo(c) != null) return Result.failure(Exception("Ya existe una cuenta con ese correo."))
        val nuevo = Usuario(nombre = nombre.trim(), correo = c, claveHash = Seguridad.hash(clave), rol = Rol.CLIENTE, avatar = avatar)
        return Result.success(nuevo.copy(id = dao.insertar(nuevo)))
    }

    /**
     * Guarda un aviso por destinatario (rol = null → todos). Si el usuario de la sesión
     * es destinatario y acepta avisos, también se muestra como notificación del sistema.
     */
    suspend fun avisar(rol: Rol?, titulo: String, mensaje: String, destino: String?, actual: Usuario?, ids: List<Long>? = null): Int {
        val destinatarios = ids ?: dao.idsActivos(rol)
        dao.insertarNotificaciones(destinatarios.map { Notificacion(usuarioId = it, titulo = titulo, mensaje = mensaje, destino = destino) })
        if (actual != null && actual.id in destinatarios && actual.notificaciones) context.notificar(titulo, mensaje, destino)
        return destinatarios.size
    }
}
