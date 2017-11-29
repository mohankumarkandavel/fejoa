package org.fejoa.repository

import kotlinx.coroutines.experimental.runBlocking
import kotlin.test.Test


class MultiBranchRepositoryTest : RepositoryTestBase() {
    @Test
    fun testMultiBranches() = runBlocking {
        val repo = createRepo("multiBranchTest", "multiBranch")
        var testRepo = TestRepository(repo)
        testRepo.putBlob("test", "test")
        testRepo.putBlob("dir1/test", "test2")
        testRepo.putBlob("dir1/subDir1/test", "test3")
        testRepo.putBlob("dir1/subDir1/test2", "test4")
        testRepo.commit("commit1")
        testRepo.verify()

        // remove something in the second commit
        testRepo.remove("dir1/subDir1/test")
        testRepo.commit("commit2")
        testRepo.verify()

        // create branch from commit2
        val branch0 = testRepo.clone()

        testRepo.putBlob("file0", "file0")
        testRepo.putBlob("dir1/file1", "file1")
        testRepo.putBlob("dir1/subDir2/file2", "file2")
        testRepo.commit("commit3")
        testRepo.verify()

        // create branches from commit3
        val branch1 = testRepo.clone()
        val branch2 = testRepo.clone()

        branch0.putBlob("branch0/file", "branch0")
        val branch0Commit = branch0.commit("branch0Commit1")
        branch0.verify()

        branch1.putBlob("branch1/file", "branch1")
        val branch1commit = branch1.commit("branch1Commit1")
        branch1.verify()

        branch2.putBlob("branch2/file", "branch2")
        val branch2Commit = branch2.commit("branch2Commit1")
        branch2.verify()

        // merge branches back content will be the original branch
        testRepo.commit("merge", listOf(branch0 to branch0Commit,
                branch1 to branch1commit,
                branch2 to branch2Commit))
        testRepo.verify()
    }
}