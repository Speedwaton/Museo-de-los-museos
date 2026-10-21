package cl.triskeledu.museo

import cl.triskeledu.museo.model.Destino
import cl.triskeledu.museo.model.LectorQr
import cl.triskeledu.museo.model.Modalidad
import cl.triskeledu.museo.model.Pantalla
import cl.triskeledu.museo.model.Rol
import cl.triskeledu.museo.model.Seguridad
import cl.triskeledu.museo.model.Validar
import cl.triskeledu.museo.model.calcularEnvio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la lógica pura (sin Android), como FormularioViewModelTest del ejemplo. */
class LogicaTest {
    @Test fun validaciones() {
        assertNull(Validar.correo("ana@ejemplo.cl"))
        assertNotNull(Validar.correo("correo malo"))
        assertNull(Validar.clave("Museo2026"))
        assertNotNull(Validar.clave("sololetras"))
        assertNotNull(Validar.numero("95", -90.0..90.0))
        assertNull(Validar.numero("-34.577", -90.0..90.0))   // Valores válidos NO deben dar error
        assertNull(Validar.numero("-34,577", -90.0..90.0))
        assertNotNull(Validar.numero("abc", -90.0..90.0))
        assertNull(Validar.entero("24900"))
        assertNotNull(Validar.entero("-1"))
        assertNotNull(Validar.entero("doce"))
        assertNull(Validar.url("https://www.museodelprado.es/"))
    }

    @Test fun envioFicticio() {
        assertEquals(0, calcularEnvio(0, 10_000, Destino.INTERNACIONAL, Modalidad.EXPRESS))       // digital
        assertEquals(5_900, calcularEnvio(1_200, 20_000, Destino.NACIONAL, Modalidad.ESTANDAR))   // 3.500 + 2 × 1.200
        assertEquals(32_800, calcularEnvio(900, 20_000, Destino.INTERNACIONAL, Modalidad.EXPRESS)) // (14.000 + 6.500) × 1,6
        assertEquals(0, calcularEnvio(3_000, 80_000, Destino.NACIONAL, Modalidad.ESTANDAR))       // gratis sobre $80.000
    }

    @Test fun lecturaQr() {
        val ok = LectorQr.leer(LectorQr.generar("museo", "MUS-004", "Prado")).getOrThrow()
        assertEquals("MUS-004", ok.codigo)
        assertTrue(LectorQr.leer("no es json").isFailure)
        assertTrue(LectorQr.leer("""{"tipo":"video","codigo":"X"}""").isFailure)
        assertTrue(LectorQr.leer("""{"tipo":"museo"}""").isFailure)
    }

    @Test fun permisosPorRol() {
        assertTrue(Pantalla.Carrito.permitida(Rol.CLIENTE))
        assertFalse(Pantalla.Carrito.permitida(Rol.ADMINISTRADOR))
        assertFalse(Pantalla.Catalogo.permitida(Rol.INVESTIGADOR))
        assertFalse(Pantalla.Escaner.permitida(Rol.ADMINISTRADOR))
        assertEquals(Pantalla.Museo(3), Pantalla.desde("museo:3"))
    }

    @Test fun contrasenaConHash() {
        val h = Seguridad.hash("Cliente#2026")
        assertTrue(Seguridad.verificar("Cliente#2026", h))
        assertFalse(Seguridad.verificar("otra", h))
    }
}
