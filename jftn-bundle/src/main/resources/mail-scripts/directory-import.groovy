import org.fidonet.jftn.script.RobotRequest
import org.fidonet.jftn.script.ScriptState
import org.fidonet.nodelist.DirectoryEdition
import org.fidonet.nodelist.DirectoryCatalog
import org.fidonet.nodelist.DirectorySource
import org.fidonet.nodelist.DirectoryUpdate
import org.fidonet.types.FTNAddr

import java.io.IOException
import java.nio.charset.Charset
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Path
import java.util.Locale

class DirectoryFeed {
    final String definition
    final DirectorySource source
    final String area
    final String fullMask
    final String diffMask
    final FTNAddr peer

    DirectoryFeed(String definition) {
        this.definition = definition
        def fields = definition.split('\\|', -1)
        if (fields.length < 7 || fields.length > 11) {
            throw new IllegalArgumentException('DirectoryFeed=id|format|area|full-mask|diff-mask|base-file|peer[|zone|net|charset|requireChecksum]')
        }
        area = fields[2].trim().toLowerCase(Locale.ROOT)
        fullMask = fields[3].trim()
        diffMask = fields[4].trim()
        peer = new FTNAddr(fields[6].trim())
        String required = fields.length > 10 ? fields[10].trim() : 'true'
        if (!peer.valid || area.isEmpty() || fullMask.isEmpty() || !(required in ['true', 'false'])) {
            throw new IllegalArgumentException('Invalid DirectoryFeed settings')
        }
        source = new DirectorySource(fields[0].trim(),
                DirectorySource.Format.valueOf(fields[1].trim().toUpperCase(Locale.ROOT)),
                Path.of(fields[5].trim()), fields.length > 7 ? Integer.parseInt(fields[7]) : 0,
                fields.length > 8 ? Integer.parseInt(fields[8]) : 0,
                Charset.forName(fields.length > 9 ? fields[9].trim() : 'US-ASCII'),
                Boolean.parseBoolean(required))
    }
}

/** Transport metadata identifies the sender; an explicit feed grants directory import authority. */
class InboundDirectoryImports {
    private static final log = LoggerFactory.getLogger('jftn.directory-import')
    final api
    DirectoryCatalog catalog
    final List<DirectoryFeed> feeds
    final boolean requireDate
    final long retryMillis
    final Map<String, Long> retries = new LinkedHashMap<>()

    InboundDirectoryImports(api, DirectoryCatalog catalog, List<DirectoryFeed> feeds) {
        this.api = api
        this.catalog = catalog
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
            receipt = 'directory-inbound:' + ScriptState.digest(feed.definition + '|' + file.peer
                    + '|' + file.path.toAbsolutePath() + '|' + file.name + '|'
                    + attributes.size() + '|' + attributes.lastModifiedTime())
            if (api.state.get(receipt) != null || waiting(receipt)) return null
            if (full && diff) throw new IOException('Directory inbound full and diff masks overlap')
            def edition = DirectoryEdition.read(file.path).orElse(null)
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
            if (!candidate.diff && superseded(feed, candidate)) {
                api.state.put(candidate.receipt, 'superseded')
                return
            }
            def result = DirectoryUpdate.install(catalog, feed.source, candidate.file.path, candidate.diff)
            api.state.put(candidate.receipt, 'done')
            retries.remove(candidate.receipt)
            log.info('Imported inbound directory {} from {}: {} records ({})',
                    result.source, feed.peer, result.records, candidate.file.name)
        } catch (IOException | IllegalArgumentException failure) {
            deferred(feed, candidate.file, candidate.receipt, failure)
        }
    }

    private boolean superseded(DirectoryFeed feed, Map candidate) {
        if (candidate.edition == null || !Files.isRegularFile(feed.source.path)) return false
        def current = DirectoryEdition.read(feed.source.path).orElse(null)
        // Re-import an equal edition to recover SQL after interrupted publication, using its journal.
        return current != null && candidate.edition < current
    }

    private void deferred(DirectoryFeed feed, file, String receipt, Exception failure) {
        if (receipt != null) {
            retries[receipt] = System.currentTimeMillis() + retryMillis
            if (retries.size() > 1024) retries.remove(retries.keySet().iterator().next())
        }
        log.warn('Directory import deferred: source={}, peer={}, file={}: {}',
                feed.source.id, file.peer, file.name, failure.message, failure)
    }
}

catalog = null

def register(api) {
    def feeds = (api.config.getValuesAsList('DirectoryFeed') ?: []).collect { new DirectoryFeed(it) }
    String enabled = api.config.getValue('DirectoryInboundEnabled', 'false')
    if (!(enabled in ['true', 'false'])) {
        throw new IllegalArgumentException('DirectoryInboundEnabled must be true or false')
    }
    def inbound = []
    if (Boolean.parseBoolean(enabled)) {
        inbound = (api.config.getValuesAsList('DirectoryInboundFeed') ?: []).collect { definition ->
            def fields = definition.split('\\|', -1) as List
            if (fields.size() < 6 || fields.size() > 10) {
                throw new IllegalArgumentException('DirectoryInboundFeed=id|format|full-mask|diff-mask|base-file|peer[|zone|net|charset|requireChecksum]')
            }
            fields.add(2, '@inbound')
            new DirectoryFeed(fields.join('|'))
        }
    }
    def all = feeds + inbound
    if (all.isEmpty()) {
        return
    }
    if (all.collect { it.source.id }.toSet().size() != all.size()
            || all.collect { it.source.path }.toSet().size() != all.size()) {
        throw new IllegalArgumentException('DirectoryFeed IDs and base paths must be unique')
    }
    all.each { feed ->
        if (api.config.getLink(feed.peer) == null) {
            throw new IllegalArgumentException('DirectoryFeed requires a configured transport peer')
        }
    }
    def importer = inbound.isEmpty() ? null : new InboundDirectoryImports(api, null, inbound)
    catalog = new DirectoryCatalog(Path.of(api.config.getValue('NodeDirectoryPath', './database/nodelist/catalog')))
    if (importer != null) {
        importer.catalog = catalog
        api.onScan { importer.scan() }
        LoggerFactory.getLogger('jftn.directory-import').info('Automatic inbound directory import enabled: {} feeds', inbound.size())
    }
    api.onFile { area, file, name, peer, receipt ->
        feeds.findAll { it.area == area && it.peer == peer }.each { feed ->
            boolean full = RobotRequest.matches(feed.fullMask, name)
            boolean diff = !feed.diffMask.isEmpty() && RobotRequest.matches(feed.diffMask, name)
            if (full && diff) {
                throw new IOException('DirectoryFeed full and diff masks overlap for ' + name)
            }
            if (full || diff) {
                def result = DirectoryUpdate.install(catalog, feed.source, file, diff)
                LoggerFactory.getLogger('jftn.directory-import').info('Imported {}: {} records', result.source, result.records)
            }
        }
    }
}

def unload(api) {
    if (catalog != null) {
        catalog.close()
        catalog = null
    }
}
