package com.github.zly2006.zhihu.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElderlyModeTest {
    @Test
    fun followsSystemWhenUserHasNotSetPreference() {
        assertTrue(resolveElderlyModeEnabled(stored = null, systemEnabled = true))
        assertFalse(resolveElderlyModeEnabled(stored = null, systemEnabled = false))
    }

    @Test
    fun userToggleOverridesSystemElderlyMode() {
        assertFalse(resolveElderlyModeEnabled(stored = false, systemEnabled = true))
        assertTrue(resolveElderlyModeEnabled(stored = true, systemEnabled = false))
    }

    @Test
    fun onlyExplicitTruthySettingsEnableSystemElderlyMode() {
        assertTrue(settingValueEnablesElderlyMode("1"))
        assertTrue(settingValueEnablesElderlyMode("true"))
        assertTrue(settingValueEnablesElderlyMode("ON"))
        assertFalse(settingValueEnablesElderlyMode(null))
        assertFalse(settingValueEnablesElderlyMode("0"))
        assertFalse(settingValueEnablesElderlyMode("false"))
        assertFalse(settingValueEnablesElderlyMode(""))
    }
}
