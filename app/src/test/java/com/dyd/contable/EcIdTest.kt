package com.dyd.contable

import com.dyd.contable.util.EcId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcIdTest {
    @Test fun cedula() {
        assertTrue(EcId.isValidCedula("1710034065"))
        assertTrue(EcId.isValidCedula("0912345675"))
        assertFalse(EcId.isValidCedula("1710034064"))   // dígito verificador
        assertFalse(EcId.isValidCedula("2510034065"))   // provincia 25
        assertFalse(EcId.isValidCedula("17100340"))
    }

    @Test fun ruc() {
        assertTrue(EcId.isValidRuc("1710034065001"))    // persona natural
        assertTrue(EcId.isValidRuc("1790011674001"))    // sociedad privada
        assertTrue(EcId.isValidRuc("1760001550001"))    // entidad pública
        assertFalse(EcId.isValidRuc("1710034065000"))
        assertFalse(EcId.isValidRuc("1710034064001"))
        assertFalse(EcId.isValidRuc("1780011674001"))   // tercer dígito 8
    }

    @Test fun tiposYSugerencias() {
        assertTrue(EcId.isValid("07", "9999999999999"))
        assertFalse(EcId.isValid("07", "1710034065"))
        assertTrue(EcId.isValid("06", "AB123456"))
        assertEquals("04", EcId.guessType("1790011674001"))
        assertEquals("05", EcId.guessType("1710034065"))
        assertEquals(null, EcId.error("04", "1790011674001"))
    }
}
