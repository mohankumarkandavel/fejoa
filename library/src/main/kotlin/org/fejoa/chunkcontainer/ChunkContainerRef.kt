package org.fejoa.chunkcontainer

import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.storage.*
import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream
import org.fejoa.support.readByte
import org.fejoa.support.write


class ChunkContainerRef private constructor(val hash: Hash, val boxSpec: BoxSpec,
                                            var length: Long = 0, var nLevel: Int = 0,
                                            var boxHash: HashValue = Config.newBoxHash(),
                                            var iv: ByteArray = Config.newIV()) {

    constructor(hashSpec: HashSpec, boxSpec: BoxSpec)
            : this(hash = Hash(hashSpec.clone(), Config.newDataHash()), boxSpec = boxSpec.clone())

    constructor(containerSpec: ContainerSpec)
            : this(hash = Hash(containerSpec.hashSpec.clone(), Config.newDataHash()),
            boxSpec = containerSpec.boxSpec.clone())

    companion object {
        suspend fun read(inStream: AsyncInStream, parent: HashSpec?): ChunkContainerRef {
            val hash = Hash.read(inStream, parent)
            val spec = BoxSpec.read(inStream)
            val length = VarInt.read(inStream).first
            val nLevel = inStream.readByte().toInt()
            val boxHash = Config.newBoxHash()
            inStream.read(boxHash.bytes)
            var iv: ByteArray
            if (!hash.spec.compact) {
                iv = Config.newIV()
                inStream.read(iv)
            } else
                iv = hash.value.bytes.copyOfRange(0, Config.IV_SIZE)
            return ChunkContainerRef(hash, spec, length, nLevel, boxHash, iv)
        }
    }

    // |Hash|
    // |BoxSpec|
    // [data length]
    // |nLevels(1|
    // |box hash (32|
    // |iv (16|
    suspend fun write(outStream: AsyncOutStream): Int {
        var bytesWritten = hash.write(outStream)
        bytesWritten += boxSpec.write(outStream)
        bytesWritten += VarInt.write(outStream, length)
        bytesWritten += outStream.write(nLevel)
        bytesWritten += outStream.write(boxHash.bytes)
        if (!hash.spec.compact)
            bytesWritten += outStream.write(iv)
        return bytesWritten
    }

    val containerSpec: ContainerSpec
        get() = ContainerSpec(hash.spec, boxSpec)

    val chunkRef: ChunkRef
        get() = ChunkRef(hash.value, boxHash, iv, length)

    var dataHash: HashValue
        get() = hash.value
        set(hash) {
            this.hash.value = hash
        }

    override fun equals(o: Any?): Boolean {
        if (o == null)
            return false
        if (o !is ChunkContainerRef)
            return false
        if (dataHash != o.dataHash)
            return false
        return if (!iv.contentEquals(o.iv)) false else boxHash.equals(o.boxHash)
    }

    fun clone(): ChunkContainerRef {
        return ChunkContainerRef(hash.clone(), boxSpec.clone(), length, nLevel, boxHash.clone(), iv.copyOf())
    }
}
