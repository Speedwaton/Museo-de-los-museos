package cl.triskeledu.museo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.triskeledu.museo.data.Categoria
import cl.triskeledu.museo.data.EstadoFicha
import cl.triskeledu.museo.data.EstadoSolicitud
import cl.triskeledu.museo.data.Favorito
import cl.triskeledu.museo.data.ItemCarrito
import cl.triskeledu.museo.data.LecturaQr
import cl.triskeledu.museo.data.LineaCarrito
import cl.triskeledu.museo.data.Museo
import cl.triskeledu.museo.data.Notificacion
import cl.triskeledu.museo.data.Producto
import cl.triskeledu.museo.data.Repositorio
import cl.triskeledu.museo.data.Solicitud
import cl.triskeledu.museo.data.SolicitudConCliente
import cl.triskeledu.museo.data.Usuario
import cl.triskeledu.museo.model.CampoFormulario
import cl.triskeledu.museo.model.ContenidoQr
import cl.triskeledu.museo.model.Destino
import cl.triskeledu.museo.model.LectorQr
import cl.triskeledu.museo.model.Modalidad
import cl.triskeledu.museo.model.Pantalla
import cl.triskeledu.museo.model.Rol
import cl.triskeledu.museo.model.Validar
import cl.triskeledu.museo.model.calcularEnvio
import cl.triskeledu.museo.model.campo
import cl.triskeledu.museo.model.clp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulario de inicio de sesión (pantalla 3). */
data class AccesoUi(val rol: Rol = Rol.CLIENTE, val correo: CampoFormulario = CampoFormulario(),
                    val clave: CampoFormulario = CampoFormulario(), val error: String? = null)

/** Formulario de registro (pantalla 2). */
data class RegistroUi(val nombre: CampoFormulario = CampoFormulario(), val correo: CampoFormulario = CampoFormulario(),
                      val clave: CampoFormulario = CampoFormulario(), val confirmar: CampoFormulario = CampoFormulario(),
                      val avatar: String = "🎨", val error: String? = null)

/** Formulario genérico para fichas de museo y productos: etiqueta → campo validado. */
data class FormUi(val id: Long? = null, val campos: Map<String, CampoFormulario> = emptyMap(), val foto: String? = null,
                  val categoria: Categoria = Categoria.CURSO, val activo: Boolean = true, val error: String? = null) {
    fun v(etiqueta: String) = campos[etiqueta]?.valor?.trim().orEmpty()
}

/** Resultado de leer un QR (pantalla 8). */
sealed interface EstadoQr {
    data object Leyendo : EstadoQr
    data class Ok(val contenido: ContenidoQr, val titulo: String, val detalle: String, val imagen: String?,
                  val destino: Pantalla, val url: String, val nueva: Boolean) : EstadoQr
    data class Error(val mensaje: String, val texto: String, val contenido: ContenidoQr?) : EstadoQr
}

/** Reglas de cada campo de los formularios (el orden es el de la pantalla). */
val CAMPOS_FICHA: Map<String, (String) -> String?> = linkedMapOf(
    "Nombre" to { Validar.texto(it) }, "País" to { Validar.texto(it) }, "Ciudad" to { Validar.texto(it, 2) },
    "Clasificación" to { Validar.texto(it) }, "Proyecto" to { Validar.texto(it) }, "Descripción" to { Validar.texto(it, 10) },
    "Latitud" to { Validar.numero(it, -90.0..90.0) }, "Longitud" to { Validar.numero(it, -180.0..180.0) },
    "Enlace a la visita virtual" to Validar::url, "Foto web (URL, opcional)" to { if (it.isBlank()) null else Validar.url(it) },
    "Código QR" to Validar::codigo,
)
val CAMPOS_PRODUCTO: Map<String, (String) -> String?> = linkedMapOf(
    "Nombre" to { Validar.texto(it) }, "Descripción" to { Validar.texto(it, 10) }, "Precio (CLP)" to Validar::entero,
    "Stock / cupos" to Validar::entero, "Peso en gramos (0 = digital)" to Validar::entero, "Código QR" to Validar::codigo,
)

