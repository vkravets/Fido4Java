package org.fidonet.nodelist;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A raw file or a single ZIP member; archive filenames are never used as filesystem paths. */
final class DirectoryInput implements AutoCloseable {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private final InputStream content;
    private final ZipInputStream archive;

    private DirectoryInput(InputStream content, ZipInputStream archive) {
        this.content = content;
        this.archive = archive;
    }

    static DirectoryInput open(InputStream raw) throws IOException {
        PushbackInputStream input = new PushbackInputStream(raw, 4);
        byte[] prefix = input.readNBytes(4);
        input.unread(prefix);
        if (prefix.length < 4 || prefix[0] != 'P' || prefix[1] != 'K'
                || !((prefix[2] == 3 && prefix[3] == 4) || (prefix[2] == 5 && prefix[3] == 6))) {
            return new DirectoryInput(input, null);
        }
        ZipInputStream archive = new ZipInputStream(input);
        try {
            if (nextFile(archive) == null) {
                throw new IOException("Directory ZIP archive has no file member");
            }
            return new DirectoryInput(bounded(archive), archive);
        } catch (IOException | RuntimeException e) {
            archive.close();
            throw e;
        }
    }

    InputStream content() {
        return content;
    }

    void finish() throws IOException {
        if (archive != null && nextFile(archive) != null) {
            throw new IOException("Directory ZIP must contain exactly one file; select/unpack a member explicitly");
        }
    }

    private static ZipEntry nextFile(ZipInputStream archive) throws IOException {
        for (int count = 0; count < 1024; count++) {
            ZipEntry entry = archive.getNextEntry();
            if (entry == null || !entry.isDirectory()) {
                return entry;
            }
            if (archive.read() != -1) {
                throw new IOException("ZIP directory entries must not contain data");
            }
        }
        throw new IOException("Too many ZIP directory entries");
    }

    static InputStream bounded(InputStream input) {
        return new FilterInputStream(input) {
            private long count;

            private void consumed(int amount) throws IOException {
                if (amount > 0 && (count += amount) > MAX_BYTES) {
                    throw new IOException("Directory input exceeds 128 MiB");
                }
            }

            @Override
            public int read() throws IOException {
                int value = in.read();
                consumed(value < 0 ? 0 : 1);
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                int amount = in.read(bytes, offset, length);
                consumed(amount);
                return amount;
            }
        };
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
