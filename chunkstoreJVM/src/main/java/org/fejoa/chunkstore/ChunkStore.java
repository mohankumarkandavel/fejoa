package org.fejoa.chunkstore;

import org.fejoa.storage.HashValue;
import org.fejoa.storage.PutResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;


interface IChunkStoreEngine {
    void create(File dir, String name) throws IOException;
    void open(File dir, String name) throws IOException;

    long size();
    ChunkStore.IChunkStoreIterator iterator() throws IOException;
    byte[] getChunk(byte[] hash) throws IOException;
    PutResult<HashValue> put(byte[] data) throws IOException;
    boolean contains(byte[] hash) throws IOException;
    void startNewTransaction();
    void commit() throws IOException;
    void cancel();
}

class SimpleChunkStoreEngine implements IChunkStoreEngine {
    public class ChunkStoreIterator implements ChunkStore.IChunkStoreIterator {
        final private Iterator<BPlusTree.Entry<Long>> iterator;

        ChunkStoreIterator(Iterator<BPlusTree.Entry<Long>> iterator) {
            this.iterator = iterator;

            lock();
        }

        public void unlock() {
            SimpleChunkStoreEngine.this.unlock();
        }

        @Override
        protected void finalize() throws Throwable {
            unlock();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public void remove() {

        }

        @Override
        public ChunkStore.Entry next() {
            BPlusTree.Entry<Long> next = iterator.next();
            Long position = next.data;
            byte[] chunk;
            try {
                chunk = packFile.get(position.intValue(), next.key);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return new ChunkStore.Entry(new HashValue(next.key), chunk);
        }
    }

    final private BPlusTree tree;
    final private PackFile packFile;
    final private Lock lock;
    private MessageDigest messageDigest;

    public SimpleChunkStoreEngine(File dir, String name) throws FileNotFoundException {
        this.tree = new BPlusTree(new RandomAccessFile(new File(dir, name +".idx"), "rw"));
        this.packFile = new PackFile(new RandomAccessFile(new File(dir, name + ".pack"), "rw"));
        this.lock = LockBucket.getInstance().getLock(new File(dir, name).getPath());
        try {
            this.messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private void lock() {
        lock.lock();
    }

    private void unlock() {
        lock.unlock();
    }

    @Override
    public void create(File dir, String name) throws IOException {
        try {
            lock();
            tree.create(ChunkStore.hashSize(), 1024);
            packFile.create(ChunkStore.hashSize());
        } finally {
            unlock();
        }
    }

    @Override
    public void open(File dir, String name) throws IOException {
        try {
            lock();
            tree.open();
            packFile.open();
        } finally {
            unlock();
        }
    }

    @Override
    public long size() {
        try {
            lock();
            return tree.size();
        } finally {
            unlock();
        }
    }

    @Override
    public ChunkStore.IChunkStoreIterator iterator() throws IOException {
        return new ChunkStoreIterator(tree.iterator());
    }

    @Override
    public byte[] getChunk(byte[] hash) throws IOException {
        try {
            lock();
            Long position = tree.get(hash);
            if (position == null)
                return null;
            return packFile.get(position.intValue(), hash);
        } finally {
            unlock();
        }
    }

    @Override
    public PutResult<HashValue> put(byte[] data) throws IOException {
        try {
            lock();
            // make this configurable
            messageDigest.reset();
            HashValue hash = new HashValue(messageDigest.digest(data));
            // TODO make it more efficient by only using one lookup
            if (tree.get(hash.getBytes()) != null)
                return new PutResult<>(hash, true);
            long position = packFile.put(hash, data);
            boolean wasInDatabase = !tree.put(hash, position);
            PutResult<HashValue> putResult = new PutResult<>(hash, wasInDatabase);
            return putResult;
        } finally {
            unlock();
        }
    }

    @Override
    public boolean contains(byte[] hash) throws IOException {
        try {
            lock();
            return tree.get(hash) != null;
        } finally {
            unlock();
        }
    }

    /**
     * TODO: make the transaction actually do something, i.e. make a transaction atomic
     */
    @Override
    public void startNewTransaction() {

    }

    @Override
    public void commit() throws IOException {

    }

    @Override
    public void cancel() {

    }
}

public class ChunkStore {
    public class Transaction {
        public long size() {
            return ChunkStore.this.size();
        }

        public IChunkStoreIterator iterator() throws IOException {
            return ChunkStore.this.iterator();
        }

        public byte[] getChunk(HashValue hash) throws IOException {
            return ChunkStore.this.getChunk(hash);
        }

        public PutResult<HashValue> put(byte[] data) throws IOException {
            return ChunkStore.this.put(data);
        }

        public boolean contains(HashValue hash) throws IOException {
            return ChunkStore.this.hasChunk(hash);
        }

        public void commit() throws IOException {
            ChunkStore.this.commit();
        }

        public void cancel() {
            ChunkStore.this.cancel();
        }
    }

    static class DatabaseBucket {
        private Map<String, WeakReference<IChunkStoreEngine>> map = new HashMap<>();

        synchronized public IChunkStoreEngine getDB(File dir, String name) throws FileNotFoundException {
            String id = new File(dir, name).getPath();
            WeakReference<IChunkStoreEngine> weakObject = map.get(id);
            if (weakObject != null) {
                IChunkStoreEngine db = weakObject.get();
                if (db != null)
                    return db;
            }

            // create new db
            IChunkStoreEngine engine = new SimpleChunkStoreEngine(dir, name);
            map.put(id, new WeakReference<>(engine));
            return engine;
        }
    }

    final static protected DatabaseBucket databaseBucket = new DatabaseBucket();
    final private IChunkStoreEngine db;
    private Transaction currentTransaction;

    protected ChunkStore(File dir, String name) throws FileNotFoundException {
        this.db = databaseBucket.getDB(dir, name);
    }

    static public ChunkStore create(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.db.create(dir, name);
        return chunkStore;
    }

    static public ChunkStore open(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.db.open(dir, name);
        return chunkStore;
    }

    static public boolean exists(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null)
            return false;
        for (File child : children) {
            if (!child.isFile())
                continue;
            if (child.getName().startsWith(name))
                return true;
        }
        return false;
    }

    public byte[] getChunk(HashValue hash) throws IOException {
        return getChunk(hash.getBytes());
    }

    public byte[] getChunk(byte[] hash) throws IOException {
        return db.getChunk(hash);
    }

    public long size() {
        return db.size();
    }

    static public class Entry {
        final public HashValue key;
        final public byte[] data;

        public Entry(HashValue key, byte[] data) {
            this.key = key;
            this.data = data;
        }
    }

    public interface IChunkStoreIterator extends Iterator<Entry> {
        void unlock();
    }

    public IChunkStoreIterator iterator() throws IOException {
        return db.iterator();
    }

    public boolean hasChunk(HashValue hashValue) throws IOException {
        return db.contains(hashValue.getBytes());
    }

    // TODO rename to getCurrentTransaction?
    public Transaction openTransaction() throws IOException {
        synchronized (this) {
            if (currentTransaction != null)
                return currentTransaction;
            currentTransaction = new Transaction();
            db.startNewTransaction();
            return currentTransaction;
        }
    }

    private void commit() throws IOException {
        synchronized (this) {
            db.commit();
            currentTransaction = null;
        }
    }

    private void cancel() {
        synchronized (this) {
            db.cancel();
            currentTransaction = null;
        }
    }

    private PutResult<HashValue> put(byte[] data) throws IOException {
        return db.put(data);
    }

    static public int hashSize() {
        return 32;
    }

}
