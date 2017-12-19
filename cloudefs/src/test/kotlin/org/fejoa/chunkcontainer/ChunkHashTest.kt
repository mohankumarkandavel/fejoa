package org.fejoa.chunkcontainer

import org.fejoa.crypto.AsyncHashOutStream
import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.HashOutStreamFactory

import org.fejoa.storage.FixedBlockSplitter
import org.fejoa.storage.HashValue
import org.fejoa.storage.RabinSplitter
import org.fejoa.storage.DynamicSplitter.Companion.CHUNK_64KB
import org.fejoa.storage.DynamicSplitter.Companion.CHUNK_8KB
import org.fejoa.support.Random
import org.fejoa.support.toUTF
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue


class ChunkHashTest {
    internal var factory: HashOutStreamFactory = object : HashOutStreamFactory {
        override fun create(): AsyncHashOutStream {
            return CryptoHelper.sha256Hash()
        }
    }

    @Test
    fun testSimple() = runBlocking {
        val messagehash = factory.create()
        val chunkHash = ChunkHash(FixedBlockSplitter(2), FixedBlockSplitter(65), factory)

        val block1 = "11".toUTF()
        messagehash.write(block1)
        val hashBlock1 = messagehash.hash()
        chunkHash.write(block1)
        assertTrue(hashBlock1 contentEquals chunkHash.hash())

        // second layer
        val block2 = "22".toUTF()
        messagehash.reset()
        messagehash.write(block2)
        val hashBlock2 = messagehash.hash()
        messagehash.reset()
        messagehash.write(hashBlock1)
        messagehash.write(hashBlock2)
        val combinedHashLayer2_0 = messagehash.hash()

        chunkHash.reset()
        chunkHash.write(block1)
        chunkHash.write(block2)
        assertTrue(combinedHashLayer2_0 contentEquals chunkHash.hash())

        // third layer
        val block3 = "33".toUTF()
        messagehash.reset()
        messagehash.write(block3)
        val hashBlock3 = messagehash.hash()
        messagehash.reset()
        messagehash.write(hashBlock3)
        val combinedHashLayer2_1 = messagehash.hash()
        messagehash.reset()
        messagehash.write(combinedHashLayer2_0)
        messagehash.write(combinedHashLayer2_1)
        val combinedHashLayer3_0 = messagehash.hash()

        chunkHash.reset()
        chunkHash.write(block1)
        chunkHash.write(block2)
        chunkHash.write(block3)
        assertTrue(combinedHashLayer3_0 contentEquals chunkHash.hash())

        // fill third layer
        val block4 = "44".toUTF()
        messagehash.reset()
        messagehash.write(block4)
        val hashBlock4 = messagehash.hash()
        messagehash.reset()
        messagehash.write(hashBlock3)
        messagehash.write(hashBlock4)
        val combinedHashLayer2_2 = messagehash.hash()
        messagehash.reset()
        messagehash.write(combinedHashLayer2_0)
        messagehash.write(combinedHashLayer2_2)
        val combinedHashLayer3_1 = messagehash.hash()

        chunkHash.reset()
        chunkHash.write(block1)
        chunkHash.write(block2)
        chunkHash.write(block3)
        chunkHash.write(block4)
        assertTrue(combinedHashLayer3_1 contentEquals chunkHash.hash())
    }

    @Test
    fun testSimpleBenchmark() = runBlocking {
        val fileSizes = arrayOf(1024 * 256, 1024 * 512, 1024 * 1024 * 1, 1024 * 1024 * 2)/*1024 * 1024 * 4,
                1024 * 1024 * 8,
                1024 * 1024 * 16,
                1024 * 1024 * 32,
                1024 * 1024 * 64,
                1024 * 1024 * 128,
                1024 * 1024 * 256,
                1024 * 1024 * 512,
                1024 * 1024 * 1024,*/

        class ChunkSizeTarget(var size: Int, var minSize: Int) {

            override fun toString(): String {
                return (size / 1024).toString() + "Kb_min" + minSize / 1024 + "Kb"
            }
        }

        val chunkSizeTargetList = ArrayList<ChunkSizeTarget>()
        chunkSizeTargetList.add(ChunkSizeTarget(CHUNK_8KB, 2 * 1024))
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_16KB, 2 * 1024));
        chunkSizeTargetList.add(ChunkSizeTarget(CHUNK_64KB, 2 * 1024))
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_32KB, 2 * 1024));
        chunkSizeTargetList.add(ChunkSizeTarget(CHUNK_8KB, 4 * 1024))
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_16KB, 4 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_32KB, 4 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_64KB, "64Kb"));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_128KB, "128Kb"));

        val kFactor = 32f / (32 * 3 + 8)
        val maxSize = 1024 * 512

        class Result {
            var fileSize: Int = 0
            var sha265Times: MutableList<Long> = ArrayList()
            var chunkHashTimes: MutableList<MutableList<Long>> = ArrayList(ArrayList())

            init {
                for (i in chunkSizeTargetList.indices)
                    chunkHashTimes.add(ArrayList())
            }
        }

        val results = ArrayList<Result>()
        val nIgnore = 3
        val numberIt = nIgnore + 0
        for (size in fileSizes) {
            println("File size: " + size)
            val messagehash = factory.create()

            val data = ByteArray(size)
            val random = Random()
            for (value in data.indices) {
                val random = (256 * random.readFloat()).toByte()
                data[value] = random
            }

            val result = Result()
            result.fileSize = size
            results.add(result)

            for (i in 0 until numberIt) {
                var hash = ByteArray(0)
                val time = measureTimeMillis {
                    messagehash.write(data)
                    hash = messagehash.hash()
                }
                result.sha265Times.add(time)
                println("Time: " + time + " " + HashValue(hash))
                messagehash.reset()
            }

            for (sizeIndex in chunkSizeTargetList.indices) {
                val chunkSizeTarget = chunkSizeTargetList[sizeIndex]
                val chunkSize = chunkSizeTarget.size
                println("Target Chunk Size: " + chunkSize)
                var prevHash: ByteArray? = null
                val nodeSplitter = RabinSplitter((kFactor * chunkSize).toInt(),
                        (kFactor * chunkSizeTarget.minSize).toInt(),
                        (kFactor * maxSize).toInt())
                val chunkHash = ChunkHash(RabinSplitter(chunkSize, chunkSizeTarget.minSize, maxSize), nodeSplitter,
                        factory)
                //ChunkHash chunkHash = new ChunkHash(new FixedBlockSplitter(1024 * 8), new FixedBlockSplitter(1024 * 8));

                val chunkHashTimes = result.chunkHashTimes[sizeIndex]
                for (i in 0 until numberIt) {
                    var hash = ByteArray(0)
                    val time = measureTimeMillis {
                        chunkHash.write(data)
                        hash = chunkHash.hash()
                    }
                    chunkHashTimes.add(time)
                    println("Time cached dataHash: " + time + " " + HashValue(hash))

                    if (prevHash != null)
                        assert(prevHash contentEquals  hash)
                    prevHash = hash
                    chunkHash.reset()
                }
            }

        }

        // print results
        print("FileSize, SHA256Time")
        for (chunkSizeTarget in chunkSizeTargetList)
            print(", ChunkSize" + chunkSizeTarget.toString() + "Time")
        println()
        for (result in results) {
            for (i in nIgnore until numberIt) {
                print(result.fileSize.toString() + ", " + result.sha265Times[i])
                for (a in chunkSizeTargetList.indices) {
                    val sizeResults = result.chunkHashTimes[a]
                    print(", " + sizeResults[i])
                }
                println()
            }
        }
    }

}
