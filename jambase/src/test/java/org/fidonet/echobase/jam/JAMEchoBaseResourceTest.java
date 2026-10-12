package org.fidonet.echobase.jam;

import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.types.FTNAddr;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class JAMEchoBaseResourceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void releasesAlreadyOpenedFilesWhenNextFileCannotOpen() throws Exception {
        EchoList areas = new EchoList(temporary.newFile("areas").getAbsolutePath());
        areas.load();
        FTNAddr address = new FTNAddr("2:467/1");
        areas.addArea("test", temporary.getRoot().getAbsolutePath(), address, address);
        String path = areas.getEcho("test").Path;
        assertTrue(new File(path + ".jhr").createNewFile());
        assertTrue(new File(path + ".jdt").mkdir());
        JAMEchoBase base = new JAMEchoBase(areas);
        try {
            base.openArea("test");
            fail("Expected opening a directory as the text file to fail");
        } catch (EchoBaseException expected) {
            assertNull(field("header").get(base));
            assertNull(field("text").get(base));
            base.closeArea();
        }
    }

    @Test
    public void attemptsRemainingClosesAfterOneFileCloseFails() throws Exception {
        boolean[] closed = new boolean[2];
        JAMEchoBase base = new JAMEchoBase(null);
        JHRFile header = new JHRFile(temporary.newFile("header")) {
            @Override public void close() throws IOException {
                super.close();
                closed[0] = true;
                throw new IOException("header close failure");
            }
        };
        JDTFile text = new JDTFile(temporary.newFile("text")) {
            @Override public void close() throws IOException {
                super.close();
                closed[1] = true;
            }
        };
        field("header").set(base, header);
        field("text").set(base, text);
        try {
            base.closeArea();
            fail("Expected close failure");
        } catch (EchoBaseException expected) {
            assertArrayEquals(new boolean[]{true, true}, closed);
            base.closeArea();
        }
    }

    private Field field(String name) throws Exception {
        Field field = JAMEchoBase.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
