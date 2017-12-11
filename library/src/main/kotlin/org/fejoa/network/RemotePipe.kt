package org.fejoa.network

import org.fejoa.support.AsyncCloseable
import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream


interface RemotePipe : AsyncCloseable {
    val outStream: AsyncOutStream
    val inStream: AsyncInStream

    override suspend fun close() {
        outStream.close()
        inStream.close()
    }
}
