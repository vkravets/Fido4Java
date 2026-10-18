package org.fidonet.jftn.script;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/** Node-owned catalog API. Scripts select deliveries, without owning catalogs or source definitions. */
public interface DirectoryImportService {
    interface Result {
        String getSource();

        long getRecords();

        boolean isChanged();
    }

    boolean hasSource(String id);

    void claimDeliverySource(String id);

    Path sourcePath(String id);

    String sourceDefinition(String id);

    LocalDate publicationDate(Path file) throws IOException;

    Result importFile(String id, Path incoming, boolean diff) throws IOException;
}
