package cl.triskeledu.museo.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.triskeledu.museo.CAMPOS_FICHA
import cl.triskeledu.museo.EstadoQr
import cl.triskeledu.museo.MuseoViewModel
import cl.triskeledu.museo.data.EstadoFicha
import cl.triskeledu.museo.data.Usuario
import cl.triskeledu.museo.model.LectorQr
import cl.triskeledu.museo.model.Pantalla
import cl.triskeledu.museo.model.Rol
import cl.triskeledu.museo.model.fecha
import cl.triskeledu.museo.util.PERMISOS_UBICACION
import cl.triskeledu.museo.util.crearArchivoFoto
import cl.triskeledu.museo.util.escanearQr
import cl.triskeledu.museo.util.imagenQr
import cl.triskeledu.museo.util.marcador
import cl.triskeledu.museo.util.tienePermiso
import cl.triskeledu.museo.util.uriCompartible
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

@Composable
fun colorFicha(e: EstadoFicha) = when (e) { EstadoFicha.PUBLICADO -> Verde; EstadoFicha.EN_REVISION -> Ambar; else -> MaterialTheme.colorScheme.error }

// ============================================================ Pantalla 5: Mapa de museos
// Mapa con un marcador por museo, filtros por país y proyecto, y botón Mi ubicación.
@Composable
fun PantallaMapa(vm: MuseoViewModel, u: Usuario, alVolver: () -> Unit) {
    val todos by vm.museos.collectAsStateWithLifecycle()
    val museos by vm.museosFiltrados.collectAsStateWithLifecycle()
    val pais by vm.filtroPais.collectAsStateWithLifecycle()
    val proyecto by vm.filtroProyecto.collectAsStateWithLifecycle()
    val idSeleccionado by vm.seleccionado.collectAsStateWithLifecycle()
    val elegido = museos.firstOrNull { it.id == idSeleccionado }
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val mapa = rememberMapView()

    // Muestra la ubicación del usuario en el mapa y lo centra ahí.
    fun miUbicacion() = MyLocationNewOverlay(GpsMyLocationProvider(context), mapa).apply {
        enableMyLocation(); mapa.overlays.add(this)
        runOnFirstFix { mapa.post { myLocation?.let { mapa.controller.animateTo(it, 12.0, 1500L) } } }
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.any { ok -> ok }) miUbicacion() }

    LaunchedEffect(mapa) {
        mapa.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = true.also { vm.seleccionado.value = null }
            override fun longPressHelper(p: GeoPoint?) = false
        }))
    }
    LaunchedEffect(museos) {
        mapa.overlays.removeAll { it is Marker }
        museos.forEach { m -> mapa.overlays.add(mapa.marcador(GeoPoint(m.latitud, m.longitud), m.nombre) { vm.seleccionado.value = m.id }) }
        mapa.invalidate()
    }
    LaunchedEffect(elegido?.id) { elegido?.let { mapa.controller.animateTo(GeoPoint(it.latitud, it.longitud)) } }

    ConBarra("Mapa de museos virtuales", alVolver, {
        if (u.rol.editaFichas) IconButton({ vm.ir(Pantalla.FichaMuseo(null)) }) { Icon(Icons.Filled.Add, "Nueva ficha") }
    }) { m ->
        Column(Modifier.padding(m)) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Selector("País", todos.map { it.pais }.distinct().sorted(), pais, { it }, { vm.filtroPais.value = it }, Modifier.weight(1f))
                Selector("Proyecto", todos.map { it.proyecto }.distinct().sorted(), proyecto, { it }, { vm.filtroProyecto.value = it }, Modifier.weight(1f))
            }
            Box(Modifier.weight(1f)) {
                VistaMapa(mapa, Modifier.fillMaxSize())
                SmallFloatingActionButton({ if (PERMISOS_UBICACION.any(context::tienePermiso)) miUbicacion() else pedir.launch(PERMISOS_UBICACION) },
                    Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Filled.LocationOn, "Mi ubicación") }
                elegido?.let { museo ->
                    Card(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Foto(museo.fotoRuta ?: museo.imagenUrl, Modifier.size(84.dp))
                            Column(Modifier.weight(1f)) {
                                Text(museo.nombre, fontWeight = FontWeight.Bold)
                                Text("📍 ${museo.ciudad}, ${museo.pais}", style = MaterialTheme.typography.bodySmall)
                                Text("🌐 Visita virtual: ${museo.urlVisita}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button({ vm.ir(Pantalla.Museo(museo.id)) }) { Text("Ficha") }
                                    OutlinedButton({ links.openUri(museo.urlVisita) }) { Text("Visita") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ Pantalla 6: Detalle del museo
// Foto, datos, ubicación y visita virtual. Según el rol: favorito, corregir, publicar o ver su QR.
@Composable
fun PantallaMuseo(vm: MuseoViewModel, u: Usuario, id: Long, alVolver: () -> Unit) {
    val museos by vm.museos.collectAsStateWithLifecycle()
    val favoritos by vm.favoritos.collectAsStateWithLifecycle()
    val museo = museos.firstOrNull { it.id == id }
    val links = LocalUriHandler.current
    var observacion by rememberSaveable { mutableStateOf("") }

    ConBarra(museo?.nombre ?: "Museo", alVolver, {
        if (museo != null && u.rol.compra) IconButton({ vm.alternarFavorito(museo) }) {
            Icon(if (id in favoritos) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito")
        }
    }) { m ->
        if (museo == null) { Text("Ficha no disponible.", Modifier.padding(m).padding(16.dp)); return@ConBarra }
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Foto(museo.fotoRuta ?: museo.imagenUrl, Modifier.fillMaxWidth().height(220.dp))
            Text(if (museo.fotoRuta != null) "Foto referencial tomada con la cámara" else if (museo.imagenUrl != null) "Foto: Wikimedia Commons" else "",
                style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta(museo.clasificacion, MaterialTheme.colorScheme.secondary)
                if (u.rol.veNoPublicadas) Etiqueta(museo.estado.etiqueta, colorFicha(museo.estado))
            }
            Text(museo.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(museo.descripcion)
            Dato("País y ciudad", "${museo.pais} · ${museo.ciudad}")
            Dato("Proyecto", museo.proyecto)
            Dato("Ubicación geográfica", "%.4f, %.4f".format(museo.latitud, museo.longitud))
            Dato("Recorrido virtual", museo.urlVisita)
            Button({ links.openUri(museo.urlVisita) }, Modifier.fillMaxWidth()) { Text("Abrir visita virtual") }
            OutlinedButton({ vm.seleccionado.value = museo.id; vm.ir(Pantalla.Mapa) }, Modifier.fillMaxWidth()) { Text("Ver en el mapa") }
            if (u.rol.compra) OutlinedButton({ vm.alternarFavorito(museo) }, Modifier.fillMaxWidth()) {
                Text(if (id in favoritos) "Quitar de favoritos" else "Guardar en favoritos")
            }
            if (vm.puedeEditar(museo)) OutlinedButton({ vm.ir(Pantalla.FichaMuseo(museo.id)) }, Modifier.fillMaxWidth()) { Text("Corregir ficha") }
            museo.observacion?.let { if (u.rol.veNoPublicadas) Dato("Observación", it) }

            if (u.rol.gestiona) Card {   // El Administrador publica u observa la ficha
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Revisión de publicación", fontWeight = FontWeight.Bold)
                    OutlinedTextField(observacion, { observacion = it }, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.revisar(museo, EstadoFicha.PUBLICADO, null) }, Modifier.weight(1f)) { Text("Publicar") }
                        OutlinedButton({ vm.revisar(museo, EstadoFicha.OBSERVADO, observacion) }, Modifier.weight(1f), enabled = observacion.isNotBlank()) { Text("Observar") }
                    }
                }
            }
            if (u.rol.editaFichas) {   // Muestra el QR de la ficha
                val json = remember(museo) { LectorQr.generar("museo", museo.codigo, museo.nombre, museo.urlVisita) }
                Titulo("Código QR · ${museo.codigo}")
                Image(remember(json) { imagenQr(json) }.asImageBitmap(), "QR", Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                if (u.rol == Rol.INVESTIGADOR) OutlinedButton({ vm.procesarQr(json) }, Modifier.fillMaxWidth()) { Text("Probar este código") }
            }
        }
    }
}

// ============================================================ Pantalla 6b: Crear o corregir una ficha
// Formulario de la ficha, mapa para marcar la ubicación y foto con la cámara.
@Composable
fun PantallaFichaMuseo(vm: MuseoViewModel, id: Long?, alVolver: () -> Unit) {
    LaunchedEffect(id) { vm.prepararFicha(id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Abre la cámara del teléfono y guarda la foto en la ficha.
    var ruta by rememberSaveable { mutableStateOf<String?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.editarForm { it.copy(foto = ruta) } else ruta?.let { File(it).delete() }
    }
    fun abrirCamara() = context.crearArchivoFoto().let { ruta = it.absolutePath; camara.launch(context.uriCompartible(it)) }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) abrirCamara() else Toast.makeText(context, "Sin permiso de cámara", Toast.LENGTH_SHORT).show()
    }

    // Mapa pequeño: al mantenerlo presionado, se llenan la latitud y la longitud.
    val mapa = rememberMapView()
    LaunchedEffect(mapa) {
        mapa.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?) = true.also {
                p?.let {
                    vm.cambiarCampo("Latitud", "%.5f".format(java.util.Locale.US, it.latitude), CAMPOS_FICHA)
                    vm.cambiarCampo("Longitud", "%.5f".format(java.util.Locale.US, it.longitude), CAMPOS_FICHA)
                }
            }
        }))
    }
    val punto = f.v("Latitud").toDoubleOrNull()?.let { lat -> f.v("Longitud").toDoubleOrNull()?.let { GeoPoint(lat, it) } }
    LaunchedEffect(punto) {
        mapa.overlays.removeAll { it is Marker }
        punto?.let { mapa.overlays.add(mapa.marcador(it, "Ubicación") {}); mapa.controller.animateTo(it) }
        mapa.invalidate()
    }

    ConBarra(if (id == null) "Nueva ficha" else "Corregir ficha", alVolver) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CAMPOS_FICHA.keys.forEach { k ->
                f.campos[k]?.let { c -> Campo(k, c, { vm.cambiarCampo(k, it, CAMPOS_FICHA) }, lineas = if (k == "Descripción") 3 else 1) }
            }
            Text("Mantén presionado el mapa para fijar la ubicación.", style = MaterialTheme.typography.bodySmall)
            VistaMapa(mapa, Modifier.fillMaxWidth().height(180.dp))
            Titulo("Foto referencial (cámara)")
            Foto(f.foto ?: f.v("Foto web (URL, opcional)").ifBlank { null }, Modifier.fillMaxWidth().height(160.dp), "📷")
            OutlinedButton({ if (context.tienePermiso(Manifest.permission.CAMERA)) abrirCamara() else permiso.launch(Manifest.permission.CAMERA) },
                Modifier.fillMaxWidth()) { Text("Tomar foto") }
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(vm::guardarFicha, Modifier.fillMaxWidth()) { Text("Guardar ficha") }
        }
    }
}

