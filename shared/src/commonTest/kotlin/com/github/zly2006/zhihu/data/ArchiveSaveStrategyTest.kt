package com.github.zly2006.zhihu.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveSaveStrategyTest {
    @Test
    fun fromKeyFallsBackToRead() {
        assertEquals(ArchiveSaveStrategy.Read, ArchiveSaveStrategy.fromKey(null))
        assertEquals(ArchiveSaveStrategy.Read, ArchiveSaveStrategy.fromKey(""))
        assertEquals(ArchiveSaveStrategy.Read, ArchiveSaveStrategy.fromKey("unknown"))
        assertEquals(ArchiveSaveStrategy.Loaded, ArchiveSaveStrategy.fromKey("loaded"))
        assertEquals(ArchiveSaveStrategy.Voted, ArchiveSaveStrategy.fromKey("voted"))
        assertEquals(ArchiveSaveStrategy.Collected, ArchiveSaveStrategy.fromKey("collected"))
    }

    @Test
    fun loadedIncludesReadButNotVoteOrCollect() {
        val strategy = ArchiveSaveStrategy.Loaded
        assertTrue(strategy.shouldPersist(ArchiveSaveTrigger.Loaded))
        assertTrue(strategy.shouldPersist(ArchiveSaveTrigger.Read))
        assertFalse(strategy.shouldPersist(ArchiveSaveTrigger.Voted))
        assertFalse(strategy.shouldPersist(ArchiveSaveTrigger.Collected))
    }

    @Test
    fun readDoesNotIncludePrefetchLoaded() {
        val strategy = ArchiveSaveStrategy.Read
        assertFalse(strategy.shouldPersist(ArchiveSaveTrigger.Loaded))
        assertTrue(strategy.shouldPersist(ArchiveSaveTrigger.Read))
        assertFalse(strategy.shouldPersist(ArchiveSaveTrigger.Voted))
        assertFalse(strategy.shouldPersist(ArchiveSaveTrigger.Collected))
    }

    @Test
    fun votedAndCollectedAreExclusive() {
        assertTrue(ArchiveSaveStrategy.Voted.shouldPersist(ArchiveSaveTrigger.Voted))
        assertFalse(ArchiveSaveStrategy.Voted.shouldPersist(ArchiveSaveTrigger.Collected))
        assertFalse(ArchiveSaveStrategy.Voted.shouldPersist(ArchiveSaveTrigger.Read))
        assertTrue(ArchiveSaveStrategy.Collected.shouldPersist(ArchiveSaveTrigger.Collected))
        assertFalse(ArchiveSaveStrategy.Collected.shouldPersist(ArchiveSaveTrigger.Voted))
        assertFalse(ArchiveSaveStrategy.Collected.shouldPersist(ArchiveSaveTrigger.Read))
    }
}
