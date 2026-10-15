package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class InternetEndpointTest {
    private List<InternetEndpoint> endpoints(String system, String flags) throws Exception {
        DirectoryEntry entry = DirectoryParserTest.parse(",101,"
                                                         + system
                                                         + ",City,Sysop,-Unpublished-,300,"
                                                         + flags
                                                         + "\n",
                                                         DirectoryParserTest.source(DirectorySource.Format.NODELIST,
                                                                                    1,
                                                                                    500,
                                                                                    false))
                                                  .get(0);
        return entry.getBinkpEndpoints();
    }

    @Test
    void handlesMultihomingPortsAndBracketedIpv6WithoutResolvingNames() throws Exception {
        List<InternetEndpoint> entries = endpoints("Station",
                                                   "INA:one.example,INA:[2001:db8::1],IBN:24555,IBN:two.example:24556,IBN:[2001:db8::2]:24557,IBN:two.example:24556");
        assertEquals(4, entries.size());
        assertEquals("one.example", entries.get(0).getHost());
        assertEquals(24555, entries.get(0).getPort());
        assertEquals("2001:db8::1", entries.get(1).getHost());
        assertEquals("two.example", entries.get(2).getHost());
        assertEquals(24556, entries.get(2).getPort());
        assertEquals("2001:db8::2", entries.get(3).getHost());
        assertEquals(24557, entries.get(3).getPort());
    }

    @Test
    void usesProtocolShorthandAndSystemFallbackButDoesNotInferIbnSupport() throws Exception {
        assertEquals("other.example",
                     endpoints("Station", "IFC:other.example:60180,IBN").get(0).getHost());
        assertEquals(24554, endpoints("node.example", "IBN").get(0).getPort());
        assertTrue(endpoints("node.example", "INA:node.example,IFC").isEmpty());
        assertTrue(endpoints("Station", "IBN").isEmpty());
        assertEquals("legacy.example",
                     endpoints("Station", "UINA:legacy.example,IBN").get(0).getHost());
        assertEquals("legacy.example.",
                     endpoints("Station", "U,INA:legacy.example.,IBN").get(0).getHost());
    }

    @Test
    void rejectsMalformedKnownEndpointsWhileRawFlagsRemainImportable() {
        for (String flag : List.of("IBN:host.example:0",
                                   "IBN:host.example:65536",
                                   "IBN:[2001:db8::1",
                                   "IBN:[not-ipv6]",
                                   "IBN:999.0.0.1",
                                   "INA:bad_host,IBN",
                                   "IBN:[]")) {
            assertThrows(IllegalArgumentException.class, () -> endpoints("Station", flag), flag);
        }
    }
}