// ============================================================ Pantalla 11: Mi colección
// Museos guardados como favoritos y museos consultados por QR.
@Composable
fun PantallaFavoritos(vm: MuseoViewModel, alVolver: () -> Unit) {
    val museos by vm.museos.collectAsStateWithLifecycle()
    val ids by vm.favoritos.collectAsStateWithLifecycle()
    val lecturas by vm.lecturas.collectAsStateWithLifecycle()
    val links = LocalUriHandler.current
    ConBarra("Mi colección", alVolver) { m ->
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Titulo("Museos guardados")
            val guardados = museos.filter { it.id in ids }
            if (guardados.isEmpty()) Text("Guarda museos con el corazón de su ficha.")
            guardados.forEach { museo ->
                Card(onClick = { vm.ir(Pantalla.Museo(museo.id)) }) {
                    ListItem(leadingContent = { Foto(museo.fotoRuta ?: museo.imagenUrl, Modifier.size(56.dp)) },
                        headlineContent = { Text(museo.nombre) }, supportingContent = { Text(museo.pais) },
                        trailingContent = {
                            Row {
                                OutlinedButton({ links.openUri(museo.urlVisita) }) { Text("Visita") }
                                IconButton({ vm.alternarFavorito(museo) }) { Icon(Icons.Filled.Favorite, "Quitar") }
                            }
                        })
                }
            }
            Titulo("Consultados por QR")
            lecturas.forEach { l ->
                Card(onClick = { vm.abrirDestino(l.destino) }) {
                    ListItem(headlineContent = { Text(l.titulo) }, supportingContent = { Text("${l.codigo} · ${fecha(l.fecha)} · ${l.veces} vez/veces") })
                }
            }
        }
    }
}

