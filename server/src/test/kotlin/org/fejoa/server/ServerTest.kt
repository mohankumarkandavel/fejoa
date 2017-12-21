package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.LoginParams
import org.fejoa.crypto.*
import org.fejoa.network.*
import org.fejoa.platformReadLoginData

import java.io.File
import java.util.ArrayList

import org.fejoa.server.JettyServer.Companion.DEFAULT_PORT
import org.fejoa.support.encodeBase64
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals


class JettyTest {
    companion object {
        internal val TEST_DIR = "jettyTest"
        internal val SERVER_TEST_DIR = TEST_DIR + "/Server"
    }

    internal val cleanUpDirs: MutableList<String> = ArrayList()
    internal var server: JettyServer? = null
    val url = "http://localhost:$DEFAULT_PORT/"

    @Before
    fun setUp() {
        cleanUpDirs.add(TEST_DIR)

        server = JettyServer(SERVER_TEST_DIR)
        server!!.setDebugNoAccessControl(true)
        server!!.start()
    }

    @After
    fun tearDown() {
        Thread.sleep(1000)
        server!!.stop()

        for (dir in cleanUpDirs)
            File(dir).deleteRecursively()
    }

    @Test
    fun testSimple() = runBlocking {
        val request = platformCreateHTMLRequest(url)
        val result = PingJob().run(request)

        assertEquals("ping pong", result.headerResponse)
        assertEquals("PING PONG", result.dataResponse)
    }

    @Test
    fun testRegistrationAndAuth() = runBlocking {
        val user = "testUser"
        val password = "password"

        val keyCache = BaseKeyCache()
        val userKeyParams = UserKeyParams(BaseKeyParams(salt = CryptoHelper.crypto.generateSalt16(),
                kdf = CryptoSettings.default.kdf),
                CryptoSettings.HASH_TYPE.SHA256, CryptoSettings.KEY_TYPE.AES, CryptoHelper.crypto.generateSalt16())
        val secret = keyCache.getUserKey(userKeyParams, password)

        val request = platformCreateHTMLRequest(url)
        val authParams = LoginParams(userKeyParams,
                CompactPAKE_SHA256_CTR.getSharedSecret(DH_GROUP.RFC5114_2048_256, secret),
                DH_GROUP.RFC5114_2048_256)

        val registerResult = RegisterJob(user, authParams).run(request)
        assertEquals(ReturnType.OK, registerResult.code)

        val readAuthParams = platformReadLoginData(SERVER_TEST_DIR, user)
        assertEquals(authParams, readAuthParams)

        // assert we are not logged in
        assertEquals(0, AuthStatusJob().run(request).accounts.size)

        val failedAuthResult = LoginJob(user, "wrong password", keyCache).run(request)
        assertEquals(ReturnType.ERROR, failedAuthResult.code)
        assertEquals(0, AuthStatusJob().run(request).accounts.size)

        val authResult = LoginJob(user, password, keyCache).run(request)
        assertEquals(ReturnType.OK, authResult.code)
        assertEquals(1, AuthStatusJob().run(request).accounts.size)
        assertEquals(user, AuthStatusJob().run(request).accounts[0])
    }
}

