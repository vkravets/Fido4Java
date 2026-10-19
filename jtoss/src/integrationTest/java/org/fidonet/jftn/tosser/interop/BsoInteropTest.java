package org.fidonet.jftn.tosser.interop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BsoInteropTest {
    private static final String NODE = "1:500/101";

    private void configure(MailPeer peer, String local, String remote, String host)
            throws Exception {
        peer.configuration("binkd.cfg", "mail-interop/binkd.cfg", local, remote, host);
    }

    private void worker(MailPeer peer, String command, String address, String flavour)
            throws Exception {
        peer.worker(BsoScenario.class.getName(), command, address, flavour);
    }

    @Test
    void exchangesRealPacketsAndAttachmentsAndAppliesActionsAfterMailerAcknowledgement()
            throws Exception {
        try (MailPeers peers = new MailPeers("packets-and-attachments")) {
            MailPeer sender = peers.start("sender");
            MailPeer receiver = peers.start("receiver");
            configure(sender, "1:500/100", NODE, "receiver");
            configure(receiver, NODE, "1:500/100", "sender");
            worker(sender, "prepare-batch", NODE, "normal");
            sender.call(NODE);
            worker(sender, "verify-actions-pending", NODE, "normal");
            receiver.serve();
            sender.call(NODE);
            worker(receiver, "verify-batch-received", NODE, "normal");
            worker(sender, "verify-actions-sent", NODE, "normal");
            worker(sender, "verify-actions-sent", NODE, "normal");
            sender.call(NODE);
            worker(receiver, "verify-batch-received", NODE, "normal");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "crash", "direct", "immediate"})
    void consumesStandardBsoFlavours(String flavour) throws Exception {
        exchange(NODE, flavour);
    }

    @Test
    void consumesHoldOnlyAfterAnExplicitPoll() throws Exception {
        exchange(NODE, "hold");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1:500/101.3", "2:500/101.3"})
    void consumesPointAndOtherZoneControlPaths(String address) throws Exception {
        exchange(address, "normal");
    }

    @Test
    void respectsAnExistingPeerBusyLockBeforePublishingOrCalling() throws Exception {
        try (MailPeers peers = new MailPeers("busy-peer")) {
            MailPeer sender = peers.start("sender");
            MailPeer receiver = peers.start("receiver");
            configure(sender, "1:500/100", NODE, "receiver");
            configure(receiver, NODE, "1:500/100", "sender");
            receiver.serve();
            worker(sender, "prepare-busy", NODE, "normal");
            sender.callAvailable();
            worker(receiver, "verify-empty", NODE, "normal");
            worker(sender, "verify-busy", NODE, "normal");
            worker(sender, "release-busy", NODE, "normal");
            sender.call(NODE);
            worker(receiver, "verify-busy-delivered", NODE, "normal");
            worker(sender, "verify-sent", NODE, "normal");
        }
    }

    private void exchange(String address, String flavour) throws Exception {
        try (MailPeers peers = new MailPeers("delivery-"
                                             + address.replaceAll("[^0-9]", "-")
                                             + "-"
                                             + flavour)) {
            MailPeer sender = peers.start("sender");
            MailPeer receiver = peers.start("receiver");
            configure(sender, "1:500/100", address, "receiver");
            configure(receiver, address, "1:500/100", "sender");
            worker(sender, "prepare-one", address, flavour);
            worker(sender, "verify-queued", address, flavour);
            receiver.serve();
            if (flavour.equals("hold")) {
                sender.callAvailable();
                worker(receiver, "verify-empty", address, flavour);
                worker(sender, "verify-queued", address, flavour);
            }
            sender.call(address);
            worker(receiver, "verify-one-received", address, flavour);
            worker(sender, "verify-sent", address, flavour);
            sender.call(address);
            worker(receiver, "verify-one-received", address, flavour);
        }
    }
}
