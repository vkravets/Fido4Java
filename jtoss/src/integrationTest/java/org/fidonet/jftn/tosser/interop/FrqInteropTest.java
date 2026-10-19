package org.fidonet.jftn.tosser.interop;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrqInteropTest {
    private void worker(MailPeer peer, String command) throws Exception {
        peer.worker(FrqScenario.class.getName(), command, "1:500/101", "normal");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sendsRealReqAndProcessesSrifWithoutEquatingRequestAckToAFileResponse(boolean available)
            throws Exception {
        try (MailPeers peers = new MailPeers("frq-" + (available ? "found" : "missing"))) {
            MailPeer sender = peers.start("sender");
            MailPeer receiver = peers.start("receiver");
            sender.configuration("binkd.cfg",
                                 "mail-interop/binkd.cfg",
                                 "1:500/100",
                                 "1:500/101",
                                 "receiver");
            receiver.configuration("binkd.cfg",
                                   "mail-interop/binkd.cfg",
                                   "1:500/101",
                                   "1:500/100",
                                   "sender");
            receiver.configuration("requests.cfg", "mail-interop/request-hook.cfg", "", "", "");
            worker(receiver, "prepare-request-server");
            worker(sender, available ? "prepare-request" : "prepare-missing-request");
            worker(sender, "verify-queued");
            receiver.serve("requests.cfg");
            sender.call("1:500/101");
            worker(receiver, "verify-request-handled");
            worker(sender, available ? "verify-response" : "verify-no-response");
            sender.call("1:500/101");
            worker(receiver, "verify-request-handled");
        }
    }
}
