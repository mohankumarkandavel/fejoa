package org.fejoa.repository

import org.fejoa.chunkcontainer.ChunkContainerInStream
import org.fejoa.chunkcontainer.Hash
import org.fejoa.chunkcontainer.HashSpec
import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.storage.*
import org.fejoa.support.*

enum class ObjectType(val value: Int) {
    COMMIT_V1(1),
    FLAT_DIR_V1(2)
}

open class ObjectRef(val type: ObjectType, val hash: Hash) {
    companion object {
        suspend fun read(inStream: AsyncInStream): ObjectRef {
            val typeValue = inStream.readByte().toInt()
            val type = ObjectType.values().firstOrNull { it.value == typeValue}
                    ?: throw Exception("Unknown object type $typeValue")
            val hash = Hash.read(inStream)
            return ObjectRef(type, hash)
        }
    }

    suspend fun write(outStream: AsyncOutStream) {
        outStream.writeByte(type.value.toByte())
        hash.write(outStream)
    }
}

class CommitRef(hash: Hash) : ObjectRef(ObjectType.COMMIT_V1, hash) {
    companion object {
        suspend fun read(inStream: AsyncInStream): CommitRef {
            val ref = ObjectRef.read(inStream)
            return when (ref.type) {
                ObjectType.COMMIT_V1 -> CommitRef(ref.hash)
                ObjectType.FLAT_DIR_V1 -> throw Exception("Wrong object type")
            }
        }
    }
}
class DirectoryRef(hash: Hash) : ObjectRef(ObjectType.FLAT_DIR_V1, hash) {
    companion object {
        suspend fun read(inStream: AsyncInStream): DirectoryRef {
            val ref = ObjectRef.read(inStream)
            return when (ref.type) {
                ObjectType.COMMIT_V1 -> throw Exception("Wrong object type")
                ObjectType.FLAT_DIR_V1 -> DirectoryRef(ref.hash)
            }
        }
    }
}

class Commit(var dir: DirectoryRef, val hashSpec: HashSpec = HashSpec(HashSpec.DEFAULT)) {
    // |Directory ObjectRef|
    // [n parents]
    // {list of parent ObjectRefs}
    // {message}

    val parents: MutableList<CommitRef> = ArrayList()
    var message: ByteArray = ByteArray(0)

    companion object {
        suspend fun read(hash: Hash, objectIndex: ObjectIndex): Commit {
            val container = objectIndex.getCommitChunkContainer(hash)
                    ?: throw Exception("Can't find commit ${hash.value}")
            return read(ChunkContainerInStream(container))
        }

        suspend private fun read(inStream: AsyncInStream): Commit {
            val dir = DirectoryRef.read(inStream)
            val nParents = VarInt.read(inStream).first

            val commit = Commit(dir)
            for (i in 0 until nParents) {
                commit.parents += CommitRef.read(inStream)
            }
            commit.message = inStream.readVarIntDelimited().first
            return commit
        }
    }

    suspend fun write(outStream: AsyncOutStream) {
        dir.write(outStream)
        VarInt.write(outStream, parents.size)
        for (parent in parents)
            parent.write(outStream)
        outStream.writeVarIntDelimited(message)
    }

    suspend fun getHash(): Hash {
        return getRef().hash
    }

    suspend fun getRef(): CommitRef {
        val hashOutStream = hashSpec.getHashOutStream()
        val outStream = AsyncHashOutStream(AsyncByteArrayOutStream(), hashOutStream)
        write(outStream)
        outStream.close()
        val hash = Hash(hashSpec, HashValue(hashOutStream.hash()))
        return CommitRef(hash)
    }
}