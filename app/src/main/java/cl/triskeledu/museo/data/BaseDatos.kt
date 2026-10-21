package cl.triskeledu.museo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.triskeledu.museo.model.Rol
import cl.triskeledu.museo.model.Seguridad

/** Base SQLite `museo.db`. `exportSchema` guarda en app/schemas un JSON por versión. */
@Database(
    entities = [Usuario::class, Museo::class, Favorito::class, Producto::class, ItemCarrito::class,
        Solicitud::class, LecturaQr::class, Notificacion::class],
    version = 2,
    exportSchema = true,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun dao(): MuseoDao

    companion object {
        @Volatile private var instancia: BaseDatos? = null

        /** Una sola conexión para toda la app (contexto de aplicación, como en el ejemplo SQLite). */
        fun obtener(context: Context): BaseDatos = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, BaseDatos::class.java, "museo.db")
                .addMigrations(MIGRACION_1_2)   // Sin fallbackToDestructiveMigration: nunca borra datos
                .build().also { instancia = it }
        }
    }
}

/** Versión 1 → 2: las fichas ganan una foto de cámara opcional. Las filas existentes se conservan con NULL. */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE museos ADD COLUMN fotoRuta TEXT")
    }
}

/**
 * ===================== DATOS DE DEMOSTRACIÓN (ficticios) =====================
 * Cuentas de prueba, museos reales con fotos de Wikimedia Commons (licencias libres
 * de cada autor) y un catálogo con precios y stock INVENTADOS.
 */
object DatosDemo {
    data class Cuenta(val rol: Rol, val nombre: String, val correo: String, val clave: String, val avatar: String)

    val CUENTAS = listOf(
        Cuenta(Rol.ADMINISTRADOR, "Admin Demo", "admin@museo.demo", "Admin#2026", "🏛️"),
        Cuenta(Rol.INVESTIGADOR, "Investigadora Demo", "investigador@museo.demo", "Investiga#2026", "🔍"),
        Cuenta(Rol.CLIENTE, "Cliente Demo", "cliente@museo.demo", "Cliente#2026", "🎨"),
        Cuenta(Rol.CLIENTE, "Lucía Demo", "lucia@museo.demo", "Cliente#2026", "🏺"),
    )

    private const val COMMONS = "https://thumb.wikimedia.org/wikipedia/commons/thumb/"

    private fun m(id: Long, nombre: String, pais: String, ciudad: String, clase: String, proyecto: String,
                  lat: Double, lon: Double, url: String, foto: String, texto: String,
                  estado: EstadoFicha = EstadoFicha.PUBLICADO) =
        Museo(id, nombre, pais, ciudad, texto, clase, proyecto, lat, lon, url, foto, "MUS-%03d".format(id), estado, autorId = 2)

