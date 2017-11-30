package org.fejoa.repository

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.support.await
import kotlin.test.Test
import kotlin.test.assertTrue


class GarbageCollectionTest : RepositoryTestBase() {
    @Test
    fun  testGC() = runBlocking {
        val repository = createRepo("gcTestSource", "gcTest")
        var testRepo = TestRepository(repository)
        testRepo.putBlob("test1", "file1")
        testRepo.putBlob("test/subDir", "file2")
        testRepo.putBlob("test/dir", "file3")
        testRepo.putBlob("file4", "file4")
        testRepo.putBlob("dir/subDir2/subDir", "file5")
        testRepo.putBlob("test6", "file6")
        testRepo.commit("commit0")
        testRepo.verify()

        val iterator = repository.chunkIterator()
        var count = 0
        while (iterator.hasNext()) {
            val next = iterator.next()
            count++
        }

        // At least 6 files, 1 for commit, 1 for the dir structure and 1 for the object index
        assertTrue(count > 9)

        // garbage collect to a new repo
        var target = createRepo("gcTestTarget", "gcTest")
        repository.gc(target.getCurrentTransaction().getRawAccessor())
        target.getCurrentTransaction().finishTransaction()
        target.log.add(repository.log.getHead().await()!!)
        // re-open to use the correct object index
        target = Repository.open(target.getBranch(), repository.getRepositoryRef(), target.branchBackend,
                target.config.crypto)
        testRepo.verify(target)
    }
}