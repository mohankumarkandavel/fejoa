package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.AccountIO
import org.fejoa.FejoaContext
import org.fejoa.network.PullJob
import org.fejoa.network.PushJob
import org.fejoa.network.platformCreateHTMLRequest
import org.fejoa.repository.Repository
import org.fejoa.repository.ThreeWayMerge
import org.fejoa.repository.sync.Request
import org.fejoa.support.NowExecutor
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.ArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class SyncTest {
    companion object {
        internal val TEST_DIR = "jettySyncTest"
        internal val SERVER_TEST_DIR = TEST_DIR + "/Server"
    }

    internal val cleanUpDirs: MutableList<String> = ArrayList()
    internal var server: JettyServer? = null
    val url = "http://localhost:${JettyServer.DEFAULT_PORT}/"

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
    fun testPushPull() = runBlocking {
        val serverUser = "user1"
        val localCSDir = TEST_DIR + "/ClientStore"
        val branch = "testBranch"

        // push
        val localContext = FejoaContext(AccountIO.Type.CLIENT, localCSDir, "clientuser1", NowExecutor())
        val local = localContext.getStorage(branch, null)
        local.writeString("testFile", "testData")
        local.commit()

        val request = platformCreateHTMLRequest(url)
        val result = PushJob(local.getBackingDatabase() as Repository, serverUser, branch).run(request)

        assertEquals(Request.ResultType.OK, result.result)

        val localContext2 = FejoaContext(AccountIO.Type.CLIENT, localCSDir, "clientuser2", NowExecutor())
        val local2 = localContext2.getStorage(branch, null)

        val pullResult = PullJob(local2.getBackingDatabase() as Repository, null, ThreeWayMerge(),
                serverUser, branch).run(request)

        assertTrue(pullResult.oldHead.value.isZero)
        assertEquals(local.getHead(), pullResult.remoteHead)
        assertEquals(local.getHead(), local2.getHead())
    }

}