// ============================================================ Pantalla 7: Escáner QR
// Leer un QR con la cámara o pegando su texto, y ver el historial.
private val JSON_EJEMPLO = LectorQr.generar("museo", "MUS-004", "Museo Nacional del Prado", "https://www.museodelprado.es/")

@Composable
fun PantallaEscaner(vm: MuseoViewModel, alVolver: (() -> Unit)?) {
    val context = LocalContext.current
    val lecturas by vm.lecturas.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    ConBarra("Escáner QR", alVolver) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Apunta la cámara a un QR de un museo, exposición, publicación o producto.")
            // Abre el escáner de Google y procesa el texto leído.
            Button({ escanearQr(context, vm::procesarQr) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                Modifier.fillMaxWidth().height(56.dp)) { Text("📷 Escanear con la cámara") }
            Titulo("Probar sin cámara")
            OutlinedTextField(texto, { texto = it }, label = { Text("JSON del QR") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ texto = JSON_EJEMPLO }, Modifier.weight(1f)) { Text("Ejemplo") }
                Button({ vm.procesarQr(texto) }, Modifier.weight(1f), enabled = texto.isNotBlank()) { Text("Interpretar") }
            }
            Titulo("Historial")
            lecturas.forEach { l -> Card(onClick = { vm.abrirDestino(l.destino) }) { ListItem(headlineContent = { Text(l.titulo) }, supportingContent = { Text("${l.codigo} · ${l.veces} lectura(s)") }) } }
        }
    }
}

