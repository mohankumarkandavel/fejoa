package org.fejoa.crypto

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.auth.crypto.CompactPAKE_SHA256_CTR
import org.fejoa.auth.crypto.DH_GROUP
import org.fejoa.support.toUTF
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class CompactPAKETest {
    @Test
    fun testBasics() = runBlocking {
        val secret = CryptoHelper.sha256Hash("3123489723412341324780867621345".toUTF())

        val proverState0 = CompactPAKE_SHA256_CTR.createProver(
                DH_GROUP.RFC5114_2048_256, secret)
        val encGxPair = proverState0.getEncGX()
        val verifier = CompactPAKE_SHA256_CTR.createVerifier(DH_GROUP.RFC5114_2048_256,
                secret, encGxPair.first, encGxPair.second)

        val encGyPair = verifier.getEncGy()
        val proverState1 = proverState0.setVerifierResponse(encGyPair.first,
                encGyPair.second, verifier.getAuthToken())

        assertNotNull(proverState1)
        assertTrue(verifier.verify(proverState1!!.getAuthToken()))
    }
}
