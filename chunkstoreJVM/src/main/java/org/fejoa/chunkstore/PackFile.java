package org.fejoa.chunkstore;

import org.fejoa.storage.HashValue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;


public class PackFile {
    private boolean isOpen;
    private short version;
    private short hashSize;
    final private RandomAccessFile file;

    public PackFile(RandomAccessFile file) {
        this.file = file;
        reset();
    }

    private void reset() {
        isOpen = false;
        version = 1;
    }

    public void create(int hashSize) throws IOException {
        reset();

        this.hashSize = (short)hashSize;
        this.isOpen = true;

        file.setLength(0);
        writeHeader();
    }

    public void open() throws IOException {
        if (isOpen)
            return;
        readHeader();
    }

    private void readHeader() throws IOException {
        file.seek(0);
        version = file.readShort();
        hashSize = file.readShort();
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeShort(version);
        file.writeShort(hashSize);
    }

    private long headerSize() {
        return 2 * 4;
    }

    public long put(HashValue hash, byte[] data) throws IOException {
        long position = file.length();
        file.seek(position);
        file.write(hash.getBytes());
        file.writeInt(data.length);
        file.write(data);
        return position;
    }

    public byte[] get(int position, byte[] expectedHash) throws IOException {
        file.seek(position);
        byte[] hash = new byte[hashSize];
        file.readFully(hash);
        if (expectedHash != null && !Arrays.equals(expectedHash, hash))
            throw new IOException("Unexpected chunk at position " + position);

        int length = file.readInt();
        byte[] data = new byte[length];
        file.readFully(data);
        return data;
    }
}
