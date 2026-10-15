package org.fidonet.jftn.tosser.jam;

import org.fidonet.echobase.jam.JamArea;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.util.Set;

/** Pure admission checks; no SQL, files or delivery state are changed here. */
final class JamSubmissionPolicy {
    private JamSubmissionPolicy() {
    }

    static void requireSupportedOperations(int attributes) {
        if ((attributes & JamArea.FILE_OPERATIONS) != 0) {
            throw new UnsupportedOperationException("JAM file requests and attachment operations are unsupported");
        }
    }

    static void requireLocalSender(Message message, Set<FTNAddr> localAddresses) {
        if (!message.hasValidAddressing() || !localAddresses.contains(message.getFAddr())) {
            throw new IllegalArgumentException("Outgoing JAM message requires valid addresses and a local sender AKA");
        }
    }
}
