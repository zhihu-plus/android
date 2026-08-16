package com.github.zly2006.zhihu.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElderlyModeTest {
    @Test
    fun onlyExplicitTruthySettingsEnableSystemElderlyMode() {
        assertTrue(settingValueEnablesElderlyMode("1"))
        assertTrue(settingValueEnablesElderlyMode(" true "))
        assertTrue(settingValueEnablesElderlyMode("ON"))
        assertTrue(settingValueEnablesElderlyMode("yes"))
        assertTrue(settingValueEnablesElderlyMode("Yes"))
        assertFalse(settingValueEnablesElderlyMode(null))
        assertFalse(settingValueEnablesElderlyMode("0"))
        assertFalse(settingValueEnablesElderlyMode("false"))
        assertFalse(settingValueEnablesElderlyMode("off"))
        assertFalse(settingValueEnablesElderlyMode("no"))
        assertFalse(settingValueEnablesElderlyMode("2"))
        assertFalse(settingValueEnablesElderlyMode(""))
        assertFalse(settingValueEnablesElderlyMode("   "))
    }
}
