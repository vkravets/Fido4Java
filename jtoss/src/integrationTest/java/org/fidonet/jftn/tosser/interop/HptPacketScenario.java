package org.fidonet.jftn.tosser.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.nio.file.Files;
import java.util.Date;
import java.util.List;

/** Exercises the actual HPT packet reader rather than only JFtn's matching parser. */
final class HptPacketScenario {
    private HptPacketScenario() {
    }

    private static Message message() {
        Message message = new Message("Packet Author",
                                      "Test Sysop",
                                      new FTNAddr("2:500/101.3"),
                                      HptScenario.LOCAL,
                                      "JFtn packet interoperability",
                                      "\1MSGID: 2:500/101.3 1234abcd\r\1REPLY: 2:500/102 11223344\r"
                                      + "\1CHRS: UTF-8 4\rPacket body\r"
                                      + "\1Via 2:500/101 @20261019.090807.UTC JFtn 1.0-dev\r",
                                      new Date(1700000000000L));
        message.setAttributes(1 | 0x20 | 0x80 | 0x100);
        return message;
    }

    static void write(HptScenario scenario) throws Exception {
        Files.write(scenario.root.resolve("inbound/12345678.pkt"),
                    PacketWriter.write(HptScenario.PEER,
                                       HptScenario.LOCAL,
                                       "SeCrEt",
                                       List.of(message())));
    }

    static void verify(HptScenario scenario) throws Exception {
        assertEquals(1, scenario.netmail.read().size());
        Message expected = message();
        Message actual = scenario.netmail.read().get(0).getMessage();
        assertEquals(expected.getFAddr(), actual.getFAddr());
        assertEquals(expected.getTAddr(), actual.getTAddr());
        assertEquals(expected.getMsgDate(), actual.getMsgDate());
        assertEquals(expected.getSubject(), actual.getSubject());
        assertEquals(expected.getMsgId(), actual.getMsgId());
        assertEquals("2:500/102 11223344", actual.getBody().getKludges().get("REPLY").trim());
        assertTrue(actual.getBody().toString().contains("Packet body\r"));
        assertTrue(actual.getBody()
                         .toString()
                         .contains("\1Via 2:500/101 @20261019.090807.UTC JFtn 1.0-dev"));
    }
}
