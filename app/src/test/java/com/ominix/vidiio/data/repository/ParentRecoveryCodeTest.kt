package com.ominix.vidiio.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recovery codes must be writable by hand, typeable on a TV remote and never stored plainly. */
class ParentRecoveryCodeTest {

    @Test
    fun generatedCodesAreUnambiguousAndLongEnough() {
        repeat(50) {
            val code = generateParentRecoveryCode()
            assertEquals(PARENT_RECOVERY_CODE_LENGTH, code.length)
            assertFalse("0, O, 1 and I are ambiguous on paper", code.any { it in "0O1I" })
            assertTrue(code.all { it.isLetterOrDigit() })
        }
    }

    @Test
    fun normalizationAcceptsTheDisplayedAndLowercaseForms() {
        assertEquals("ABCDEFGHJKLM", normalizeParentRecoveryCode("ABCD-EFGH-JKLM"))
        assertEquals("ABCDEFGHJKLM", normalizeParentRecoveryCode("abcd efgh jklm"))
        assertEquals("ABCD-EFGH-JKLM", formatParentRecoveryCode("ABCDEFGHJKLM"))
    }

    @Test
    fun hashesIgnoreFormattingButNotContent() {
        val hash = hashParentRecoveryCode("ABCD-EFGH-JKLM")
        assertEquals(hash, hashParentRecoveryCode("abcd efgh jklm"))
        assertNotEquals(hash, hashParentRecoveryCode("ABCD-EFGH-JKMN"))
    }
}
