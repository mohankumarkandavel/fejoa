package org.fejoa.revlog

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.chunkcontainer.BoxSpec
import org.fejoa.chunkcontainer.ChunkContainerTestBase
import org.fejoa.chunkcontainer.ContainerSpec
import org.fejoa.storage.HashSpec
import org.fejoa.support.toUTF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RevlogTest : ChunkContainerTestBase() {
    @Test
    fun testBasics() = runBlocking {
        val dirName = "testRevlogContainerDir"
        val name = "test"
        val config = ContainerSpec(HashSpec(HashSpec.HashType.FEJOA_FIXED_8K, null), BoxSpec())
        config.hashSpec.setFixedSizeChunking(180)
        var chunkContainer = prepareContainer(dirName, name, config)

        val revlog = Revlog(chunkContainer)
        revlog.add("Some initial data".toUTF())
        revlog.add("Some initial data and some changes".toUTF())
        revlog.add("Some initial data and some changes, more changes".toUTF())
        revlog.add("Some older data and some changes, more changes".toUTF())
        val inventory = revlog.inventory()
        assertEquals(4, inventory.size)
        assertTrue(revlog.get(chunkContainer.getDataLength() - 1) contentEquals "Some older data and some changes, more changes".toUTF())
    }
}