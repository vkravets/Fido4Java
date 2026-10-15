package org.fidonet.jftn.tosser.jam;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.echobase.jam.JamArea;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

public class JamSubmissionPolicyTest {
    @Test
    public void acceptsAnOrdinaryPostByTheLocalNode() {
        JamSubmissionPolicy.requireSupportedOperations(JamArea.LOCAL | JamArea.NET);
        JamSubmissionPolicy.requireLocalSender(message(),
                                               Collections.singleton(new FTNAddr("2:500/1")));
    }

    @Test
    public void rejectsAForeignSenderWithoutOpeningAnyStorage() {
        assertThrows(IllegalArgumentException.class,
                     () -> JamSubmissionPolicy.requireLocalSender(message(),
                                                                  Collections.singleton(new FTNAddr("2:500/3"))));
    }

    @Test
    public void rejectsFileOperationsBeforeSubmission() {
        assertThrows(UnsupportedOperationException.class,
                     () -> JamSubmissionPolicy.requireSupportedOperations(JamArea.LOCAL
                             | JamArea.FILE_OPERATIONS));
    }

    private Message message() {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("2:500/1"),
                           new FTNAddr("2:500/2"),
                           "Subject",
                           "Text\r",
                           new Date(1000000));
    }
}
