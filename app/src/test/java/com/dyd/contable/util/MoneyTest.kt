package com.dyd.contable.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesPlainIntegers() = assertEquals(150_000L, Money.parse("1500"))
    @Test fun parsesDotDecimals() = assertEquals(123_450L, Money.parse("1234.5"))
    @Test fun parsesCommaDecimals() = assertEquals(123_456L, Money.parse("1234,56"))
    @Test fun parsesLatinThousands() = assertEquals(123_456L, Money.parse("1.234,56"))
    @Test fun parsesUsThousands() = assertEquals(123_456L, Money.parse("1,234.56"))
    @Test fun dotWithThreeDigitsIsThousands() = assertEquals(150_000L, Money.parse("1.500"))
    @Test fun manyThousandSeparators() = assertEquals(1_250_000_00L, Money.parse("1.250.000"))
    @Test fun ignoresCurrencySymbol() = assertEquals(2_000_000L, Money.parse("$ 20.000"))
    @Test fun roundsToCents() = assertEquals(1_055_556L, Money.parse("10,555.555"))
    @Test fun rejectsGarbage() = assertNull(Money.parse("abc"))
    @Test fun rejectsEmpty() = assertNull(Money.parse(""))
    @Test fun roundTripsInput() = assertEquals(123_450L, Money.parse(Money.toInput(123_450L)))
}

class QuantityTest {
    @Test fun keepsThreeDecimals() = assertEquals(0.125, Quantity.parse("0,125")!!, 1e-9)
    @Test fun readsThousands() = assertEquals(1500.0, Quantity.parse("1.500")!!, 1e-9)
    @Test fun rejectsText() = assertNull(Quantity.parse("x"))
}
