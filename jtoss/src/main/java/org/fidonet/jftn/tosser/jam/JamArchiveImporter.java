package org.fidonet.jftn.tosser.jam;

import org.fidonet.echobase.ArchiveStore;
import org.fidonet.echobase.jam.JamArea;

import java.io.IOException;

/** Explicit historical import. Never submits mail and never modifies the source JAM. */
public final class JamArchiveImporter {
    private JamArchiveImporter() {
    }

    public static Result importInto(JamArea source, ArchiveStore target) throws IOException {
        int[] counts = new int[2];
        source.visitReadOnly(entry -> {
            if (target.importArchive(entry.getMessage(), source.getPath().toString())) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        });
        return new Result(counts[0], counts[1]);
    }

    public static final class Result {
        private final int imported;
        private final int duplicates;

        private Result(int imported, int duplicates) {
            this.imported = imported;
            this.duplicates = duplicates;
        }

        public int getImported() {
            return imported;
        }

        public int getDuplicates() {
            return duplicates;
        }
    }
}
