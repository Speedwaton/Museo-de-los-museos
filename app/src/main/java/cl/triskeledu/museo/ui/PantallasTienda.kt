package cl.triskeledu.museo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.triskeledu.museo.CAMPOS_PRODUCTO
import cl.triskeledu.museo.MuseoViewModel
import cl.triskeledu.museo.data.Categoria
import cl.triskeledu.museo.data.EstadoSolicitud
import cl.triskeledu.museo.data.Producto
import cl.triskeledu.museo.data.Usuario
import cl.triskeledu.museo.model.Destino
import cl.triskeledu.museo.model.Modalidad
import cl.triskeledu.museo.model.Pantalla
import cl.triskeledu.museo.model.clp
import cl.triskeledu.museo.model.fecha
import kotlinx.coroutines.delay

private fun emoji(c: Categoria) = when (c) { Categoria.CURSO -> "🎓"; Categoria.PUBLICACION -> "📚"; Categoria.ARTICULO -> "🧤"; Categoria.CONSERVACION -> "📦" }

@Composable
private fun Disponibilidad(p: Producto) = when {
    !p.activo -> Etiqueta("Desactivado", MaterialTheme.colorScheme.outline)
    p.stock == 0 -> Etiqueta(if (p.esCurso) "Sin cupos" else "Agotado", MaterialTheme.colorScheme.error)
    p.stock <= 5 -> Etiqueta("Últimas ${p.stock}", Ambar)
    else -> Etiqueta("Disponible", Verde)
}

