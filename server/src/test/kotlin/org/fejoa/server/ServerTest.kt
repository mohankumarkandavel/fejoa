package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.network.PingJob
import org.fejoa.network.RemoteJob
import org.fejoa.network.platformCreateHTMLRequest

import java.io.File
import java.util.ArrayList

import org.fejoa.server.JettyServer.Companion.DEFAULT_PORT
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
        val pingJob = PingJob()
        val result = RemoteJob.run(pingJob, request)

        assertEquals("ping pong", result.headerResponse)
        assertEquals("PING PONG", result.dataResponse)
    }

}
