package org.fidonet.jftn.tosser.interop;

import org.fidonet.fileecho.HtickScenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HtickInteropTest {
    private void worker(MailPeer peer, String command) throws Exception {
        peer.worker(HtickScenario.class.getName(), command);
    }

    @Test
    void exchangesGeneratedTicsThroughBinkdAndImportsHtickReplacesAcrossRestart() throws Exception {
        exchange(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-password", "bad-crc", "bad-area"})
    void retainsRejectedRealHtickDeliveriesAndImportsThemAfterRepair(String problem)
            throws Exception {
        exchange(problem);
    }

    private void exchange(String problem) throws Exception {
        try (MailPeers peers = new MailPeers("htick-"
                                             + (problem == null ? "roundtrip" : problem))) {
            MailPeer java = peers.start("sender");
            MailPeer htick = peers.start("receiver");
            java.configuration("binkd.cfg",
                               "mail-interop/binkd.cfg",
                               "1:500/100",
                               "1:500/101",
                               "receiver");
            htick.configuration("binkd.cfg",
                                "mail-interop/binkd.cfg",
                                "1:500/101",
                                "1:500/100",
                                "sender");
            htick.configuration("htick.cfg", "mail-interop/htick.cfg", "", "", "");
            worker(htick, "init");
            htick.exec("tparser", MailPeer.ROOT + "/htick.cfg");
            worker(java, "prepare");
            htick.serve();
            java.call("1:500/101");
            htick.exec("timeout", "30s", "htick", "-c", MailPeer.ROOT + "/htick.cfg", "toss");
            worker(htick, "verify-htick");
            htick.exec("timeout",
                       "30s",
                       "htick",
                       "-c",
                       MailPeer.ROOT + "/htick.cfg",
                       "hatch",
                       MailPeer.ROOT + "/UPDATE.ZIP",
                       "interop",
                       "replace",
                       "JAVA.ZIP",
                       "desc",
                       "HTick description");
            java.serve();
            htick.call("1:500/100");
            worker(java, problem == null ? "receive" : problem);
            if (problem != null) {
                worker(java, "repair");
            }
            worker(java, "verify-imported");
            htick.call("1:500/100");
            worker(java, "verify-imported");
        }
    }
}
