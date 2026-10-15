package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.fts.FidoPath;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.fts.SeenBy;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

public class FtscCompatibilityTest {
    private byte[] packed(String text, Charset charset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
        for (int n : new int[]{2, 101, 102, 500, 501, 0, 0}) {
            header.putShort((short) n);
        }
        header.put("12 Oct 26  12:34:56".getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
        out.write(header.array());
        for (String field : new String[]{"Получатель", "Автор", "Привет", text}) {
            out.write(field.getBytes(charset));
            out.write(0);
        }
        return out.toByteArray();
    }

    private ByteBuffer packet(byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(60 + message.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) 101);
        buffer.putShort(2, (short) 102);
        buffer.putShort(18, (short) 2);
        buffer.putShort(20, (short) 500);
        buffer.putShort(22, (short) 501);
        buffer.putShort(34, (short) 1);
        buffer.putShort(36, (short) 3);
        buffer.position(58);
        buffer.put(message).putShort((short) 0);
        buffer.flip();
        return buffer;
    }

    @Test
    public void readsLegacyHeaderWithoutTreatingReservedFieldsAsPoints() {
        ByteBuffer buffer = packet(new byte[0]);
        buffer.putShort(46, (short) 9);
        buffer.putShort(50, (short) 7);
        assertEquals(new FTNAddr(1, 500, 101, 0), new FtsPkt(buffer).getOrigaddr());
    }

    @Test
    public void validatesCapabilityWordAndResolvesAuxNet() {
        ByteBuffer buffer = packet(new byte[0]);
        buffer.putShort(20, (short) -1);
        buffer.putShort(38, (short) 500);
        buffer.putShort(40, (short) 256);
        buffer.putShort(44, (short) 1);
        buffer.putShort(46, (short) 1);
        buffer.putShort(50, (short) 7);
        assertEquals(new FTNAddr(1, 500, 101, 7), new FtsPkt(buffer).getOrigaddr());
        buffer.rewind();
        buffer.putShort(40, (short) 0);
        buffer.putShort(20, (short) 500);
        assertEquals(new FTNAddr(1, 500, 101, 0), new FtsPkt(buffer).getOrigaddr());
    }

    @Test
    public void parsesLittleEndianEvenForDefaultByteBuffer() throws Exception {
        byte[] bytes = packet(packed("Hello\r", StandardCharsets.US_ASCII)).array();
        assertEquals(1, new FtsPkt(ByteBuffer.wrap(bytes)).getMsgs().length);
    }

    @Test
    public void rejectsMissingTerminatorAndUnknownMessageType() {
        ByteBuffer buffer = packet(new byte[0]);
        buffer.limit(58);
        try {
            new FtsPkt(buffer);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        buffer = packet(new byte[]{3, 0});
        try {
            new FtsPkt(buffer);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void decodesUtf8HeaderAndBodyFromChrs() throws Exception {
        Message message = new Message(new FtsPackMsg(ByteBuffer.wrap(packed("\1CHRS: UTF-8 4\rПривет\r",
                                                                            StandardCharsets.UTF_8))),
                                      1,
                                      3);
        assertEquals("Привет", message.getSubject());
        assertEquals("Автор", message.getFrom());
        assertEquals("Привет\r", message.getBody().getBody());
        assertEquals("UTF-8", message.getMessageCharset());
    }

    @Test
    public void resolvesNetmailAddressAndUnsignedPointNumber() throws Exception {
        Message message = new Message(new FtsPackMsg(ByteBuffer.wrap(packed("\1INTL 3:501/102 1:500/101\r"
                + "\1FMPT 65535\r" + "\1TOPT 8\r" + "Hello\r", StandardCharsets.US_ASCII))), 1, 3);
        assertEquals(new FTNAddr(1, 500, 101, 65535), message.getFAddr());
        assertEquals(new FTNAddr(3, 501, 102, 8), message.getTAddr());
        assertEquals("Hello\r", message.getBody().getBody());
        assertTrue(message.getBody().toString().contains("\1FMPT 65535\r"));
    }

    @Test
    public void preservesQuotedMsgidAndUsesOriginForSender() {
        Message message = echo("\1MSGID: \"someone@example.net\" 12345678\r"
                               + "Hello\r"
                               + " * Origin: Test (1:500/101.7)\r");
        assertEquals("\"someone@example.net\" 12345678", message.getMsgId());
        assertEquals(new FTNAddr(1, 500, 101, 7), message.getFAddr());
        assertTrue(message.getBody().toString().startsWith("AREA:TEST\r\1MSGID:"));
    }

    @Test
    public void serializesEmptyRoutingListsAndPreservesControlParagraphs() {
        Message message = echo("\1MSGID: 1:500/101 12345678\r\1X-UNKNOWN: data\rHello\r");
        String text = message.getBody().toString();
        assertTrue(text.contains("\1MSGID: 1:500/101 12345678\r"));
        assertTrue(text.contains("\1X-UNKNOWN: data\r"));
        assertFalse(text.contains("null"));
        assertEquals(message.getBody().getKludges(),
                     echo(text.substring(text.indexOf('\r') + 1)).getBody().getKludges());
    }

    @Test
    public void supportsLargeTextButRejectsOversizedHeadersAndTruncation() throws Exception {
        FtsPackMsg message = new FtsPackMsg(ByteBuffer.wrap(packed("x".repeat(70000),
                                                                   StandardCharsets.US_ASCII)));
        assertEquals(70000, message.getBody().length);
        byte[] valid = packed("Hello", StandardCharsets.US_ASCII);
        try {
            new FtsPackMsg(ByteBuffer.wrap(Arrays.copyOf(valid, valid.length - 1)));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        ByteBuffer invalid = ByteBuffer.allocate(100);
        invalid.put(Arrays.copyOf(valid, 34)).put(new byte[36]);
        for (int i = 34; i < 70; i++) {
            invalid.put(i, (byte) 'x');
        }
        invalid.flip();
        try {
            new FtsPackMsg(invalid);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void handlesEmptyRoutingListsAndMultipleSpaces() {
        assertEquals(0, SeenBy.valueOf(null, 1).toSeenByStrings().length);
        assertEquals(0, FidoPath.valueOf("", 1).toPathStrings().length);
        assertEquals("500/101 102", SeenBy.valueOf("500/101  102", 1).toString());
    }

    @Test
    public void preservesRepeatedUnknownParagraphsAndAllowsHookEdits() {
        Message message = echo("\1X-UNKNOWN: first\r\1X-UNKNOWN: second\rHello\r");
        assertTrue(message.getBody()
                          .toString()
                          .contains("\1X-UNKNOWN: first\r\1X-UNKNOWN: second\r"));
        message.getBody().getKludges().put("X-UNKNOWN", " changed");
        String updated = message.getBody().toString();
        assertTrue(updated.contains("\1X-UNKNOWN: changed\r"));
        assertFalse(updated.contains("second"));
    }

    private Message echo(String body) {
        return new Message("from",
                           "to",
                           new FTNAddr(1, 500, 101, 0),
                           new FTNAddr(1, 501, 102, 0),
                           "subject",
                           "AREA:TEST\r" + body,
                           new Date());
    }
}
