package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Owns a catalog and periodically refreshes configured files under the mail processing lock. */
public final class DirectoryService implements MailProcessingExtension {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryService.class);
    private final DirectoryCatalog catalog;
    private final List<DirectorySource> sources;
    private final long interval;
    private long lastAttempt;

    private DirectoryService(DirectoryCatalog catalog,
                             List<DirectorySource> sources,
                             long seconds) {
        this.catalog = catalog;
        this.sources = List.copyOf(sources);
        interval = TimeUnit.SECONDS.toNanos(seconds);
    }

    /** Explicit operator API; does not consult the automatic plugin enable switch. */
    public static DirectoryService open(IConfig config) throws IOException {
        List<String> values = config.getValuesAsList("DirectorySource");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Configure at least one DirectorySource");
        }
        List<DirectorySource> sources = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String value : values) {
            DirectorySource source = DirectorySource.parse(value);
            if (!names.add(source.getId())) {
                throw new IllegalArgumentException("Duplicate directory source ID: "
                                                   + source.getId());
            }
            sources.add(source);
        }
        long seconds = Long.parseLong(config.getValue("DirectoryRefreshSeconds", "300"));
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException("DirectoryRefreshSeconds must be 1..86400");
        }
        return new DirectoryService(new DirectoryCatalog(Path.of(config.getValue("NodeDirectoryPath",
                                                                                 "./database/nodelist/catalog"))),
                                    sources,
                                    seconds);
    }

    public DirectoryCatalog getCatalog() {
        return catalog;
    }

    /** Each source is independent: valid sources are updated even if another one fails. */
    public synchronized List<DirectoryImportResult> refresh() throws IOException {
        lastAttempt = System.nanoTime();
        IOException failure = null;
        List<DirectoryImportResult> result = new ArrayList<>();
        for (DirectorySource source : sources) {
            try {
                DirectoryImportResult imported = catalog.importSource(source);
                result.add(imported);
                if (imported.isChanged()) {
                    logger.info("Imported directory {}: {} records",
                                source.getId(),
                                imported.getRecords());
                }
            } catch (IOException e) {
                if (failure == null) {
                    failure = new IOException("One or more directory sources could not be imported");
                }
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return List.copyOf(result);
    }

    @Override
    public synchronized void beforeExport(Tosser tosser) {
        if (System.nanoTime() - lastAttempt < interval) {
            return;
        }
        try {
            refresh();
        } catch (IOException e) {
            logger.error("Directory update failed; previous source snapshots were retained", e);
        }
    }

    @Override
    public void afterExport() {
    }

    @Override
    public void close() {
        catalog.close();
    }
}
