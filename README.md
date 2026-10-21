# Museo de los Museos

App Android del caso Museo de los Museos, hecha de la forma más simple posible:
Kotlin, Jetpack Compose, MVVM sencillo con **un solo ViewModel**, Room con migración,
OpenStreetMap, cámara, escáner QR, animaciones, notificaciones locales y los 3 roles.
Los museos muestran **fotos reales** de Wikimedia Commons.

## Ejecutar

Abre esta carpeta en Android Studio y ejecuta `app` (Android 8.0 / API 26 o superior). Por consola:

```bash
./gradlew assembleDebug                # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest            # pruebas de validaciones, envío, QR, permisos y contraseñas
./gradlew connectedDebugAndroidTest    # prueba de migración Room 1 → 2 (necesita dispositivo)
```

Se necesita Internet para el mapa y las fotos, y Google Play services para el escáner QR.

## Cuentas de demostración (ficticias)

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | admin@museo.demo | Admin#2026 |
| Investigador | investigador@museo.demo | Investiga#2026 |
| Cliente | cliente@museo.demo | Cliente#2026 |
| Cliente 2 | lucia@museo.demo | Cliente#2026 |

El botón **"Usar cuenta de demostración"** las rellena. Los clientes nuevos se crean en **Registro**.

## Estructura (13 archivos Kotlin)

```text
MainActivity.kt          Activity única, configuración de osmdroid y apertura desde notificaciones
MuseoViewModel.kt        ÚNICO ViewModel: sesión, navegación, museos, catálogo, carrito, QR, avisos, perfil
data/Entidades.kt        Tablas Room (usuarios, museos, favoritos, productos, carrito, solicitudes, lecturas_qr, notificaciones)
data/MuseoDao.kt         Único DAO (compra y lecturas QR en transacciones)
data/BaseDatos.kt        Base Room v2, MIGRACION_1_2 y DatosDemo (datos de demostración)
data/Repositorio.kt      Acceso a datos: login, registro y avisos
model/Logica.kt          Roles/permisos, pantallas, validaciones, envío ficticio, lector QR, hash
util/Utilidades.kt       Permisos, notificaciones, cámara, imágenes web, QR y marcadores del mapa
ui/Componentes.kt        Tema, barra, campos, foto web, selector y mapa
ui/MuseoApp.kt           Raíz con barra inferior por rol
ui/Pantallas*.kt         Las 15 pantallas del documento
```

## Pantallas por rol

1 Bienvenida · 2 Registro (Cliente) · 3 Selección de rol · 4 Dashboard por rol · 5 Mapa OSM con filtros país/proyecto ·
6 Detalle del museo (favorito, corrección, revisión, QR de la ficha) · 7 Escáner QR · 8 Resultado del QR ·
9 Catálogo (Admin gestiona) · 10 Detalle y compra/información · 11 Favoritos · 12 Carrito y envío ·
13 Solicitudes (Admin cambia estados) · 14 Notificaciones (Admin envía avisos) · 15 Perfil y permisos.

## Fotos reales

Las 12 fichas usan fotografías de **Wikimedia Commons**, que se descargan al abrir la app (se identifica con su
propio User-Agent y las guarda en memoria). Cada foto tiene la licencia libre de su autor, indicada en
su página de Commons. La foto tomada con la cámara (columna `fotoRuta`, versión 2 de la base) reemplaza a la foto web.
Sin Internet se muestra un emoji.

## Migración

`app/schemas` guarda `1.json` y `2.json`. `MIGRACION_1_2` agrega `museos.fotoRuta` sin borrar datos, y
`MigracionTest` lo comprueba.

## QR

**Lectura con cámara:** se usa solo el escáner de Google Play services, como el ejemplo de QR: abre su propia
pantalla de cámara y devuelve el texto. En algunos teléfonos ese módulo de Google falla (en el Huawei STK-LX3
responde "Failed to scan code"); ahí se puede probar con **"Probar sin cámara"**, pegando el JSON.
La carpeta `qr/` trae un PNG por museo y `qr_museos.txt` sus JSON para probar.

El JSON lleva `tipo` (`museo`, `exposicion`, `publicacion`, `producto`, `curso`) y `codigo` (`MUS-001`…`MUS-012`,
`PRD-001`…`PRD-009`). Ejemplo: `{"tipo":"museo","codigo":"MUS-004","titulo":"Museo Nacional del Prado"}`.

## Limitaciones (requieren backend o servicios externos)

- Backend REST (Spring Boot + Retrofit) no integrado: Room es la única fuente; el `Repositorio` es el punto de conexión.
- Notificaciones push reales (FCM) requieren Firebase y un servidor; hoy son avisos locales por usuario.
- Pagos, courier y validación oficial de stock son simulados.
- La sesión no persiste al cerrar la app y el idioma solo se guarda como preferencia.
