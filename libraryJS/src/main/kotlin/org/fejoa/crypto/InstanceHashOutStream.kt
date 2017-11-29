package org.fejoa.crypto

actual fun getInstanceHashOutStream(hash: String): AsyncHashOutStream {
    when (hash) {
        "SHA-1" -> return JSAsyncHashOutStream(hash)
        "SHA-256" -> return JSAsyncHashOutStream(hash)
    }

    throw Error("Unsupported hash")
}