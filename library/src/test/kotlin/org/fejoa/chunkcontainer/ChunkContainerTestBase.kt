package org.fejoa.chunkcontainer

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.CryptoInterface
import org.fejoa.crypto.SecretKey
import org.fejoa.crypto.platformCrypto
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
    protected var cryptoInterface: CryptoInterface = platformCrypto()
    protected var secretKey: SecretKey? = null
    protected var storageBackend: StorageBackend? = null

    @BeforeTest
    open fun setUp() = runBlocking {
        secretKey = cryptoInterface.generateSymmetricKey(settings.symmetric).await()
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
                .encrypted(cryptoInterface, secretKey!!, settings.symmetric).compressed()
    }

    protected fun ChunkStorage.prepareAccessors(): ChunkAccessors {
        val boxSpec = BoxSpec(
                encInfo = BoxSpec.EncryptionInfo(BoxSpec.EncryptionInfo.Type.PARENT),
                zipType = BoxSpec.ZipType.DEFLATE,
                zipBeforeEnc = true
        )
        return RepoChunkAccessors(this, RepositoryConfig(boxSpec = boxSpec,
                crypto = CryptoConfig(cryptoInterface, secretKey!!, settings.symmetric)))
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