    fun museos() = listOf(
        m(1, "Museo Nacional de Bellas Artes", "Chile", "Santiago", "Arte", "Arte Iberoamericano", -33.4344, -70.6436,
            "https://www.mnba.gob.cl/", COMMONS + "c/c5/Museo_Nacional_de_Bellas_Artes%2C_Santiago_20230311.jpg/960px-Museo_Nacional_de_Bellas_Artes%2C_Santiago_20230311.jpg",
            "Pintura y escultura chilena y universal en un edificio de 1910 frente al Parque Forestal."),
        m(2, "Museo Chileno de Arte Precolombino", "Chile", "Santiago", "Arqueología", "Patrimonio Precolombino", -33.4387, -70.6519,
            "https://museo.precolombino.cl/", COMMONS + "c/cc/Palacio_de_la_Real_Aduana%2C_Santiago_20231109.jpg/960px-Palacio_de_la_Real_Aduana%2C_Santiago_20231109.jpg",
            "Piezas de las culturas originarias de América en el Palacio de la Real Aduana."),
        m(3, "Museo Nacional de Antropología", "México", "Ciudad de México", "Antropología", "Patrimonio Precolombino", 19.4260, -99.1863,
            "https://www.mna.inah.gob.mx/", "https://upload.wikimedia.org/wikipedia/commons/9/94/Musee_National_Anthropologie-Entree.jpg",
            "Salas dedicadas a las culturas prehispánicas y a los pueblos indígenas de México."),
        m(4, "Museo Nacional del Prado", "España", "Madrid", "Arte", "Arte Iberoamericano", 40.4138, -3.6921,
            "https://www.museodelprado.es/", COMMONS + "6/68/Museo_del_Prado_2016_%2825185969599%29.jpg/960px-Museo_del_Prado_2016_%2825185969599%29.jpg",
            "Pinacoteca con obras de maestros europeos entre los siglos XII y XIX."),
        m(5, "Museo Reina Sofía", "España", "Madrid", "Arte contemporáneo", "Arte Moderno", 40.4086, -3.6943,
            "https://www.museoreinasofia.es/", "https://upload.wikimedia.org/wikipedia/commons/2/23/Edificio_Sabatini_puerta_principal.jpg",
            "Arte moderno y contemporáneo en el edificio Sabatini, antiguo hospital de Madrid."),
        m(6, "Museo del Oro", "Colombia", "Bogotá", "Arqueología", "Patrimonio Precolombino", 4.6019, -74.0721,
            "https://www.banrepcultural.org/bogota/museo-del-oro", COMMONS + "9/97/BOG_04_2012_Museo_de_Oro_1273.JPG/960px-BOG_04_2012_Museo_de_Oro_1273.JPG",
            "Orfebrería y objetos de las sociedades prehispánicas del territorio colombiano."),
        m(7, "MALBA", "Argentina", "Buenos Aires", "Arte latinoamericano", "Arte Moderno", -34.5770, -58.4035,
            "https://www.malba.org.ar/", COMMONS + "a/a7/Malba_-_Milla_Museos.jpg/960px-Malba_-_Milla_Museos.jpg",
            "Museo de Arte Latinoamericano de Buenos Aires, centrado en el arte del siglo XX."),
        m(8, "MASP", "Brasil", "São Paulo", "Arte", "Arte Moderno", -23.5614, -46.6559,
            "https://masp.org.br/", COMMONS + "5/5e/MASP_-_Museu_de_Arte_de_S%C3%A3o_Paulo_%283407308679%29.jpg/960px-MASP_-_Museu_de_Arte_de_S%C3%A3o_Paulo_%283407308679%29.jpg",
            "Museu de Arte de São Paulo, suspendido sobre la Avenida Paulista."),
        m(9, "Museo Larco", "Perú", "Lima", "Arqueología", "Patrimonio Precolombino", -12.0727, -77.0707,
            "https://www.museolarco.org/", COMMONS + "c/c7/Lima_museo_larco.jpg/960px-Lima_museo_larco.jpg",
            "Arte precolombino peruano en una casona virreinal de Pueblo Libre."),
        m(10, "Museu Nacional de Arte Antiga", "Portugal", "Lisboa", "Arte", "Arte Iberoamericano", 38.7050, -9.1610,
            "https://www.museudearteantiga.pt/", COMMONS + "3/3e/MNAA.jpg/960px-MNAA.jpg",
            "Pintura, escultura y artes decorativas portuguesas y europeas."),
        m(11, "Museo Nacional de Costa Rica", "Costa Rica", "San José", "Historia", "Historia y Sociedad", 9.9326, -84.0716,
            "https://www.museocostarica.go.cr/", COMMONS + "2/28/Museo_Nacional_de_Costa_Rica_01.jpg/960px-Museo_Nacional_de_Costa_Rica_01.jpg",
            "Historia natural y cultural de Costa Rica en el antiguo Cuartel Bellavista."),
        m(12, "Museo de la Memoria y los Derechos Humanos", "Chile", "Santiago", "Historia", "Historia y Sociedad", -33.4400, -70.6790,
            "https://web.museodelamemoria.cl/", COMMONS + "b/b7/Museo_de_la_Memoria_y_los_Derechos_Humanos.jpg/960px-Museo_de_la_Memoria_y_los_Derechos_Humanos.jpg",
            "Espacio dedicado a la memoria y a los derechos humanos. Ficha EN REVISIÓN para probar la publicación.",
            EstadoFicha.EN_REVISION),
    )

    private fun p(id: Long, nombre: String, cat: Categoria, precio: Int, stock: Int, peso: Int, texto: String, activo: Boolean = true) =
        Producto(id, nombre, cat, texto, precio, stock, peso, activo, "PRD-%03d".format(id))

    fun productos() = listOf(
        p(1, "Introducción a la museología", Categoria.CURSO, 89_000, 30, 0, "Curso en línea de 8 semanas sobre funciones y ética de los museos."),
        p(2, "Conservación preventiva de colecciones", Categoria.CURSO, 120_000, 20, 0, "Control de humedad, luz y plagas en colecciones."),
        p(3, "Manual de documentación de colecciones", Categoria.PUBLICACION, 24_900, 40, 600, "Guía práctica para inventariar y catalogar objetos."),
        p(4, "Revista Museos Iberoamericanos N.º 12", Categoria.PUBLICACION, 12_500, 3, 300, "Número dedicado a museos virtuales y patrimonio digital."),
        p(5, "Guantes de nitrilo (caja 100)", Categoria.ARTICULO, 9_900, 50, 400, "Para manipular piezas sin dejar residuos."),
        p(6, "Luxómetro para salas", Categoria.ARTICULO, 64_900, 5, 250, "Mide la iluminación que reciben las obras."),
        p(7, "Caja de archivo libre de ácido", Categoria.CONSERVACION, 15_900, 25, 900, "Cartón neutro para documentos y textiles."),
        p(8, "Papel tissue libre de ácido", Categoria.CONSERVACION, 11_900, 0, 500, "SIN STOCK para demostrar la validación de disponibilidad."),
        p(9, "Set de etiquetas museográficas", Categoria.ARTICULO, 7_500, 12, 150, "Producto DESACTIVADO (gestión del Administrador).", activo = false),
    )

    /** Inserta lo que falte; si ya existe (mismo id) no lo duplica. */
    suspend fun poblar(dao: MuseoDao) {
        dao.insertarUsuarios(CUENTAS.mapIndexed { i, c -> Usuario(i + 1L, c.nombre, c.correo, Seguridad.hash(c.clave), c.rol, c.avatar) })
        dao.insertarMuseos(museos())
        dao.insertarProductos(productos())
    }
}
