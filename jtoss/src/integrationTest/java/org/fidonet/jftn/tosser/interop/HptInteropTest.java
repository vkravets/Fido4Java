package org.fidonet.jftn.tosser.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HptInteropTest {
    @Test
    void hptTossesJavaPacketsWithPointAddressesReplyAndViaWithoutDuplicatingMail()
            throws Exception {
        try (HptContainer peer = new HptContainer("java-packet-to-hpt")) {
            peer.start();
            peer.worker("write-java-packet");
            peer.hpt("toss");
            peer.worker("verify-java-packet");
            peer.hpt("toss");
            peer.worker("verify-java-packet");
        }
    }

    @Test
    public void hptHonorsJamLockHeldByJavaProcess() throws Exception {
        try (HptContainer peer = new HptContainer("jam-lock")) {
            peer.start();
            peer.holdJavaLock();
            assertEquals(124, peer.postWhileLocked());
            peer.releaseJavaLock();
            peer.postEcho();
            peer.worker("verify-single-post");
        }
    }

    @Test
    public void hptReadsAndScansJavaWrittenJamWithoutResendingEditorViews() throws Exception {
        try (HptContainer peer = new HptContainer("java-to-hpt")) {
            peer.start();
            peer.worker("export-java");
            peer.hpt("scan", "-w");
            peer.worker("handoff-to-hpt");
            peer.hpt("scan", "-w");
            peer.worker("verify-hpt-export");
        }
    }

    @Test
    public void importsHptPostedEchoAndPointNetmailAcrossWorkerRestart() throws Exception {
        try (HptContainer peer = new HptContainer("hpt-to-java")) {
            peer.start();
            peer.postMessages();
            peer.worker("import-posts");
            peer.worker("import-posts");
        }
    }

    @Test
    public void importsRealHptArchiveWithoutChangingJamOrRedistributingMail() throws Exception {
        try (HptContainer peer = new HptContainer("hpt-archive")) {
            peer.start();
            peer.postMessages();
            peer.worker("archive-posts");
        }
    }
}
