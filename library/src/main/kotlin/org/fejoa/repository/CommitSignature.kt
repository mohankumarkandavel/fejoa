package org.fejoa.repository

import org.fejoa.storage.HashValue

interface CommitSignature {
    suspend fun signMessage(message: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): ByteArray
    suspend fun verifySignedMessage(signedMessage: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): Boolean
}

suspend fun Commit.verify(commitSignature: CommitSignature): Boolean {
    return commitSignature.verifySignedMessage(message, dir.value, parents.map { it.value })
}