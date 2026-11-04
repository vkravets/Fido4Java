package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BodySerializationTest {
    @Test
    public void readerLinesPreservedInterleavedControlsWithoutEchoTrailers() {
        Message message = new Message("Author",
                                      "All",
                                      new FTNAddr("1:500/101"),
                                      null,
                                      "Subject",
                                      "AREA:TEST\r\1MSGID: 1:500/101 abc\r"
                                      + "First\r\1X-UNKNOWN: literal\r\1CHRS: UTF-8 4\r"
                                      + "Second\r\1CHRS: CP866 2\r> Quote\r"
                                      + " * Origin: source (1:500/101)\rSEEN-BY: 500/101\r"
                                      + "\1PATH: 500/101\r",
                                      new Date());
        Body body = message.getBody();
        String original = body.toString();
        assertEquals(List.of("\1MSGID: 1:500/101 abc",
                             "First",
                             "\1X-UNKNOWN: literal",
                             "\1CHRS: UTF-8 4",
                             "Second",
                             "\1CHRS: CP866 2",
                             "> Quote",
                             "\1PATH: 500/101"),
                     body.getReaderLines());
        assertEquals(original, body.toString());
        assertEquals("First\rSecond\r> Quote\r", body.getBody());
        assertTrue(body.getKludgeLines().contains("\1X-UNKNOWN: literal"));
    }

    @Test
    public void readerLinesPreservedNetmailViaAndLiteralBodyLines() {
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("1:500/101"),
                                      new FTNAddr("1:500/100"),
                                      "Subject",
                                      "\1INTL 1:500/100 1:500/101\rFirst\r\r"
                                      + "SEEN-BY: literal text\r\1Via 1:500/102\r",
                                      new Date());
        assertEquals(List.of("\1INTL 1:500/100 1:500/101",
                             "First",
                             "",
                             "SEEN-BY: literal text",
                             "\1Via 1:500/102"),
                     message.getBody().getReaderLines());
    }

    @Test
    public void extractedParsedKludgesWithDuplicatesAndRoutingWithoutChangingText() {
        Message message = new Message("Author",
                                      "All",
                                      new FTNAddr("1:500/101"),
                                      null,
                                      "Subject",
                                      "AREA:TEST\r\1MSGID: 1:500/101 abc\r"
                                      + "\1X-UNKNOWN: first\r\1X-UNKNOWN: second\r"
                                      + "Plain body\rSEEN-BY: 500/101\r"
                                      + "\1PATH: 500/101\r\1Via 1:500/102\r",
                                      new Date());
        Body body = message.getBody();
        String serialized = body.toString();
        String plain = body.getBody();
        assertEquals(List.of("\1MSGID: 1:500/101 abc",
                             "\1X-UNKNOWN: first",
                             "\1X-UNKNOWN: second",
                             "\1PATH: 500/101",
                             "\1Via 1:500/102"),
                     body.getKludgeLines());
        assertEquals(serialized, body.toString());
        assertEquals(plain, body.getBody());
    }

    @Test
    public void extractedConstructedKludgesReflectCurrentValues() {
        Body body = body("Plain body\n", new LinkedHashMap<>(), null);
        assertEquals(List.of(), body.getKludgeLines());
        body.getKludges().put("CHRS", "UTF-8 4");
        body.addVia("\1Via 1:500/102");
        assertEquals(List.of("\1CHRS: UTF-8 4", "\1Via 1:500/102"), body.getKludgeLines());
        body.getKludges().put("CHRS", "CP866 2");
        assertEquals(List.of("\1CHRS: CP866 2", "\1Via 1:500/102"), body.getKludgeLines());
        assertEquals(List.of("\1CHRS: CP866 2", "Plain body", "\1Via 1:500/102"),
                     body.getReaderLines());
    }

    @Test
    public void newAddressControlLinesUseSpaceSyntaxWithoutColons() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("INTL", " 2:500/2 2:500/1 ");
        values.put("FMPT", " 3 ");
        assertEquals("\1INTL 2:500/2 2:500/1\r\1FMPT 3\rtext",
                     body("text", values, null).toString());
    }

    private Body body(String text, Map<String, String> kludges, String origin) {
        return new Body(text, kludges, origin, new SeenBy(), new FidoPath());
    }

    @Test
    public void editsOrRemovesRepeatedControlLinesWithoutEmittingDuplicates() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("MSGID", "new");
        values.put("CHRS", " UTF-8 4");
        values.put("EMPTY", "");
        values.put("TOPT", " 3 ");
        Body body = body("text", values, " * Origin: here (2:500/1)");
        body.setArea("TEST");
        body.setControlLines(Arrays.asList("\1MSGID: old",
                                           "\1MSGID: duplicate",
                                           "\1REMOVED: old",
                                           "invalid"));
        assertEquals("AREA:TEST\r\1MSGID: new\r\1CHRS: UTF-8 4\r\1EMPTY:\r\1TOPT 3\r"
                     + "text\r * Origin: here (2:500/1)\r", body.toString());
    }

    @Test
    public void preservesRepeatedUnchangedControlsAndAvoidsExtraOriginNewline() {
        Body body = body("text\r", Collections.singletonMap("MSGID", " old"), "origin");
        body.setControlLines(Arrays.asList("\1MSGID: old", "\1MSGID: another"));
        assertEquals("\1MSGID: old\r\1MSGID: another\rtext\rorigin\r", body.toString());
        assertEquals("origin\r", body("", Collections.emptyMap(), "origin").toString());
        Body emptyArea = body("text", Collections.emptyMap(), null);
        emptyArea.setArea("");
        assertEquals("text", emptyArea.toString());
    }
}
