package org.fidonet.fts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.tools.CharsetTools;
import org.fidonet.tools.StringTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

public class AddressAndTextBoundaryTest {
    @Test
    public void addressIdentityAndOrderingIncludeEveryComponent() {
        FTNAddr address = new FTNAddr("2:500/1.3@fidonet.org");
        assertEquals(new FTNAddr(2, 500, 1, 3), address);
        assertEquals(address.hashCode(), new FTNAddr(address).hashCode());
        assertEquals(address, address);
        assertFalse(address.equals(null));
        assertFalse(address.equals("2:500/1.3"));
        for (FTNAddr other : Arrays.asList(new FTNAddr(3, 500, 1, 3),
                                           new FTNAddr(2, 501, 1, 3),
                                           new FTNAddr(2, 500, 2, 3),
                                           new FTNAddr(2, 500, 1, 4))) {
            assertNotEquals(address, other);
            assertNotEquals(0, address.compareTo(other));
            assertEquals(-Integer.signum(address.compareTo(other)),
                         Integer.signum(other.compareTo(address)));
        }
        assertEquals(0, address.compareTo(new FTNAddr(address)));
        assertEquals("01f40001", address.toHex());
        assertEquals("2:500/1.3@fidonet.org", address.as5D());
        assertEquals("2:500/1.3", address.as4D());
        assertEquals(Integer.valueOf(0), FTNAddr.valueOf("2:500/1").getPnt());
        assertFalse(new FTNAddr("500/1").isValid());
        assertFalse(new FTNAddr("2:500").isValid());
    }

    @Test
    public void parsesLinkAliasesPasswordsAndDefaultOrExplicitPorts() {
        Link explicit = new Link("2:500/2, 2:500/1  invalid 2:500/1.3, secret, localhost:24554");
        assertEquals(Arrays.asList(new FTNAddr("2:500/1"), new FTNAddr("2:500/1.3")),
                     explicit.getMyaddr());
        assertEquals("secret", explicit.getPass());
        assertEquals("localhost", explicit.getHostAddress());
        assertEquals(24554, explicit.getPort());
        assertEquals("2:500/1.0 -> 2:500/2.0", explicit.toString());
        assertEquals("", new Link("2:500/2, 2:500/1, -, localhost").getPass());
        assertNull(new Link("2:500/2, 2:500/1").getPass());
        assertEquals(0, new Link("2:500/2, 2:500/1, secret, localhost").getPort());
        Link hostOnly = new Link(null, null, null, "localhost", 0);
        assertEquals("localhost", hostOnly.toString());
        Link noAliases = new Link(new FTNAddr("2:500/2"),
                                  Collections.emptyList(),
                                  null,
                                  "localhost",
                                  0);
        assertEquals("localhost", noAliases.toString());
        assertEquals("secret", new Link("2:500/2, 2:500/1, secret").getPass());
        try {
            new Link("2:500/2");
            fail("Missing AKA accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void wrapsWithoutSplittingWordsAndHonoursPrefix() {
        assertNull(StringTools.wrap(null, 5));
        assertEquals(Collections.singletonList(""), StringTools.wrap("", 5));
        assertEquals(Arrays.asList("a", "b"), StringTools.wrap("  a b", 1, ""));
        assertEquals(Arrays.asList("longword", "next"), StringTools.wrap("longword next", 3));
        assertEquals(Collections.singletonList(">longword"), StringTools.wrap("longword", 3, ">"));
        assertEquals(Arrays.asList("a", "b"), StringTools.wrap("a b", 0));
        assertEquals("a\rb\r", StringTools.join(Arrays.asList("a", "b")));
        assertEquals(">a\r>b\r", StringTools.join(Arrays.asList("a", "b"), ">"));
        assertEquals(">a;>b;", StringTools.join(Arrays.asList("a", "b"), ";", ">"));
        assertEquals("", StringTools.join(Collections.emptyList()));
    }

    @Test
    public void detectsFidoAliasesAndFallsBackForUnknownOrInvalidCharset() {
        Charset fallback = Charset.forName("CP866");
        for (String name : new String[]{"IBMPC", "+7_FIDO", "unknown-encoding", "bad charset"}) {
            assertEquals(fallback, CharsetTools.charsetDetect(name));
        }
        assertEquals(Charset.forName("UTF-8"), CharsetTools.charsetDetect("UTF-8"));
    }

    @Test
    public void addressListsAreImmutableViewsAndSupportSetOperations() {
        FTNAddr first = new FTNAddr("2:500/1");
        FTNAddr second = new FTNAddr("2:500/2");
        FidoPath path = FidoPath.valueOf("500/1", 2);
        FidoPath same = FidoPath.valueOf("500/1", 2);
        assertEquals(path, same);
        assertEquals(path.hashCode(), same.hashCode());
        assertFalse(path.equals(null));
        assertFalse(path.equals(SeenBy.valueOf("500/1", 2)));
        path.add(FidoPath.valueOf("500/2", 2));
        path.addAll(Collections.singleton(new FTNAddr("2:501/3")));
        assertTrue(path.getAddresses().contains(second));
        path.remove(first);
        assertFalse(path.getAddresses().contains(first));
        try {
            path.getAddresses().clear();
            fail("Mutable address view");
        } catch (UnsupportedOperationException expected) {
        }
        assertTrue(FidoPath.valueOf("  ", 2).getAddresses().isEmpty());
        assertTrue(FidoPath.valueOf(null, 2).getAddresses().isEmpty());
    }
}
