package org.fejoa.crypto


class SHA256Factory : HashOutStreamFactory {
    override fun create(): AsyncHashOutStream {
        return getInstanceHashOutStream("SHA-256")
    }
}


interface HashOutStreamFactory {
    fun create(): AsyncHashOutStream
}
