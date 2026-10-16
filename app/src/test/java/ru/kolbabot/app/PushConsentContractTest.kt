package ru.kolbabot.app

import org.junit.Assert.*
import org.junit.Test

class PushConsentContractTest {
    @Test fun automaticRegistrationRequiresTheSameVerifiedAccountAndOsPermission() {
        assertTrue(PushConsentContract.shouldRegister(42, 42, true, true))
        assertFalse(PushConsentContract.shouldRegister(0, 0, true, true))
        assertFalse(PushConsentContract.shouldRegister(42, 0, true, true))
        assertFalse(PushConsentContract.shouldRegister(42, 43, true, true))
        assertFalse(PushConsentContract.shouldRegister(42, 42, true, false))
        assertFalse(PushConsentContract.shouldRegister(42, 42, false, true))
    }

    @Test fun androidPermissionResultCannotEnablePushForANewOrSignedOutAccount() {
        assertTrue(PushConsentContract.permissionResultBelongsTo(42, 42, 42))
        assertFalse(PushConsentContract.permissionResultBelongsTo(42, 43, 43))
        assertFalse(PushConsentContract.permissionResultBelongsTo(42, 0, 42))
        assertFalse(PushConsentContract.permissionResultBelongsTo(0, 42, 42))
        assertFalse(PushConsentContract.permissionResultBelongsTo(42, 42, 43))
    }
}