/**
 * ============================================================================
 * MuseoViewModel: ÚNICO ViewModel de la app (MVVM sencillo)
 * ============================================================================
 * - Sesión activa y navegación (pila de pantallas, igual idea que el ejemplo de formularios).
 * - Expone StateFlow de solo lectura; las pantallas llaman funciones para cambiar el estado.
 * - Todo lo personal se consulta con el id del usuario activo (flatMapLatest),
 *   así los datos de cada perfil nunca se mezclan.
 * ============================================================================
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MuseoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repositorio(app)
    private val dao = repo.dao
    private val datosListos = viewModelScope.launch { repo.prepararDatos() }

    // ======================================================== Sesión, navegación y mensajes
    private val idSesion = MutableStateFlow<Long?>(null)
    val usuario: StateFlow<Usuario?> = idSesion.flatMapLatest { id -> id?.let(dao::usuario) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val yo get() = usuario.value

    val pila = MutableStateFlow<List<Pantalla>>(listOf(Pantalla.Bienvenida))
    private var pendiente: Pantalla? = null
    val mensajes = MutableSharedFlow<String>(extraBufferCapacity = 5)
    private fun aviso(texto: String) = mensajes.tryEmit(texto)

    /** Abre una pantalla comprobando sesión y permisos del rol. */
    fun ir(p: Pantalla) {
        val u = yo
        when {
            p == Pantalla.Acceso || p == Pantalla.Registro -> pila.update { it + p }
            u == null -> { pendiente = p; pila.value = listOf(Pantalla.Bienvenida, Pantalla.Acceso); aviso("Inicia sesión para continuar.") }
            !p.permitida(u.rol) -> aviso("El rol ${u.rol.etiqueta} no tiene acceso a esta función.")
            else -> pila.update { it + p }
        }
    }
    fun pestana(p: Pantalla) { pila.value = if (p == Pantalla.Inicio) listOf(p) else listOf(Pantalla.Inicio, p) }
    fun reemplazar(p: Pantalla) = pila.update { it.dropLast(1) + p }
    fun volver(): Boolean = (pila.value.size > 1).also { if (it) pila.update { l -> l.dropLast(1) } }
    fun abrirDestino(destino: String?) { Pantalla.desde(destino)?.let(::ir) }

    private fun <T> porUsuario(vacio: T, consulta: (Usuario) -> Flow<T>): StateFlow<T> =
        usuario.flatMapLatest { u -> u?.let(consulta) ?: flowOf(vacio) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vacio)

    // ======================================================== Acceso y registro (pantallas 2 y 3)
    val acceso = MutableStateFlow(AccesoUi())
    val registro = MutableStateFlow(RegistroUi())

    fun elegirRol(rol: Rol) = acceso.update { it.copy(rol = rol, error = null) }
    fun cambiarCorreo(v: String) = acceso.update { it.copy(correo = campo(v, Validar::correo), error = null) }
    fun cambiarClave(v: String) = acceso.update { it.copy(clave = campo(v) { c -> if (c.isBlank()) "Campo obligatorio" else null }, error = null) }
    fun usarDemo() = acceso.update { a ->
        val c = cl.triskeledu.museo.data.DatosDemo.CUENTAS.first { it.rol == a.rol }
        a.copy(correo = CampoFormulario(c.correo), clave = CampoFormulario(c.clave), error = null)
    }

    fun iniciarSesion() {
        val a = acceso.value
        if (!a.correo.esValido || a.correo.valor.isBlank() || a.clave.valor.isBlank()) {
            acceso.update { it.copy(error = "Completa correo y contraseña.") }; return
        }
        viewModelScope.launch {
            datosListos.join()
            repo.iniciarSesion(a.correo.valor, a.clave.valor, a.rol)
                .onSuccess { entrar(it); acceso.value = AccesoUi(rol = a.rol) }
                .onFailure { e -> acceso.update { it.copy(error = e.message) } }
        }
    }

    fun editarRegistro(f: (RegistroUi) -> RegistroUi) = registro.update(f)

    fun registrar() {
        val r = registro.value
        val v = r.copy(
            nombre = campo(r.nombre.valor) { Validar.texto(it) }, correo = campo(r.correo.valor, Validar::correo),
            clave = campo(r.clave.valor, Validar::clave),
            confirmar = campo(r.confirmar.valor) { if (it == r.clave.valor && it.isNotEmpty()) null else "Las contraseñas no coinciden" },
        )
        registro.value = v
        if (listOf(v.nombre, v.correo, v.clave, v.confirmar).any { !it.esValido }) return
        viewModelScope.launch {
            datosListos.join()
            repo.registrar(v.nombre.valor, v.correo.valor, v.clave.valor, v.avatar)
                .onSuccess { entrar(it); registro.value = RegistroUi(); aviso("Cuenta creada. ¡Bienvenido/a!") }
                .onFailure { e -> registro.update { it.copy(error = e.message) } }
        }
    }

    private fun entrar(u: Usuario) {
        idSesion.value = u.id
        pila.value = listOfNotNull(Pantalla.Inicio, pendiente?.takeIf { it.permitida(u.rol) })
        pendiente = null
    }

    fun cerrarSesion(cambiarUsuario: Boolean) {
        idSesion.value = null
        pila.value = listOf(if (cambiarUsuario) Pantalla.Acceso else Pantalla.Bienvenida)
    }

    // ======================================================== Museos, mapa y favoritos (5, 6 y 11)
    val museos: StateFlow<List<Museo>> = combine(dao.museos(), usuario) { lista, u ->
        if (u?.rol?.veNoPublicadas == true) lista else lista.filter { it.estado == EstadoFicha.PUBLICADO }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filtroPais = MutableStateFlow<String?>(null)
    val filtroProyecto = MutableStateFlow<String?>(null)
    val seleccionado = MutableStateFlow<Long?>(null)
    val museosFiltrados = combine(museos, filtroPais, filtroProyecto) { l, pais, proyecto ->
        l.filter { (pais == null || it.pais == pais) && (proyecto == null || it.proyecto == proyecto) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoritos: StateFlow<List<Long>> = porUsuario(emptyList()) { dao.favoritos(it.id) }

    fun alternarFavorito(m: Museo) {
        val u = yo ?: return
        viewModelScope.launch {
            if (m.id in favoritos.value) { dao.quitarFavorito(u.id, m.id); aviso("Quitado de tu colección.") }
            else { dao.guardarFavorito(Favorito(u.id, m.id)); aviso("Guardado en favoritos.") }
        }
    }

    fun puedeEditar(m: Museo) = yo?.let { it.rol == Rol.ADMINISTRADOR || (it.rol == Rol.INVESTIGADOR && m.autorId == it.id) } == true

    /** Revisión del Administrador: publica u observa la ficha y avisa al investigador. */
    fun revisar(m: Museo, estado: EstadoFicha, observacion: String?) {
        val u = yo?.takeIf { it.rol.gestiona } ?: return
        viewModelScope.launch {
            dao.actualizar(m.copy(estado = estado, observacion = observacion?.ifBlank { null }))
            m.autorId?.let { repo.avisar(null, "Revisión de ficha", "«${m.nombre}»: ${estado.etiqueta}. ${observacion.orEmpty()}", "museo:${m.id}", u, listOf(it)) }
            if (estado == EstadoFicha.PUBLICADO) repo.avisar(Rol.CLIENTE, "Museo incorporado", "${m.nombre} ya está en el mapa.", "museo:${m.id}", u)
            aviso("Ficha marcada como ${estado.etiqueta}.")
        }
    }

    // ======================================================== Formularios de ficha y producto
    val form = MutableStateFlow(FormUi())

    fun prepararFicha(id: Long?) = viewModelScope.launch {
        val m = id?.let { dao.museo(it) }
        val valores = listOf(m?.nombre, m?.pais, m?.ciudad, m?.clasificacion, m?.proyecto, m?.descripcion,
            m?.latitud?.toString(), m?.longitud?.toString(), m?.urlVisita, m?.imagenUrl, m?.codigo)
        form.value = FormUi(id, CAMPOS_FICHA.keys.zip(valores).associate { (k, v) -> k to CampoFormulario(v.orEmpty()) }, m?.fotoRuta)
    }

    fun prepararProducto(id: Long?) = viewModelScope.launch {
        val p = dao.productos().first().firstOrNull { it.id == id }
        val valores = listOf(p?.nombre, p?.descripcion, p?.precio?.toString(), p?.stock?.toString(), p?.pesoGramos?.toString(), p?.codigo)
        form.value = FormUi(id, CAMPOS_PRODUCTO.keys.zip(valores).associate { (k, v) -> k to CampoFormulario(v.orEmpty()) },
            categoria = p?.categoria ?: Categoria.CURSO, activo = p?.activo ?: true)
    }

    fun cambiarCampo(etiqueta: String, valor: String, reglas: Map<String, (String) -> String?>) = form.update {
        it.copy(campos = it.campos + (etiqueta to campo(valor, reglas.getValue(etiqueta))), error = null)
    }
    fun editarForm(f: (FormUi) -> FormUi) = form.update(f)

    /** Valida todos los campos; devuelve false y marca errores si alguno falla. */
    private fun validarForm(reglas: Map<String, (String) -> String?>): Boolean {
        form.update { f -> f.copy(campos = reglas.mapValues { (k, r) -> campo(f.v(k), r) }) }
        return form.value.campos.values.all { it.esValido }.also { if (!it) form.update { f -> f.copy(error = "Revisa los campos marcados.") } }
    }

    fun guardarFicha() {
        val u = yo?.takeIf { it.rol.editaFichas } ?: return
        if (!validarForm(CAMPOS_FICHA)) return
        val f = form.value
        viewModelScope.launch {
            val anterior = f.id?.let { dao.museo(it) }
            val otro = dao.museoPorCodigo(f.v("Código QR").uppercase())
            if (otro != null && otro.id != f.id) { form.update { it.copy(error = "Ese código ya es de «${otro.nombre}».") }; return@launch }
            if (anterior != null && !puedeEditar(anterior)) { form.update { it.copy(error = "Solo puedes corregir tus fichas.") }; return@launch }
            val estado = if (u.rol.gestiona) anterior?.estado ?: EstadoFicha.PUBLICADO else EstadoFicha.EN_REVISION
            val m = Museo(
                id = f.id ?: 0, nombre = f.v("Nombre"), pais = f.v("País"), ciudad = f.v("Ciudad"),
                descripcion = f.v("Descripción"), clasificacion = f.v("Clasificación"), proyecto = f.v("Proyecto"),
                latitud = f.v("Latitud").replace(',', '.').toDouble(), longitud = f.v("Longitud").replace(',', '.').toDouble(),
                urlVisita = f.v("Enlace a la visita virtual"), imagenUrl = f.v("Foto web (URL, opcional)").ifBlank { null },
                codigo = f.v("Código QR").uppercase(), estado = estado, autorId = anterior?.autorId ?: u.id, fotoRuta = f.foto,
            )
            val id = if (f.id == null) dao.insertar(m) else m.id.also { dao.actualizar(m) }
            if (estado == EstadoFicha.EN_REVISION) repo.avisar(Rol.ADMINISTRADOR, "Ficha por revisar", "${u.nombre} envió «${m.nombre}».", "museo:$id", u)
            aviso("Ficha guardada.")
            reemplazar(Pantalla.Museo(id))
        }
    }

    fun guardarProducto() {
        val u = yo?.takeIf { it.rol.gestiona } ?: return
        if (!validarForm(CAMPOS_PRODUCTO)) return
        val f = form.value
        viewModelScope.launch {
            val otro = dao.productoPorCodigo(f.v("Código QR").uppercase())
            if (otro != null && otro.id != f.id) { form.update { it.copy(error = "Ese código ya es de «${otro.nombre}».") }; return@launch }
            val p = Producto(f.id ?: 0, f.v("Nombre"), f.categoria, f.v("Descripción"), f.v("Precio (CLP)").toInt(),
                f.v("Stock / cupos").toInt(), f.v("Peso en gramos (0 = digital)").toInt(), f.activo, f.v("Código QR").uppercase())
            if (f.id == null) {
                val id = dao.insertar(p)
                repo.avisar(Rol.CLIENTE, if (p.esCurso) "Nuevo curso" else "Nuevo producto", "Ya está disponible «${p.nombre}».", "producto:$id", u)
            } else dao.actualizar(p)
            aviso("Producto guardado.")
            volver()
        }
    }

    // ======================================================== Catálogo, carrito y solicitudes (9, 10, 12, 13)
    val productos: StateFlow<List<Producto>> = combine(dao.productos(), usuario) { l, u -> if (u?.rol?.gestiona == true) l else l.filter { it.activo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val busqueda = MutableStateFlow("")
    val categoria = MutableStateFlow<Categoria?>(null)
    val catalogo = combine(productos, busqueda, categoria) { l, texto, cat ->
        l.filter { (cat == null || it.categoria == cat) && it.nombre.contains(texto.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun alternarActivo(p: Producto) = viewModelScope.launch { dao.actualizar(p.copy(activo = !p.activo)) }

    val carrito: StateFlow<List<LineaCarrito>> = porUsuario(emptyList()) { if (it.rol.compra) dao.carrito(it.id) else flowOf(emptyList()) }
    val destino = MutableStateFlow(Destino.NACIONAL)
    val modalidad = MutableStateFlow(Modalidad.ESTANDAR)

    fun costoEnvio(lineas: List<LineaCarrito>) =
        calcularEnvio(lineas.sumOf { it.pesoGramos * it.cantidad }, lineas.sumOf { it.subtotal }, destino.value, modalidad.value)

    fun agregar(p: Producto, cantidad: Int, alTerminar: (Boolean) -> Unit) {
        val u = yo ?: return
        val enCarrito = carrito.value.firstOrNull { it.productoId == p.id }?.cantidad ?: 0
        if (!p.disponible || enCarrito + cantidad > p.stock) { aviso("No hay stock suficiente (quedan ${p.stock})."); alTerminar(false); return }
        viewModelScope.launch { dao.guardarItem(ItemCarrito(u.id, p.id, enCarrito + cantidad)); aviso("Agregado al carrito."); alTerminar(true) }
    }

    fun cambiarCantidad(l: LineaCarrito, cantidad: Int) {
        val u = yo ?: return
        if (cantidad > l.stock) { aviso("Solo hay ${l.stock} unidades."); return }
        viewModelScope.launch { if (cantidad <= 0) dao.quitarItem(u.id, l.productoId) else dao.guardarItem(ItemCarrito(u.id, l.productoId, cantidad)) }
    }

    /** Confirma la compra: el DAO valida stock dentro de una transacción. */
    fun comprar() {
        val u = yo?.takeIf { it.rol.compra } ?: return
        viewModelScope.launch {
            runCatching {
                dao.comprar(u.id) { lineas ->
                    val subtotal = lineas.sumOf { it.subtotal }
                    val envio = costoEnvio(lineas)
                    Solicitud(usuarioId = u.id, tipo = "Compra", resumen = lineas.joinToString { "${it.cantidad} × ${it.nombre}" },
                        subtotal = subtotal, envio = envio, total = subtotal + envio,
                        destino = "${destino.value.etiqueta} · ${modalidad.value.etiqueta}")
                }
            }.onSuccess { s ->
                repo.avisar(Rol.ADMINISTRADOR, "Nueva solicitud #${s.id}", "${u.nombre}: ${clp(s.total)}", "solicitudes", u)
                repo.avisar(null, "Solicitud recibida", "Tu pedido #${s.id} quedó Pendiente.", "solicitudes", u, listOf(u.id))
                reemplazar(Pantalla.Solicitudes)
            }.onFailure { aviso(it.message ?: "No se pudo comprar.") }
        }
    }

    fun pedirInformacion(p: Producto, mensaje: String) {
        val u = yo ?: return
        if (mensaje.trim().length < 10) { aviso("Escribe tu consulta (mínimo 10 caracteres)."); return }
        viewModelScope.launch {
            dao.insertar(Solicitud(usuarioId = u.id, tipo = "Información", resumen = p.nombre, mensaje = mensaje.trim()))
            repo.avisar(Rol.ADMINISTRADOR, "Consulta por curso", "${u.nombre} pregunta por «${p.nombre}».", "solicitudes", u)
            aviso("Solicitud de información enviada.")
        }
    }

    val solicitudes: StateFlow<List<SolicitudConCliente>> =
        porUsuario(emptyList()) { dao.solicitudes(if (it.rol.gestiona) null else it.id) }

    fun cambiarEstado(s: Solicitud, estado: EstadoSolicitud) {
        val u = yo?.takeIf { it.rol.gestiona } ?: return
        viewModelScope.launch {
            dao.cambiarEstado(s.id, estado)
            repo.avisar(null, "Estado de tu pedido", "La solicitud #${s.id} ahora está ${estado.etiqueta}.", "solicitudes", u, listOf(s.usuarioId))
        }
    }

    // ======================================================== QR (pantallas 7 y 8)
    val qr = MutableStateFlow<EstadoQr>(EstadoQr.Leyendo)
    val lecturas: StateFlow<List<LecturaQr>> = porUsuario(emptyList()) { dao.lecturas(it.id) }

    /** Valida el JSON, busca la ficha y la guarda en el historial sin duplicar. */
    fun procesarQr(texto: String) {
        val u = yo ?: return
        qr.value = EstadoQr.Leyendo
        ir(Pantalla.ResultadoQr)
        viewModelScope.launch {
            val c = LectorQr.leer(texto).getOrElse { qr.value = EstadoQr.Error(it.message.orEmpty(), texto, null); return@launch }
            val resultado = if (c.tipo == "museo" || c.tipo == "exposicion") {
                dao.museoPorCodigo(c.codigo)?.takeIf { u.rol.veNoPublicadas || it.estado == EstadoFicha.PUBLICADO }?.let {
                    EstadoQr.Ok(c, it.nombre, "${it.ciudad}, ${it.pais} · ${it.descripcion}", it.fotoRuta ?: it.imagenUrl,
                        Pantalla.Museo(it.id), it.urlVisita, false)
                }
            } else {
                dao.productoPorCodigo(c.codigo)?.takeIf { it.activo }?.let {
                    EstadoQr.Ok(c, it.nombre, "${it.categoria.etiqueta} · ${clp(it.precio)} · ${it.descripcion}", null, Pantalla.Producto(it.id), "", false)
                }
            }
            if (resultado == null) { qr.value = EstadoQr.Error("El código ${c.codigo} no corresponde a ninguna ficha registrada.", texto, c); return@launch }
            val destino = (resultado.destino as? Pantalla.Museo)?.let { "museo:${it.id}" } ?: "producto:${(resultado.destino as Pantalla.Producto).id}"
            val nueva = dao.registrarLectura(LecturaQr(usuarioId = u.id, codigo = c.codigo, titulo = resultado.titulo, destino = destino))
            qr.value = resultado.copy(nueva = nueva)
        }
    }

    // ======================================================== Avisos y perfil (pantallas 14 y 15)
    val notificaciones: StateFlow<List<Notificacion>> = porUsuario(emptyList()) { dao.notificaciones(it.id) }
    val usuarios: StateFlow<List<Usuario>> = porUsuario(emptyList()) { if (it.rol.gestiona) dao.usuarios() else flowOf(emptyList()) }

    fun leer(n: Notificacion?) { val u = yo ?: return; viewModelScope.launch { dao.marcarLeida(u.id, n?.id) }; n?.destino?.let(::abrirDestino) }

    fun enviarAviso(rol: Rol?, titulo: String, mensaje: String) {
        val u = yo?.takeIf { it.rol.gestiona } ?: return
        if (titulo.isBlank() || mensaje.isBlank()) { aviso("Completa título y mensaje."); return }
        viewModelScope.launch { aviso("Aviso enviado a ${repo.avisar(rol, titulo.trim(), mensaje.trim(), "catalogo", u)} perfil(es).") }
    }

    fun guardarPerfil(nombre: String, avatar: String, notificaciones: Boolean, textoGrande: Boolean, idioma: String) {
        val u = yo ?: return
        Validar.texto(nombre)?.let { aviso("Nombre: $it"); return }
        viewModelScope.launch { dao.actualizar(u.copy(nombre = nombre.trim(), avatar = avatar, notificaciones = notificaciones, textoGrande = textoGrande, idioma = idioma)); aviso("Perfil guardado ✓") }
    }

    fun alternarCuenta(c: Usuario) {
        if (c.id == yo?.id) { aviso("No puedes desactivar tu propia cuenta."); return }
        viewModelScope.launch { dao.cambiarActivo(c.id, !c.activo) }
    }
}
