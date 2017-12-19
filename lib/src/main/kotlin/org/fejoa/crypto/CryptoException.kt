package org.fejoa.crypto


class CryptoException : Exception {
    constructor(message: String?) : super(message ?: "")

    constructor(e: Exception) : super(e.message ?: "")
}