// ============================================================ 9. Catálogo de productos y cursos
@Composable
fun PantallaCatalogo(vm: MuseoViewModel, u: Usuario, alVolver: () -> Unit) {
    val lista by vm.catalogo.collectAsStateWithLifecycle()
    val texto by vm.busqueda.collectAsStateWithLifecycle()
    val cat by vm.categoria.collectAsStateWithLifecycle()
    val carrito by vm.carrito.collectAsStateWithLifecycle()
    ConBarra(if (u.rol.gestiona) "Gestión del catálogo" else "Catálogo", alVolver, {
        if (u.rol.gestiona) IconButton({ vm.ir(Pantalla.FichaProducto(null)) }) { Icon(Icons.Filled.Add, "Nuevo") }
        if (u.rol.compra) IconButton({ vm.ir(Pantalla.Carrito) }) {
            BadgedBox({ if (carrito.isNotEmpty()) Badge { Text("${carrito.sumOf { it.cantidad }}") } }) { Icon(Icons.Filled.ShoppingCart, "Carrito") }
        }
    }) { m ->
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(texto, { vm.busqueda.value = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(cat == null, { vm.categoria.value = null }, { Text("Todo") }) }
                items(Categoria.entries) { c -> FilterChip(cat == c, { vm.categoria.value = c }, { Text(c.etiqueta) }) }
            }
            lista.groupBy { it.categoria }.forEach { (c, productos) ->
                Titulo(c.etiqueta)
                productos.forEach { p ->
                    Card(onClick = { vm.ir(Pantalla.Producto(p.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Foto(null, Modifier.size(56.dp), emoji(c))
                            Column(Modifier.weight(1f)) { Text(p.nombre, fontWeight = FontWeight.Bold); Text(clp(p.precio)); Disponibilidad(p) }
                            if (u.rol.gestiona) Switch(p.activo, { vm.alternarActivo(p) })   // Desactivación
                        }
                    }
                }
            }
            if (lista.isEmpty()) Text("Sin resultados.")
        }
    }
}

// ============================================================ 10. Detalle y solicitud de compra
@Composable
fun PantallaProducto(vm: MuseoViewModel, u: Usuario, id: Long, alVolver: () -> Unit) {
    val productos by vm.productos.collectAsStateWithLifecycle()
    val p = productos.firstOrNull { it.id == id }
    var cantidad by rememberSaveable { mutableIntStateOf(1) }
    var consulta by rememberSaveable { mutableStateOf("") }
    var listo by rememberSaveable { mutableStateOf<Boolean?>(null) }   // Animación breve de éxito o error
    LaunchedEffect(listo) { if (listo != null) { delay(1800); listo = null } }

    ConBarra(p?.categoria?.etiqueta ?: "Producto", alVolver) { m ->
        if (p == null) { Text("Producto no disponible.", Modifier.padding(m).padding(16.dp)); return@ConBarra }
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Foto(null, Modifier.fillMaxWidth().height(150.dp), emoji(p.categoria))
            Text(p.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(clp(p.precio), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); Disponibilidad(p)
            }
            Text(p.descripcion)
            Dato(if (p.esCurso) "Cupos" else "Stock / peso", if (p.esCurso) "${p.stock}" else "${p.stock} u. · ${p.pesoGramos} g")
            Text("Precio ficticio de demostración.", style = MaterialTheme.typography.bodySmall)
            AnimatedVisibility(listo != null, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Text(if (listo == true) "✅ ¡Listo!" else "⚠️ No se pudo", style = MaterialTheme.typography.titleLarge,
                    color = if (listo == true) Verde else MaterialTheme.colorScheme.error)
            }
            if (u.rol.compra && p.esCurso) {
                OutlinedTextField(consulta, { consulta = it }, label = { Text("Tu consulta sobre el curso") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Button({ vm.pedirInformacion(p, consulta); consulta = "" }, Modifier.fillMaxWidth()) { Text("Solicitar información") }
            } else if (u.rol.compra) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ if (cantidad > 1) cantidad-- }) { Text("−") }
                    Text("$cantidad", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton({ if (cantidad < p.stock) cantidad++ }) { Text("+") }
                    Text("= ${clp(p.precio * cantidad)}")
                }
                Button({ vm.agregar(p, cantidad) { listo = it } }, Modifier.fillMaxWidth(), enabled = p.disponible) { Text("Agregar al carrito") }
                OutlinedButton({ vm.ir(Pantalla.Carrito) }, Modifier.fillMaxWidth()) { Text("Ver carrito") }
            }
            if (u.rol.gestiona) Button({ vm.ir(Pantalla.FichaProducto(p.id)) }, Modifier.fillMaxWidth()) { Text("Modificar") }
            Text("Código QR: ${p.codigo}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ============================================================ 9b. Alta y modificación (Administrador)
@Composable
fun PantallaFichaProducto(vm: MuseoViewModel, id: Long?, alVolver: () -> Unit) {
    LaunchedEffect(id) { vm.prepararProducto(id) }
    val f by vm.form.collectAsStateWithLifecycle()
    ConBarra(if (id == null) "Nuevo producto" else "Modificar producto", alVolver) { m ->
        Column(Modifier.padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Categoria.entries) { c -> FilterChip(f.categoria == c, { vm.editarForm { it.copy(categoria = c) } }, { Text(c.etiqueta) }) }
            }
            CAMPOS_PRODUCTO.keys.forEach { k ->
                f.campos[k]?.let { c ->
                    Campo(k, c, { vm.cambiarCampo(k, it, CAMPOS_PRODUCTO) },
                        teclado = if (k.startsWith("Precio") || k.startsWith("Stock") || k.startsWith("Peso")) KeyboardType.Number else KeyboardType.Text)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Publicado (activo)", Modifier.weight(1f)); Switch(f.activo, { a -> vm.editarForm { it.copy(activo = a) } }) }
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(vm::guardarProducto, Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }
}

// ============================================================ 12. Carrito y cálculo de envío
@Composable
fun PantallaCarrito(vm: MuseoViewModel, alVolver: () -> Unit) {
    val lineas by vm.carrito.collectAsStateWithLifecycle()
    val destino by vm.destino.collectAsStateWithLifecycle()
    val modalidad by vm.modalidad.collectAsStateWithLifecycle()
    var confirmar by rememberSaveable { mutableStateOf(false) }
    val subtotal = lineas.sumOf { it.subtotal }
    val envio = remember(lineas, destino, modalidad) { vm.costoEnvio(lineas) }

    ConBarra("Carrito", alVolver) { m ->
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lineas.isEmpty()) { Text("Tu carrito está vacío."); Button({ vm.ir(Pantalla.Catalogo) }) { Text("Ir al catálogo") }; return@Column }
            lineas.forEach { l ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(l.nombre, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ vm.cambiarCantidad(l, l.cantidad - 1) }) { Text("−") }
                            Text("${l.cantidad}")
                            OutlinedButton({ vm.cambiarCantidad(l, l.cantidad + 1) }) { Text("+") }
                            Text("${clp(l.precio)} c/u → ${clp(l.subtotal)}", Modifier.weight(1f))
                        }
                    }
                }
            }
            Titulo("Destino y modalidad")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Destino.entries.forEach { d -> FilterChip(destino == d, { vm.destino.value = d }, { Text(d.etiqueta) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Modalidad.entries.forEach { d -> FilterChip(modalidad == d, { vm.modalidad.value = d }, { Text(d.etiqueta) }) } }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Subtotal: ${clp(subtotal)}")
                    Text("Envío: ${if (envio == 0) "Gratis / digital" else clp(envio)}")
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Total final: ${clp(subtotal + envio)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Reglas de envío ficticias, sin courier real.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button({ confirmar = true }, Modifier.fillMaxWidth()) { Text("Enviar solicitud") }
        }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("Confirmar pedido") },
        text = { Text("Total final ${clp(subtotal + envio)} (${destino.etiqueta}, ${modalidad.etiqueta}). Se validará el stock antes de registrar.") },
        confirmButton = { Button({ confirmar = false; vm.comprar() }) { Text("Confirmar") } },
        dismissButton = { TextButton({ confirmar = false }) { Text("Cancelar") } },
    )
}

// ============================================================ 13. Historial y gestión de solicitudes
@Composable
fun PantallaSolicitudes(vm: MuseoViewModel, u: Usuario, alVolver: () -> Unit) {
    val lista by vm.solicitudes.collectAsStateWithLifecycle()
    ConBarra(if (u.rol.gestiona) "Gestión de solicitudes" else "Mis solicitudes", alVolver) { m ->
        Column(Modifier.padding(m).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lista.isEmpty()) Text("No hay solicitudes.")
            lista.forEach { (s, cliente) ->
                Card(Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${s.id} · ${s.tipo}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Etiqueta(s.estado.etiqueta, if (s.estado == EstadoSolicitud.ENTREGADO) Verde else Ambar)
                        }
                        if (u.rol.gestiona) Text("Cliente: $cliente")
                        Text(s.resumen)
                        if (s.mensaje.isNotBlank()) Text("Consulta: ${s.mensaje}", style = MaterialTheme.typography.bodySmall)
                        if (s.total > 0) Text("${clp(s.subtotal)} + envío ${clp(s.envio)} = ${clp(s.total)} · ${s.destino}")
                        Text(fecha(s.fecha), style = MaterialTheme.typography.bodySmall)
                        if (u.rol.gestiona) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(EstadoSolicitud.entries) { e -> FilterChip(s.estado == e, { vm.cambiarEstado(s, e) }, { Text(e.etiqueta) }) }
                        }
                    }
                }
            }
        }
    }
}
