package org.fejoa.binarydiff

import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.support.ByteArrayOutStream
import org.fejoa.support.InStream
import org.fejoa.support.OutStream
import org.fejoa.support.readFully
import org.rabinfingerprint.fingerprint.RabinFingerprintLongWindowed
import org.rabinfingerprint.polynomial.Polynomial
import org.fejoa.support.assert
import kotlin.math.max


class BinaryDiff {
    enum class OperationType(val id: Int) {
        INSERT(0),
        COPY(1)
    }

    companion object {
        fun unpack(inputStream: InStream): BinaryDiff {
            val size = VarInt.read(inputStream)
            val diff = BinaryDiff()
            for (i in 0 until size.first) {
                val pair = VarInt.read(inputStream, 1)
                val opCode = pair.third
                when (opCode) {
                    OperationType.INSERT.id -> diff.operations.add(Insert.unpack(pair.first, inputStream))
                    OperationType.COPY.id -> diff.operations.add(Copy.unpack(pair.first, inputStream))
                    else -> throw Exception("Invalid op code: $opCode")
                }
            }
            return diff
        }
    }

    fun pack(out: OutStream) {
        VarInt.write(out, operations.size)
        for (op in operations) {
            op.pack(out)
        }
    }

    abstract class Operation(val type: OperationType) {
        // pack the operation specific data into a byte array
        abstract fun pack(out: OutStream)
        abstract fun apply(base: ByteArray, outputStream: OutStream)
    }

    class Insert(val data: ByteArray): Operation(OperationType.INSERT) {
        companion object {
            fun unpack(first: Long, inputStream: InStream): Insert {
                val size = first.toInt()
                var buffer = ByteArray(size)
                inputStream.readFully(buffer)
                return Insert(buffer)
            }
        }
        override fun pack(out: OutStream) {
            VarInt.write(out, data.size.toLong(), OperationType.INSERT.id, 1)
            out.write(data)
        }

        override fun apply(base: ByteArray, outputStream: OutStream) {
            outputStream.write(data)
        }
    }
    class Copy(val range: IntRange): Operation(OperationType.COPY) {
        companion object {
            fun unpack(first: Long, inputStream: InStream): Copy {
                val offset = first.toInt()
                val length = VarInt.read(inputStream).first.toInt()
                return Copy(IntRange(offset, offset + length - 1))
            }
        }
        override fun pack(out: OutStream) {
            VarInt.write(out, range.start.toLong(), OperationType.COPY.id, 1)
            VarInt.write(out, range.endInclusive - range.start + 1)
        }

        override fun apply(base: ByteArray, outputStream: OutStream) {
            outputStream.write(base.copyOfRange(range.first, range.endInclusive + 1))
        }
    }

    private val operations: MutableList<Operation> = ArrayList()

    operator fun get(i: Int): Operation {
        return operations[i]
    }

    val size: Int
        get() = operations.size

    fun insert(data: ByteArray) {
        operations.add(Insert(data))
    }

    fun getOperations(): List<Operation> {
        return operations
    }

    fun copy(range: IntRange) {
        operations.add(Copy(range))
    }
}

class TichyDiff {
    private class Index {
        // hash -> list of offsets
        private val map: MutableMap<Long, MutableList<Int>> = HashMap()

        private fun createList(fingerprint: Long): MutableList<Int> {
            val newList: MutableList<Int> = ArrayList()
            map[fingerprint] = newList
            return newList
        }

        fun put(fingerprint: Long, offset: Int) {
            val list = map[fingerprint] ?: createList(fingerprint)
            list.add(offset)
        }

        fun find(fingerprint: Long): MutableList<Int>? {
            return map[fingerprint]
        }
    }

