package org.fejoa.chunkstore;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockBucket {
    private Map<String, WeakReference<Lock>> lockMap = new HashMap<>();

    synchronized public Lock getLock(String id) {
        WeakReference<Lock> weakObject = lockMap.get(id);
        if (weakObject != null) {
            Lock lock = weakObject.get();
            if (lock != null)
                return lock;
        }

        // create new lock
        Lock lock = new ReentrantLock();
        lockMap.put(id, new WeakReference<>(lock));
        return lock;
    }

    static private LockBucket instance;
    synchronized static public LockBucket getInstance() {
        if (instance == null)
            instance = new LockBucket();
        return instance;
    }
}
