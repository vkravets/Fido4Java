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
        List<String> lines
        try {
            lines = RobotRequest.commands(message.body.body)
        } catch (IllegalArgumentException invalid) {
            request.reply(invalid.message + '\r')
            return true
        }
        def output = new StringBuilder()
        for (String line : lines) {
            try {
                output.append(command(request, peer, line)).append('\r')
            } catch (IllegalArgumentException invalid) {
                output.append('Invalid command: ').append(invalid.message).append('\r')
            }
        }
        request.reply(output.toString())
        return true
    }

    String command(request, peer, String line) {
        def fields = line.toLowerCase(Locale.ROOT).split('\\s+')
        String command = fields[0]
        if (command == '%help') {
            return '%HELP, %INFO, %LIST [! mask | mask], %QUERY, %LINKED, %UNLINKED, ' +
                    '+area, -area, %PAUSE, %RESUME, %RESEND filename area'
        }
        if (command == '%info' && fields.length == 1) {
            return 'FileFix for ' + peer.addr + '\rDelivery: ' +
                    (api.robotDelivery.isPaused('FileFix', peer.addr) ? 'paused' : 'active')
        }
        if (command in ['%pause', '%resume'] && fields.length == 1) {
            api.robotDelivery.pause('FileFix', peer.addr, command == '%pause')
            return command == '%pause' ? 'Fileecho delivery paused; subscriptions retained.' :
                    'Fileecho delivery resumed.'
        }
        if (command == '%resend') {
            if (fields.length != 3 || !request.allows(fields[2])) {
                return 'Usage: %RESEND exact-filename allowed-linked-area'
            }
            api.fileEcho.resend(peer.addr, fields[2], fields[1], request.id)
            return 'Resend queued: ' + fields[1] + ' from ' + fields[2]
        }
        def linked = subscriptions(peer)
        if (command in ['%list', '%query', '%linked', '%unlinked']) {
            RobotRequest.selects(fields, '')
            return api.fileEcho.areas().findAll { tag ->
                request.allows(tag) && RobotRequest.selects(fields, tag)
                        && (command == '%list' || (command == '%unlinked' ? !linked.contains(tag) : linked.contains(tag)))
            }.join('\r') ?: 'No matching file areas.'
        }
        if (command.startsWith('%') || fields.length != 1) {
            return 'Unsupported command: ' + command
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