    companion object {
        private val RABIN_WINDOW = 16
        private val poly = Polynomial.createFromLong(9256118209264353L)
        private val rabin = RabinFingerprintLongWindowed(poly, RABIN_WINDOW)

        private fun createIndex(base: ByteArray): Index {
            var index = Index()
            if (base.size < RABIN_WINDOW)
                return index

            var prev = 0xFFFFFFFF
            for (pos in 0 until base.size step RABIN_WINDOW) {
                if (base.size - pos < RABIN_WINDOW)
                    break
                rabin.reset()
                rabin.pushBytes(base, pos, RABIN_WINDOW)
                val fingerPrint = rabin.fingerprintLong
                index.put(fingerPrint, pos)
            }
            return index
        }

        fun diff(base: ByteArray, newData: ByteArray): BinaryDiff {
            var diff = BinaryDiff()
            if (newData.size < RABIN_WINDOW) {
                diff.insert(newData)
                return diff
            }

            // current position in newData to which point (exclusive) the edit script has been written
            var current = 0
            val index = createIndex(base)
            rabin.reset()
            rabin.pushBytes(newData, 0, RABIN_WINDOW - 1)
            var i = RABIN_WINDOW - 1
            while (i < newData.size) {
                val pos = i
                i++
                rabin.pushByte(newData[pos])
                val newDataMatchPosition = pos - RABIN_WINDOW + 1
                if (newDataMatchPosition < current)
                    continue

                val matchList = index.find(rabin.fingerprintLong)
                if (matchList != null) {
                    assert(matchList.size > 0)
                    var longestMatchBase = IntRange(0, 0)
                    var longestMatchNew = IntRange(0, 0)
                    for (baseMatchPosition in matchList) {
                        // actual match or a rabin collision?
                        if ((base.slice(baseMatchPosition until baseMatchPosition + RABIN_WINDOW)
                                != newData.slice(newDataMatchPosition until newDataMatchPosition + RABIN_WINDOW)))
                            continue

                        val pair = expandMatch(base, baseMatchPosition, newData, newDataMatchPosition, current)
                        val match = pair.first
                        if (match.endInclusive - match.start > longestMatchBase.endInclusive - longestMatchBase.start) {
                            longestMatchBase = match
                            longestMatchNew = pair.second
                        }
                    }

                    // was it an actual match or just a rabin collision?
                    if (longestMatchNew != IntRange(0, 0)) {
                        rabin.reset()
                        // first insert if necessary
                        if (current < longestMatchNew.start)
                            diff.insert(newData.copyOfRange(current, longestMatchNew.start))
                        diff.copy(longestMatchBase)

                        current = max(pos, longestMatchNew.endInclusive) + 1
                        i = current
                    }
                }
            }
            if (current < newData.size)
                diff.insert(newData.copyOfRange(current, newData.size))
            return diff
        }

        /**
         * Returns the matched range in the base and the new data.
         */
        private fun expandMatch(base: ByteArray, baseMatchPosition: Int, newData: ByteArray, newMatchPosition: Int,
                                current: Int): Pair<IntRange, IntRange> {
            var baseStartIndex = baseMatchPosition
            var baseEndIndex = baseMatchPosition + RABIN_WINDOW - 1
            var newStartIndex = newMatchPosition
            var newEndIndex = newMatchPosition + RABIN_WINDOW - 1
            // search backwards
            for (i in 1 until newMatchPosition - current + 1) {
                val baseIndex = baseMatchPosition - i
                if (baseIndex < 0)
                    break
                val newIndex = newMatchPosition - i
                if (newIndex < 0)
                    break
                if (base[baseIndex] != newData[newIndex])
                    break
                baseStartIndex--
                newStartIndex--
            }
            // search forwards
            for (i in RABIN_WINDOW until newData.size) {
                val baseIndex = baseMatchPosition + i
                if (baseIndex >= base.size)
                    break
                val newIndex = newMatchPosition + i
                if (newIndex >= newData.size)
                    break
                if (base[baseIndex] != newData[newIndex])
                    break
                baseEndIndex++
                newEndIndex++
            }

            return IntRange(baseStartIndex, baseEndIndex) to IntRange(newStartIndex, newEndIndex)
        }

        fun apply(base: ByteArray, diff: BinaryDiff): ByteArray {
            val outStream = ByteArrayOutStream()
            for (operation in diff.getOperations())
                operation.apply(base, outStream)

            return outStream.toByteArray()
        }
    }
}