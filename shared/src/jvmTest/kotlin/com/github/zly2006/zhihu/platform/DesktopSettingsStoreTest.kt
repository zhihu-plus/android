package com.github.zly2006.zhihu.platform

import kotlin.test.Test
import kotlin.test.assertSame

class DesktopSettingsStoreTest {
    @Test
    fun desktopSettingsStoreIsSharedSingleton() {
        assertSame(desktopSettingsStore(), desktopSettingsStore())
    }
}
