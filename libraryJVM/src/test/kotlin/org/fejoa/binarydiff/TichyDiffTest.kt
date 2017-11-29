package org.fejoa.binarydiff

import org.fejoa.support.*
import kotlin.collections.ArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TichyDiffTest {
    // window is 16 byte
    val match = "0123456789abcdef"
    val block = "AAAABBBBCCCCDDDD"

    @Test
    fun testBasicDiffs() {
        // no base version
        var diff = TichyDiff.diff("".toUTF(), "test".toUTF())
        assertTrue { diff.size == 1 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.INSERT }
        assertEquals("test", (diff[0] as BinaryDiff.Insert).data.toUTFString())

        // short base version (unusable
        diff = TichyDiff.diff("t".toUTF(), "test".toUTF())
        assertTrue { diff.size == 1 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.INSERT }
        assertEquals("test", (diff[0] as BinaryDiff.Insert).data.toUTFString())

        // exact minimal match
        diff = TichyDiff.diff(match.toUTF(), match.toUTF())
        assertTrue { diff.size == 1 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.COPY }
        assertEquals(IntRange(0, 15), (diff[0] as BinaryDiff.Copy).range)

        diff = TichyDiff.diff(match.toUTF(), ("0123" + match).toUTF())
        assertTrue { diff.size == 2 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.INSERT }
        assertEquals("0123", (diff[0] as BinaryDiff.Insert).data.toUTFString())
        assertTrue { diff[1].type == BinaryDiff.OperationType.COPY }
        assertEquals(IntRange(0, 15), (diff[1] as BinaryDiff.Copy).range)

        diff = TichyDiff.diff(match.toUTF(), (match + "0123").toUTF())
        assertTrue { diff.size == 2 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.COPY }
        assertEquals(IntRange(0, 15), (diff[0] as BinaryDiff.Copy).range)
        assertTrue { diff[1].type == BinaryDiff.OperationType.INSERT }
        assertEquals("0123", (diff[1] as BinaryDiff.Insert).data.toUTFString())

        // overlapping match
        diff = TichyDiff.diff(("45" + match + "123").toUTF(), ("12345" + match + "12345").toUTF())
        assertTrue { diff.size == 3 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.INSERT }
        assertEquals("123", (diff[0] as BinaryDiff.Insert).data.toUTFString())
        assertTrue { diff[1].type == BinaryDiff.OperationType.COPY }
        assertEquals(IntRange(0, 20), (diff[1] as BinaryDiff.Copy).range)
        assertTrue { diff[2].type == BinaryDiff.OperationType.INSERT }
        assertEquals("45", (diff[2] as BinaryDiff.Insert).data.toUTFString())

        // longest match
        diff = TichyDiff.diff((block + "AAAABBBBCCCCDDD5" + match + block + "AAAABBBBCCCCDDD5" + match + "12Z").toUTF(),
                ("12345" + match + "12345").toUTF())
        assertTrue { diff.size == 3 }
        assertTrue { diff[0].type == BinaryDiff.OperationType.INSERT }
        assertEquals("1234", (diff[0] as BinaryDiff.Insert).data.toUTFString())
        assertTrue { diff[1].type == BinaryDiff.OperationType.COPY }
        assertEquals(IntRange(4 * 16 + 15, 4 * 16 + 15 + 18), (diff[1] as BinaryDiff.Copy).range)
        assertTrue { diff[2].type == BinaryDiff.OperationType.INSERT }
        assertEquals("345", (diff[2] as BinaryDiff.Insert).data.toUTFString())
    }

    fun assertIO(base: String, new: String) {
        assertIO(base.toUTF(), new.toUTF())
    }

    class Stats(val newSize: Int, val savedSize: Int)

    fun assertIO(base: ByteArray, new: ByteArray): Stats {
        val diff = TichyDiff.diff(base, new)
        assertTrue(new contentEquals TichyDiff.apply(base, diff))
        val out = ByteArrayOutStream()
        diff.pack(out)
        val outBuffer = out.toByteArray()
        val loadedDiff = BinaryDiff.unpack(ByteArrayInStream(outBuffer))
        val reconstructedNew = TichyDiff.apply(base, loadedDiff)
        assertTrue(new contentEquals reconstructedNew)
        return Stats(new.size, new.size - outBuffer.size)
    }

    @Test
    fun testIO() {
        assertIO("Base version $match End1", "New version $match End2")
    }

    private fun testRandom(random: Random): Stats {
        val baseMinSize = 10//256
        val baseSizeRange = 1024 * 100 // 100kb

        val minEdits = 1
        val editRange = 10

        val minEditSize = 1
        val editSizeRange = 1024 * 10

        val triggerRemove = 0.25
        val triggerInsert = 0.5
        val triggerEdit = 1

        val baseSize = (baseMinSize + baseSizeRange * random.readFloat()).toInt()
        val base = ByteArray(baseSize)
        random.read(base)

        var new = base.copyOf()

        val nEdits = (minEdits + editRange * random.readFloat()).toInt()
        for (i in 1..nEdits) {
            val editPosition = (new.size * random.readFloat()).toInt()
            val editType = random.readFloat()
            val editSize = (minEditSize + editSizeRange * random.readFloat()).toInt()
            val buffer = ByteArrayOutStream()
            // write beginning
            if (editPosition > 0)
                buffer.write(new, 0, editPosition)

            if (editType < triggerRemove) {
                // remove
                val endRemove = editPosition + editSize
                if (endRemove < new.size)
                    buffer.write(new, endRemove, new.size - endRemove)
            } else {
                val editData = ByteArray(editSize)
                random.read(editData)

                if (editType < triggerInsert) {
                    // insert
                    buffer.write(editData)
                    buffer.write(new, editPosition, new.size - editPosition)
                } else {
                    // edit
                    buffer.write(editData)
                    val editEnd = editPosition + editSize
                    if (editEnd < new.size)
                        buffer.write(new, editEnd, new.size - editEnd)
                }
            }
            new = buffer.toByteArray()
        }

        return assertIO(base, new)
    }

    @Test
    fun testRandomChanges() {
        val nTests = 100

        val stats = ArrayList<Stats>()
        for (i in 1 .. nTests) {
            //println("Test number $i")
            stats += testRandom(Random(7 + i.toLong()))
        }

        val wasted = stats.filter { it.savedSize < 0 }
        if (wasted.size > 0)
            println("n wasted: ${wasted.size} average: " + wasted.sumBy { it.savedSize } / nTests)

        val savedTotal = stats.sumBy { it.savedSize }
        val newSizeTotal = stats.sumBy { it.newSize }
        println("Average saved: " +  savedTotal / nTests + " (" + savedTotal.toFloat() * 100 /newSizeTotal + "%)" )
    }
}