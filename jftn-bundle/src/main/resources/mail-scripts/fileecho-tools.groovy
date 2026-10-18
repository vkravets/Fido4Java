import org.fidonet.types.FTNAddr
import org.fidonet.types.Message

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    FileEchoTools(api) {
        this.api = api
        root = api.scriptDirectory.toRealPath()
        charset = Charset.forName(api.config.getValue('FileEchoIndexCharset', 'UTF-8'))
        indexEnabled = flag('FileEchoIndexEnabled', 'true')
        notifyEnabled = flag('FileEchoNotifyEnabled', 'true')
        indexName = api.config.getValue('FileEchoIndexName', 'files.bbs')
        if (!(indexName in ['files.bbs', 'file.bbs'])) {
            throw new IllegalArgumentException('FileEchoIndexName must be files.bbs or file.bbs')
        }
        local = api.config.localAddresses.iterator().next()
        if (indexEnabled) template('FileEchoIndexTemplate', 'templates/fileecho-index.txt')
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
        return value.toString().replaceAll('[\\p{Cntrl}]', ' ')
    }

    private String render(String text, Map values) {
        // Literal substitution; received descriptions never become executable template source.
        def pattern = Pattern.compile('\\$\\{([a-z]+)\\}')
        def matcher = pattern.matcher(text)
        def result = new StringBuffer()
        while (matcher.find()) {
            String value = values.containsKey(matcher.group(1)) ? clean(values[matcher.group(1)]) : ''
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
                    rows[key] = name
                }
            }
        }
        String row = render(template('FileEchoIndexTemplate', 'templates/fileecho-index.txt'),
                [name: event.name, description: event.descriptions.collect { clean(it) }.join(' | ')])
                .replaceAll('[\\r\\n]+', ' ').trim()
        rows[event.name.toLowerCase(Locale.ROOT)] = row
        Path temporary = Files.createTempFile(index.parent, '.file-index-', '.tmp')
        try {
            Files.writeString(temporary, rows.values().join('\r\n') + '\r\n', charset)
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

def register(api) {
    if (api.fileEcho == null) return
    def tools = new FileEchoTools(api)
    api.onFileEcho { event -> tools.accept(event) }
}

def unload(api) {
}
