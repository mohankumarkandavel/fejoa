package org.fejoa.repository

import org.fejoa.support.Random
import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.chunkcontainer.*
import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.platformCrypto
import org.fejoa.storage.*
import org.fejoa.support.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class RepositoryTest : RepositoryTestBase() {

    val simpleCommitSignature = object : CommitSignature {
        override fun signMessage(message: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): ByteArray {
            return message
        }

        override fun verifySignedMessage(signedMessage: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): Boolean {
            return true
        }
    }

    @Test
    fun testEncrypted() = runBlocking {
        val dirName = "testEncryptedDir"
        val branch = "testEnc"

        val storage = prepareStorage(dirName, branch)
        val crypto = platformCrypto()
        val settings = CryptoSettings.default
        val secretKey = crypto.generateSymmetricKey(settings.symmetric).await()
        val rawAccessor = storage.getChunkStorage().startTransaction().toChunkAccessor()
        val accessor = rawAccessor.encrypted(crypto, secretKey, settings.symmetric)

        val testData = ByteArray(1024 * 1000)
        Random().read(testData)

        val config = ContainerSpec(HashSpec(), BoxSpec())
        var container = ChunkContainer.create(accessor, config)
        val outStream = ChunkContainerOutStream(container)
        outStream.write(testData)
        outStream.close()

        container = ChunkContainer.read(accessor, container.ref)
        assertTrue(testData contentEquals ChunkContainerInStream(container).readAll())
    }

    @Test
    fun testBasics() = runBlocking {
        val dirName = "testBasics"
        val branch = "basicBranch"

        val storage = prepareStorage(dirName, branch)
        val branchLog = storage.getBranchLog()

        val repoConfig = RepositoryConfig()
        repoConfig.hashSpec.setFixedSizeChunking(500)
        val objectIndexCC = createChunkContainer(storage, repoConfig)
        val repoAccessors = storage.getChunkStorage().prepareAccessors()
        val objectIndex = ObjectIndex.create(repoConfig, objectIndexCC)

        var repository = Repository.create(branch, objectIndex, branchLog, repoAccessors, branchLogIO!!,
                RepositoryConfig())

        repository.putBytes("test", "test".toUTF())
        assertTrue(repository.readBytes("test") contentEquals "test".toUTF())
        val commitHash = repository.commit(ByteArray(0), simpleCommitSignature)

        assertTrue(repository.readBytes("test") contentEquals "test".toUTF())

        repository = Repository.open(branch, branchLog, repoAccessors, repository.getHead(), branchLogIO!!)
        assertTrue(repository.readBytes("test") contentEquals "test".toUTF())
    }

    @Test
    fun testRepositoryBasics() = runBlocking {
        val dirName = "testRepositoryBasicsDir"
        val branch = "basicBranch"
        val storage = prepareStorage(dirName, branch)
        val branchLog = storage.getBranchLog()
        val repoConfig = RepositoryConfig()
        repoConfig.hashSpec.setFixedSizeChunking(500)
        val objectIndexCC = createChunkContainer(storage, repoConfig)
        val repoAccessors = storage.getChunkStorage().prepareAccessors()
        val objectIndex = ObjectIndex.create(repoConfig, objectIndexCC)
        var repository = Repository.create(branch, objectIndex, branchLog, repoAccessors, branchLogIO!!,
                repoConfig)

        val content = HashMap<String, DatabaseStringEntry>()
        add(repository, content, DatabaseStringEntry("file1", "file1"))
        add(repository, content, DatabaseStringEntry("dir1/file2", "file2"))
        add(repository, content, DatabaseStringEntry("dir1/file3", "file3"))
        add(repository, content, DatabaseStringEntry("dir2/file4", "file4"))
        add(repository, content, DatabaseStringEntry("dir1/sub1/file5", "file5"))
        add(repository, content, DatabaseStringEntry("dir1/sub1/sub2/file6", "file6"))

        repository.commit(ByteArray(0), simpleCommitSignature)
        containsContent(repository, content)
        val tip = repository.getHead()
        assertEquals(tip, repository.getHeadCommit()!!.getRef().hash)

        repository = Repository.open(branch, branchLog, repoAccessors, repository.getHead(), branchLogIO!!)
        containsContent(repository, content)

        // test add to existing dir
        add(repository, content, DatabaseStringEntry("dir1/file6", "file6"))
        repository.commit(ByteArray(0), simpleCommitSignature)
        repository = Repository.open(branch, branchLog, repoAccessors, repository.getHead(), branchLogIO!!)
        containsContent(repository, content)

        // test update
        add(repository, content, DatabaseStringEntry("dir1/file3", "file3Update"))
        add(repository, content, DatabaseStringEntry("dir1/sub1/file5", "file5Update"))
        add(repository, content, DatabaseStringEntry("dir1/sub1/sub2/file6", "file6Update"))
        repository.commit(ByteArray(0), simpleCommitSignature)
        repository = Repository.open(branch, branchLog, repoAccessors, repository.getHead(), branchLogIO!!)
        containsContent(repository, content)

        // test remove
        remove(repository, content, "dir1/sub1/file5")
        repository.commit(ByteArray(0), simpleCommitSignature)
        repository = Repository.open(branch, branchLog, repoAccessors, repository.getHead(), branchLogIO!!)
        containsContent(repository, content)

        assertEquals(0, repository.listFiles("notThere").size)
        assertEquals(0, repository.listDirectories("notThere").size)
        assertEquals(0, repository.listFiles("file1").size)
        assertEquals(0, repository.listDirectories("file1").size)
        assertEquals(0, repository.listDirectories("dir1/file2").size)
    }

    @Test
    fun testRepositoryOpenModes() = runBlocking {
        val repository = createRepo("RepoOpenModeTest", "repoBranch")

        var randomDataAccess: RandomDataAccess? = null
        try {
            randomDataAccess = repository.open("test", RandomDataAccess.Mode.READ)
        } catch (e: Exception) {

        }
        // file does not exist
        assertNull(randomDataAccess)

        randomDataAccess = repository.open("test", RandomDataAccess.Mode.WRITE)
        randomDataAccess.write("Hello World".toUTF())
        randomDataAccess.close()
        assertEquals("Hello World", repository.readBytes("test").toUTFString())

        randomDataAccess = repository.open("test", RandomDataAccess.Mode.APPEND)
        randomDataAccess.write("!".toUTF())
        randomDataAccess.close()
        assertEquals("Hello World!", repository.readBytes("test").toUTFString())

        randomDataAccess = repository.open("test", RandomDataAccess.Mode.WRITE)
        randomDataAccess.seek(6)
        randomDataAccess.write("there".toUTF())
        randomDataAccess.close()
        assertEquals("Hello there!",  repository.readBytes("test").toUTFString())

        randomDataAccess = repository.open("test", RandomDataAccess.Mode.INSERT)
        randomDataAccess.seek(5)
        randomDataAccess.write(" you".toUTF())
        randomDataAccess.close()
        assertEquals("Hello you there!",  repository.readBytes("test").toUTFString())

        randomDataAccess = repository.open("test", RandomDataAccess.Mode.TRUNCATE)
        randomDataAccess.write("New string!".toUTF())
        randomDataAccess.close()
        assertEquals("New string!", repository.readBytes("test").toUTFString())

        // delete
        randomDataAccess = repository.open("test", RandomDataAccess.Mode.WRITE)
        randomDataAccess.delete(4, 2)
        randomDataAccess.close()
        assertEquals("New ring!", repository.readBytes("test").toUTFString())

        // try to write in read mode
        randomDataAccess = repository.open("test", RandomDataAccess.Mode.READ)
        var failed = false
        try {
            randomDataAccess.write("Hello World".toUTF())
        } catch (e: IOException) {
            failed = true
        }
        randomDataAccess.close()
        assertTrue(failed)

        // try to read in write mode
        randomDataAccess = repository.open("test", RandomDataAccess.Mode.WRITE)
        failed = false
        try {
            val buffer = ByteArray(2)
            randomDataAccess.read(buffer)
        } catch (e: IOException) {
            failed = true
        }
        randomDataAccess.close()
        assertTrue(failed)
    }
}