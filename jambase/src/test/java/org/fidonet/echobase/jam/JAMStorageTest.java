package org.fidonet.echobase.jam;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.jam.struct.FixedHeaderInfoStruct;
import org.fidonet.echobase.jam.struct.MessageHeader;
import org.fidonet.echobase.jam.struct.SubField;
import org.fidonet.misc.MyCRC;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;

public class JAMStorageTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private EchoList areas() throws Exception {
        EchoList areas = new EchoList(temporary.newFile().getAbsolutePath());
        areas.load();
        FTNAddr address = new FTNAddr("1:500/100");
        areas.addArea("test", temporary.getRoot().getAbsolutePath(), address, address);
        return areas;
    }

    @Test
    public void storesUtf8MessageWithoutMsgidUsingUnknownIdCrc() throws Exception {
        EchoList areas = areas();
        String path = areas.getEcho("test").Path;
        Message message = new Message("Автор",
                                      "Читатель",
                                      new FTNAddr("2:500/1"),
                                      new FTNAddr("2:500/2"),
                                      "Тема",
                                      "AREA:TEST\r\1CHRS: UTF-8 4\rТекст\r",
                                      new Date());
        JAMEchoBase base = new JAMEchoBase(areas);
        base.openArea("test");
        try {
            base.addMessage(message);
        } finally {
            base.closeArea();
        }
        assertArrayEquals(JamCodec.text(message),
                          Files.readAllBytes(new File(path + ".jdt").toPath()));
        try (JHRFile header = new JHRFile(new File(path + ".jhr"))) {
            MessageHeader stored = header.getMsgHeaderByShift(1024);
            assertEquals(-1, stored.MSGIDcrc);
            assertNull(stored.SubFieldList.stream()
                                          .filter(field -> field.loID == 4)
                                          .findFirst()
                                          .orElse(null));
        }
    }

    @Test
    public void createsValidBaseAndAppendsMessagesAcrossReopen() throws Exception {
        EchoList areas = areas();
        String path = areas.getEcho("test").Path;
        JAMEchoBase base = new JAMEchoBase(areas);
        long before = Integer.toUnsignedLong(JamCodec.timestamp(new Date()));
        base.openArea("test");
        base.closeArea();
        try (JHRFile header = new JHRFile(new File(path + ".jhr"))) {
            FixedHeaderInfoStruct fixed = header.getFixedHeader();
            assertTrue(fixed.getDatecreated() >= before);
            assertTrue(fixed.getDatecreated() <= Integer.toUnsignedLong(JamCodec.timestamp(new Date())));
            assertEquals(0, fixed.getActivemsg());
            assertEquals(1, fixed.getBasemsgnum());
            assertEquals(1, fixed.getModcounter());
            assertEquals(-1, fixed.getPasswordcrc());
            assertNull(header.getMsgHeaderByShift(0));
        }
        assertEquals(1024, new File(path + ".jhr").length());
        assertTrue(new File(path + ".jlr").isFile());
        Message first = message("first", true);
        Message second = message("second", false);
        for (Message message : new Message[]{first, second}) {
            base.openArea("test");
            try {
                base.addMessage(message);
            } finally {
                base.closeArea();
            }
        }
        byte[] data = Files.readAllBytes(new File(path + ".jdt").toPath());
        Charset encoding = Charset.forName(CharsetTools.DEFAULT_ENCODING);
        byte[] firstText = JamCodec.text(first);
        byte[] secondText = JamCodec.text(second);
        ByteBuffer index = ByteBuffer.wrap(Files.readAllBytes(new File(path + ".jdx").toPath()))
                                     .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(16, index.remaining());
        assertEquals(MyCRC.CRC("reader".getBytes()), index.getInt());
        int firstOffset = index.getInt();
        index.getInt();
        int secondOffset = index.getInt();
        try (JHRFile header = new JHRFile(new File(path + ".jhr"))) {
            assertEquals(2, header.getFixedHeader().getActivemsg());
            assertEquals(3, header.getFixedHeader().getModcounter());
            MessageHeader one = header.getMsgHeaderByShift(firstOffset);
            MessageHeader two = header.getMsgHeaderByShift(secondOffset);
            assertEquals(1024, firstOffset);
            assertEquals(1, one.MessageNumber);
            assertEquals(2, two.MessageNumber);
            assertEquals(0, one.Offset);
            assertEquals(firstText.length, two.Offset);
            assertEquals(firstText.length, one.TxtLen);
            assertEquals(secondText.length, two.TxtLen);
            assertArrayEquals(firstText, Arrays.copyOfRange(data, 0, two.Offset));
            assertArrayEquals(secondText, Arrays.copyOfRange(data, two.Offset, data.length));
            assertEquals("Author", subfield(one, 2));
            assertEquals("Reader", subfield(one, 3));
            assertEquals("first", subfield(one, 6));
            assertEquals(first.getBody().getKludges().get("REPLY").trim(), subfield(one, 5));
            assertEquals(first.getBody().getKludges().get("TZUTC").trim(), subfield(one, 2004));
            assertEquals(JamCodec.crc(first.getMsgId().getBytes()), one.MSGIDcrc);
            assertEquals(JamCodec.crc(first.getBody().getKludges().get("REPLY").trim().getBytes()),
                         one.REPLYcrc);
        }
    }

    @Test
    public void headerWithoutSubfieldsRoundTripsAndEmptyIndexHasNoMessage() throws Exception {
        File file = temporary.newFile();
        try (JHRFile header = new JHRFile(file)) {
            FixedHeaderInfoStruct fixed = new FixedHeaderInfoStruct();
            fixed.setBasemsgnum(1);
            fixed.setPasswordcrc(-1);
            header.setFixedHeader(fixed);
            MessageHeader message = new MessageHeader();
            message.MessageNumber = 7;
            message.TxtLen = 123;
            message.DateWritten = 1700000000;
            int offset = header.writeHeader(message);
            MessageHeader restored = header.getMsgHeaderByShift(offset);
            assertEquals(7, restored.MessageNumber);
            assertEquals(123, restored.TxtLen);
            assertEquals(1700000000, restored.DateWritten);
            assertTrue(restored.SubFieldList.isEmpty());
        }
        try (JDXFile index = new JDXFile(temporary.newFile())) {
            assertEquals(0, index.getLastMessageShift());
            index.writeIndex("Reader", 1024);
            index.writeIndex("Reader", 1100);
            assertEquals(1100, index.getLastMessageShift());
        }
    }

    @Test
    public void rejectsTruncatedHeadersAndMalformedSubfields() throws Exception {
        File file = temporary.newFile();
        try (JHRFile header = new JHRFile(file)) {
            expectIOException(header::getFixedHeader);
            header.setFixedHeader(new FixedHeaderInfoStruct());
            expectIOException(() -> header.getMsgHeaderByShift(1024));
            MessageHeader message = new MessageHeader();
            SubField field = new SubField();
            field.buffer = new byte[]{1};
            field.datalen = 1;
            message.SubFieldList.add(field);
            message.SubfieldLen = message.getSubLen();
            int offset = header.writeHeader(message);
            try (RandomAccessFile corrupt = new RandomAccessFile(file, "rw")) {
                corrupt.seek(offset + MessageHeader.MessageHeaderSize + 4);
                corrupt.writeInt(Integer.reverseBytes(-1));
            }
            expectIOException(() -> header.getMsgHeaderByShift(offset));
        }
    }

    private static Message message(String subject, boolean reply) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("1:500/100"),
                           new FTNAddr("1:500/101"),
                           subject,
                           "AREA:TEST\r\1MSGID: 1:500/100 "
                           + subject
                           + "\r"
                           + (reply ? "\1REPLY: 1:500/101 reply\r\1TZUTC: +0200\r" : "")
                           + "\1PID: JFtn\rHello\rSEEN-BY: 500/100 101\r\1PATH: 500/100\r",
                           new Date(1700000000000L));
    }

    @Test
    public void rejectsInvalidSignaturesAndSubfieldSizes() throws Exception {
        int[][] corruptions = {{0, 0}, {8, -1}, {8, Integer.MAX_VALUE}, {8, 4},
                {MessageHeader.MessageHeaderSize + 4, 2}};
        for (int[] corruption : corruptions) {
            File file = temporary.newFile();
            try (JHRFile header = new JHRFile(file)) {
                header.setFixedHeader(new FixedHeaderInfoStruct());
                MessageHeader message = new MessageHeader();
                SubField field = new SubField();
                field.buffer = new byte[]{1};
                field.datalen = 1;
                message.SubFieldList.add(field);
                message.SubfieldLen = message.getSubLen();
                int offset = header.writeHeader(message);
                try (RandomAccessFile corrupt = new RandomAccessFile(file, "rw")) {
                    corrupt.seek(offset + corruption[0]);
                    corrupt.writeInt(Integer.reverseBytes(corruption[1]));
                }
                expectIOException(() -> header.getMsgHeaderByShift(offset));
            }
        }
        File file = temporary.newFile();
        Files.write(file.toPath(), new byte[1024]);
        try (JHRFile header = new JHRFile(file)) {
            expectIOException(header::getFixedHeader);
        }
    }

    private interface IOOperation {
        void run() throws IOException;
    }

    private static void expectIOException(IOOperation operation) throws IOException {
        try {
            operation.run();
            fail("Expected malformed JAM data to be rejected");
        } catch (IOException expected) {
        }
    }

    private static String subfield(MessageHeader header, int id) {
        for (SubField field : header.SubFieldList) {
            if (field.loID == id) {
                return new String(field.buffer, StandardCharsets.US_ASCII);
            }
        }
        throw new AssertionError("Missing subfield " + id);
    }
}
