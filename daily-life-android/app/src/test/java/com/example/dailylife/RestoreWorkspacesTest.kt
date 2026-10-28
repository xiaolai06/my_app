package com.example.dailylife

import com.example.dailylife.data.files.RestoreWorkspaces
import com.example.dailylife.data.files.RestoreWorkspaceOwner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class RestoreWorkspacesTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun closingControllerRemovesEveryOwnedPreviewAndKeepsUnrelatedCache() {
        val cache=temporary.newFolder("cache")
        val unrelated=File(cache,"thumbnail").apply {writeText("keep")}
        val owner=RestoreWorkspaceOwner(cache)
        val previews=(1..5).map {owner.create("tools-restore-").apply {File(this,"manifest.json").writeText("sample")}}
        owner.close();owner.close()
        assertTrue(previews.none {it.exists()});assertTrue(unrelated.exists())
    }
    @Test fun rejectsFolderOutsideCacheEvenWhenNameLooksValid() {
        val cache=temporary.newFolder("cache")
        val outside=temporary.newFolder("tools-restore-"+UUID.randomUUID())
        assertTrue(runCatching {RestoreWorkspaces.discard(cache,outside)}.isFailure)
        assertTrue(outside.exists())
    }
    @Test fun expiryRemovesAbandonedFoldersButKeepsActiveAndRecentPreviews() {
        val cache=temporary.newFolder("cache");val now=System.currentTimeMillis()
        val abandoned=File(cache,"ledger-restore-"+UUID.randomUUID()).apply {mkdirs();setLastModified(now-172800000)}
        val recent=File(cache,"plan-restore-"+UUID.randomUUID()).apply {mkdirs()}
        val owner=RestoreWorkspaceOwner(cache);val active=owner.create("restore-").apply {setLastModified(now-172800000)}
        RestoreWorkspaces.cleanup(cache,now)
        assertFalse(abandoned.exists());assertTrue(recent.exists());assertTrue(active.exists())
        owner.close()
    }
}
