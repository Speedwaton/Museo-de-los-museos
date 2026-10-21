package cl.triskeledu.museo.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cl.triskeledu.museo.MainActivity
import cl.triskeledu.museo.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------- Permisos
val PERMISOS_UBICACION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.tienePermiso(permiso: String) =
    ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED

// ---------------------------------------------------------------- Notificaciones locales
const val EXTRA_DESTINO = "destino"

/** Muestra un aviso del sistema; al tocarlo, MainActivity abre la pantalla `destino`. */
@SuppressLint("MissingPermission")
fun Context.notificar(titulo: String, mensaje: String, destino: String?) {
    if (Build.VERSION.SDK_INT >= 33 && !tienePermiso(Manifest.permission.POST_NOTIFICATIONS)) return
    getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel("avisos", "Avisos del museo", NotificationManager.IMPORTANCE_DEFAULT))
    val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    val abrir = PendingIntent.getActivity(
        this, id, Intent(this, MainActivity::class.java).putExtra(EXTRA_DESTINO, destino),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    NotificationManagerCompat.from(this).notify(
        id, NotificationCompat.Builder(this, "avisos").setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo).setContentText(mensaje).setContentIntent(abrir).setAutoCancel(true).build(),
    )
}

// ---------------------------------------------------------------- Cámara (como el ejemplo de cámara)
fun Context.crearArchivoFoto(): File = File(filesDir, "fotos").apply { mkdirs() }.let { File(it, "ficha_${System.currentTimeMillis()}.jpg") }
fun Context.uriCompartible(archivo: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)

// ---------------------------------------------------------------- Imágenes (archivo o Internet)
private val cache = LruCache<String, Bitmap>(24)

/** Decodifica bytes reduciendo la imagen para no agotar memoria. */
private fun decodificar(bytes: ByteArray, lado: Int): Bitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    var escala = 1
    while (medidas.outWidth / (escala * 2) >= lado && medidas.outHeight / (escala * 2) >= lado) escala *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = escala })
}

/**
 * Carga una foto local (cámara) o descarga una imagen web. Llamar desde un hilo secundario.
 * Wikimedia pide identificar la app con un User-Agent propio.
 */
fun cargarImagen(origen: String, lado: Int = 900): Bitmap? = cache.get(origen) ?: runCatching {
    val bytes = if (origen.startsWith("http")) {
        (URL(origen).openConnection() as HttpURLConnection).run {
            setRequestProperty("User-Agent", "MuseoDeLosMuseos/3.0 (app academica Android)")
            connectTimeout = 10_000
            readTimeout = 15_000
            inputStream.use { it.readBytes() }
        }
    } else File(origen).readBytes()
    decodificar(bytes, lado)
}.getOrNull()?.also { cache.put(origen, it) }

// ---------------------------------------------------------------- QR (como el ejemplo de QR)
/** Abre el escáner de Google Play services, que usa su propia pantalla de cámara. */
fun escanearQr(context: Context, alLeer: (String) -> Unit, alFallar: (String) -> Unit) {
    GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        .startScan()
        .addOnSuccessListener { alLeer(it.rawValue ?: "") }
        .addOnCanceledListener { alFallar("Escaneo cancelado.") }
        .addOnFailureListener { alFallar("No se pudo escanear: ${it.message}") }
}

/** Dibuja el QR de una ficha (ZXing) para probarlo desde otro teléfono. */
fun imagenQr(texto: String, lado: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado)
    val px = IntArray(lado * lado) { if (m[it % lado, it / lado]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(px, lado, lado, Bitmap.Config.ARGB_8888)
}

// ---------------------------------------------------------------- Mapa (como el ejemplo de mapas)
fun MapView.marcador(punto: GeoPoint, titulo: String, alTocar: () -> Unit) = Marker(this).apply {
    position = punto
    title = titulo
    icon = ContextCompat.getDrawable(context, R.drawable.ic_marcador_museo)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    setOnMarkerClickListener { _, _ -> alTocar(); true }
}
