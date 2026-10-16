import org.fidonet.jftn.script.FileDeliveryQueue
import org.fidonet.jftn.script.ReceivedFile
import org.fidonet.jftn.script.RobotRequest
import org.fidonet.jftn.script.ScriptState
import org.fidonet.types.FTNAddr

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

class FileFixRobot {
    final api
    final Map<String, Path> areas = new TreeMap<>()
    final charset

    FileFixRobot(api) {
        this.api = api
        charset = Charset.forName(api.config.getValue('TicCharset', 'IBM437'))
        (api.config.getValuesAsList('FileArea') ?: []).each { value ->
            def fields = value.split('\\|', -1)
            if (fields.length < 2 || fields.length > 3 || !(fields[0] ==~ '[A-Za-z0-9_.-]{1,60}')) {
                throw new IllegalArgumentException('FileArea=tag|directory[|description]')
            }
            def tag = fields[0].toLowerCase(Locale.ROOT)
            if (areas.put(tag, Path.of(fields[1]).toAbsolutePath().normalize()) != null) {
                throw new IllegalArgumentException('Duplicate FileArea ' + tag)
            }
        }

    }

    String subscriptionKey(peer) {
        'filefix-subscriptions:' + peer.addr
    }

    Set<String> subscriptions(peer) {
        new TreeSet<String>((api.state.get(subscriptionKey(peer)) ?: '').split('\\n').findAll { !it.isEmpty() })
    }

    boolean handle(message, peer, key) {
        if (!(message.to.toLowerCase(Locale.ROOT) in ['filefix', 'filemgr'])) {
            return false
        }
        def request = RobotRequest.authorize(api, 'FileFix', message, peer, key)
        if (request == null) {
            return true
        }
        def previous = request.previousReply()
        if (previous != null) {
            request.reply(previous)
            return true
        }
        def lines = message.body.body.split('[\\r\\n]+')
        if (message.body.body.length() > 64000 || lines.length > 200) {
            request.reply('Request exceeds 200 lines or 64000 characters.\r')
            return true
        }
        def output = new StringBuilder()
        for (String line : lines) {
            line = line.trim()
            if (line == '---' || line.startsWith('--- ')) {
                break
            }
            if (!line.isEmpty() && !line.startsWith('\u0001')) {
                output.append(command(request, peer, line)).append('\r')
            }
        }
        request.reply(output.toString())
        return true
    }

    String command(request, peer, String line) {
        def fields = line.toLowerCase(Locale.ROOT).split('\\s+')
        String command = fields[0]
        if (command == '%help') {
            return '%HELP, %LIST [mask], %QUERY, %LINKED, %UNLINKED, +area, -area'
        }
        def linked = subscriptions(peer)
        if (command in ['%list', '%query', '%linked', '%unlinked']) {
            def mask = fields.length > 1 ? fields[1] : '*'
            return areas.keySet().findAll { tag ->
                request.allows(tag) && RobotRequest.matches(mask, tag)
                        && (command == '%list' || (command == '%unlinked' ? !linked.contains(tag) : linked.contains(tag)))
            }.join('\r') ?: 'No matching file areas.'
        }
        if (command.startsWith('%') || fields.length != 1) {
            return 'Unsupported command: ' + line
        }
        boolean remove = command.startsWith('-')
        String mask = remove || command.startsWith('+') ? command.substring(1) : command
        def selected = areas.keySet().findAll { request.allows(it) && RobotRequest.matches(mask, it) }
        selected.each { tag ->
            if (remove) {
                linked.remove(tag)
            } else {
                linked.add(tag)
            }
        }
        api.state.put(subscriptionKey(peer), linked.join('\n'))
        return (remove ? 'Unlinked: ' : 'Linked: ') + (selected.join(', ') ?: 'no available areas')
    }

    FTNAddr address(String value) {
        String text = value.trim().split('\\s+')[0]
        // This installation has one FTN namespace; retain original domain text on forwarding.
        text = text.substring(text.lastIndexOf('#') + 1).split('@', 2)[0]
        def address = new FTNAddr(text)
        if (!address.valid) {
            throw new IOException('Invalid TIC address')
        }
        return address
    }

    Map parse(Path path) {
        if (Files.size(path) > 65536) {
            throw new IOException('TIC exceeds 64 KiB')
        }
        def fields = [:].withDefault { [] }
        def lines = Files.readString(path, charset).split('[\\r\\n]+')
        lines.each { String line ->
            if (line.length() > 1024 || (line =~ '[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]').find()) {
                throw new IOException('Invalid TIC line')
            }
            if (!line.trim().isEmpty()) {
                def pair = line.trim().split('\\s+', 2)
                fields[pair[0].toLowerCase(Locale.ROOT)].add(pair.length == 2 ? pair[1] : '')
            }
        }
        ['area', 'file', 'origin', 'from', 'crc', 'path', 'seenby'].each { required ->
            if (!fields.containsKey(required) || fields[required].any { it.isEmpty() }) {
                throw new IOException('Missing TIC keyword: ' + required)
            }
        }
        ['area', 'file', 'origin', 'from', 'crc', 'size', 'pw', 'to'].each { single ->
            if (fields.containsKey(single) && fields[single].size() != 1) {
                throw new IOException('Duplicate TIC keyword: ' + single)
            }
        }
        fields.raw = lines.findAll { !it.trim().isEmpty() }
        return fields
    }

