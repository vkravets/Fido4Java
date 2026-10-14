package org.fidonet;

import static org.junit.Assert.assertEquals;

import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BodySerializationTest {
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
