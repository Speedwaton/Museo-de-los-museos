package cl.triskeledu.museo.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.triskeledu.museo.ViewModel
import cl.triskeledu.museo.data.EstadoFicha
import cl.triskeledu.museo.data.EstadoSolicitud
import cl.triskeledu.museo.data.Usuario
import cl.triskeledu.museo.model.AVATARES
import cl.triskeledu.museo.model.Pantalla
import cl.triskeledu.museo.model.Rol
import cl.triskeledu.museo.model.campo
import cl.triskeledu.museo.model.fecha
import cl.triskeledu.museo.util.PERMISOS_UBICACION
import cl.triskeledu.museo.util.tienePermiso

// ============================================================ Pantalla 1: Bienvenida
// Logo animado, explicación de la app y botón Comenzar.
@Composable
fun PantallaBienvenida(vm: ViewModel) {
    // Animación: el logo crece y se achica, y el texto aparece de abajo hacia arriba.
    val escala by rememberInfiniteTransition(label = "logo").animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "e")
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Burdeo, Color(0xFF2E0A1A)))).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.padding(top = 32.dp).size(120.dp).scale(escala).clip(CircleShape).background(Color(0xFFFFDEA6)), Alignment.Center) { Text("🏛️", fontSize = 60.sp) }
        AnimatedVisibility(visible, enter = fadeIn(tween(900)) + slideInVertically(tween(900)) { it / 3 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Museo de los Museos", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Educación museológica, investigación, formación y desarrollo de audiencias. Explora el Mapa de Museos " +
                    "Virtuales de Iberoamérica, cursos y productos.", color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🛒 Catálogo" to Pantalla.Catalogo, "🗺️ Mapa" to Pantalla.Mapa, "🎓 Cursos" to Pantalla.Catalogo).forEach { (t, p) ->
                        Card(onClick = { vm.ir(p) }, modifier = Modifier.weight(1f)) { Text(t, Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
                    }
                }
                Button(onClick = { vm.ir(Pantalla.Acceso) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Comenzar") }
                Text("MVP académico · datos de demostración", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ============================================================ Pantalla 3: Selección de rol e inicio de sesión
// Se elige el rol, se escribe correo y contraseña, y se entra.
@Composable
fun PantallaAcceso(vm: ViewModel, alVolver: (() -> Unit)?) {
    val a by vm.acceso.collectAsStateWithLifecycle()
    ConBarra("Selecciona tu rol", alVolver) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rol.entries.forEach { FilterChip(a.rol == it, { vm.elegirRol(it) }, { Text(it.etiqueta) }) }
            }
            Campo("Correo", a.correo, vm::cambiarCorreo, teclado = KeyboardType.Email)
            Campo("Contraseña", a.clave, vm::cambiarClave, clave = true)
            a.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::iniciarSesion, modifier = Modifier.fillMaxWidth()) { Text("Ingresar como ${a.rol.etiqueta}") }
            TextButton(onClick = vm::usarDemo) { Text("Usar cuenta de demostración") }
            if (a.rol == Rol.CLIENTE) TextButton(onClick = { vm.ir(Pantalla.Registro) }) { Text("¿Cliente nuevo? Crea tu cuenta") }
            else Text("Administrador e Investigador son perfiles de prueba.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ============================================================ Pantalla 2: Registro de Cliente
// Formulario para crear una cuenta nueva con nombre, correo, contraseña y avatar.
@Composable
fun PantallaRegistro(vm: ViewModel, alVolver: () -> Unit) {
    val r by vm.registro.collectAsStateWithLifecycle()
    ConBarra("Registro de cliente", alVolver) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Campo("Nombre", r.nombre, { v -> vm.editarRegistro { it.copy(nombre = campo(v) { t -> if (t.isBlank()) "Campo obligatorio" else null }) } })
            Campo("Correo", r.correo, { v -> vm.editarRegistro { it.copy(correo = campo(v, cl.triskeledu.museo.model.Validar::correo)) } }, teclado = KeyboardType.Email)
            Campo("Contraseña (8+, letras y números)", r.clave, { v -> vm.editarRegistro { it.copy(clave = campo(v, cl.triskeledu.museo.model.Validar::clave)) } }, clave = true)
            Campo("Repite la contraseña", r.confirmar, { v -> vm.editarRegistro { it.copy(confirmar = campo(v) { c -> if (c == it.clave.valor) null else "No coinciden" }) } }, clave = true)
            Text("Avatar ficticio")
            SelectorAvatar(r.avatar) { av -> vm.editarRegistro { it.copy(avatar = av) } }
            r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::registrar, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
        }
    }
}

@Composable
fun SelectorAvatar(actual: String, alElegir: (String) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    AVATARES.forEach { a ->
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (a == actual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
            .clickable { alElegir(a) }, Alignment.Center) { Text(a) }
    }
}

// ============================================================ Pantalla 4: Inicio (dashboard)
// Muestra números y accesos rápidos distintos según el rol.
@Composable
fun PantallaInicio(vm: ViewModel, u: Usuario) {
    val museos by vm.museos.collectAsStateWithLifecycle()
    val favoritos by vm.favoritos.collectAsStateWithLifecycle()
    val carrito by vm.carrito.collectAsStateWithLifecycle()
    val solicitudes by vm.solicitudes.collectAsStateWithLifecycle()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    val (indicadores, accesos) = when (u.rol) {
        Rol.CLIENTE -> listOf("Favoritos" to favoritos.size, "En carrito" to carrito.sumOf { it.cantidad }, "Solicitudes" to solicitudes.size) to
            listOf("🗺️ Mapa" to Pantalla.Mapa, "🛒 Catálogo" to Pantalla.Catalogo, "📷 Escanear QR" to Pantalla.Escaner,
                "❤️ Favoritos" to Pantalla.Favoritos, "🧺 Carrito" to Pantalla.Carrito, "📋 Mis solicitudes" to Pantalla.Solicitudes)
        Rol.ADMINISTRADOR -> listOf("Pendientes" to solicitudes.count { it.solicitud.estado == EstadoSolicitud.PENDIENTE },
            "Fichas por revisar" to museos.count { it.estado == EstadoFicha.EN_REVISION }, "Museos" to museos.size) to
            listOf("📋 Solicitudes" to Pantalla.Solicitudes, "🛒 Gestionar catálogo" to Pantalla.Catalogo,
                "➕ Nuevo producto" to Pantalla.FichaProducto(null), "📣 Enviar avisos" to Pantalla.Avisos)
        Rol.INVESTIGADOR -> museos.filter { it.autorId == u.id }.let { mias ->
            listOf("Mis fichas" to mias.size, "En revisión" to mias.count { it.estado == EstadoFicha.EN_REVISION },
                "Observadas" to mias.count { it.estado == EstadoFicha.OBSERVADO })
        } to listOf("➕ Nueva ficha" to Pantalla.FichaMuseo(null), "🗺️ Mapa" to Pantalla.Mapa, "📷 Escanear QR" to Pantalla.Escaner)
    }

    ConBarra("Museo de los Museos", null) { m ->
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${u.avatar} Hola, ${u.nombre}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Etiqueta(u.rol.etiqueta)
            AnimatedVisibility(visible, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        indicadores.forEach { (t, n) ->
                            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Column(Modifier.padding(12.dp)) { Text("$n", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(t, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    accesos.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { (t, p) -> Card(onClick = { vm.ir(p) }, modifier = Modifier.weight(1f)) { Text(t, Modifier.padding(16.dp), fontWeight = FontWeight.Bold) } }
                            if (fila.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
            val destacados = if (u.rol == Rol.ADMINISTRADOR) museos.filter { it.estado == EstadoFicha.EN_REVISION } else museos
            Titulo(if (u.rol == Rol.ADMINISTRADOR) "Fichas por revisar" else "Museos destacados")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(destacados, key = { it.id }) { museo ->
                    Card(onClick = { vm.ir(Pantalla.Museo(museo.id)) }, modifier = Modifier.width(170.dp)) {
                        Foto(museo.fotoRuta ?: museo.imagenUrl, Modifier.fillMaxWidth().height(100.dp))
                        Text(museo.nombre, Modifier.padding(8.dp), fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                }
            }
        }
    }
}

// ============================================================ Pantalla 14: Notificaciones
// Lista de avisos del usuario. El Administrador también puede enviar avisos.
@Composable
fun PantallaAvisos(vm: ViewModel, u: Usuario, alVolver: () -> Unit) {
    val lista by vm.notificaciones.collectAsStateWithLifecycle()
    var titulo by rememberSaveable { mutableStateOf("") }
    var mensaje by rememberSaveable { mutableStateOf("") }
    var rol by rememberSaveable { mutableStateOf<Rol?>(null) }
    ConBarra("Notificaciones", alVolver, { TextButton({ vm.leer(null) }) { Text("Leer todo", color = Color.White) } }) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (u.rol.gestiona) Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Enviar aviso de prueba", fontWeight = FontWeight.Bold)
                    OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(mensaje, { mensaje = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth())
                    Selector("Para", Rol.entries, rol, { it.etiqueta }, { rol = it })
                    Button({ vm.enviarAviso(rol, titulo, mensaje); titulo = ""; mensaje = "" }, Modifier.fillMaxWidth()) { Text("Enviar") }
                }
            }
            if (lista.isEmpty()) Text("No tienes notificaciones.")
            lista.forEach { n ->
                Card(onClick = { vm.leer(n) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (n.leida) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer)) {
                    ListItem(headlineContent = { Text(n.titulo, fontWeight = if (n.leida) null else FontWeight.Bold) },
                        supportingContent = { Text("${n.mensaje}\n${fecha(n.fecha)}") })
                }
            }
            Text("Los avisos se guardan por usuario; las notificaciones push reales requieren un backend (FCM).", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ============================================================ Pantalla 15: Perfil
// Datos y preferencias del usuario, permisos, cuentas de prueba (Admin) y cerrar sesión.
@Composable
fun PantallaPerfil(vm: ViewModel, u: Usuario, alVolver: () -> Unit) {
    val cuentas by vm.usuarios.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var nombre by rememberSaveable(u.id) { mutableStateOf(u.nombre) }
    var avatar by rememberSaveable(u.id) { mutableStateOf(u.avatar) }
    var avisos by rememberSaveable(u.id) { mutableStateOf(u.notificaciones) }
    var grande by rememberSaveable(u.id) { mutableStateOf(u.textoGrande) }
    var idioma by rememberSaveable(u.id) { mutableStateOf(u.idioma) }
    var refrescar by remember { mutableIntStateOf(0) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refrescar++ }

    ConBarra("Perfil", alVolver) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$avatar ${u.correo}", style = MaterialTheme.typography.titleMedium)
            Etiqueta(u.rol.etiqueta)
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            SelectorAvatar(avatar) { avatar = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Recibir notificaciones", Modifier.weight(1f)); Switch(avisos, { avisos = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Texto grande (accesibilidad)", Modifier.weight(1f)); Switch(grande, { grande = it }) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("es" to "Español", "pt" to "Português", "en" to "English").forEach { (c, t) -> FilterChip(idioma == c, { idioma = c }, { Text(t) }) }
            }
            Button({ vm.guardarPerfil(nombre, avatar, avisos, grande, idioma) }, Modifier.fillMaxWidth()) { Text("Guardar") }

            Titulo("Permisos")
            listOf("Cámara" to arrayOf(Manifest.permission.CAMERA), "Ubicación" to PERMISOS_UBICACION).forEach { (t, permisos) ->
                val ok = remember(refrescar) { permisos.any { context.tienePermiso(it) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t, Modifier.weight(1f))
                    if (ok) Etiqueta("Concedido", Verde) else OutlinedButton({ pedir.launch(permisos) }) { Text("Conceder") }
                }
            }

            if (u.rol.gestiona) {
                Titulo("Cuentas de demostración")
                cuentas.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.avatar} ${c.nombre} · ${c.rol.etiqueta}", Modifier.weight(1f))
                        Switch(c.activo, { vm.alternarCuenta(c) }, enabled = c.id != u.id)
                    }
                }
            }
            Titulo("Sesión")
            OutlinedButton({ vm.cerrarSesion(true) }, Modifier.fillMaxWidth()) { Text("Cambiar de usuario") }
            Button({ vm.cerrarSesion(false) }, Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        }
    }
}
