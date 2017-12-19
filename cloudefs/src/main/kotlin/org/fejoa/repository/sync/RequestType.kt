package org.fejoa.repository.sync

import org.fejoa.support.*


object Request {
    val PROTOCOL_VERSION = 1

    val CS_REQUEST_METHOD = "csRequest"

    // requests
    enum class RequestType(val value: Int) {
        GET_REMOTE_TIP(1),
        GET_CHUNKS(3),
        PUT_CHUNKS(4),
        HAS_CHUNKS(5),
        GET_ALL_CHUNKS(6)
    }

    // errors
    enum class ResultType(val value: Int) {
        PULL_REQUIRED(-2),
        ERROR(-1),
        OK(0),
    }

    suspend fun writeRequestHeader(outStream: AsyncOutStream, request: RequestType) {
        writeRequestHeader(outStream, request.value)
    }

    suspend private fun writeRequestHeader(outStream: AsyncOutStream, request: Int) {
        outStream.writeInt(PROTOCOL_VERSION)
        outStream.writeInt(request)
    }

    suspend fun writeResponseHeader(outStream: AsyncOutStream, request: RequestType, status: ResultType) {
        writeResponseHeader(outStream, request.value, status)
    }

    suspend fun writeResponseHeader(outStream: AsyncOutStream, request: Int, status: ResultType) {
        writeRequestHeader(outStream, request)
        outStream.writeInt(status.value)
    }

    suspend fun receiveRequest(inStream: AsyncInStream): Int {
        val version = inStream.readInt()
        if (version != PROTOCOL_VERSION)
            throw IOException("Version $PROTOCOL_VERSION expected but got:$version")
        return inStream.readInt()
    }

    /**
     * @return chunks written
     */
    suspend fun receiveHeader(inStream: AsyncInStream, expectedRequest: RequestType): Int {
        val request = receiveRequest(inStream)
        if (expectedRequest.value != request)
            throw IOException("Unexpected request: $request but $expectedRequest expected")
        val status = inStream.readInt()
        if (status <= ResultType.ERROR.value) {
            throw IOException("ERROR (request " + expectedRequest + "): "
                    + StreamHelper.readString(inStream, 1024 * 20))
        }
        return status
    }
}
