package org.fidonet.jftn.tosser.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.fidonet.types.Message;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

final class HptMessages {
    static final String TEXT = "Привет из интеграционного теста";
    static final String JAVA_ID = "2:500/102 ABCD1234";

    private HptMessages() {
    }

    static Message fromJava() {
        return new Message("Java Author",
                           "All",
                           HptScenario.LOCAL,
                           HptScenario.PEER,
                           "Java Тема",
                           "AREA:TEST\r\1MSGID: "
                           + JAVA_ID
                           + "\r\1REPLY: 2:500/101 aabbccdd\r"
                           + "\1CHRS: UTF-8 4\r"
                           + TEXT
                           + "\r"
                           + " * Origin: Interop (2:500/102)\r",
                           new Date(1700000000000L));
    }

    static void assertHptPost(Message message, boolean echo) {
        assertNotNull(message);
        assertEquals(echo, message.isEchomail());
        assertEquals(HptScenario.LOCAL, message.getFAddr());
        assertEquals("HPT Author", message.getFrom());
        assertEquals("HPT Тема", message.getSubject());
        assertNotNull(message.getMsgId());
        assertFalse(message.getMsgId().isEmpty());
        List<String> lines = Arrays.stream(message.getBody().getBody().split("\r"))
                                   .filter(line -> !line.isEmpty())
                                   .collect(Collectors.toList());
        assertEquals(echo ? Arrays.asList(TEXT, "--- ") : Arrays.asList(TEXT), lines);
    }
}
