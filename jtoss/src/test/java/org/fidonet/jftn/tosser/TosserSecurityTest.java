package org.fidonet.jftn.tosser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.exception.TosserException;
import org.fidonet.misc.PktTemp;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class TosserSecurityTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Tosser tosser(String password, Path root) throws Exception {
        Link link = new Link(new FTNAddr("2:467/1"),
                             Collections.singletonList(new FTNAddr("0:0/0")),
                             password,
                             "localhost",
                             0);
        JFtnConfig config = new JFtnConfig() {
            @Override
            public Link getLink(FTNAddr address) {
                return link;
            }

            @Override
            public String getTmpDir() {
                return root.toString();
            }
        };
        return new Tosser(config, null);
    }

    private boolean accept(String expected, String provided) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort(18, (short) 2);
        byte[] pass = provided.getBytes(StandardCharsets.US_ASCII);
        packet.position(26);
        packet.put(pass);
        packet.position(0);
        Method method = Tosser.class.getDeclaredMethod("tosspkt", ByteBuffer.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(tosser(expected, temporary.getRoot().toPath()), packet);
    }

    @Test
    public void requiresExactConfiguredPassword() throws Exception {
        assertFalse(accept("secret", ""));
        assertFalse(accept("secret", "wrong"));
        assertTrue(accept("secret", "secret"));
        assertTrue(accept("", ""));
        assertTrue(accept("-", ""));
    }

    private void save(Tosser tosser, String name) throws Exception {
        Method method = Tosser.class.getDeclaredMethod("saveBad", PktTemp.class);
        method.setAccessible(true);
        try {
            method.invoke(tosser, new PktTemp(name, ByteBuffer.wrap(new byte[]{1, 2, 3})));
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    public void rejectsTraversalAndAbsolutePacketNames() throws Exception {
        Path root = temporary.newFolder("inbound").toPath();
        for (String name : new String[]{"../escape.pkt", "..\\escape.pkt", "/escape.pkt",
                "C:\\escape.pkt"}) {
            try {
                save(tosser("", root), name);
                fail("Accepted " + name);
            } catch (TosserException expected) {
            }
        }
        assertFalse(Files.exists(temporary.getRoot().toPath().resolve("escape.bad")));
    }

    @Test
    public void writesInsideDirectoryWithoutTrailingSeparator() throws Exception {
        Path root = temporary.newFolder("bad").toPath();
        save(tosser("", root), "message.pkt");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("message.bad")));
        save(tosser("", root), "message.pkt");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("message.bad")));
    }

    @Test
    public void validatesInboundAndIgnoresDirectoriesAndUnrelatedFiles() throws Exception {
        Path root = temporary.newFolder("input").toPath();
        Tosser tosser = tosser("", root);
        for (String invalid : new String[]{null, root.resolve("missing").toString()}) {
            try {
                tosser.runFast(invalid);
                fail("Invalid inbound must fail");
            } catch (TosserException expected) {
            }
        }
        tosser.runFast(root.toString());
        Files.createDirectory(root.resolve("directory.mo0"));
        Files.writeString(root.resolve("notes.txt"), "keep");
        tosser.runFast(root.toString());
        assertEquals("keep", Files.readString(root.resolve("notes.txt")));
        assertTrue(Files.isDirectory(root.resolve("directory.mo0")));
    }

    @Test
    public void badPacketWithoutPktExtensionIsSavedWithoutOverwriting() throws Exception {
        Path root = temporary.newFolder("bad-other").toPath();
        save(tosser("", root), "message.bin");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("message.bin.bad")));
    }
}
