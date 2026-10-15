package org.fidonet.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.fidonet.types.Link;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class PacketPasswordTest {
    @Test
    public void packetPasswordCanDifferFromSessionPassword() {
        JFtnConfig config = new JFtnConfig() {
            @Override
            public List<String> getValuesAsList(String key) {
                return Collections.singletonList("2:500/101,packet");
            }
        };
        Link link = new Link("2:500/101,2:500/102,long-session-password,localhost");
        assertEquals("packet", config.getPacketPassword(link));
        assertEquals("long-session-password", link.getPass());
    }

    @Test
    public void rejectsUnrepresentablePasswordsInsteadOfTruncatingThem() {
        for (String value : new String[]{"123456789", "пароль"}) {
            try {
                new JFtnConfig().getPacketPassword(new Link("2:500/101,2:500/102," + value));
                fail("Invalid packet password accepted");
            } catch (IllegalArgumentException expected) {
                assertEquals(true, expected.getMessage().contains("eight ASCII bytes"));
            }
        }
    }
}
