package org.fejoa.repository

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.chunkcontainer.*
import org.fejoa.support.readAll
import org.fejoa.support.toUTF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ObjectIndexTest : ChunkContainerTestBase() {

    suspend private fun createChunkContainer(): ChunkContainer {
        val dirName = "ObjectIndexTestDir"
        val name = "test"
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setFixedSizeChunking(500)
        return prepareContainer(dirName, name, config)
    }

    suspend private fun addVersion(content: HashMap<String, MutableList<Pair<Hash, ByteArray>>>,
                                   index: ObjectIndex, path: String, dataString: String) {
        val data = dataString.toUTF()
        var chunkContainer = createChunkContainer()
        var outStream = ChunkContainerOutStream(chunkContainer)
        outStream.write(data)
        outStream.close()
        val hash = index.putBlob(path, chunkContainer)

        val versions = content[path] ?: ArrayList()
        versions.add(hash to data)
        content.put(path, versions)
    }

    suspend private fun verifyContent(content: HashMap<String, MutableList<Pair<Hash, ByteArray>>>,
                                      index: ObjectIndex) {
        for (item in content) {
            val path = item.key
            val versions = item.value
            for (version in versions) {
                val hash = version.first
                val readContainer = index.getBlob(path, hash) ?: throw Exception()
                assertTrue(version.second contentEquals ChunkContainerInStream(readContainer).readAll())
            }
        }
    }

    @Test
    fun testBasics() = runBlocking {
        val objectIndexCC = createChunkContainer()
        var objectIndex = ObjectIndex.create(RepositoryConfig(), objectIndexCC)
        val content = HashMap<String, MutableList<Pair<Hash, ByteArray>>>()

        addVersion(content, objectIndex, "test", "Hello")
        assertEquals(null, objectIndex.getBlob("wrong/path", content["test"]!![0].first))

        verifyContent(content, objectIndex)
        objectIndex.flush()
        objectIndex = ObjectIndex.open(RepositoryConfig(), objectIndexCC)
        verifyContent(content, objectIndex)

        addVersion(content, objectIndex,"test", "Hello World")
        verifyContent(content, objectIndex)
        // flush and read again
        objectIndex.flush()
        objectIndex = ObjectIndex.open(RepositoryConfig(), objectIndexCC)
        verifyContent(content, objectIndex)

        addVersion(content, objectIndex,"test", "Hello World more changes")
        verifyContent(content, objectIndex)
        objectIndex.flush()
        objectIndex = ObjectIndex.open(RepositoryConfig(), objectIndexCC)
        verifyContent(content, objectIndex)

        // multiple paths
        addVersion(content, objectIndex,"test", "Hello World more changes and more")
        addVersion(content, objectIndex,"test2", "Another path")
        addVersion(content, objectIndex,"test2", "Version 2 in another path")
        addVersion(content, objectIndex,"test2", "Version 3 in another path")
        addVersion(content, objectIndex,"test2/sub", "And another one")
        verifyContent(content, objectIndex)
        objectIndex.flush()
        objectIndex = ObjectIndex.open(RepositoryConfig(), objectIndexCC)
        verifyContent(content, objectIndex)

        // some more edits
        addVersion(content, objectIndex,"test2/sub", "And another one...")
        addVersion(content, objectIndex,"test3", "Test 3")
        verifyContent(content, objectIndex)
        objectIndex.flush()
        objectIndex = ObjectIndex.open(RepositoryConfig(), objectIndexCC)
        verifyContent(content, objectIndex)
    }
}
