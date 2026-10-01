package com.dyd.contable

import com.dyd.contable.data.remote.SriCatastro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SriCatastroTest {
    // Forma de la respuesta pública de ConsolidadoContribuyente/obtenerPorNumerosRuc.
    private val sample = """
        [{"numeroRuc":"1790016919001","razonSocial":"CORPORACION FAVORITA C.A.","estadoContribuyenteRuc":"ACTIVO",
          "actividadEconomicaPrincipal":"VENTA AL POR MENOR DE GRAN VARIEDAD DE PRODUCTOS EN TIENDAS",
          "tipoContribuyente":"SOCIEDAD","regimen":"GENERAL","categoria":null,"obligadoLlevarContabilidad":"SI",
          "agenteRetencion":"SI","contribuyenteEspecial":"SI"}]
    """

    @Test fun parsesContribuyente() {
        val c = SriCatastro.parseContribuyente(sample, "AV. GENERAL ENRIQUEZ")!!
        assertEquals("CORPORACION FAVORITA C.A.", c.razonSocial)
        assertEquals("ACTIVO", c.estado)
        assertEquals("VENTA AL POR MENOR DE GRAN VARIEDAD DE PRODUCTOS EN TIENDAS", c.actividadEconomica)
        assertEquals("SOCIEDAD", c.tipo)
        assertEquals("SI", c.obligadoContabilidad)
        assertEquals("AV. GENERAL ENRIQUEZ", c.direccion)
    }

    @Test fun emptyOrUnknown() {
        assertNull(SriCatastro.parseContribuyente("[]"))
        assertNull(SriCatastro.parseContribuyente("<html>error</html>"))
    }

    @Test fun picksMatrizAddress() {
        val body = """[{"numeroEstablecimiento":"002","matriz":"NO","estado":"ABIERTO","direccionCompleta":"SUCURSAL"},
                       {"numeroEstablecimiento":"001","matriz":"SI","estado":"ABIERTO","direccionCompleta":"MATRIZ QUITO"}]"""
        assertEquals("MATRIZ QUITO", SriCatastro.parseDireccionMatriz(body))
    }
}
