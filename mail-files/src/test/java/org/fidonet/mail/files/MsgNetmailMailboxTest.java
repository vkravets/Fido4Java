package org.fidonet.mail.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

class MsgNetmailMailboxTest {
    @TempDir
    Path temporary;

    private MsgNetmailMailbox mailbox(Charset charset) {
        return new MsgNetmailMailbox(temporary, charset, new FTNAddr("1:500/100"));
    }

    /** Independent fixture, using stored-message offsets from FidoHdr/FTS-0001. */
    private byte[] editorFile(Charset charset, int attributes, boolean chrs) {
        String text = "\1INTL 1:500/101 1:500/100\r\1FMPT 2\r\1TOPT 3\r"
                      + (chrs ? "\1CHRS: " + charset.name() + " 2\r" : "")
                      + "Привет\r";
        byte[] body = text.getBytes(charset);
        ByteBuffer file = ByteBuffer.allocate(190 + body.length + 1).order(ByteOrder.LITTLE_ENDIAN);
        file.put("Sender".getBytes(StandardCharsets.US_ASCII));
        file.position(36);
        file.put("Recipient".getBytes(StandardCharsets.US_ASCII));
        file.position(72);
        file.put("Subject".getBytes(StandardCharsets.US_ASCII));
        file.position(144);
        file.put("14 Nov 23  22:13:20".getBytes(StandardCharsets.US_ASCII));
        file.putShort(164, (short) 7);
        file.putShort(166, (short) 101);
        file.putShort(168, (short) 100);
        file.putShort(170, (short) 19);
        file.putShort(172, (short) 500);
        file.putShort(174, (short) 500);
        // Zero zones/points: INTL/FMPT/TOPT carry the complete addresses.
        file.putShort(184, (short) 42);
        file.putShort(186, (short) attributes);
        file.putShort(188, (short) 43);
        file.position(190);
        file.put(body);
        return file.array();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CP866", "KOI8-R", "UTF-8"})
    void readEditorMessageAndPreservedHeaderWhenAssigningIdAndSnt(String encoding)
            throws Exception {
        Charset charset = Charset.forName(encoding);
        byte[] original = editorFile(charset, MsgNetmailMailbox.LOCAL | 1, true);
        Path path = temporary.resolve("15.MSG");
        Files.write(path, original);
        MsgNetmailMailbox mailbox = mailbox(charset);
        Message message = mailbox.read().get(0);
        assertEquals(new FTNAddr("1:500/100.2"), message.getFAddr());
        assertEquals(new FTNAddr("1:500/101.3"), message.getTAddr());
        assertEquals("Привет\r", message.getBody().getBody());
        mailbox.scan(entry -> {
            entry.assignMessageId("1:500/100.2 12345678");
            entry.markSent();
        });
        byte[] updated = Files.readAllBytes(path);
        byte[] expectedHeader = Arrays.copyOf(original, 190);
        expectedHeader[186] |= 8;
        assertArrayEquals(expectedHeader, Arrays.copyOf(updated, 190));
        assertEquals("1:500/100.2 12345678", mailbox.read().get(0).getMsgId());
        assertEquals(message.getBody().getBody(), mailbox.read().get(0).getBody().getBody());
        mailbox.scan(entry -> {
            throw new AssertionError("Sent MSG was scanned twice");
        });
    }

    @Test
    void publishedNumberedMessagesWithoutOverwritingEditorFilesOrLastread() throws Exception {
        MsgNetmailMailbox mailbox = mailbox(StandardCharsets.UTF_8);
        byte[] original = editorFile(StandardCharsets.UTF_8, 1, true);
        Files.write(temporary.resolve("9.msg"), original);
        Files.writeString(temporary.resolve("lastread"), "editor state");
        Message message = mailbox.read().get(0);
        Path first = mailbox.append(message, MsgNetmailMailbox.LOCAL);
        Path second = mailbox.append(message, MsgNetmailMailbox.LOCAL);
        assertEquals("10.msg", first.getFileName().toString());
        assertEquals("11.msg", second.getFileName().toString());
        byte[] encoded = Files.readAllBytes(first);
        ByteBuffer header = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(101, Short.toUnsignedInt(header.getShort(166)));
        assertEquals(100, Short.toUnsignedInt(header.getShort(168)));
        assertEquals(1, Short.toUnsignedInt(header.getShort(176)));
        assertEquals(3, Short.toUnsignedInt(header.getShort(180)));
        assertEquals(2, Short.toUnsignedInt(header.getShort(182)));
        assertEquals(256, Short.toUnsignedInt(header.getShort(186)));
        assertEquals(0, encoded[encoded.length - 1]);
        assertArrayEquals(original, Files.readAllBytes(temporary.resolve("9.msg")));
        assertEquals("editor state", Files.readString(temporary.resolve("lastread")));
    }

    @Test
    void skippedReceivedAndSentMailAndLeftFailedSubmissionUnsent() throws Exception {
        Charset charset = StandardCharsets.UTF_8;
        Files.write(temporary.resolve("1.msg"), editorFile(charset, 1, true));
        Files.write(temporary.resolve("2.msg"), editorFile(charset, 264, true));
        Files.write(temporary.resolve("3.msg"), editorFile(charset, 256, true));
        MsgNetmailMailbox mailbox = mailbox(charset);
        int[] count = {0};
        assertThrows(IOException.class, () -> mailbox.scan(entry -> {
            count[0]++;
            throw new IOException("Queue unavailable");
        }));
        assertEquals(1, count[0]);
        assertEquals(256, mailbox.read().get(2).getAttributes());
    }

    @Test
    void rejectedTruncatedAmbiguousAndBusyMessages() throws Exception {
        MsgNetmailMailbox mailbox = mailbox(StandardCharsets.UTF_8);
        Path path = temporary.resolve("1.msg");
        Files.write(path, new byte[190]);
        assertThrows(IOException.class, mailbox::read);
        byte[] original = editorFile(StandardCharsets.UTF_8, 256, true);
        Files.write(path, original);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
                FileLock lock = file.getChannel().lock()) {
            assertThrows(IOException.class, mailbox::read);
        }
        Files.write(temporary.resolve("01.msg"), original);
        assertThrows(IOException.class, mailbox::read);
        Files.delete(temporary.resolve("01.msg"));
        original[original.length - 1] = 1;
        Files.write(path, original);
        assertThrows(IOException.class, mailbox::read);
    }

    @Test
    void usedConfiguredFallbackAndRejectedUnrepresentableFields() throws Exception {
        Charset koi8 = Charset.forName("KOI8-R");
        MsgNetmailMailbox mailbox = mailbox(koi8);
        Files.write(temporary.resolve("1.msg"), editorFile(koi8, 256, false));
        Message message = mailbox.read().get(0);
        assertEquals("Привет\r", message.getBody().getBody());
        assertThrows(IOException.class,
                     () -> mailbox.append(message.withSubject("x".repeat(72)), 256));
        assertEquals(1, mailbox.read().size());
        assertFalse(Files.exists(temporary.resolve("2.msg")));
    }
}
