import org.fidonet.jftn.script.RobotRequest
import org.fidonet.nodelist.DirectoryCatalog
import org.fidonet.nodelist.DirectorySource
import org.fidonet.nodelist.DirectoryUpdate
import org.fidonet.types.FTNAddr

import java.nio.charset.Charset
import java.nio.file.Path
import java.util.Locale
import java.util.logging.Logger

class DirectoryFeed {
    final DirectorySource source
    final String area
    final String fullMask
    final String diffMask
    final FTNAddr peer

    DirectoryFeed(String definition) {
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

catalog = null

def register(api) {
    def feeds = (api.config.getValuesAsList('DirectoryFeed') ?: []).collect { new DirectoryFeed(it) }
    if (feeds.isEmpty()) {
        return
    }
    if (feeds.collect { it.source.id }.toSet().size() != feeds.size()
            || feeds.collect { it.source.path }.toSet().size() != feeds.size()) {
        throw new IllegalArgumentException('DirectoryFeed IDs and base paths must be unique')
    }
    feeds.each { feed ->
        if (api.config.getLink(feed.peer) == null) {
            throw new IllegalArgumentException('DirectoryFeed requires a configured transport peer')
        }
    }
    catalog = new DirectoryCatalog(Path.of(api.config.getValue('NodeDirectoryPath', './database/nodelist/catalog')))
    api.onFile { area, file, name, peer, receipt ->
        feeds.findAll { it.area == area && it.peer == peer }.each { feed ->
            boolean full = RobotRequest.matches(feed.fullMask, name)
            boolean diff = !feed.diffMask.isEmpty() && RobotRequest.matches(feed.diffMask, name)
            if (full && diff) {
                throw new IOException('DirectoryFeed full and diff masks overlap for ' + name)
            }
            if (full || diff) {
                def result = DirectoryUpdate.install(catalog, feed.source, file, diff)
                Logger.getLogger('jftn.directory-import').info('Imported ' + result.source + ': ' + result.records + ' records')
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
