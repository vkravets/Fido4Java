package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.config.StorageConfiguration;
import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.plugins.ui.SettingsExtensionProvider;
import org.fidonet.jftn.script.DirectoryImportService;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Owns a catalog and periodically refreshes configured files under the mail processing lock. */
public final class DirectoryService
        implements
            MailProcessingExtension,
            DirectoryImportService,
            SettingsExtensionProvider {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryService.class);
    private final DirectoryCatalog catalog;
    private List<DirectorySource> sources;
    private final long interval;
    private final boolean refreshEnabled;
    private volatile Map<String, DirectorySource> registry;
    private final List<DirectorySource> localSources;
    private final Set<String> deliverySources = new HashSet<>();
    private DirectoryChecksumExceptions checksumExceptions;
    private final SettingsExtension settings;
    private long lastAttempt;

    private DirectoryService(DirectoryCatalog catalog,
                             List<DirectorySource> sources,
                             long seconds,
                             boolean refreshEnabled,
                             Set<String> localIds,
                             DirectoryChecksumExceptions checksumExceptions,
                             IConfig config,
                             DirectoryConfiguration crcFile) {
        this.catalog = catalog;
        this.sources = List.copyOf(sources);
        interval = TimeUnit.SECONDS.toNanos(seconds);
        this.refreshEnabled = refreshEnabled;
        this.checksumExceptions = checksumExceptions;
        settings = new DirectorySettings(this, config, crcFile);
        Map<String, DirectorySource> definitions = new LinkedHashMap<>();
        sources.forEach(source -> definitions.put(source.getId(), source));
        registry = Map.copyOf(definitions);
        localSources = new ArrayList<>();
        for (String id : localIds) {
            localSources.add(source(id));
        }
    }

    /** Explicit operator API; does not consult the automatic plugin enable switch. */
    public static DirectoryService open(IConfig config) throws IOException {
        List<String> values = config.getValuesAsList("DirectorySource");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Configure at least one DirectorySource");
        }
        List<DirectorySource> sources = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Path> paths = new HashSet<>();
        for (String value : values) {
            DirectorySource source = DirectorySource.parse(value);
            if (!names.add(source.getId())) {
                throw new IllegalArgumentException("Duplicate directory source ID: "
                                                   + source.getId());
            }
            if (!paths.add(source.getPath())) {
                throw new IllegalArgumentException("Directory sources must have unique base paths");
            }
            sources.add(source);
        }
        long seconds = Long.parseLong(config.getValue("DirectoryRefreshSeconds", "300"));
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException("DirectoryRefreshSeconds must be 1..86400");
        }
        List<String> configuredLocal = config.getValuesAsList("DirectoryLocalSource");
        Set<String> localIds = new HashSet<>();
        if (configuredLocal != null) {
            for (String id : configuredLocal) {
                if (!names.contains(id) || !localIds.add(id)) {
                    throw new IllegalArgumentException("Unknown or duplicate DirectoryLocalSource: "
                                                       + id);
                }
            }
        }
        boolean refreshEnabled = flag(config, "DirectoryRefreshEnabled", false);
        if (refreshEnabled && localIds.isEmpty()) {
            throw new IllegalArgumentException("DirectoryRefreshEnabled requires DirectoryLocalSource IDs");
        }
        for (String key : List.of("DirectoryFeed", "DirectoryInboundFeed")) {
            List<String> feeds = config.getValuesAsList(key);
            if (feeds == null)
                continue;
            for (String feed : feeds) {
                String id = feed.split("\\|", 2)[0].trim();
                if (localIds.contains(id)) {
                    throw new IllegalArgumentException("Directory source is both local and delivery-managed: "
                            + id);
                }
            }
        }
        DirectoryChecksumExceptions checksumExceptions = new DirectoryChecksumExceptions(config,
                                                                                         names);
        DirectoryConfiguration crcFile = new DirectoryConfiguration(config);
        return new DirectoryService(new DirectoryCatalog(StorageConfiguration.open(config,
                                                                                   "directory",
                                                                                   Path.of(config.getValue("NodeDirectoryPath",
                                                                                                           "./database/nodelist/catalog")))),
                                    sources,
                                    seconds,
                                    refreshEnabled,
                                    localIds,
                                    checksumExceptions,
                                    config,
                                    crcFile);
    }

    static boolean flag(IConfig config, String key, boolean fallback) {
        String value = config.getValue(key, Boolean.toString(fallback)).trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public List<SettingsExtension> getSettingsExtensions() {
        return List.of(settings);
    }

    List<String> sourceIds() {
        return registry.keySet().stream().sorted().collect(Collectors.toList());
    }

    synchronized void validateSourceChange(List<DirectorySource> next) {
        Set<String> ids = new HashSet<>();
        Set<Path> paths = new HashSet<>();
        for (DirectorySource source : next) {
            if (!ids.add(source.getId()) || !paths.add(source.getPath())) {
                throw new IllegalArgumentException("Directory source IDs and base paths must be unique");
            }
            DirectorySource previous = registry.get(source.getId());
            if (deliverySources.contains(source.getId())
                    && !previous.getPath().equals(source.getPath())) {
                throw new IllegalArgumentException("Changing a delivery-managed base path requires a restart");
            }
        }
        if (!ids.containsAll(registry.keySet())) {
            throw new IllegalArgumentException("Removing directory sources requires a restart");
        }
    }

    synchronized void replaceSources(List<DirectorySource> next) {
        Set<String> localIds = localSources.stream()
                                           .map(DirectorySource::getId)
                                           .collect(Collectors.toSet());
        Map<String, DirectorySource> definitions = new LinkedHashMap<>();
        next.forEach(source -> definitions.put(source.getId(), source));
        sources = List.copyOf(next);
        registry = Map.copyOf(definitions);
        localSources.clear();
        next.stream()
            .filter(source -> localIds.contains(source.getId()))
            .forEach(localSources::add);
        lastAttempt = 0;
    }

    synchronized void setChecksumPolicy(DirectoryChecksumExceptions policy) {
        checksumExceptions = policy;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    @Override
    public boolean hasSource(String id) {
        return registry.containsKey(id);
    }

    @Override
    public synchronized void claimDeliverySource(String id) {
        DirectorySource source = source(id);
        if (localSources.contains(source) || !deliverySources.add(id)) {
            throw new IllegalArgumentException("Directory source has conflicting local/delivery owners: "
                    + id);
        }
    }

    private DirectorySource source(String id) {
        DirectorySource source = registry.get(id);
        if (source == null) {
            throw new IllegalArgumentException("Unknown directory source: " + id);
        }
        return source;
    }

    @Override
    public Path sourcePath(String id) {
        return source(id).getPath();
    }

    @Override
    public synchronized String sourceDefinition(String id) {
        String masks = checksumExceptions.definition(id);
        String compatibility = checksumExceptions.compatibilityDefinition(id);
        return source(id).definition() + (masks.isEmpty() ? "" : "|ignore-crc-masks=" + masks)
               + (compatibility.isEmpty() ? "" : "|crc-compatibility=" + compatibility);
    }

    @Override
    public LocalDate publicationDate(Path file) throws IOException {
        return DirectoryEdition.read(file).orElse(null);
    }

    @Override
    public synchronized DirectoryImportResult importFile(String id,
                                                         Path incoming,
                                                         String originalName,
                                                         boolean diff)
            throws IOException {
        DirectorySource source = source(id);
        if (!diff && Files.isRegularFile(source.getPath())) {
            LocalDate next = publicationDate(incoming);
            LocalDate current = publicationDate(source.getPath());
            if (next != null && current != null && next.isBefore(current)) {
                return new DirectoryImportResult(id, 0, false, "", "Older publication skipped");
            }
        }
        return DirectoryUpdate.install(catalog,
                                       checksumExceptions.select(source, originalName, diff),
                                       incoming,
                                       diff);
    }

    public DirectoryCatalog getCatalog() {
        return catalog;
    }

    /** Each source is independent: valid sources are updated even if another one fails. */
    public synchronized List<DirectoryImportResult> refresh() throws IOException {
        return refreshSources(sources);
    }

    public synchronized List<DirectoryImportResult> refreshLocal() throws IOException {
        return refreshSources(localSources);
    }

    private List<DirectoryImportResult> refreshSources(List<DirectorySource> selected)
            throws IOException {
        lastAttempt = System.nanoTime();
        IOException failure = null;
        List<DirectoryImportResult> result = new ArrayList<>();
        for (DirectorySource source : selected) {
            try {
                DirectoryImportResult imported = catalog.importSource(checksumExceptions.select(source,
                                                                                                source.getPath()
                                                                                                      .getFileName()
                                                                                                      .toString(),
                                                                                                false));
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
        if (!refreshEnabled || System.nanoTime() - lastAttempt < interval) {
            return;
        }
        try {
            refreshLocal();
        } catch (IOException e) {
            logger.error("Directory update failed; previous source snapshots were retained", e);
        }
    }

    @Override
    public void afterExport() {
    }

    @Override
    public void close() throws IOException {
        catalog.close();
    }
}
