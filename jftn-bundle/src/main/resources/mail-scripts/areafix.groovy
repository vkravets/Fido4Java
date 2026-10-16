import org.fidonet.echobase.AccessLevel
import org.fidonet.echobase.EchoHistory
import org.fidonet.jftn.script.RobotRequest
import org.fidonet.types.FTNAddr

import java.util.Locale

class AreaFixRobot {
    final api

    AreaFixRobot(api) {
        this.api = api
        if (!api.config.getValue('EchoSubscriptions', 'config').equalsIgnoreCase('database')) {
            throw new IllegalArgumentException('AreaFix requires EchoSubscriptions=database')
        }

    }

    boolean handle(message, peer, key) {
        if (!(message.to.toLowerCase(Locale.ROOT) in ['areafix', 'areamgr'])) {
            return false
        }
        def request = RobotRequest.authorize(api, 'AreaFix', message, peer, key)
        if (request == null) {
            return true
        }
        def cached = request.previousReply()
        if (cached != null) {
            request.reply(cached)
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
        def command = fields[0]
        if (command == '%help') {
            return '%HELP, %LIST [mask], %QUERY, %LINKED, %UNLINKED, +area, -area, %RESCAN area [count]'
        }
        if (command in ['%list', '%query', '%linked', '%unlinked']) {
            def mask = fields.length > 1 ? fields[1] : '*'
            def names = available(request, mask).findAll { area ->
                boolean linked = api.database.canReadEcho(peer, area)
                command == '%list' || (command == '%unlinked' ? !linked : linked)
            }
            return names.join('\r') ?: 'No matching areas.'
        }
        if (command == '%rescan') {
            return rescan(request, peer, fields)
        }
        if (command.startsWith('%') || fields.length != 1) {
            return 'Unsupported command: ' + line
        }
        boolean remove = command.startsWith('-')
        String mask = command.startsWith('+') || remove ? command.substring(1) : command
        def areas = available(request, mask)
        if (areas.isEmpty()) {
            return 'No available areas: ' + mask
        }
        areas.each { area ->
            if (remove) {
                api.tosser.removeEchoSubscription(peer.addr, area)
            } else if (!api.database.canReadEcho(peer, area)) {
                api.tosser.configureEchoSubscription(peer.addr, area,
                        AccessLevel.valueOf(request.policy[1]), EchoHistory.NEW_ONLY)
            }
        }
        return (remove ? 'Unlinked: ' : 'Linked: ') + areas.join(', ')
    }

    List available(request, String mask) {
        api.database.areas.findAll { area ->
            request.allows(area) && RobotRequest.matches(mask, area)
        }.sort()
    }

    String rescan(request, peer, String[] fields) {
        if (fields.length < 2 || fields.length > 3
                || (fields.length == 3 && !(fields[2] ==~ '[1-9][0-9]{0,5}'))) {
            return 'Usage: %RESCAN area [count=100], maximum 10000 messages'
        }
        int count = fields.length == 3 ? Integer.parseInt(fields[2]) : 100
        if (count > 10000) {
            return 'Rescan count exceeds 10000.'
        }
        def areas = available(request, fields[1])
        areas.each { area ->
            if (api.database.canReadEcho(peer, area)) {
                api.tosser.rescanRecentEcho(area, peer.addr, count, request.id)
            }
        }
        return 'Rescan requested for linked areas: ' + areas.join(', ')
    }
}

def register(api) {
    def robot = new AreaFixRobot(api)
    api.onNetmail { message, peer, key -> robot.handle(message, peer, key) }
}

def unload(api) {
}
