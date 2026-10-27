package cl.triskeledu.museo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cl.triskeledu.museo.ui.MuseoApp
import cl.triskeledu.museo.util.EXTRA_DESTINO
import org.osmdroid.config.Configuration
import java.io.File

// Pantalla principal de Android. Desde aquí Compose dibuja todas las pantallas.
class MainActivity : ComponentActivity() {
    private val viewModel: ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configura el mapa: nombre de la app y carpeta donde guarda el mapa descargado.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) abrirDestino(intent)
        setContent { MuseoApp(viewModel) }
    }

    // Si se toca una notificación con la app abierta, abre su pantalla.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDestino(intent)
    }

    // Abre la pantalla que indica la notificación.
    private fun abrirDestino(intent: Intent?) {
        intent?.getStringExtra(EXTRA_DESTINO)?.let(viewModel::abrirDestino)
        intent?.removeExtra(EXTRA_DESTINO)
    }
}
