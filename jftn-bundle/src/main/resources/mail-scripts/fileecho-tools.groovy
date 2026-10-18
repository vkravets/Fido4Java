import org.fidonet.types.FTNAddr
import org.fidonet.types.Message

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.DateTimeException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Local policy only: storage, authentication and collision safety stay in the plugin. */
class FileEchoTools {
    final api
    final Path root
    final Charset charset
    final boolean indexEnabled
    final boolean notifyEnabled
    final String indexName
    final FTNAddr local

    FileEchoTools(api, boolean offline = false) {
        this.api = api
        root = api.scriptDirectory.toRealPath()
        charset = Charset.forName(api.config.getValue('FileEchoIndexCharset', 'UTF-8'))
        indexEnabled = flag('FileEchoIndexEnabled', 'true')
        notifyEnabled = !offline && flag('FileEchoNotifyEnabled', 'true')
        indexName = api.config.getValue('FileEchoIndexName', 'files.bbs')
        if (!(indexName in ['files.bbs', 'file.bbs'])) {
            throw new IllegalArgumentException('FileEchoIndexName must be files.bbs or file.bbs')
        }
        local = offline ? null : api.config.localAddresses.iterator().next()
        if (indexEnabled || offline) template('FileEchoIndexTemplate', 'templates/fileecho-index.txt')
        if (notifyEnabled) template('FileEchoNoticeTemplate', 'templates/fileecho-notice.txt')
    }

    private boolean flag(String name, String fallback) {
        String value = api.config.getValue(name, fallback)
        if (!(value in ['true', 'false'])) throw new IllegalArgumentException(name + ' must be true or false')
        return Boolean.parseBoolean(value)
    }

    private String template(String setting, String fallback) {
        Path relative = Path.of(api.config.getValue(setting, fallback))
        Path file = root.resolve(relative).normalize()
        if (relative.absolute || !file.startsWith(root) || !file.toRealPath().startsWith(root)
                || Files.size(file) > 65536) {
            throw new IOException('Template must be a file beneath the mail script directory')
        }
        return Files.readString(file)
    }

    private String clean(Object value) {
        return value == null ? '' : value.toString().replaceAll('[\\p{Cntrl}]', ' ')
    }

    private String render(String text, Map values) {
        // Literal substitution; received descriptions never become executable template source.
        def pattern = Pattern.compile('\\$\\{([a-z]+)(?::([<>])([1-9][0-9]{0,2}))?\\}')
        def matcher = pattern.matcher(text)
        def result = new StringBuffer()
        while (matcher.find()) {
            String value = values.containsKey(matcher.group(1)) ? clean(values[matcher.group(1)]) : ''
            if (matcher.group(3) != null) {
                int width = Integer.parseInt(matcher.group(3))
                if (width > 256) throw new IllegalArgumentException('Template column width exceeds 256')
                value = matcher.group(2) == '>' ? value.padLeft(width) : value.padRight(width)
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    void accept(event) {
        if (event.status == 'accepted') {
            if (indexEnabled) updateIndex(event)
            return
        }
        if (notifyEnabled) notify(event)
    }

    private void updateIndex(event) {
        Path index = event.file.parent.resolve(indexName)
        if (Files.isSymbolicLink(index)) throw new IOException('Fileecho index cannot be a symbolic link')
        if (Files.exists(index) && Files.size(index) > 8 * 1024 * 1024) {
            throw new IOException('Fileecho index exceeds 8 MiB')
        }
        def rows = new LinkedHashMap<String, String>()
        if (Files.exists(index)) {
            Files.readAllLines(index, charset).each { line ->
                if (!line.isBlank()) rows[line.split('\\s+', 2)[0].toLowerCase(Locale.ROOT)] = line
            }
        }
        // Include existing regular payloads too; exclude staging files, indexes and subdirectories.
        try (def entries = Files.list(event.file.parent)) {
            entries.filter { Files.isRegularFile(it) && !Files.isSymbolicLink(it) }.forEach { file ->
                String name = file.fileName.toString()
                String key = name.toLowerCase(Locale.ROOT)
                if (!name.startsWith('.') && !(key in ['file.bbs', 'files.bbs']) && !rows.containsKey(key)) {
                    rows[key] = indexRow(name, [], Files.size(file), null)
                }
            }
        }
        rows[event.name.toLowerCase(Locale.ROOT)] = indexRow(event.name, event.descriptions,
                event.size == null ? Files.size(event.file) : event.size, event.date)
        writeIndex(index, rows.values())
    }

    private String indexRow(name, descriptions, size, date) {
        return render(template('FileEchoIndexTemplate', 'templates/fileecho-index.txt'),
                [name: name, size: String.format(Locale.US, '%,d', size), date: indexDate(date),
                 description: descriptions.collect { clean(it) }.join(' | ')])
                .replaceAll('[\\r\\n]+', ' ').trim()
    }

    private String indexDate(date) {
        if (date == null) return '-- -- --'
        try {
            return DateTimeFormatter.ofPattern('MM-dd-yy', Locale.US).withZone(ZoneOffset.UTC).format(date)
        } catch (DateTimeException ignored) {
            return '-- -- --'
        }
    }

    Path rebuild(Path directory, records) {
        Path index = directory.resolve(indexName)
        if (Files.isSymbolicLink(index)) throw new IOException('Fileecho index cannot be a symbolic link')
        def rows = records.collect { record -> indexRow(record.name, record.descriptions, record.size, record.date) }
        if (Files.exists(index)) {
            // Backups are hidden so subsequent indexing cannot treat them as payloads.
            Path backup = Files.createTempFile(directory, '.' + indexName + '-backup-', '.bak')
            Files.copy(index, backup, StandardCopyOption.REPLACE_EXISTING)
        }
        writeIndex(index, rows)
        return index
    }

    private void writeIndex(Path index, rows) {
        Path temporary = Files.createTempFile(index.parent, '.file-index-', '.tmp')
        try {
            String text = rows.isEmpty() ? '' : rows.join('\r\n') + '\r\n'
            if (text.getBytes(charset).length > 8 * 1024 * 1024) {
                throw new IOException('Fileecho index exceeds 8 MiB')
            }
            Files.writeString(temporary, text, charset)
            Files.move(temporary, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private void notify(event) {
        String key = 'fileecho-notice:' + event.status + ':' + event.receipt
        if (api.state.get(key) != null) return
        String text = render(template('FileEchoNoticeTemplate', 'templates/fileecho-notice.txt'),
                [status: event.status, area: event.area, name: event.name, peer: event.peer,
                 detail: event.detail, path: event.file == null ? '' : event.file.toString()])
        if (text.length() > 48000) throw new IOException('Fileecho notice exceeds 48000 characters')
        def message = new Message('FileEcho', api.config.getValue('SysOp', 'Sysop'), local, local,
                'Fileecho ' + event.status, text.replaceAll('[\\r\\n]+', '\r'), new Date())
        message.attributes = 1
        message.msgId = local.toString() + ' ' + event.receipt
        api.tosser.submitNetmail(message, key)
        api.state.put(key, 'done')
    }
}

def rebuildIndex(api, Path directory, records) {
    return new FileEchoTools(api, true).rebuild(directory, records)
}

def register(api) {
    if (api.fileEcho == null) return
    def tools = new FileEchoTools(api)
    api.onFileEcho { event -> tools.accept(event) }
}

def unload(api) {
}
