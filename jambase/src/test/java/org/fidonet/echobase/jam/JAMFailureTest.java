package org.fidonet.echobase.jam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.echobase.jam.struct.FixedHeaderInfoStruct;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.Date;

public class JAMFailureTest {
    @Test
    public void rejectsEachCorruptedSignatureByte() throws Exception {
        for (int index = 0; index < 4; index++) {
            File path = Files.createTempFile(temporary.toPath(), "test-", ".tmp").toFile();
            try (JHRFile header = new JHRFile(path)) {
                header.setFixedHeader(new FixedHeaderInfoStruct());
                try (RandomAccessFile corrupt = new RandomAccessFile(path, "rw")) {
                    corrupt.seek(index);
                    corrupt.write(0xff);
                }
                try {
                    header.getFixedHeader();
                    fail("Invalid signature accepted");
                } catch (IOException expected) {
                }
            }
        }
    }

    @Test
    public void appendsEchomailWithoutRecipientAddress() throws Exception {
        EchoList list = areas(temporary.getPath());
        JAMEchoBase base = new JAMEchoBase(list);
        Message echo = new Message("Author",
                                   "All",
                                   new FTNAddr("2:500/1"),
                                   null,
                                   "subject",
                                   "AREA:TEST\r\1MSGID: 2:500/1 unique\rHello\r",
                                   new Date());
        base.openArea("test");
        try {
            base.addMessage(echo);
        } finally {
            base.closeArea();
        }
        assertTrue(new File(list.getEcho("test").Path + ".jdt").length() > 0);
    }

    @TempDir
    public File temporary;

    private EchoList areas(String root) throws Exception {
        EchoList list = new EchoList(Files.createTempFile(temporary.toPath(), "test-", ".tmp")
                                          .toFile()
                                          .getPath());
        list.addArea("test", root, new FTNAddr("2:500/1"), new FTNAddr("2:500/1"));
        return list;
    }

    private Message message() {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("2:500/1"),
                           new FTNAddr("2:500/2"),
                           "subject",
                           "AREA:TEST\r\1MSGID: 2:500/1 unique\rHello\r",
                           new Date());
    }

    @Test
    public void unknownAreaAndMissingDirectoryHaveUsefulExceptions() throws Exception {
        JAMEchoBase empty = new JAMEchoBase(new EchoList(Files.createTempFile(temporary.toPath(),
                                                                              "test-",
                                                                              ".tmp")
                                                              .toFile()
                                                              .getPath()));
        try {
            empty.openArea("missing");
            fail("Unknown area accepted");
        } catch (EchoBaseException expected) {
            assertTrue(expected.getMessage().contains("missing"));
        }
        JAMEchoBase inaccessible = new JAMEchoBase(areas(new File(temporary, "missing").getPath()));
        try {
            inaccessible.createArea("test");
            fail("Missing directory accepted");
        } catch (EchoBaseException expected) {
            assertTrue(expected.getCause() instanceof IOException);
        }
    }

    @Test
    public void truncatedIndexPreventsAppendWithoutWritingMessageFiles() throws Exception {
        EchoList list = areas(temporary.getPath());
        JAMEchoBase base = new JAMEchoBase(list);
        base.openArea("test");
        try {
            String path = list.getEcho("test").Path;
            Files.write(new File(path + ".jdx").toPath(), new byte[]{1});
            try {
                base.addMessage(message());
                fail("Broken index accepted");
            } catch (EchoBaseException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
            assertEquals(1024, new File(path + ".jhr").length());
            assertEquals(0, new File(path + ".jdt").length());
        } finally {
            base.closeArea();
        }
    }

    @Test
    public void malformedDateIsReportedBeforeAppend() throws Exception {
        EchoList list = areas(temporary.getPath());
        JAMEchoBase base = new JAMEchoBase(list);
        Message invalid = new Message("Author",
                                      "Reader",
                                      new FTNAddr("2:500/1"),
                                      new FTNAddr("2:500/2"),
                                      "subject",
                                      "AREA:TEST\r\1MSGID: 2:500/1 unique\rHello\r",
                                      new Date()) {
            @Override
            public String getMsgDate() {
                return "invalid";
            }
        };
        base.openArea("test");
        try {
            try {
                base.addMessage(invalid);
                fail("Bad date accepted");
            } catch (EchoBaseException expected) {
                assertTrue(expected.getCause() instanceof ParseException);
            }
            assertEquals(0, new File(list.getEcho("test").Path + ".jdt").length());
        } finally {
            base.closeArea();
        }
    }
}
