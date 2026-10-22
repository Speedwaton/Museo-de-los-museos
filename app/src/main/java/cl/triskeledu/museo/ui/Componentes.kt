package cl.triskeledu.museo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.triskeledu.museo.model.CampoFormulario
import cl.triskeledu.museo.util.cargarImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

// ---------------------------------------------------------------- Tema
// Colores de la app.
val Burdeo = Color(0xFF6D2E46)
val Verde = Color(0xFF2E7D32)
val Ambar = Color(0xFFB26A00)

// Colores y letras de la app. Con texto grande, las letras son un 25 % más grandes.
@Composable
fun TemaMuseo(textoGrande: Boolean, contenido: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * if (textoGrande) 1.25f else 1f)) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFE3A9BE), secondary = Color(0xFFE9C16C))
            else lightColorScheme(primary = Burdeo, primaryContainer = Color(0xFFF6DCE5), secondary = Color(0xFF8A6116),
                secondaryContainer = Color(0xFFFFDEA6), background = Color(0xFFFBF8F3), surface = Color(0xFFFBF8F3)),
            content = contenido,
        )
    }
}

// ---------------------------------------------------------------- Estructura y campos
// Pantalla con barra superior, título y flecha para volver.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConBarra(titulo: String, alVolver: (() -> Unit)?, acciones: @Composable () -> Unit = {}, contenido: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo, maxLines = 1) },
                navigationIcon = { alVolver?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } } },
                actions = { acciones() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
            )
        },
        content = contenido,
    )
}

// Campo de texto que muestra su error debajo.
@Composable
fun Campo(etiqueta: String, campo: CampoFormulario, alCambiar: (String) -> Unit, clave: Boolean = false,
          teclado: KeyboardType = KeyboardType.Text, lineas: Int = 1) {
    OutlinedTextField(
        value = campo.valor, onValueChange = alCambiar, label = { Text(etiqueta) },
        isError = campo.error != null, supportingText = campo.error?.let { { Text(it) } },
        visualTransformation = if (clave) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (clave) KeyboardType.Password else teclado),
        singleLine = lineas == 1, minLines = lineas, modifier = Modifier.fillMaxWidth(),
    )
}

// Un dato: título pequeño y su valor debajo.
@Composable
fun Dato(etiqueta: String, valor: String) = Column {
    Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(valor)
}

// Etiqueta de color, por ejemplo el estado de una ficha.
@Composable
fun Etiqueta(texto: String, color: Color = MaterialTheme.colorScheme.primary) =
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(texto, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }

// Título de una sección.
@Composable
fun Titulo(texto: String) = Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

// Menú desplegable para elegir una opción (o "Todos").
@Composable
fun <T> Selector(etiqueta: String, opciones: List<T>, valor: T?, texto: (T) -> String, alElegir: (T?) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) { Text("$etiqueta: ${valor?.let(texto) ?: "Todos"}", maxLines = 1) }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { alElegir(null); abierto = false })
            opciones.forEach { o -> DropdownMenuItem(text = { Text(texto(o)) }, onClick = { alElegir(o); abierto = false }) }
        }
    }
}

// ---------------------------------------------------------------- Imágenes
// Muestra una foto (de la cámara o de internet). Mientras carga muestra un círculo, y si no hay foto, un emoji.
@Composable
fun Foto(origen: String?, modifier: Modifier, emoji: String = "🏛️") {
    // Carga la imagen en segundo plano.
    val estado by produceState<Pair<Boolean, Bitmap?>>((origen != null) to null, origen) {
        value = false to origen?.let { withContext(Dispatchers.IO) { cargarImagen(it) } }
    }
    val (cargando, imagen) = estado
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        when {
            imagen != null -> Image(imagen.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            cargando -> CircularProgressIndicator()
            else -> Text(emoji, fontSize = 40.sp)
        }
    }
}

// ---------------------------------------------------------------- Mapa
// Crea el mapa de OpenStreetMap centrado en Iberoamérica.
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapa = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 2.0
            controller.setZoom(2.8)
            controller.setCenter(GeoPoint(10.0, -45.0)) // Centro entre América y España
        }
    }
    // Pausa y reanuda el mapa junto con la app.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) mapa.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) mapa.onPause()
        }
        ciclo.addObserver(observador)
        onDispose { ciclo.removeObserver(observador); mapa.onDetach() }
    }
    return mapa
}

// Muestra el mapa dentro de la pantalla sin que se salga de su espacio.
@Composable
fun VistaMapa(mapa: MapView, modifier: Modifier) = AndroidView({ mapa }, modifier.clipToBounds())
