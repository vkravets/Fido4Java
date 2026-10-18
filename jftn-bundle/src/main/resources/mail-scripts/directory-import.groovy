import org.fidonet.jftn.script.RobotRequest
import org.fidonet.jftn.script.ScriptState
import org.fidonet.jftn.script.ReceivedFile
import org.fidonet.types.FTNAddr

import java.io.IOException
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Path
import java.util.Locale

class DirectoryFeed {
    final String definition
    final String id
    final String area
    final String fullMask
    final String diffMask
    final FTNAddr peer

    DirectoryFeed(String definition) {
        this.definition = definition
        def fields = definition.split('\\|', -1)
        if (fields.length != 5) {
            throw new IllegalArgumentException('DirectoryFeed=source-id|area|full-mask|diff-mask|peer; define the source once in 30-nodelist.conf')
        }
        id = fields[0].trim()
        area = fields[1].trim().toLowerCase(Locale.ROOT)
        fullMask = fields[2].trim()
        diffMask = fields[3].trim()
        peer = new FTNAddr(fields[4].trim())
        if (!peer.valid || id.isEmpty() || area.isEmpty() || fullMask.isEmpty()) {
            throw new IllegalArgumentException('Invalid DirectoryFeed settings')
        }
    }
}

/** Transport metadata identifies the sender; an explicit feed grants directory import authority. */
class InboundDirectoryImports {
    private static final log = LoggerFactory.getLogger('jftn.directory-import')
    final api
    final directories
    final List<DirectoryFeed> feeds
    final boolean requireDate
    final long retryMillis
    final Map<String, Long> retries = new LinkedHashMap<>()

    InboundDirectoryImports(api, directories, List<DirectoryFeed> feeds) {
        this.api = api
        this.directories = directories
        this.feeds = feeds
        String required = api.config.getValue('DirectoryInboundRequireDate', 'true')
        if (!(required in ['true', 'false'])) {
            throw new IllegalArgumentException('DirectoryInboundRequireDate must be true or false')
        }
        requireDate = Boolean.parseBoolean(required)
        long seconds = Long.parseLong(api.config.getValue('DirectoryInboundRetrySeconds', '300'))
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException('DirectoryInboundRetrySeconds must be in 1..86400')
        }
        retryMillis = seconds * 1000
    }

    void scan() {
        def inbox = api.receivedFiles
        feeds.each { feed -> scanFeed(feed, inbox) }
    }

    /** Explicitly configured accepted-file folders; never scan dupes or arbitrary subfolders. */
    void scanFolder(DirectoryFeed feed, Path directory) {
        if (!Files.isDirectory(directory)) return
        def files = []
        try (def entries = Files.list(directory)) {
            entries.filter { Files.isRegularFile(it) && !Files.isSymbolicLink(it) }.forEach { path ->
                String name = path.fileName.toString().replaceFirst('^[a-fA-F0-9]{64}-', '')
                if (ReceivedFile.isSafeName(name)) files.add(new ReceivedFile(path, name, feed.peer))
            }
        }
        scanFeed(feed, files)
    }

    private void scanFeed(DirectoryFeed feed, List inbox) {
        def candidates = []
        inbox.findAll { it.peer == feed.peer }.each { file ->
            def candidate = candidate(feed, file)
            if (candidate != null) candidates.add(candidate)
        }
        // Import the newest full list first, so a backlog does not rebuild SQL for every edition.
        def full = candidates.findAll { !it.diff }.sort { a, b ->
            if (a.edition != null && b.edition != null) return b.edition <=> a.edition
            if (a.edition != null) return -1
            if (b.edition != null) return 1
            return a.modified <=> b.modified ?: a.file.name <=> b.file.name
        }
        full.each { install(feed, it) }
        candidates.findAll { it.diff }.sort { a, b ->
            a.edition <=> b.edition ?: a.modified <=> b.modified ?: a.file.name <=> b.file.name
        }.each { install(feed, it) }
    }

    private Map candidate(DirectoryFeed feed, file) {
        boolean full = RobotRequest.matches(feed.fullMask, file.name)
        boolean diff = !feed.diffMask.isEmpty() && RobotRequest.matches(feed.diffMask, file.name)
        if (!full && !diff) return null
        String receipt = null
        try {
            def attributes = Files.readAttributes(file.path, BasicFileAttributes)
            receipt = 'directory-inbound:' + ScriptState.digest(feed.definition + '|' + directories.sourceDefinition(feed.id) + '|' + file.peer
                    + '|' + file.path.toAbsolutePath() + '|' + file.name + '|'
                    + attributes.size() + '|' + attributes.lastModifiedTime())
            if (api.state.get(receipt) != null || waiting(receipt)) return null
            if (full && diff) throw new IOException('Directory inbound full and diff masks overlap')
            def edition = directories.publicationDate(file.path)
            if (requireDate && edition == null) {
                throw new IOException('Directory publication date is missing; set DirectoryInboundRequireDate=false only for an explicitly ordered feed')
            }
            return [file: file, diff: diff, edition: edition,
                    modified: attributes.lastModifiedTime().toMillis(), receipt: receipt]
        } catch (IOException | IllegalArgumentException failure) {
            deferred(feed, file, receipt, failure)
            return null
        }
    }

    private boolean waiting(String receipt) {
        Long until = retries[receipt]
        return until != null && until > System.currentTimeMillis()
    }

    private void install(DirectoryFeed feed, Map candidate) {
        try {
            def result = directories.importFile(feed.id, candidate.file.path, candidate.file.name, candidate.diff)
            api.state.put(candidate.receipt, 'done')
            retries.remove(candidate.receipt)
            if (result.changed) {
                log.info('Imported inbound directory {} from {}: {} records ({})',
                        result.source, feed.peer, result.records, candidate.file.name)
            }
        } catch (IOException | IllegalArgumentException failure) {
            deferred(feed, candidate.file, candidate.receipt, failure)
        }
    }

    private void deferred(DirectoryFeed feed, file, String receipt, Exception failure) {
        if (receipt != null) {
            retries[receipt] = System.currentTimeMillis() + retryMillis
            if (retries.size() > 1024) retries.remove(retries.keySet().iterator().next())
        }
        log.warn('Directory import deferred: source={}, peer={}, file={}: {}',
                feed.id, file.peer, file.name, failure.message, failure)
    }
}

