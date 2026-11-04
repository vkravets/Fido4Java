package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class ReaderKludgesTest {
    @Test
    void recognizedStandardAndHistoricalControlsWithoutInterpretingValues() {
        for (String tag : List.of("MSGID",
                                  "REPLY",
                                  "INTL",
                                  "FMPT",
                                  "TOPT",
                                  "PATH",
                                  "CHRS",
                                  "TZUTC",
                                  "TZUTCINFO",
                                  "Via",
                                  "FLAGS",
                                  "PID",
                                  "TID",
                                  "CHRC",
                                  "DOMAIN",
                                  "ENC",
                                  "ENCLFILE",
                                  "FWDFROM",
                                  "FWDMSGID",
                                  "REPLYADDR",
                                  "REPLYTO",
                                  "SPLIT",
                                  "SPTH",
                                  "GATECHK",
                                  "RFC")) {
            assertTrue(ReaderKludges.isKnown("\1" + tag + ": <literal>"), tag);
        }
        assertTrue(ReaderKludges.isKnown("\1vIA 1:500/100"));
        assertTrue(ReaderKludges.isKnown("\1original data"));
        assertTrue(ReaderKludges.isKnown("\1EOT"));
        assertFalse(ReaderKludges.isKnown("\1msgid: data"));
        assertFalse(ReaderKludges.isKnown("MSGID: ordinary text"));
        assertFalse(ReaderKludges.isKnown("\1CUSTOM: <literal>"));
        assertFalse(ReaderKludges.isKnown("\1"));
    }

    @Test
    void recognizedGatewayHeadersWithGoldedCaseAndDelimiterRules() {
        for (String line : List.of("\1Subject: data",
                                   "\1RFC-Subject: data",
                                   "\1RFCSubject: data",
                                   "\1rfc-subject: data",
                                   "\1X-Custom: data",
                                   "\1Resent-From: data",
                                   "\1From data",
                                   "\1#! rnews")) {
            assertTrue(ReaderKludges.isKnown(line), line);
        }
        assertFalse(ReaderKludges.isKnown("\1Subject data"));
        assertFalse(ReaderKludges.isKnown("\1X-Custom data"));
        assertFalse(ReaderKludges.isKnown("\1CUSTOM: data"));
    }
}
