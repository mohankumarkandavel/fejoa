package org.fejoa.repository

import org.fejoa.chunkcontainer.ChunkContainerInStream
import org.fejoa.chunkcontainer.Hash
import org.fejoa.chunkcontainer.HashSpec
import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.storage.*
import org.fejoa.support.*


class Commit constructor(var dir: Hash, private val hash: Hash) {
    // |type (1|
    // |Directory ObjectRef|
    // [n parents]
    // {list of parent ObjectRefs}
    // {message}

    val parents: MutableList<Hash> = ArrayList()
    var message: ByteArray = ByteArray(0)

    enum class CommitType(val value: Int) {
        COMMIT_V1(1)
    }

    companion object {
        suspend fun read(hash: Hash, objectIndex: ObjectIndex): Commit {
            val container = objectIndex.getCommit(hash)
                    ?: throw Exception("Can't find commit ${hash.value}")
            return read(ChunkContainerInStream(container), hash.spec.createChild(), hash)
        }

        suspend private fun read(inStream: AsyncInStream, parent: HashSpec, expectedHash: Hash): Commit {
            val type = inStream.readByte().toInt()
            if (type != CommitType.COMMIT_V1.value)
                throw Exception("Unexpected commit type; $type")

            val dir = Hash.read(inStream, parent)
            val nParents = VarInt.read(inStream).first

            val commit = Commit(dir, Hash.createChild(parent))
            for (i in 0 until nParents) {
                commit.parents += Hash.read(inStream, parent)
            }
            commit.message = inStream.readVarIntDelimited().first
            if (commit.getHash() != expectedHash)
                throw Exception("Expected hash: ${expectedHash.value}, read hash: ${commit.getHash()}")
            return commit
        }
    }

    suspend fun write(outStream: AsyncOutStream) {
        outStream.write(CommitType.COMMIT_V1.value)
        dir.write(outStream)
        VarInt.write(outStream, parents.size)
        for (parent in parents)
            parent.write(outStream)
        outStream.writeVarIntDelimited(message)
    }

    suspend fun getHash(): Hash {
        val hashOutStream = hash.spec.getHashOutStream()
        val outStream = AsyncHashOutStream(AsyncByteArrayOutStream(), hashOutStream)
        write(outStream)
        outStream.close()
        hash.value = HashValue(hashOutStream.hash())
        return hash
    }
}