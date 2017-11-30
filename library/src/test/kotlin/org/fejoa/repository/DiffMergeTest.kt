package org.fejoa.repository

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.chunkcontainer.Hash
import org.fejoa.chunkcontainer.HashSpec
import org.fejoa.crypto.CryptoHelper
import org.fejoa.storage.HashValue
import org.fejoa.storage.Database
import org.fejoa.storage.KeepOursUnchanged
import org.fejoa.support.toUTF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class DiffMergeTest : RepositoryTestBase() {
    suspend private fun addFile(box: Directory, name: String): BlobEntry {
        val dataHash = HashValue(CryptoHelper.sha256Hash(CryptoHelper.crypto.generateSalt()))

        val entry = BlobEntry(name, Hash(HashSpec(), dataHash))
        box.put(entry)
        return entry
    }

    @Test
    fun testDiff() = runBlocking {
        val ours = Directory("ours")
        val theirs = Directory("theirs")

        val file1 = addFile(ours, "test1")
        var iterator = DirectoryDiffIterator("", ours, theirs)
        assertTrue(iterator.hasNext())
        var change = iterator.next()
        assertEquals(DiffIterator.Type.REMOVED, change.type)
        assertEquals("test1", change.path)
        assertFalse(iterator.hasNext())
        theirs.put(file1)

        iterator = DirectoryDiffIterator("", ours, theirs)
        assertFalse(iterator.hasNext())

        val file2 = addFile(theirs, "test2")
        iterator = DirectoryDiffIterator("", ours, theirs)
        assertTrue(iterator.hasNext())
        change = iterator.next()
        assertEquals(DiffIterator.Type.ADDED, change.type)
        assertEquals("test2", change.path)
        assertFalse(iterator.hasNext())
        ours.put(file2)

        val file3 = addFile(ours, "test3")
        theirs.put(file3)
        val file4 = addFile(ours, "test4")
        theirs.put(file4)
        val file5 = addFile(ours, "test5")
        theirs.put(file5)

        val file31 = addFile(ours, "test31")
        iterator = DirectoryDiffIterator("", ours, theirs)
        assertTrue(iterator.hasNext())
        change = iterator.next()
        assertEquals(DiffIterator.Type.REMOVED, change.type)
        assertEquals("test31", change.path)
        assertFalse(iterator.hasNext())

        theirs.put(file31)
        val file41 = addFile(theirs, "test41")
        iterator = DirectoryDiffIterator("", ours, theirs)
        assertTrue(iterator.hasNext())
        change = iterator.next()
        assertEquals(DiffIterator.Type.ADDED, change.type)
        assertEquals("test41", change.path)
        assertFalse(iterator.hasNext())

        addFile(ours, "test41")
        iterator = DirectoryDiffIterator("", ours, theirs)
        assertTrue(iterator.hasNext())
        change = iterator.next()
        assertEquals(DiffIterator.Type.MODIFIED, change.type)
        assertEquals("test41", change.path)
        assertFalse(iterator.hasNext())
    }

    @Test
    fun testMerge() = runBlocking {
        val branch = "repoBranch"
        val directory = "testMergeRepoTest"
        val directory2 = "testMergeRepoTest2"
        val directory3 = "testMergeRepoTest3"

        val repository = createRepo(directory, branch)
        val repository2 = createRepo(directory2, branch)
        val repository3 = createRepo(directory3, branch)

        // create a common base
        repository.putBytes("file1", "file1".toUTF())
        repository.commit("Commit0".toUTF(), null)
        repository2.putBytes("file1", "file1".toUTF())
        repository2.commit("Commit0".toUTF(), null)
        repository3.putBytes("file1", "file1".toUTF())
        repository3.commit("Commit0".toUTF(), null)

        repository2.putBytes("file2", "file2".toUTF())
        repository2.commit("Commit1".toUTF(), null)

        val mergedContent = HashMap<String, RepositoryTestBase.DatabaseStringEntry>()
        mergedContent.put("file1", RepositoryTestBase.DatabaseStringEntry("file1", "file1"))
        mergedContent.put("file2", RepositoryTestBase.DatabaseStringEntry("file2", "file2"))


        // test common ancestor finder
        val ours = repository.getHeadCommit()!!
        var theirs = repository2.getHeadCommit()!!
        val chains = CommonAncestorsFinder.find(repository.commitCache, ours, repository2.commitCache, theirs)
        assertTrue(chains.chains.size == 1)
        val chain = chains.chains[0]
        assertTrue(chain.commits.size == 2)
        val parent = chain.commits[chain.commits.size - 1]
        assertTrue(parent.getHash() == repository.getHeadCommit()!!.getHash())

        var result = repository.merge(listOf(repository2), ThreeWayMerge())
        assertEquals(Database.MergeResult.FAST_FORWARD, result)
        repository.commit("merge1".toUTF(), null)
        containsContent(repository, mergedContent)

        repository.putBytes("file2", "our file 2".toUTF())
        repository.commit("Commit3".toUTF(), null)
        repository2.putBytes("file2", "their file 2".toUTF())
        repository2.commit("Commit4".toUTF(), null)

        result = repository.merge(listOf(repository2), ThreeWayMerge())
        assertEquals(Database.MergeResult.MERGED, result)
        repository.commit("merge2".toUTF(), null)

        mergedContent.clear()
        mergedContent.put("file1", RepositoryTestBase.DatabaseStringEntry("file1", "file1"))
        mergedContent.put("file2", RepositoryTestBase.DatabaseStringEntry("file2", "our file 2"))
        containsContent(repository, mergedContent)

        // manual merge:
        // create commit that we want to merge in
        repository2.putBytes("mergefile", "changeIn2".toUTF())
        repository2.commit("Commit5".toUTF(), null)
        repository3.putBytes("mergefile", "changeIn3".toUTF())
        repository3.commit("Commit6".toUTF(), null)
        // create data that we actually want to have after the merge
        repository.putBytes("mergefile", "change".toUTF())
        mergedContent.put("mergefile", RepositoryTestBase.DatabaseStringEntry("mergefile", "change"))
        // use the KeepOursUnchanged strategy to keep our local changes
        result = repository.merge(listOf(repository2, repository3), KeepOursUnchanged())
        assertEquals(Database.MergeResult.MERGED, result)
        containsContent(repository, mergedContent)
        repository.commit("merge3".toUTF(), null)
        containsContent(repository, mergedContent)
    }
}
