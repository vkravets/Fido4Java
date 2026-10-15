package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class DirectoryParserTest {
    static DirectorySource source(DirectorySource.Format format,
                                  int zone,
                                  int net,
                                  boolean checksum) {
        return new DirectorySource("test",
                                   format,
                                   Path.of("unused"),
                                   zone,
                                   net,
                                   StandardCharsets.US_ASCII,
                                   checksum);
    }

    static List<DirectoryEntry> parse(String text, DirectorySource source) throws IOException {
        List<DirectoryEntry> entries = new ArrayList<>();
        DirectoryParser.parse(new ByteArrayInputStream(text.getBytes(source.getCharset())),
                              source,
                              entries::add);
        return entries;
    }

    static String row(String type, int number, String name) {
        return type + "," + number + "," + name + ",Test_City,Jane_Doe,-Unpublished-,300\r\n";
    }

    static String checked(String body) {
        int crc = 0;
        for (byte value : body.getBytes(StandardCharsets.US_ASCII)) {
            crc = DirectoryParser.crc16(crc, value & 255);
        }
        return ";A Test list day 288 : " + String.format("%05d", crc) + "\r\n" + body + "\u001a";
    }

    @Test
    void restoresHierarchyAndRetainsUnknownAndUserFlags() throws Exception {
        String body = row("Zone", 1, "Zone") + row("Region", 50, "Region") + row("Host", 500, "Net")
                + row("Hub", 100, "Hub")
                + ",101,node.example,Test_City,Jane_Doe,-Unpublished-,300,CM,INA:node.example,IBN,U,ENC,X_NEW\r\n"
                + row("Point", 9, "Point") + row("Pvt", 102, "Private")
                + row("Hold", 103, "Holding") + row("Down", 104, "Offline")
                + row("Future", 105, "Unknown") + row("Host", 501, "Other_net")
                + row("", 101, "Other_node");
        List<DirectoryEntry> entries = parse(checked(body),
                                             source(DirectorySource.Format.NODELIST, 0, 0, true));
        assertEquals(12, entries.size());
        DirectoryEntry node = entries.get(4);
        assertEquals(new FTNAddr("1:500/101"), node.getAddress());
        assertEquals(50, node.getRegion());
        assertEquals(100, node.getHub());
        assertEquals("Jane Doe", node.getSysop());
        assertEquals("Test City", node.getLocation());
        assertEquals(List.of("CM", "INA:node.example", "IBN", "U", "ENC", "X_NEW"),
                     node.getFlags());
        assertEquals(new FTNAddr("1:500/101.9"), entries.get(5).getAddress());
        assertEquals("FUTURE", entries.get(9).getKeyword());
        assertEquals(null, entries.get(11).getHub());
        assertEquals(new FTNAddr("1:501/101"), entries.get(11).getAddress());
    }

    @ParameterizedTest
    @EnumSource(value = DirectorySource.Format.class, names = {"BOSS", "POSS"})
    void readsBossAndPossWithoutTurningPointNumbersIntoNodeNumbers(DirectorySource.Format format)
            throws Exception {
        String body = "Boss,1:500/101\r\n"
                      + row("", 1, "Point_one")
                      + row("Point", 2, "Point_two")
                      + "Boss,1:500/102,Ignored,Metadata\r\n"
                      + row("", 1, "Other_point");
        List<DirectoryEntry> entries = parse(body, source(format, 0, 0, false));
        assertEquals(List.of(new FTNAddr("1:500/101.1"),
                             new FTNAddr("1:500/101.2"),
                             new FTNAddr("1:500/102.1")),
                     entries.stream().map(DirectoryEntry::getAddress).collect(Collectors.toList()));
    }

    @Test
    void readsFakeNetSystemAddressAndUbossOverride() throws Exception {
        String text = row("Host", 32061, "500/101")
                      + row("", 1, "Arbitrary_name")
                      + "Host,32062,Not_an_address,City,Sysop,-Unpublished-,300,UBOSS:500/102\n"
                      + row("", 2, "Other_name");
        List<DirectoryEntry> entries = parse(text,
                                             source(DirectorySource.Format.FAKENET, 1, 0, false));
        assertEquals(2, entries.size());
        assertEquals(new FTNAddr("1:500/101.1"), entries.get(0).getAddress());
        assertEquals(new FTNAddr("1:500/102.2"), entries.get(1).getAddress());
        assertThrows(IOException.class,
                     () -> parse(text, source(DirectorySource.Format.FAKENET, 0, 0, false)));
    }

    @Test
    void readsFixedWidthFidoUserAndExplicitTextVariant() throws Exception {
        String first = String.format("%-45s%-20s\r\n", "Doe, Jane", "1:500/101.1");
        String second = String.format("%-45s%-20s\r\n", "Smith, John", "500/101.2");
        DirectorySource source = source(DirectorySource.Format.FIDOUSER, 1, 0, false);
        List<DirectoryEntry> entries = parse(first + second, source);
        assertEquals("Jane Doe", entries.get(0).getSysop());
        assertEquals(new FTNAddr("1:500/101.2"), entries.get(1).getAddress());
        assertThrows(IOException.class, () -> parse(second + first, source));
        assertThrows(IOException.class, () -> parse(first + second.stripTrailing() + "\n", source));
        assertThrows(IOException.class, () -> parse("Doe, Jane  1:500/101.1\n", source));
        List<DirectoryEntry> text = parse("Doe, Jane\t1:500/101.1\n",
                                          source(DirectorySource.Format.FIDOUSER_TEXT,
                                                 0,
                                                 0,
                                                 false));
        assertEquals("Jane Doe", text.get(0).getSysop());
    }

    @Test
    void verifiesCrcAgainstKnownXmodemVectorAndCanonicalLineEndings() throws Exception {
        int crc = 0;
        for (byte value : "123456789".getBytes(StandardCharsets.US_ASCII)) {
            crc = DirectoryParser.crc16(crc, value);
        }
        assertEquals(0x31c3, crc);
        String text = checked(row("Host", 500, "Net") + row("", 101, "Node"));
        DirectorySource source = source(DirectorySource.Format.NODELIST, 1, 0, true);
        assertEquals(2, parse(text, source).size());
        assertEquals(2, parse(text.replace("\r\n", "\n"), source).size());
        assertEquals(2, parse(text.replace("\r\n", "\r"), source).size());
        assertThrows(IOException.class, () -> parse(text.replace("Node", "Edited"), source));
        assertThrows(IOException.class, () -> parse(row("Host", 500, "Net"), source));
        assertThrows(IOException.class,
                     () -> parse(";A day 288 : 100000\n" + row("Host", 500, "Net"),
                                 source(DirectorySource.Format.NODELIST, 1, 0, false)));
    }

    @Test
    void rejectsBrokenContextsControlsRangesAndDataAfterDosEof() {
        DirectorySource source = source(DirectorySource.Format.NODELIST, 0, 0, false);
        for (String text : List.of(row("", 101, "Missing_context"),
                                   row("Host", 0, "Zero"),
                                   row("Host", 65536, "Too_large"),
                                   "; comments_only\n",
                                   "\n",
                                   "\u001aExtra",
                                   "\tBad")) {
            assertThrows(IOException.class, () -> parse(text, source), text);
        }
        assertThrows(IOException.class,
                     () -> parse(row("Point", 1, "No_boss"),
                                 source(DirectorySource.Format.BOSS, 1, 500, false)));
        IOException error = assertThrows(IOException.class,
                                         () -> parse("Boss,1:500/101\n,1,too,few\n",
                                                     source(DirectorySource.Format.BOSS,
                                                            0,
                                                            0,
                                                            false)));
        assertTrue(error.getMessage().startsWith("test:2:"));
    }

    @Test
    void supportsConfiguredCharsetAndLongFieldsWithoutTruncation() throws Exception {
        DirectorySource source = new DirectorySource("test",
                                                     DirectorySource.Format.NODELIST,
                                                     Path.of("unused"),
                                                     1,
                                                     500,
                                                     Charset.forName("CP866"),
                                                     false);
        String name = "Long_".repeat(100);
        List<DirectoryEntry> entries = parse(",101," + name + ",City,Тест,-Unpublished-,300\n",
                                             source);
        assertEquals(name, entries.get(0).getSystem());
        assertEquals("Тест", entries.get(0).getSysop());
        assertThrows(IOException.class, () -> parse(",101," + "X".repeat(65536), source));
    }
}
