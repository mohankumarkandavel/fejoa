package org.fejoa.chunkstore;

import java.io.RandomAccessFile;


public class BPlusTree extends BaseBPlusTree<Long, Long> {
    public BPlusTree(RandomAccessFile file) {
        super(file, new LongType(), new LongType());
    }
}
