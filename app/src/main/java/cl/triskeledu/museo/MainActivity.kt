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

/** Única Activity: Compose dibuja todas las pantallas según el estado del ViewModel. */
class MainActivity : ComponentActivity() {
    private val viewModel: MuseoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OpenStreetMap exige identificar la app; la caché de tiles queda en almacenamiento privado.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) abrirDestino(intent)
        setContent { MuseoApp(viewModel) }
    }

    /** Al tocar una notificación con la app abierta. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDestino(intent)
    }

    private fun abrirDestino(intent: Intent?) {
        intent?.getStringExtra(EXTRA_DESTINO)?.let(viewModel::abrirDestino)
        intent?.removeExtra(EXTRA_DESTINO)
    }
}
