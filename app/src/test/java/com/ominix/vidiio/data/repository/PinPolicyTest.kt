package com.ominix.vidiio.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parent PIN must never become a lock a child can set from inside a Kids profile. */
class PinPolicyTest {

    @Test
    fun adultsCanAlwaysConfigureThePin() {
        assertTrue(canConfigureParentPin(isKids = false, hasPin = false, unlocked = false))
        assertTrue(canConfigureParentPin(isKids = false, hasPin = true, unlocked = false))
    }

    @Test
    fun pinLessKidsCannotCreateTheFirstPin() {
        assertFalse(canConfigureParentPin(isKids = true, hasPin = false, unlocked = false))
        // A stale unlock must not let a PIN-less Kids profile invent a lock either.
        assertFalse(canConfigureParentPin(isKids = true, hasPin = false, unlocked = true))
    }

    @Test
    fun kidsCanChangeAnExistingPinOnlyAfterUnlocking() {
        assertFalse(canConfigureParentPin(isKids = true, hasPin = true, unlocked = false))
        assertTrue(canConfigureParentPin(isKids = true, hasPin = true, unlocked = true))
    }
}
