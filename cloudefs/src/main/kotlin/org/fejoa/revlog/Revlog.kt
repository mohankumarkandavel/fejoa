package org.fejoa.revlog

import org.fejoa.binarydiff.BinaryDiff
import org.fejoa.binarydiff.TichyDiff
import org.fejoa.chunkcontainer.ChunkContainer
import org.fejoa.chunkcontainer.ChunkContainerInStream
import org.fejoa.chunkcontainer.ChunkContainerOutStream
import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream
import org.fejoa.storage.RandomDataAccess
import org.fejoa.support.readFully
import org.fejoa.support.ByteArrayInStream
import org.fejoa.support.ByteArrayOutStream
import org.fejoa.support.IOException


class Revlog(val chunkContainer: ChunkContainer) {
    /**
     * Entry in the revlog
     *
     * Format:
     * 1) VarInt storing the entry size s and the entry opcode as extra
     * 2) Entry data of size s
     */
    private interface Entry {
        suspend fun write(outStream: AsyncOutStream)
        /**
         * @return the content and number of diffs that have been applied
         */
        suspend fun readContent(chunkContainer: ChunkContainer): Pair<ByteArray, Int>
    }

    class BaseEntry(val data: ByteArray) : Entry {
        companion object {
            suspend fun read(inStream: AsyncInStream, size: Int): BaseEntry {
                return BaseEntry(inStream.readFully(size))
            }
        }

        suspend override fun write(outStream: AsyncOutStream) {
            VarInt.write(outStream, data.size.toLong(), EntryType.BASE.opCode, 1)
            outStream.write(data)
        }

        suspend override fun readContent(chunkContainer: ChunkContainer): Pair<ByteArray, Int> {
            return data to 0
        }
    }

    class DeltaEntry(val basePosition: Long, val diff: ByteArray) : Entry {
        companion object {
            suspend fun read(inStream: AsyncInStream, entrySize: Int): DeltaEntry {
                val basePosition = VarInt.read(inStream)
                val diff = inStream.readFully(entrySize - basePosition.second)

                return DeltaEntry(basePosition.first, diff)
            }
        }

        suspend override fun write(outStream: AsyncOutStream) {
            // test how much space the basePosition while take
            val basePositionSize = VarInt.write(ByteArrayOutStream(), basePosition)
            val entrySize = basePositionSize + diff.size

            VarInt.write(outStream, entrySize.toLong(), EntryType.DELTA.opCode, 1)
            VarInt.write(outStream, basePosition)
            outStream.write(diff)
        }

        override suspend fun readContent(chunkContainer: ChunkContainer): Pair<ByteArray, Int> {
            val inputStream = ChunkContainerInStream(chunkContainer)
            inputStream.seek(chunkContainer.getDataLength() - 1 - basePosition)
            val baseEntry = Revlog.readEntry(inputStream)
            val base = baseEntry.readContent(chunkContainer)

            // apply diff to base
            val binaryDiff = BinaryDiff.unpack(ByteArrayInStream(diff))
            val newVersion = TichyDiff.apply(base.first, binaryDiff)
            return newVersion to 1 + base.second
        }
    }

    enum class EntryType(val opCode: Int) {
        BASE(0),
        DELTA(1)
    }

    companion object {
        suspend private fun readEntryHeader(inStream: AsyncInStream): Triple<Long, Int, Int> {
            return VarInt.read(inStream, 1)
        }

        suspend private fun readEntry(inStream: AsyncInStream): Entry {
            val header = readEntryHeader(inStream)
            val opCode = header.third
            val size = header.first.toInt()
            return when (opCode) {
                EntryType.BASE.opCode -> BaseEntry.read(inStream, size)
                EntryType.DELTA.opCode -> DeltaEntry.read(inStream, size)
                else -> throw IOException("Invalid entry opcode")
            }
        }
    }

    /**
     * @param maxEntrySize maximal size of an unpacked version
     * @param maxDeltas maximal number of deltas that needs to be applied to unpack any version
     * @param minDiffSaving minimal saving in bytes in order to store a diff
     */
    class Policy(val maxDeltas: Int = 10, val minDiffSaving: Int = 4)

    var policy = Policy()

    /**
     * @return the position from which the version can be accessed
     */
    suspend private fun write(entry: Entry): Long {
        val outStream = ChunkContainerOutStream(chunkContainer, RandomDataAccess.Mode.INSERT)
        outStream.seek(0)
        entry.write(outStream)
        outStream.close()
        return chunkContainer.getDataLength() - 1
    }

    /**
     * @param basePosition -1 means to simply write the newVersion without diff
     * @return the position from which the version can be accessed
     */
    suspend fun add(newVersion: ByteArray, basePosition: Long = chunkContainer.getDataLength() - 1): Long {
        if (basePosition < 0 || chunkContainer.getDataLength() == 0L) {
            // write the full newVersion
            return write(BaseEntry(newVersion))
        }

        // diff
        val inputStream = ChunkContainerInStream(chunkContainer)
        inputStream.seek(chunkContainer.getDataLength() -1 - basePosition)
        val base = readEntry(inputStream).readContent(chunkContainer)
        if (base.second >= policy.maxDeltas) {
            return write(BaseEntry(newVersion))
        }

        val diff = TichyDiff.diff(base.first, newVersion)
        val buffer = ByteArrayOutStream()
        diff.pack(buffer)
        val rawDiff = buffer.toByteArray()
        if (rawDiff.size >= newVersion.size - policy.minDiffSaving) {
            // no point in writing the diff; write the full version
            return write(BaseEntry(newVersion))
        }

        return write(DeltaEntry(basePosition, rawDiff))
    }

    /**
     * @param position entry position counted from the back of the chunk container
     * @return the data and the number of deltas that needed to be applied to restore this version
     */
    suspend fun get(position: Long): ByteArray {
        if (position <= 0)
            throw IllegalArgumentException()

        val inputStream = ChunkContainerInStream(chunkContainer)
        inputStream.seek(chunkContainer.getDataLength() - 1 - position)
        return readEntry(inputStream).readContent(chunkContainer).first
    }

    /**
     * Debug method.
     *
     * @return information about all entries, i.e. the entry type and the size of the entry
     */
    suspend fun inventory(): List<Pair<EntryType, Int>> {
        val out: MutableList<Pair<EntryType, Int>> = ArrayList()
        var pos = 0
        val inputStream = ChunkContainerInStream(chunkContainer)
        while (pos < chunkContainer.getDataLength()) {
            val header = readEntryHeader(inputStream)
            val opCode = header.third
            val size = header.first.toInt()

            var type = when (opCode) {
                EntryType.BASE.opCode -> EntryType.BASE
                EntryType.DELTA.opCode -> EntryType.DELTA
                else -> throw IOException("Invalid entry opcode")
            }
            out.add(type to size)

            pos += header.second + size
            inputStream.seek(pos.toLong())
        }
        return out
    }
}