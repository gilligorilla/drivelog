package com.example.drivelog

import com.example.drivelog.data.InchesFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InchesFormatTest {
    @Test
    fun formatsExamplesFromSpec() {
        assertEquals("8-3/4", InchesFormat.format(8, 6))
        assertEquals("0-1/8", InchesFormat.format(0, 1))
        assertEquals("11-1/2", InchesFormat.format(11, 4))
    }

    @Test
    fun zeroFractionIsWrittenAsZero() {
        assertEquals("5-0", InchesFormat.format(5, 0))
    }

    @Test
    fun everyWheelCombinationIsValid() {
        for (w in InchesFormat.WHOLES) {
            for (f in InchesFormat.FRACTIONS.indices) {
                assertTrue(InchesFormat.isValid(InchesFormat.format(w, f)))
            }
        }
        assertEquals(12, InchesFormat.WHOLES.size)
        assertEquals(8, InchesFormat.FRACTIONS.size)
    }

    @Test
    fun rejectsMalformedValues() {
        assertFalse(InchesFormat.isValid("12-0"))
        assertFalse(InchesFormat.isValid("8-5/16"))
        assertFalse(InchesFormat.isValid("8 3/4"))
        assertFalse(InchesFormat.isValid("8-"))
        assertFalse(InchesFormat.isValid(""))
    }
}
