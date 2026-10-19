import org.fidonet.config.RuntimeProfile
import org.fidonet.echobase.AccessLevel
import org.fidonet.echobase.EchoHistory
import org.fidonet.jftn.script.RobotRequest

import java.util.Locale

class AreaFixRobot {
    final api

    AreaFixRobot(api) {
        this.api = api
        if (RuntimeProfile.read(api.config) == RuntimeProfile.FULL && !api.config.getValue('EchoSubscriptions', 'config').equalsIgnoreCase('database')) {
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
        List<String> lines
        try {
            lines = RobotRequest.commands(message.body.body)
        } catch (IllegalArgumentException invalid) {
            request.reply(invalid.message + '\r')
            return true
        }
        def output = new StringBuilder()
        boolean rescanFollowing = false
        for (String line : lines) {
            if (line.equalsIgnoreCase('%RESCAN')) {
                rescanFollowing = true
                output.append('Rescan enabled for subsequent subscription commands.\r')
                continue
            }
            try {
                output.append(command(request, peer, line, rescanFollowing)).append('\r')
            } catch (IllegalArgumentException invalid) {
                output.append('Invalid command: ').append(invalid.message).append('\r')
            }
        }
        request.reply(output.toString())
        return true
    }

    String command(request, peer, String line, boolean rescanFollowing) {
        def fields = line.toLowerCase(Locale.ROOT).split('\\s+')
        def command = fields[0]
        if (command == '%help') {
            return '%HELP, %INFO, %LIST [! mask | mask], %QUERY, %LINKED, %UNLINKED, ' +
                    '+area [/r[=count]], -area, %RESCAN [area [count]], %PAUSE, %RESUME\r' +
                    'Rescans default to 100 messages; maximum 10000. Days-based rescans are unsupported.'
        }
        if (command == '%info' && fields.length == 1) {
            return 'AreaFix for ' + peer.addr + '\rDelivery: ' +
                    (api.robotDelivery.isPaused('AreaFix', peer.addr) ? 'paused' : 'active') +
                    '\rAvailable local areas: ' + available(request, '*').size()
        }
        if (command in ['%pause', '%resume'] && fields.length == 1) {
            api.robotDelivery.pause('AreaFix', peer.addr, command == '%pause')
            return command == '%pause' ? 'Echomail delivery paused; subscriptions retained.' :
                    'Echomail delivery resumed.'
        }
        if (command in ['%list', '%query', '%linked', '%unlinked']) {
            RobotRequest.selects(fields, '')
            def names = available(request, '*').findAll { area ->
                boolean linked = api.storage.canReadEcho(peer, area)
                RobotRequest.selects(fields, area) &&
                        (command == '%list' || (command == '%unlinked' ? !linked : linked))
            }
            return names.join('\r') ?: 'No matching areas.'
        }
        if (command == '%rescan') {
            return rescan(request, peer, fields)
        }
        if (command.startsWith('%') || command.startsWith('~')) {
            return 'Unsupported command: ' + command
        }
        boolean remove = command.startsWith('-')
        Integer count = subscriptionRescan(fields, remove, rescanFollowing)
        String mask = command.startsWith('+') || remove ? command.substring(1) : command
        def areas = available(request, mask)
        if (areas.isEmpty()) {
            return 'No available areas: ' + mask
        }
        areas.each { area ->
            if (remove) {
                api.tosser.removeEchoSubscription(peer.addr, area)
            } else if (!api.storage.canReadEcho(peer, area)) {
                api.tosser.configureEchoSubscription(peer.addr, area,
                        AccessLevel.valueOf(request.policy[1]), EchoHistory.NEW_ONLY)
            }
            if (count != null && api.storage.canReadEcho(peer, area)) {
                api.tosser.rescanRecentEcho(area, peer.addr, count, request.id)
            }
        }
        return (remove ? 'Unlinked: ' : 'Linked: ') + areas.join(', ')
    }

    Integer subscriptionRescan(String[] fields, boolean remove, boolean following) {
        if (fields.length == 1) {
            return !remove && following ? 100 : null
        }
        if (remove || fields.length != 2 || !(fields[1] ==~ '/r(=[1-9][0-9]{0,4})?')) {
            throw new IllegalArgumentException('Usage: +area [/r[=count]], -area')
        }
        int count = fields[1] == '/r' ? 100 : Integer.parseInt(fields[1].substring(3))
        if (count > 10000) {
            throw new IllegalArgumentException('Rescan count exceeds 10000.')
        }
        return count
    }

    List available(request, String mask) {
        api.storage.areas.findAll { area ->
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
            if (api.storage.canReadEcho(peer, area)) {
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