// ============================================================ Pantalla 8: Resultado del QR
// Muestra la ficha encontrada, o el error si el código no sirve.
@Composable
fun PantallaResultadoQr(vm: MuseoViewModel, alVolver: () -> Unit) {
    val estado by vm.qr.collectAsStateWithLifecycle()
    val links = LocalUriHandler.current
    val escala = remember { Animatable(0f) }
    LaunchedEffect(estado) { if (estado !is EstadoQr.Leyendo) { escala.snapTo(0f); escala.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) } }

    ConBarra("Resultado del QR", alVolver) { m ->
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val e = estado) {
                EstadoQr.Leyendo -> CircularProgressIndicator()
                is EstadoQr.Ok -> {
                    Box(Modifier.size(90.dp).scale(escala.value).clip(CircleShape).background(Verde), Alignment.Center) { Text("✓", color = Color.White, fontSize = 48.sp) }
                    Text("¡Lectura exitosa!", color = Verde, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (e.nueva) "Agregado a tu historial." else "Ya estaba en tu historial (se actualizó).", style = MaterialTheme.typography.bodySmall)
                    Foto(e.imagen, Modifier.fillMaxWidth().height(180.dp), if (e.destino is Pantalla.Museo) "🏛️" else "📦")
                    Text(e.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(e.detalle)
                    Button({ vm.reemplazar(e.destino) }, Modifier.fillMaxWidth()) { Text("Abrir ficha") }
                    if (e.url.isNotBlank()) OutlinedButton({ links.openUri(e.url) }, Modifier.fillMaxWidth()) { Text("Visita virtual") }
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("JSON interpretado", fontWeight = FontWeight.Bold); e.contenido.campos.forEach { (k, v) -> Dato(k, v) } } }
                }
                is EstadoQr.Error -> {
                    Box(Modifier.size(90.dp).scale(escala.value).clip(CircleShape).background(MaterialTheme.colorScheme.error), Alignment.Center) { Text("✕", color = Color.White, fontSize = 48.sp) }
                    Text("Código no válido", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(e.mensaje)
                    e.contenido?.campos?.forEach { (k, v) -> Dato(k, v) }
                    Text(e.texto, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Button(alVolver, Modifier.fillMaxWidth()) { Text("Volver a escanear") }
                }
            }
        }
    }
}
