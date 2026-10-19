package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.util.Date;

class IncomingMailAttributesTest {
    @Test
    void receivingNodeOwnsDeliveryFlavoursAndLocalNetmailIsPrivateAndUnread() {
        Message message = message("Body\r");
        message.setAttributes(2 | 4 | 8 | 32 | 128 | 256 | 512 | 2048 | 4096);
        IncomingMailAttributes.apply(message, true);
        assertEquals(1 | 4096, message.getAttributes());
    }

    @Test
    void clearsSenderDeliveryStateOnTransitAndEchoWithoutChangingControlLines() {
        for (String body : new String[]{"Body\r", "AREA:TEST\rBody\r"}) {
            Message message = message(body);
            String before = message.getBody().toString();
            message.setAttributes(2 | 8 | 256 | 512);
            IncomingMailAttributes.apply(message, false);
            assertEquals(message.isEchomail() ? 0 : 32, message.getAttributes());
            assertEquals(before, message.getBody().toString());
        }
    }

    private Message message(String body) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("1:500/101"),
                           new FTNAddr("1:500/100"),
                           "Subject",
                           body,
                           new Date());
    }
}
