export const protocolLines = [
    '2026-10-16T12:00:00Z INFO binkp <<< [id=incoming remote=/127.0.0.1:24554] ADR 1:500/101',
    '2026-10-16T12:00:00Z INFO binkp <<< [id=incoming remote=/127.0.0.1:24554] PWD [redacted]',
    '2026-10-16T12:00:00Z INFO binkp session ready: peer=1:500/101, role=SERVER, NR=true',
    '2026-10-16T12:00:01Z INFO binkp <<< [id=incoming remote=/127.0.0.1:24554] FILE inbound.pkt 17009 1700000000 -1',
    '2026-10-16T12:00:01Z INFO binkp >>> [id=incoming remote=/127.0.0.1:24554] GET inbound.pkt 17009 1700000000 0',
    '2026-10-16T12:00:01Z INFO binkp file received: peer=1:500/101, name=inbound.pkt, bytes=17009, transferred=17009, elapsedMs=100, CPS=170090',
    '2026-10-16T12:00:01Z INFO binkp >>> [id=incoming remote=/127.0.0.1:24554] GOT inbound.pkt 17009 1700000000',
    '2026-10-16T12:00:02Z INFO binkp session ready: peer=1:500/102, role=CLIENT, NR=false',
    '2026-10-16T12:00:02Z INFO binkp file sent and acknowledged: peer=1:500/102, name=outbound.pkt, bytes=20003, transferred=20003, elapsedMs=100, CPS=200030'
];
