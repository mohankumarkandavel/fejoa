package org.fejoa.chunkcontainer

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.crypto.*
import org.fejoa.repository.*
import org.fejoa.storage.*
import org.fejoa.support.AsyncInStream
import org.fejoa.support.readAll
import org.fejoa.support.await
import org.fejoa.support.toUTFString

import kotlin.test.BeforeTest
import kotlin.test.AfterTest


open class ChunkContainerTestBase {
    protected val cleanUpList: MutableList<String> = ArrayList()
    protected var settings = CryptoSettings.default
    protected var secretKey: SecretKey? = null
    protected var storageBackend: StorageBackend? = null

    @BeforeTest
    fun setUp() = runBlocking {
        secretKey = CryptoHelper.crypto.generateSymmetricKey(settings.symmetric.key).await()
        storageBackend = platformCreateStorage()
    }

    @AfterTest
    fun tearDown() = runBlocking {
        for (namespace in cleanUpList)
            storageBackend!!.deleteNamespace(namespace)
    }

    suspend protected fun prepareStorage(dirName: String, name: String): StorageBackend.BranchBackend {
        return storageBackend?.let {
            val branchBackend = if (it.exists(dirName, name))
                it.open(dirName, name)
            else {
                it.create(dirName, name)
            }
            cleanUpList.add(dirName)
            return@let branchBackend
        } ?: throw Exception("storageBackend should not be null")
    }

    private fun ChunkStorage.prepare(): ChunkAccessor {
        // first compressed then encrypted
        return this.startTransaction().toChunkAccessor()
                .encrypted(CryptoHelper.crypto, secretKey!!, settings.symmetric).compressed()
    }

    protected fun getRepoConfig(): RepositoryConfig {
        val seed = ByteArray(10) // just some zeros
        val hashSpec = HashSpec.createCyclicPoly(HashSpec.HashType.FEJOA_CYCLIC_POLY_2KB_8KB, seed)

        val boxSpec = BoxSpec(
                encInfo = BoxSpec.EncryptionInfo(BoxSpec.EncryptionInfo.Type.PARENT),
                zipType = BoxSpec.ZipType.DEFLATE,
                zipBeforeEnc = true
        )

        return RepositoryConfig(
                hashSpec = hashSpec,
                boxSpec = boxSpec
        )
    }

    protected fun ChunkStorage.prepareAccessors(): ChunkAccessors {
        return RepoChunkAccessors(this, getRepoConfig(), SymBaseCredentials(secretKey!!, settings.symmetric))
    }

    suspend protected fun prepareContainer(storage: StorageBackend.BranchBackend, config: ContainerSpec)
            : ChunkContainer {
        val accessor = storage.getChunkStorage().prepare()
        return ChunkContainer.create(accessor, config)
    }

    suspend protected fun prepareContainer(dirName: String, name: String, config: ContainerSpec): ChunkContainer {
        val accessor = prepareStorage(dirName, name).getChunkStorage().prepare()
        return ChunkContainer.create(accessor, config)
    }

    suspend protected fun openContainer(dirName: String, name: String, pointer: ChunkContainerRef): ChunkContainer {
        val accessor = prepareStorage(dirName, name).getChunkStorage().prepare()
        return ChunkContainer.read(accessor, pointer)
    }

    suspend protected fun toString(inStream: AsyncInStream): String {
        return inStream.readAll().toUTFString()
    }

    suspend protected fun printStream(inStream: AsyncInStream) {
        println(toString(inStream))
    }
}