    Map checksums(Path file) {
        if (Files.size(file) > FileDeliveryQueue.MAX_FILE_BYTES) {
            throw new IOException('Fileecho payload exceeds 128 MiB')
        }
        def crc = new CRC32()
        def sha = MessageDigest.getInstance('SHA-256')
        long size = 0
        Files.newInputStream(file).withCloseable { input ->
            byte[] buffer = new byte[65536]
            int count
            while ((count = input.read(buffer)) != -1) {
                size += count
                if (size > FileDeliveryQueue.MAX_FILE_BYTES) {
                    throw new IOException('Fileecho payload grew beyond 128 MiB')
                }
                crc.update(buffer, 0, count)
                sha.update(buffer, 0, count)
            }
        }
        return [crc: String.format('%08X', crc.value), sha: sha.digest().encodeHex().toString(), size: size]
    }

    void scan() {
        def inbox = api.receivedFiles
        inbox.findAll { it.name.toLowerCase(Locale.ROOT).endsWith('.tic') }.each { ticket ->
            try {
                process(ticket, inbox)
            } catch (IOException | IllegalArgumentException failure) {
                // Keep both files for inspection and retry; never include ticket/password in logs.
                System.err.println('FileFix deferred TIC ' + ticket.path.fileName + ': ' + failure.message)
            }
        }
    }

    void process(ticket, List inbox) {
        def fields = parse(ticket.path)
        String tag = fields.area[0].toLowerCase(Locale.ROOT)
        def credentials = api.robotAccess.find('Tic', ticket.peer)
        def sender = address(fields.from[0])
        if (credentials == null || sender != ticket.peer || !areas.containsKey(tag)
                || !credentials.allows(tag)
                || !fields.containsKey('pw') || !credentials.verifyPassword(fields.pw[0])) {
            throw new IOException('TIC sender, area or password is not authorized')
        }
        if (fields.containsKey('to') && !api.config.localAddresses.contains(address(fields.to[0]))) {
            throw new IOException('TIC is addressed to another station')
        }
        def origin = address(fields.origin[0])
        def seen = fields.seenby.collect { address(it) } as Set
        def paths = fields.path.collect { address(it) } as Set
        if (api.config.localAddresses.any { paths.contains(it) }) {
            throw new IOException('TIC path loop')
        }
        String name = ReceivedFile.safeName(fields.file[0])
        if (!(name ==~ '[A-Za-z0-9_~-]{1,8}(\\.[A-Za-z0-9_~-]{1,3})?') || name.toLowerCase(Locale.ROOT).endsWith('.tic')) {
            throw new IOException('TIC File must be a DOS 8.3 payload name')
        }
        if (!(fields.crc[0] ==~ '[0-9a-fA-F]{8}')) {
            throw new IOException('Invalid TIC CRC')
        }
        def candidates = inbox.findAll { it.peer == ticket.peer && it.name.equalsIgnoreCase(name) }
        def payload = candidates.find { candidate ->
            def sum = checksums(candidate.path)
            sum.crc.equalsIgnoreCase(fields.crc[0]) && (!fields.containsKey('size') || fields.size[0] == sum.size.toString())
        }
        if (payload == null) {
            return // It is normal for a TIC to arrive before its payload.
        }
        def sum = checksums(payload.path)
        String receipt = ScriptState.digest(tag + ':' + origin + ':' + name.toLowerCase(Locale.ROOT) + ':' + sum.sha)
        if (api.state.get('tic:' + receipt) != null) {
            return
        }
        Path directory = areas[tag]
        Files.createDirectories(directory)
        Path stored = directory.resolve(receipt + '-' + name)
        Path staging = Files.createTempFile(directory, '.incoming-', '.tmp')
        try {
            FileDeliveryQueue.copy(payload.path, staging)
            if (checksums(staging) != sum) {
                throw new IOException('Payload changed while storing it')
            }
            Files.move(staging, stored, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(staging)
        }
        forward(fields, tag, name, stored, ticket.peer, seen, receipt)
        api.publishFile(tag, stored, name, ticket.peer, receipt)
        api.state.put('tic:' + receipt, 'done')
    }

    void forward(Map fields, String tag, String name, Path stored, FTNAddr sender, Set seen, String receipt) {
        def recipients = api.config.configuredLinks.findAll { link ->
            link.addr != sender && !seen.contains(link.addr) && !api.config.localAddresses.contains(link.addr)
                    && api.robotAccess.find('Tic', link.addr)?.allows(tag)
                    && subscriptions(link).contains(tag)
        }
        def seenAll = new LinkedHashSet(seen)
        seenAll.add(sender)
        seenAll.addAll(api.config.localAddresses)
        seenAll.addAll(recipients.collect { it.addr })
        recipients.each { link ->
            def local = link.myaddr[0]
            def output = fields.raw.findAll { String line ->
                !(line.trim().split('\\s+', 2)[0].toLowerCase(Locale.ROOT) in
                        ['from', 'to', 'pw', 'created', 'seenby', 'path', 'magic', 'receiptrequest'])
            }
            output.add('From ' + local)
            output.add('To ' + link.addr)
            def credentials = api.robotAccess.find('Tic', link.addr)
            if (credentials == null || !credentials.allows(tag)) return
            output.add('Pw ' + credentials.deliveryPassword)
            output.add('Created by JFtn Groovy FileFix')
            fields.path.each { output.add('Path ' + it) }
            output.add('Path ' + local + ' ' + (System.currentTimeMillis().intdiv(1000)))
            seenAll.each { output.add('Seenby ' + it) }
            api.fileQueue.enqueue(link, receipt, stored, name, (output.join('\r\n') + '\r\n').getBytes(charset))
        }
    }
}

def register(api) {
    def robot = new FileFixRobot(api)
    api.onNetmail { message, peer, key -> robot.handle(message, peer, key) }
    api.onScan { robot.scan() }
}

def unload(api) {
}
