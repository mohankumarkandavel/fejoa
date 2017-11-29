package org.fejoa.chunkstore;

import junit.framework.TestCase;
import org.fejoa.storage.HashValue;
import org.fejoa.storage.PutResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChunkStoreTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            BPlusTreeTest.recursiveDeleteFile(new File(dir));
    }

    public void testSimple() throws Exception {
        String dirName = "testDir";
        File dir = new File(dirName);
        dir.mkdirs();
        cleanUpFiles.add(dirName);

        ChunkStore chunkStore = ChunkStore.create(dir, "test");
        byte[] data1 = "Hello".getBytes();
        byte[] data2 = "Test Data".getBytes();
        ChunkStore.Transaction transaction = chunkStore.openTransaction();
        PutResult<HashValue> result1 = transaction.put(data1);
        PutResult<HashValue> result2 = transaction.put(data2);
        transaction.commit();

        assertEquals(new String(data1), new String(chunkStore.getChunk(result1.getKey())));
        assertEquals(new String(data2), new String(chunkStore.getChunk(result2.getKey())));
    }
}
