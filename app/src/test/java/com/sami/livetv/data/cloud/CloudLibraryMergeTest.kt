package com.sami.livetv.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudLibraryMergeTest {
    @Test
    fun firstSyncAddsLocalAndRemoteLibraryWithoutDuplicates() {
        val merged = mergeAccountLibraries(
            local = CloudLibrary(
                favoriteIds = setOf("local-favorite", "shared-favorite"),
                watchHistory = listOf("local-channel", "shared-channel"),
            ),
            remote = CloudLibrary(
                favoriteIds = setOf("remote-favorite", "shared-favorite"),
                watchHistory = listOf("shared-channel", "remote-channel"),
            ),
            baseline = null,
        )

        assertEquals(
            setOf("local-favorite", "shared-favorite", "remote-favorite"),
            merged.favoriteIds,
        )
        assertEquals(
            listOf("local-channel", "shared-channel", "remote-channel"),
            merged.watchHistory,
        )
    }

    @Test
    fun localFavoriteRemovalIsNotRestoredFromAnUnchangedCloudCopy() {
        val merged = mergeAccountLibraries(
            local = CloudLibrary(favoriteIds = emptySet()),
            remote = CloudLibrary(favoriteIds = setOf("channel-a")),
            baseline = CloudLibrary(favoriteIds = setOf("channel-a")),
        )

        assertEquals(emptySet<String>(), merged.favoriteIds)
    }

    @Test
    fun changesFromBothDevicesAreCombinedAgainstTheLastSyncedBaseline() {
        val merged = mergeAccountLibraries(
            local = CloudLibrary(favoriteIds = setOf("local-addition")),
            remote = CloudLibrary(favoriteIds = setOf("old-favorite", "remote-addition")),
            baseline = CloudLibrary(favoriteIds = setOf("old-favorite")),
        )

        assertEquals(
            setOf("local-addition", "remote-addition"),
            merged.favoriteIds,
        )
    }

    @Test
    fun historyKeepsNewEntriesFromBothDevicesAndCapsTheMergedList() {
        val baselineHistory = (1..10).map { "channel-$it" }
        val merged = mergeAccountLibraries(
            local = CloudLibrary(
                watchHistory = listOf("local-new", "channel-1", "channel-2"),
            ),
            remote = CloudLibrary(
                watchHistory = listOf("remote-new", "channel-1", "channel-2"),
            ),
            baseline = CloudLibrary(watchHistory = baselineHistory),
        )

        assertEquals(
            listOf(
                "local-new",
                "remote-new",
                "channel-1",
                "channel-2",
                "channel-3",
                "channel-4",
                "channel-5",
                "channel-6",
                "channel-7",
                "channel-8",
            ),
            merged.watchHistory,
        )
    }
}
