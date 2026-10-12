package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.misc.PktTemp;
import org.fidonet.types.*;
import org.fidonet.jftn.tosser.exception.TosserException;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class TosserSecurityTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Tosser tosser(String password, Path root) throws Exception {
        Link link = new Link(new FTNAddr("2:467/1"), null, password, "localhost", 0);
        JFtnConfig config = new JFtnConfig() {
            @Override public Link getLink(FTNAddr address) { return link; }
            @Override public String getTmpDir() { return root.toString(); }
        };
        return new Tosser(config, null);
    }
    private boolean accept(String expected, String provided) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort(18, (short)2);
        byte[] pass = provided.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        packet.position(26); packet.put(pass); packet.position(0);
        Method method = Tosser.class.getDeclaredMethod("tosspkt", ByteBuffer.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(tosser(expected, temporary.getRoot().toPath()), packet);
    }
    @Test public void requiresExactConfiguredPassword() throws Exception {
        assertFalse(accept("secret", ""));
        assertFalse(accept("secret", "wrong"));
        assertTrue(accept("secret", "secret"));
        assertTrue(accept("", ""));
        assertTrue(accept("-", ""));
    }
    private void save(Tosser tosser, String name) throws Exception {
        Method method = Tosser.class.getDeclaredMethod("saveBad", PktTemp.class);
        method.setAccessible(true);
        try { method.invoke(tosser, new PktTemp(name, ByteBuffer.wrap(new byte[]{1, 2, 3}))); }
        catch (InvocationTargetException e) { throw (Exception) e.getCause(); }
    }
    @Test public void rejectsTraversalAndAbsolutePacketNames() throws Exception {
        Path root = temporary.newFolder("inbound").toPath();
        for (String name : new String[]{"../escape.pkt", "..\\escape.pkt", "/escape.pkt", "C:\\escape.pkt"}) {
            try { save(tosser("", root), name); fail("Accepted " + name); }
            catch (TosserException expected) { }
        }
        assertFalse(Files.exists(temporary.getRoot().toPath().resolve("escape.bad")));
    }
    @Test public void writesInsideDirectoryWithoutTrailingSeparator() throws Exception {
        Path root = temporary.newFolder("bad").toPath();
        save(tosser("", root), "message.pkt");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("message.bad")));
        save(tosser("", root), "message.pkt");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("message.bad")));
    }
}
