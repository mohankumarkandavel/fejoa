package org.fejoa.storage

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.support.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class CyclicPolySplitterTest {
    @Test
    fun testBasics() = runBlocking {
        test(1, 100, 48)
        test(1, 100, 64)

        val seed = 11L
        val random = Random(seed)
        for (i in 1 .. 100) {
            val target = 50 + 8000 * random.readFloat()
            test(seed + i, target.toInt(), 48)
        }
    }

    suspend private fun test(randomSeed: Long, target: Int, windowSize: Int) {
        val min = windowSize
        val max = 100 * target

        val random = Random(randomSeed)
        val seed = ByteArray(16)
        random.read(seed)

        val data = ByteArray(max)
        random.read(data)

        val splitter = CyclicPolySplitter.create(seed, target, min, max, windowSize)
        val triggerPoint = firstTriggerPoint(splitter, data, 0)
        assertTrue(triggerPoint >= 0)
        verifyTriggerPoint(splitter.newInstance(), data, triggerPoint, windowSize)
    }


    private fun firstTriggerPoint(splitter: ChunkSplitter, data: ByteArray, offset: Int): Int {
        for (i in offset until data.size) {
            val triggered = splitter.update(data[i])
            if (triggered)
                return i
        }
        return -1
    }

    private fun verifyTriggerPoint(splitter: ChunkSplitter, data: ByteArray, triggerPoint: Int, windowSize: Int) {
        var start = triggerPoint - 2 * windowSize + 1
        if (start < 0)
            start = 0

        for (i in start .. triggerPoint - windowSize + 1) {
            val currentTriggerPoint = firstTriggerPoint(splitter.newInstance(), data, i)
            assertEquals(triggerPoint, currentTriggerPoint)
        }
    }
}