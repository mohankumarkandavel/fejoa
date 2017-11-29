package org.fejoa.crypto

import org.fejoa.async.await
import org.fejoa.jsbindings.crypto
import org.fejoa.support.ByteArrayOutStream
import kotlin.browser.window


open class JSAsyncHashOutStream : AsyncHashOutStream {
    actual protected constructor() {
        this.algo = "SHA-256"
    }

    constructor(algo: String) {
        this.algo = algo
    }

    val algo: String

    var outStream: ByteArrayOutStream = ByteArrayOutStream()

    override fun reset() {
        outStream = ByteArrayOutStream()
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        outStream.write(buffer, offset, length)
        return length
    }

    override suspend fun hash(): ByteArray {
        return window.crypto().subtle.digest(algo, outStream.toByteArray()).await()
    }
}