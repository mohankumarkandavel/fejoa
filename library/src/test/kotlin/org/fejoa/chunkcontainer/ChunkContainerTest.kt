package org.fejoa.chunkcontainer

import org.fejoa.storage.*
import kotlin.test.Test
import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.support.ByteArrayOutStream
import org.fejoa.support.Random
import org.fejoa.support.readAll
import org.fejoa.support.toUTF
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ChunkContainerTest : ChunkContainerTestBase() {
    @Test
    fun testAppend() {
        runBlocking {
            val dirName = "testAppendDir"
            val name = "test"
            val config = ContainerSpec(HashSpec(), BoxSpec())
            config.hashSpec.setFixedSizeChunking(180)
            var chunkContainer = prepareContainer(dirName, name, config)

            chunkContainer.append(DataChunk("Hello".toUTF()))
            chunkContainer.append(DataChunk(" World!".toUTF()))
            assertEquals(1, chunkContainer.level)

            chunkContainer.append(DataChunk(" Split!".toUTF()))
            println(chunkContainer.printAll())
            chunkContainer.flush(false)
            assertEquals(2, chunkContainer.level)

            chunkContainer.append(DataChunk(" more!".toUTF()))
            chunkContainer.append(DataChunk(" another Split!".toUTF()))
            chunkContainer.flush(false)
            assertEquals(3, chunkContainer.level)

            chunkContainer.append(DataChunk(" and more!".toUTF()))

            println(chunkContainer.printAll())

            chunkContainer.flush(false)

            println("After flush:")
            println(chunkContainer.printAll())

            // load
            println("Load:")
            val rootPointer = chunkContainer.ref

            chunkContainer = openContainer(dirName, name, rootPointer)
            println(chunkContainer.printAll())

            var inputStream = ChunkContainerInStream(chunkContainer)
            val string = toString(inputStream)
            assertEquals("Hello World! Split! more! another Split! and more!", string)

            inputStream.seek(2)
            printStream(inputStream)

            // test output stream
            println("Test out stream:")
            chunkContainer = prepareContainer(dirName, name, config)
            val containerOutputStream = ChunkContainerOutStream(chunkContainer)
            containerOutputStream.write("Chunk1".toUTF())
            containerOutputStream.flush()
            containerOutputStream.write("Chunk2".toUTF())
            containerOutputStream.flush()
            println(chunkContainer.printAll())

            println("Load from stream:")
            inputStream = ChunkContainerInStream(chunkContainer)
            val result = toString(inputStream)
            assertEquals("Chunk1Chunk2", result)
        }
    }


    private fun assertContent(ref: ChunkContainerRef, dirName: String, name: String, data: String) = runBlocking {
        val chunkContainer = openContainer(dirName, name, ref)
        val string = toString(ChunkContainerInStream(chunkContainer))
        assertEquals(data, string)
        val chunkHash = ChunkHash(ref.hash.spec)
        chunkHash.write(data.toUTF())
        assertEquals(HashValue(chunkHash.hash()), chunkContainer.hash())
    }

    @Test
    fun testTruncate() = runBlocking {
        val dirName = "testTruncateDir"
        val name = "test"
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setFixedSizeChunking(180)
        val chunkContainer = prepareContainer(dirName, name, config)
        val containerOutputStream = ChunkContainerOutStream(chunkContainer)
        val chunk = "Hello World. Some long data to create multiple splits/nodes" +
                "(length is equal the chunk size == 180)" +
                "data data data data data data data data data data data data data data data data 12"
        assertEquals(180, chunk.length)
        val data = (chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk
                + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk
                + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + " a few more bytes")
        containerOutputStream.write(data.toUTF())
        containerOutputStream.flush()
        assertEquals(6, chunkContainer.level)
        assertContent(chunkContainer.ref, dirName, name, data)
        assertEquals(data.toUTF().size.toLong(), chunkContainer.getDataLength())

        // cases:
        // 1. middle of a node
        containerOutputStream.truncate(200)
        containerOutputStream.flush()
        assertEquals(chunkContainer.getDataLength(), 200)

        // 2. just at the beginning
        containerOutputStream.truncate(178)
        containerOutputStream.flush()
        assertEquals(chunkContainer.getDataLength(), 178)

        // 3. end of a node
        containerOutputStream.truncate(177)
        containerOutputStream.flush()
        assertEquals(chunkContainer.getDataLength(), 177)

        containerOutputStream.truncate(11)
        containerOutputStream.flush()
        containerOutputStream.flush()

        // load
        var string = toString(ChunkContainerInStream(chunkContainer))
        assertEquals(string, "Hello World")

        containerOutputStream.write("!".toUTF())
        containerOutputStream.flush()
        string = toString(ChunkContainerInStream(chunkContainer))
        assertEquals(string, "Hello World!")
    }

    @Test
    fun testTruncateAll() = runBlocking {
        val dirName = "testTruncateAllDir"
        val name = "test"
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setFixedSizeChunking(180)
        val chunkContainer = prepareContainer(dirName, name, config)
        val containerOutputStream = ChunkContainerOutStream(chunkContainer)
        val chunk = "Hello World. Some long data to create multiple splits/nodes" +
                "(length is equal the chunk size == 177)" +
                "data data data data data data data data data data data data data data data data"
        assertEquals(177, chunk.length)
        val data = (chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk + chunk
                + " a few more bytes")
        containerOutputStream.write(data.toUTF())
        containerOutputStream.flush()
        assertEquals(4, chunkContainer.level)
        assertContent(chunkContainer.ref, dirName, name, data)

        for (i in 0 until data.length) {
            val truncateLength = data.length - i
            val reducedData = data.substring(0, truncateLength)
            containerOutputStream.truncate(truncateLength.toLong())
            containerOutputStream.flush()
            assertContent(chunkContainer.ref, dirName, name, reducedData)
        }
    }

    @Test
    fun testEditing() = runBlocking {
        val dirName = "testEditingDir"
        val name = "test"
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setFixedSizeChunking(180)
        var chunkContainer = prepareContainer(dirName, name, config)
        // test empty chunk container
        assertEquals("", toString(ChunkContainerInStream(chunkContainer)))
        chunkContainer.flush(false)
        chunkContainer = ChunkContainer.read(chunkContainer.blobAccessor, chunkContainer.ref)
        assertEquals("", toString(ChunkContainerInStream(chunkContainer)))

        chunkContainer.append(DataChunk("Hello".toUTF()))
        assertEquals("Hello", toString(ChunkContainerInStream(chunkContainer)))
        //println(chunkContainer.printAll())
        chunkContainer.append(DataChunk(" World!".toUTF()))
        chunkContainer.append(DataChunk("more".toUTF()))
        chunkContainer.insert(DataChunk("22".toUTF()), 5)
        //println(chunkContainer.printAll())
        assertEquals("Hello22 World!more", toString(ChunkContainerInStream(chunkContainer)))

        chunkContainer.flush(false)
        //println(chunkContainer.printAll())
        assertEquals("Hello22 World!more", toString(ChunkContainerInStream(chunkContainer)))

        // remove the chunks "22" and " World!"
        chunkContainer.remove(5, 2)
        chunkContainer.remove(5, 7)

        chunkContainer.flush(false)
        //println(chunkContainer.printAll())
        assertEquals("Hellomore", toString(ChunkContainerInStream(chunkContainer)))
    }

    @Test
    fun testHash() = runBlocking {
        val dirName = "testHashDir"
        val name = "test"
        val dataSplitter = FixedBlockSplitter(2)
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setFixedSizeChunking(180)
        val chunkContainer = prepareContainer(dirName, name, config)

        chunkContainer.append(DataChunk("11".toUTF()))
        chunkContainer.append(DataChunk("22".toUTF()))
        chunkContainer.append(DataChunk("33".toUTF()))
        chunkContainer.insert(DataChunk("44".toUTF()), 2)
        //println(chunkContainer.printAll())

        chunkContainer.flush(false)
        //println(chunkContainer.printAll())

        // setup a factory for the custom data splitter
        val splitterFactory = object : NodeSplitterFactory {
            val nodeFactory = config.hashSpec.getNodeSplitterFactory()
            override fun nodeSizeFactor(level: Int): Float {
                return nodeFactory.nodeSizeFactor(level)
            }

            override fun create(level: Int): ChunkSplitter {
                if (level == ChunkHash.DATA_LEVEL)
                    return dataSplitter
                else
                    return nodeFactory.create(level)
            }
        }

        var chunkHash = ChunkHash(splitterFactory, config.hashSpec.getBaseHashFactory())
        chunkHash.write("11".toUTF())
        chunkHash.write("44".toUTF())
        chunkHash.write("22".toUTF())
        chunkHash.write("33".toUTF())
        assertTrue(chunkContainer.hash().bytes contentEquals chunkHash.hash())

        // test chunk container with a single chunk
        chunkContainer.clear()
        chunkContainer.append(DataChunk("11".toUTF()))
        chunkContainer.flush(false)
        chunkHash = ChunkHash(config.hashSpec)
        chunkHash.write("11".toUTF())
        assertEquals(chunkContainer.hash(), HashValue(chunkHash.hash()))
    }

    private fun createByteTriggerSplitter(trigger: Byte): ChunkSplitter {
        return object : ChunkSplitter() {

            override fun writeInternal(i: Byte): Boolean {
                return if (i == trigger) true else false
            }

            override fun resetInternal() {

            }

            override fun newInstance(): ChunkSplitter {
                return createByteTriggerSplitter(trigger)
            }
        }
    }

    @Test
    fun testDynamicSplitter() = runBlocking {
        val dirName = "testDynamicSplitterDir"
        val name = "test"
        val dataString = "1|2|3|4|5|6|7|8|9|10|11|12|13|14|15|"
        val dataSplitter = createByteTriggerSplitter('|'.toByte())
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setRabinChunking(256, 180)
        val chunkContainer = prepareContainer(dirName, name, config)
        var outputStream = ByteArrayOutStream()
        for (c in dataString.toUTF()) {
            outputStream.write(c)
            if (dataSplitter.update(c)) {
                chunkContainer.append(DataChunk(outputStream.toByteArray()))
                outputStream = ByteArrayOutStream()
                dataSplitter.reset()
            }
        }
        chunkContainer.flush(false)
        println(chunkContainer.printAll())
        printStream(ChunkContainerInStream(chunkContainer))
        println("Insert:")
        chunkContainer.insert(DataChunk("i4|".toUTF()), 6)
        chunkContainer.flush(false)
        val newString = toString(ChunkContainerInStream(chunkContainer))
        assertEquals(newString, "1|2|3|i4|4|5|6|7|8|9|10|11|12|13|14|15|")
        println(chunkContainer.printAll())

        // setup a factory for the custom data splitter
        val splitterFactory = object : NodeSplitterFactory {
            val nodeFactory = config.hashSpec.getNodeSplitterFactory()
            override fun nodeSizeFactor(level: Int): Float {
                return nodeFactory.nodeSizeFactor(level)
            }

            override fun create(level: Int): ChunkSplitter {
                if (level == ChunkHash.DATA_LEVEL)
                    return dataSplitter
                else
                    return nodeFactory.create(level)
            }
        }
        val chunkHash = ChunkHash(splitterFactory, config.hashSpec.getBaseHashFactory())
        chunkHash.write(newString.toUTF())
        assertTrue(chunkContainer.hash().bytes contentEquals chunkHash.hash())
    }

    class DataWriteStrategy(dataSplitter: ChunkSplitter) : NodeWriteStrategy {
        override val splitter: ChunkSplitter = dataSplitter

        override fun newInstance(level: Int): NodeWriteStrategy {
            return DataWriteStrategy(splitter)
        }

        override fun reset(level: Int) {
            splitter.reset()
        }

        override fun finalizeWrite(data: ByteArray): ByteArray {
            return data
        }
    }
    
    @Test
    fun testSeekOutputStream() = runBlocking {
        val dirName = "testSeekOutputStreamDir"
        val name = "test"
        val dataSplitter = createByteTriggerSplitter('|'.toByte())
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setRabinChunking(256, 180)

        var chunkContainer = prepareContainer(dirName, name, config)
        var outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|2|3|4|".toUTF())
        outputStream.flush(false)
        assertEquals("1|2|3|4|", toString(ChunkContainerInStream(chunkContainer)))
        chunkContainer.flush(false)
        assertEquals("1|2|3|4|", toString(ChunkContainerInStream(chunkContainer)))

        // append: "1|2|3|4|" -> "1|2|3|4|5|6|"
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(chunkContainer.getDataLength())
        outputStream.write("5|6|".toUTF())
        outputStream.close()
        assertEquals("1|2|3|4|5|6|", toString(ChunkContainerInStream(chunkContainer)))

        // overwrite + append: "1|2|3|4|" -> "1|2|3|i|5|6|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|2|3|4|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(6)
        outputStream.write("i|5|6|".toUTF())
        outputStream.close()
        assertEquals("1|2|3|i|5|6|", toString(ChunkContainerInStream(chunkContainer)))

        // overwrite + append: "1|2|3|4|" -> "1|2|iii|5|6|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|2|3|4|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(4)
        outputStream.write("iii|5|6|".toUTF())
        outputStream.close()
        assertEquals("1|2|iii|5|6|", toString(ChunkContainerInStream(chunkContainer)))

        //"1|2|3|4|" -> "1|i|i|4|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|2|3|4|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(2)
        outputStream.write("i|i|".toUTF())
        outputStream.close()
        assertEquals("1|i|i|4|", toString(ChunkContainerInStream(chunkContainer)))
        // try again but with a shorter overwrite:
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|2|3|4|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(2)
        outputStream.write("i|i".toUTF())
        outputStream.close()
        assertEquals("1|i|i|4|", toString(ChunkContainerInStream(chunkContainer)))

        //"1|222|3|" -> "1|i|i|3|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|222|3|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(2)
        outputStream.write("i|i|".toUTF())
        outputStream.close()
        assertEquals("1|i|i|3|", toString(ChunkContainerInStream(chunkContainer)))
        // try again but with a shorter overwrite:
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|222|3|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(2)
        outputStream.write("i|i".toUTF())
        outputStream.close()
        assertEquals("1|i|i|3|", toString(ChunkContainerInStream(chunkContainer)))

        //"1|222|3|" -> "1|i|2|3|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|222|3|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(2)
        outputStream.write("i|".toUTF())
        outputStream.close()
        assertEquals("1|i|2|3|", toString(ChunkContainerInStream(chunkContainer)))

        //"1|2222|3|" -> "1|2i|2|3|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|2222|3|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(3)
        outputStream.write("i|".toUTF())
        outputStream.close()
        assertEquals("1|2i|2|3|", toString(ChunkContainerInStream(chunkContainer)))

        //"1|222|3|" -> "1|2i2|3|"
        chunkContainer = prepareContainer(dirName, name, config)
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.write("1|222|3|".toUTF())
        outputStream.close()
        outputStream = ChunkContainerOutStream(chunkContainer, writeStrategy = DataWriteStrategy(dataSplitter))
        outputStream.seek(3)
        outputStream.write("i".toUTF())
        outputStream.close()
        assertEquals("1|2i2|3|", toString(ChunkContainerInStream(chunkContainer)))
    }

    @Test
    fun testSeekOutputStreamEditingLarge() = runBlocking {
        val nBytes = 1024 * 1000 * 50
        val data = ByteArray(nBytes)
        val random = Random()
        for (value in data.indices) {
            val random = (256 * random.readFloat()).toByte()
            data[value] = random
            //data[value] = (byte)value;
        }

        val dirName = "testSeekOutputStreamEditingLarge"
        val name = "test"
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setRabinChunking(HashSpec.HashType.FEJOA_RABIN_2KB_8KB)
        var chunkContainer = prepareContainer(dirName, name, config)
        val outputStream = ChunkContainerOutStream(chunkContainer)
        outputStream.write(data)
        outputStream.close()

        var chunkHash = ChunkHash(config.hashSpec)
        chunkHash.write(data)
        val dataHash = HashValue(chunkHash.hash())

        // verify
        chunkContainer = openContainer(dirName, name, chunkContainer.ref)
        val iter = chunkContainer.getChunkIterator(0)
        chunkHash = ChunkHash(config.hashSpec)
        while (iter.hasNext()) {
            val pointer = iter.next()
            chunkHash.write(pointer.getDataChunk().getData())
        }

        assertTrue(dataHash.bytes contentEquals chunkHash.hash())
    }

    @Test
    fun testInsertSimple() = runBlocking {
        val dirName = "testInsertSimpleDir"
        val name = "insertTest"
        val dataSplitter = createByteTriggerSplitter('|'.toByte())
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setRabinChunking(256, 180)
        var chunkContainer = prepareContainer(dirName, name, config)
        var outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT,
                DataWriteStrategy(dataSplitter))
        // write to empty container
        outputStream.write("1|2|3|4|".toUTF())
        outputStream.close()
        assertEquals("1|2|3|4|", toString(ChunkContainerInStream(chunkContainer)))

        // prepend
        chunkContainer = openContainer(dirName, name, chunkContainer.ref)
        outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT,
                DataWriteStrategy(dataSplitter))
        outputStream.write("s|t|art".toUTF())
        outputStream.close()
        assertEquals("s|t|art1|2|3|4|", toString(ChunkContainerInStream(chunkContainer)))

        // append
        chunkContainer = openContainer(dirName, name, chunkContainer.ref)
        outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT,
                DataWriteStrategy(dataSplitter))
        outputStream.seek(chunkContainer.getDataLength())
        outputStream.write("end".toUTF())
        outputStream.close()
        assertEquals("s|t|art1|2|3|4|end", toString(ChunkContainerInStream(chunkContainer)))

        // insert at chunk boundary
        chunkContainer = openContainer(dirName, name, chunkContainer.ref)
        outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT,
                DataWriteStrategy(dataSplitter))
        outputStream.seek(9)
        outputStream.write("1.5|".toUTF())
        outputStream.close()
        assertEquals("s|t|art1|1.5|2|3|4|end", toString(ChunkContainerInStream(chunkContainer)))

        // insert in chunk
        chunkContainer = openContainer(dirName, name, chunkContainer.ref)
        outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT,
                DataWriteStrategy(dataSplitter))
        outputStream.seek(6)
        outputStream.write("Z|Z|Z".toUTF())
        outputStream.close()
        assertEquals("s|t|arZ|Z|Zt1|1.5|2|3|4|end", toString(ChunkContainerInStream(chunkContainer)))

        // insert in chunk
        chunkContainer = openContainer(dirName, name, chunkContainer.ref)
        outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT,
                DataWriteStrategy(dataSplitter))
        outputStream.seek(5)
        outputStream.write("XX".toUTF())
        outputStream.close()
        assertEquals("s|t|aXXrZ|Z|Zt1|1.5|2|3|4|end", toString(ChunkContainerInStream(chunkContainer)))
    }

    suspend private fun validateContent(expected: ByteArray, chunkContainer: ChunkContainer) {
        assertTrue(expected contentEquals ChunkContainerInStream(chunkContainer).readAll())

        var chunkHash = ChunkHash(chunkContainer.ref.hash.spec)
        chunkHash.write(expected)

        assertTrue(chunkContainer.hash().bytes contentEquals chunkHash.hash())
    }

    @Test
    fun testInsertRandom() = runBlocking {
        val dirName = "testInsertRandomDir"
        val name = "insertTest"
        val config = ContainerSpec(HashSpec(), BoxSpec())
        config.hashSpec.setRabinChunking(1024 * 1, 256)
        var chunkContainer = prepareContainer(dirName, name, config)
        var outputStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT)

        val random = Random(6)
        val minInsertSize = 64
        val insertSizeRange = 6 * 1024

        val nIterations = 100
        var expected = ByteArray(0)
        for (i in 1..nIterations) {
            val insertSize = minInsertSize + (random.readFloat() * insertSizeRange).toInt()
            val insertBuffer = ByteArray(insertSize)
            random.read(insertBuffer)

            val insertPosition = (random.readFloat() * chunkContainer.getDataLength()).toInt()
            outputStream.seek(insertPosition.toLong())
            outputStream.write(insertBuffer)
            outputStream.flush()
            assertEquals((insertPosition + insertBuffer.size).toLong(), outputStream.position())

            // insert into expected
            val byteStream = ByteArrayOutStream()
            byteStream.write(expected, 0, insertPosition)
            byteStream.write(insertBuffer)
            byteStream.write(expected, insertPosition, expected.size - insertPosition)
            expected = byteStream.toByteArray()

            validateContent(expected, chunkContainer)
        }

        for (i in 1..nIterations) {
            var deleteSize = minInsertSize + (random.readFloat() * insertSizeRange * 2).toInt()
            val chunkContainerSize = chunkContainer.getDataLength().toInt()
            var deletePosition = (random.readFloat() * (chunkContainerSize - 1)).toInt()
            if (deletePosition + deleteSize > chunkContainerSize)
                deleteSize = chunkContainerSize - deletePosition

            outputStream.delete(deletePosition.toLong(), deleteSize.toLong())
            outputStream.flush()

            // insert into expected
            val byteStream = ByteArrayOutStream()
            byteStream.write(expected, 0, deletePosition)
            byteStream.write(expected, deletePosition + deleteSize, expected.size - (deletePosition + deleteSize))
            expected = byteStream.toByteArray()

            validateContent(expected, chunkContainer)

            if (chunkContainerSize == 0)
                break
        }
    }
}
