package cl.triskeledu.museo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.triskeledu.museo.MuseoViewModel
import cl.triskeledu.museo.model.Pantalla

// Pantalla base: muestra la pantalla actual y la barra inferior (Inicio, Mapa, Catálogo o QR, Avisos y Perfil).
@Composable
fun MuseoApp(vm: MuseoViewModel) {
    val usuario by vm.usuario.collectAsStateWithLifecycle()
    val pila by vm.pila.collectAsStateWithLifecycle()
    val avisos by vm.notificaciones.collectAsStateWithLifecycle()
    val pantalla = pila.last()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.mensajes.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }
    BackHandler(pila.size > 1) { vm.volver() }

    // Pide permiso para mostrar notificaciones (Android 13 o más).
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(usuario?.id) { if (usuario != null && Build.VERSION.SDK_INT >= 33) pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS) }

    TemaMuseo(usuario?.textoGrande == true) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                val u = usuario ?: return@Scaffold
                if (pantalla == Pantalla.Bienvenida || pantalla == Pantalla.Acceso || pantalla == Pantalla.Registro) return@Scaffold
                val raiz = pila.getOrNull(1) ?: Pantalla.Inicio
                val tercera = if (u.rol.veCatalogo) Pantalla.Catalogo else Pantalla.Escaner
                NavigationBar {
                    listOf(Pantalla.Inicio to "Inicio", Pantalla.Mapa to "Mapa", tercera to if (u.rol.veCatalogo) "Catálogo" else "QR",
                        Pantalla.Avisos to "Avisos", Pantalla.Perfil to "Perfil").forEach { (p, nombre) ->
                        NavigationBarItem(
                            selected = raiz == p, onClick = { vm.pestana(p) }, label = { Text(nombre) },
                            icon = {
                                val icono = when (p) {
                                    Pantalla.Inicio -> Icons.Filled.Home; Pantalla.Mapa -> Icons.Filled.Place
                                    Pantalla.Catalogo -> Icons.Filled.ShoppingCart; Pantalla.Avisos -> Icons.Filled.Notifications
                                    Pantalla.Perfil -> Icons.Filled.Person; else -> Icons.Filled.Search
                                }
                                val sinLeer = if (p == Pantalla.Avisos) avisos.count { !it.leida } else 0
                                BadgedBox(badge = { if (sinLeer > 0) Badge { Text("$sinLeer") } }) { Icon(icono, null) }
                            },
                        )
                    }
                }
            },
        ) { margen ->
            Box(Modifier.fillMaxSize().padding(margen).consumeWindowInsets(margen)) {
                AnimatedContent(pantalla, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pantallas") { p ->
                    val volver: () -> Unit = { vm.volver() }
                    when (p) {
                        Pantalla.Bienvenida -> PantallaBienvenida(vm)
                        Pantalla.Acceso -> PantallaAcceso(vm, if (pila.size > 1) volver else null)
                        Pantalla.Registro -> PantallaRegistro(vm, volver)
                        else -> usuario?.let { u ->
                            when (p) {
                                Pantalla.Inicio -> PantallaInicio(vm, u)
                                Pantalla.Mapa -> PantallaMapa(vm, u, volver)
                                is Pantalla.Museo -> PantallaMuseo(vm, u, p.id, volver)
                                is Pantalla.FichaMuseo -> PantallaFichaMuseo(vm, p.id, volver)
                                Pantalla.Escaner -> PantallaEscaner(vm, if (pila.size > 1) volver else null)
                                Pantalla.ResultadoQr -> PantallaResultadoQr(vm, volver)
                                Pantalla.Catalogo -> PantallaCatalogo(vm, u, volver)
                                is Pantalla.Producto -> PantallaProducto(vm, u, p.id, volver)
                                is Pantalla.FichaProducto -> PantallaFichaProducto(vm, p.id, volver)
                                Pantalla.Favoritos -> PantallaFavoritos(vm, volver)
                                Pantalla.Carrito -> PantallaCarrito(vm, volver)
                                Pantalla.Solicitudes -> PantallaSolicitudes(vm, u, volver)
                                Pantalla.Avisos -> PantallaAvisos(vm, u, volver)
                                Pantalla.Perfil -> PantallaPerfil(vm, u, volver)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
