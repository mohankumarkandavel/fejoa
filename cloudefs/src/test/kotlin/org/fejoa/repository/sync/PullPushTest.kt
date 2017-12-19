package org.fejoa.repository.sync

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.network.RemotePipe
import org.fejoa.repository.BranchLog
import org.fejoa.repository.RepositoryTestBase
import org.fejoa.repository.ThreeWayMerge
import org.fejoa.repository.chunkIterator
import org.fejoa.support.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class PullPushTest : RepositoryTestBase() {
    private fun connect(handler: RequestHandler): RemotePipe {
        return object : RemotePipe {
            private var backingOutStream: AsyncByteArrayOutStream? = AsyncByteArrayOutStream()
            private var backingInStream: AsyncInStream? = null

            override val outStream: AsyncOutStream = object : AsyncOutStream {
                suspend override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
                    backingInStream = null
                    val outStream = backingOutStream
                            ?: AsyncByteArrayOutStream().also { backingOutStream = it }
                    return outStream.write(buffer, offset, length)
                }
            }

            override val inStream: AsyncInStream = object : AsyncInStream {
                suspend override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val stream = backingInStream ?: run {
                        val inputStream = ByteArrayInStream(backingOutStream!!.toByteArray()).toAsyncInputStream()
                        backingOutStream = null

                        val reply = AsyncByteArrayOutStream()
                        handler.handle(object : RemotePipe {
                            override val inStream: AsyncInStream
                                get() = inputStream

                            override val outStream: AsyncOutStream
                                get() = reply
                        }, AccessRight.ALL)

                        val replyStream = ByteArrayInStream(reply.toByteArray()).toAsyncInputStream()
                        backingInStream = replyStream
                        replyStream
                    }

                    return stream.read(buffer, offset, length)
                }
            }
        }
    }

    @Test
    fun testPull() = runBlocking {
        val branch = "pullBranch"
        val localDir = "PullLocalTest"
        val remoteDir = "PullRemoteTest"

        val requestRepo = createRepo(localDir, branch)
        val remoteRepo = createRepo(remoteDir, branch)

        val handler = RequestHandler(remoteRepo.getCurrentTransaction().getRawAccessor(),
                object : RequestHandler.BranchLogGetter {
                    override fun get(branch: String): BranchLog? {
                        return remoteRepo.log
                    }
                })
        val senderPipe = connect(handler)

        val pullRequest = PullRequest(requestRepo, null)
        var pulledTip = pullRequest.pull(senderPipe, branch, ThreeWayMerge())

        assertTrue(pulledTip == null)

        // change the remote repo
        val testRemoteRepo = TestRepository(remoteRepo)
        testRemoteRepo.putBlob("testFile", "Hello World")
        testRemoteRepo.commit("")
        var remoteHead = remoteRepo.getHead()

        pulledTip = pullRequest.pull(senderPipe, branch, ThreeWayMerge())
        testRemoteRepo.verify(requestRepo)

        assertTrue(pulledTip!! == remoteHead)
        val requestBranchLogEntry = requestRepo.branchLogIO.readFromLog(requestRepo.log.getHead().await()!!.message).head
        assertEquals(pulledTip, requestBranchLogEntry)

        // make another remote
        testRemoteRepo.putBlob("testFile2", "Hello World 2")
        testRemoteRepo.putBlob("sub/testFile", "Hello World 3")
        testRemoteRepo.putBlob("sub/testFile2", "Hello World 4")
        testRemoteRepo.commit("")
        remoteHead = remoteRepo.getHead()

        pulledTip = pullRequest.pull(senderPipe, branch, ThreeWayMerge())
        testRemoteRepo.verify(requestRepo)

        assertTrue(pulledTip!! == remoteHead)
        assertEquals(pulledTip, requestRepo.branchLogIO.readFromLog(requestRepo.log.getHead().await()!!.message).head)
    }

    @Test
    fun testPush() = runBlocking {
        val branch = "pushBranch"
        val localDir = "PushLocalTest"
        val remoteDir = "PushRemoteTest"

        val localRepo = createRepo(localDir, branch)
        var remoteRepo = createRepo(remoteDir, branch)

        val handler = RequestHandler(remoteRepo.getCurrentTransaction().getRawAccessor(),
                object : RequestHandler.BranchLogGetter {
                    override fun get(branch: String): BranchLog? {
                        return remoteRepo.log
                    }
                })
        val senderPipe = connect(handler)

        val testLocalRepo = TestRepository(localRepo)
        // change the local repo
        testLocalRepo.putBlob("testFile", "Hello World!")
        testLocalRepo.commit("Initial commit")

        val localTransaction = localRepo.getCurrentTransaction()
        val pushRequest = PushRequest(localRepo)
        // push changes
        pushRequest.push(senderPipe, localTransaction, branch)
        // verify
        var remoteHeadRef = remoteRepo.branchLogIO.readFromLog(remoteRepo.log.getHead().await()!!.message)
        remoteRepo = openRepo(remoteDir, branch, remoteHeadRef)
        testLocalRepo.verify(remoteRepo)

        // add more
        testLocalRepo.putBlob("testFile2", "Hello World 2")
        testLocalRepo.putBlob("sub/testFile3", "Hello World 3")
        testLocalRepo.putBlob("sub/sub2/testFile4", "Hello World 4")
        testLocalRepo.commit("Commit 1")

        testLocalRepo.putBlob("sub/sub2/testFile4", "Hello World 5")
        testLocalRepo.commit("Commit 2")

        // push changes
        pushRequest.push(senderPipe, localTransaction, branch)
        // verify
        remoteHeadRef = remoteRepo.branchLogIO.readFromLog(remoteRepo.log.getHead().await()!!.message)
        remoteRepo = openRepo(remoteDir, branch, remoteHeadRef)

        testLocalRepo.verify(remoteRepo)
    }
}

