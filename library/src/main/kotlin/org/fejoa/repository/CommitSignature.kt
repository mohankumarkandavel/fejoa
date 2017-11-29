package org.fejoa.repository

import org.fejoa.storage.HashValue

interface CommitSignature {
    fun signMessage(message: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): ByteArray
    fun verifySignedMessage(signedMessage: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): Boolean
}
