package cl.triskeledu.museo

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.triskeledu.museo.data.BaseDatos
import cl.triskeledu.museo.data.MIGRACION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Prueba que al pasar la base de la versión 1 a la 2 no se pierden museos ni favoritos.
// Se ejecuta en un teléfono o emulador.
@RunWith(AndroidJUnit4::class)
class MigracionTest {
    @get:Rule val ayudante = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BaseDatos::class.java)

    @Test fun migrar1a2SinPerderDatos() {
        ayudante.createDatabase("prueba.db", 1).apply {
            execSQL("INSERT INTO museos (id, nombre, pais, ciudad, descripcion, clasificacion, proyecto, latitud, longitud, " +
                "urlVisita, imagenUrl, codigo, estado, observacion, autorId) VALUES (1, 'Museo Prueba', 'Chile', 'Santiago', " +
                "'Texto', 'Arte', 'Demo', -33.4, -70.6, 'https://ejemplo.cl', NULL, 'MUS-900', 'PUBLICADO', NULL, NULL)")
            execSQL("INSERT INTO favoritos (usuarioId, museoId, fecha) VALUES (1, 1, 0)")
            close()
        }
        val db = ayudante.runMigrationsAndValidate("prueba.db", 2, true, MIGRACION_1_2)
        db.query("SELECT nombre, fotoRuta FROM museos").use { assertTrue(it.moveToFirst()); assertEquals("Museo Prueba", it.getString(0)); assertTrue(it.isNull(1)) }
        db.query("SELECT COUNT(*) FROM favoritos").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
