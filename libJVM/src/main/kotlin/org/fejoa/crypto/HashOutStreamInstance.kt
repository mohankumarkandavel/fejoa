package org.fejoa.crypto

actual fun getInstanceHashOutStream(hash: String): AsyncHashOutStream {
    return JVMAsyncHashOutStream(java.security.MessageDigest.getInstance(hash))
}
