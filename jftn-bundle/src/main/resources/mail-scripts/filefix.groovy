import org.fidonet.jftn.script.RobotRequest

import java.util.Locale

class FileFixRobot {
    final api
    FileFixRobot(api) {
        this.api = api
    }

    Set<String> subscriptions(peer) {
        api.fileEcho.subscriptions(peer.addr)
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
            return api.fileEcho.areas().findAll { tag ->
                request.allows(tag) && RobotRequest.matches(mask, tag)
                        && (command == '%list' || (command == '%unlinked' ? !linked.contains(tag) : linked.contains(tag)))
            }.join('\r') ?: 'No matching file areas.'
        }
        if (command.startsWith('%') || fields.length != 1) {
            return 'Unsupported command: ' + line
        }
        boolean remove = command.startsWith('-')
        String mask = remove || command.startsWith('+') ? command.substring(1) : command
        def selected = api.fileEcho.areas().findAll { request.allows(it) && RobotRequest.matches(mask, it) }
        selected.each { tag ->
            if (remove) {
                linked.remove(tag)
            } else {
                linked.add(tag)
            }
        }
        selected.each { tag -> api.fileEcho.subscribe(peer.addr, tag, !remove) }
        return (remove ? 'Unlinked: ' : 'Linked: ') + (selected.join(', ') ?: 'no available areas')
    }

 }

def register(api) {
    if (api.fileEcho == null) {
        return
    }
    def robot = new FileFixRobot(api)
    api.onNetmail { message, peer, key -> robot.handle(message, peer, key) }
}

def unload(api) {
}
