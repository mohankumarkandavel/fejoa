package org.fejoa.repository

import org.fejoa.chunkcontainer.*
import org.fejoa.crypto.SymCredentials
import org.fejoa.storage.KeepOursUnchanged
import org.fejoa.support.PathUtils
import org.fejoa.support.toUTF
import org.fejoa.support.toUTFString

import kotlin.test.assertEquals
import kotlin.test.assertNotNull


open class RepositoryTestBase : ChunkContainerTestBase() {
    protected class DatabaseStringEntry(var path: String, var content: String)

    protected suspend fun add(database: Repository, content: MutableMap<String, DatabaseStringEntry>, entry: DatabaseStringEntry) {
        content.put(entry.path, entry)
        database.putBytes(entry.path, entry.content.toUTF())
    }

    protected suspend fun remove(database: Repository, content: MutableMap<String, DatabaseStringEntry>, path: String) {
        if (content.containsKey(path)) {
            content.remove(path)
            database.remove(path)
        }
    }

    private suspend fun countFiles(database: Repository, dirPath: String): Int {
        var fileCount = database.listFiles(dirPath).size
        for (dir in database.listDirectories(dirPath))
            fileCount += countFiles(database, PathUtils.appendDir(dirPath, dir))
        return fileCount
    }

    protected suspend fun containsContent(database: Repository, content: Map<String, DatabaseStringEntry>) {
        for (entry in content.values) {
            val bytes = database.readBytes(entry.path)
            assertNotNull(bytes)
            assertEquals(entry.content, bytes.toUTFString())
        }
        assertEquals(content.size, countFiles(database, ""))
    }

    suspend protected fun createRepo(dirName: String, branch: String): Repository {
        val storage = prepareStorage(dirName, branch)
        return Repository.create(branch, storage, getRepoConfig(),  SymCredentials(secretKey!!, settings.symmetric))
    }

    internal class TestBlob(val content: String)

    internal class TestDirectory(spec: HashSpec) {
        var hash: Hash = Hash.createChild(spec.createChild())
        var content: MutableMap<String, TestBlob> = HashMap()

        fun clone(): TestDirectory {
            val clone = TestDirectory(hash.spec)
            clone.content.putAll(this.content)
            clone.hash = hash.clone()
            return clone
        }
    }

    internal class TestCommit(val message: String, val directory: TestDirectory, val hash: Hash, parent: TestCommit?) {
        val parents: MutableList<TestCommit> = ArrayList()

        init {
            parent?.let { parents.add(it) }
        }
    }

    internal class TestRepository(val repository: Repository, var head: TestCommit? = null) {
        var workingDir = TestDirectory(repository.config.hashSpec)

        suspend fun clone(): TestRepository {
            val clone = TestRepository(Repository.open(repository.getBranch(), repository.getRepositoryRef(),
                    repository.branchBackend, repository.crypto), head)
            clone.head?.let {
                clone.repository.setHeadCommit(it.hash)
            }
            clone.workingDir = workingDir.clone()
            return clone
        }

        suspend fun commit(message: String, parents: List<Pair<TestRepository, TestCommit>> = emptyList()): TestCommit {
            if (parents.isNotEmpty()) {
                repository.merge(parents.map { it.first.repository }, KeepOursUnchanged())
            }
            val hash = repository.commit(message.toUTF(), null)
            val testCommit = TestCommit(message, workingDir, hash, head)
            testCommit.directory.hash = repository.ioDatabase.getRootDirectory().hash.clone()
            testCommit.parents.addAll(parents.map { it.second })
            head = testCommit
            workingDir = workingDir.clone()
            return testCommit
        }

        suspend fun putBlob(path: String, content: String) {
            workingDir.content.put(path, TestBlob(content))
            repository.putBytes(path, content.toUTF())
        }

        suspend fun remove(path: String) {
            workingDir.content.remove(path)
            repository.remove(path)
        }

        /**
         * Verifies that the Repository content matches the TestRepository content
         */
        suspend fun verify(repo: Repository? = null) {
            val verifyRepo = repo ?: repository
            // verify the in memory version
            verifyInternal(verifyRepo)
            // re-open the repo and verify again
            val clone = clone()
            clone.verifyInternal(verifyRepo)
        }

        suspend private fun verifyInternal(repo: Repository) {
            val ongoingCommits = head?.let {
                val list = ArrayList<TestCommit>()
                list.add(it)
                return@let list
            } ?: return

            while (ongoingCommits.isNotEmpty()) {
                val commit = ongoingCommits.removeAt(0)
                ongoingCommits.addAll(commit.parents)
                verifyCommit(repo, commit)
            }
            // set back to head
            verifyCommit(repo, head!!)
        }

        suspend fun verifyCommit(repo: Repository, commit: TestCommit) {
            repo.setHeadCommit(commit.hash)
            val repoHead = repo.getHeadCommit() ?: throw Exception("Should not be null")
            assertEquals(commit.directory.hash, repoHead.dir)
            assertEquals(commit.message, repoHead.message.toUTFString())
            assertEquals(commit.parents.size, repoHead.parents.size)
            val nBlobs = verifyDir(repo, commit.directory, "")
            assertEquals(commit.directory.content.size, nBlobs)
        }

        /**
         * @return the number of files in the repository
         */
        suspend fun verifyDir(repo: Repository, dir: TestDirectory, path: String): Int {
            var nBlobs = 0
            repo.listFiles(path).forEach {
                val filePath = PathUtils.appendDir(path, it)
                val testBlob = dir.content[filePath] ?: throw Exception("Unexpected blob")
                assertEquals(testBlob.content, repo.readBytes(filePath).toUTFString())
                nBlobs++
            }
            repo.listDirectories(path).forEach {
                val dirPath = PathUtils.appendDir(path, it)
                nBlobs += verifyDir(repo, dir, dirPath)
            }
            return nBlobs
        }
    }
}