def register(api) {
    def directories = api.directoryImports
    if (directories == null) return
    def feeds = (api.config.getValuesAsList('DirectoryFeed') ?: []).collect { new DirectoryFeed(it) }
    String enabled = api.config.getValue('DirectoryInboundEnabled', 'false')
    if (!(enabled in ['true', 'false'])) {
        throw new IllegalArgumentException('DirectoryInboundEnabled must be true or false')
    }
    def inbound = []
    if (Boolean.parseBoolean(enabled)) {
        inbound = (api.config.getValuesAsList('DirectoryInboundFeed') ?: []).collect { definition ->
            def fields = definition.split('\\|', -1) as List
            if (fields.size() != 4) {
                throw new IllegalArgumentException('DirectoryInboundFeed=source-id|full-mask|diff-mask|peer')
            }
            fields.add(1, '@inbound')
            new DirectoryFeed(fields.join('|'))
        }
    }
    def all = feeds + inbound
    if (all.isEmpty()) {
        if (api.config.getValuesAsList('DirectoryFeedFolder')) {
            throw new IllegalArgumentException('DirectoryFeedFolder requires a matching DirectoryFeed')
        }
        return
    }
    if (all.collect { it.id }.toSet().size() != all.size()) {
        throw new IllegalArgumentException('A directory source must have one delivery feed')
    }
    all.each { feed ->
        if (!directories.hasSource(feed.id)) {
            throw new IllegalArgumentException('Unknown directory source ' + feed.id + '; define DirectorySource in 30-nodelist.conf')
        }
        if (api.config.getLink(feed.peer) == null) {
            throw new IllegalArgumentException('DirectoryFeed requires a configured transport peer')
        }
        directories.claimDeliverySource(feed.id)
    }
    def folders = new LinkedHashMap<DirectoryFeed, Path>()
    (api.config.getValuesAsList('DirectoryFeedFolder') ?: []).each { definition ->
        def fields = definition.split('\\|', -1)
        def feed = fields.length == 2 ? feeds.find { it.id == fields[0].trim() } : null
        if (feed == null || fields[1].trim().isEmpty() || folders.containsKey(feed)) {
            throw new IllegalArgumentException('DirectoryFeedFolder=existing-feed-id|accepted-file-folder')
        }
        Path folder = Path.of(fields[1].trim()).toAbsolutePath().normalize()
        if (directories.sourcePath(feed.id).startsWith(folder)) {
            throw new IllegalArgumentException('Retained directory base must be outside its source folder')
        }
        folders[feed] = folder
    }
    def importer = inbound.isEmpty() && folders.isEmpty() ? null : new InboundDirectoryImports(api, directories, inbound)
    if (importer != null) {
        api.onScan { importer.scan() }
        folders.each { feed, path -> api.onScan { importer.scanFolder(feed, path) } }
        LoggerFactory.getLogger('jftn.directory-import').info('Automatic directory import enabled: {} inbound feeds, {} accepted folders', inbound.size(), folders.size())
    }
    api.onFile { area, file, name, peer, receipt ->
        feeds.findAll { it.area == area && it.peer == peer }.each { feed ->
            boolean full = RobotRequest.matches(feed.fullMask, name)
            boolean diff = !feed.diffMask.isEmpty() && RobotRequest.matches(feed.diffMask, name)
            if (full && diff) {
                throw new IOException('DirectoryFeed full and diff masks overlap for ' + name)
            }
            if (full || diff) {
                def result = directories.importFile(feed.id, file, name, diff)
                if (result.changed) LoggerFactory.getLogger('jftn.directory-import').info('Imported {}: {} records', result.source, result.records)
            }
        }
    }
}

def unload(api) {
    // The node-owned directory service closes the catalog after scripts unload.
}
