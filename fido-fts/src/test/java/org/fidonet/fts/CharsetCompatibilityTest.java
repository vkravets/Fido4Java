package org.fidonet.fts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.tools.CharsetTools;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class CharsetCompatibilityTest {
    @Test
    void mapsFtscAliasesToTheirActualCharsets() {
        Map<String, String> aliases = Map.of("LATIN-1",
                                             "ISO-8859-1",
                                             "LATIN-2",
                                             "ISO-8859-2",
                                             "LATIN-5",
                                             "ISO-8859-9",
                                             "LATIN-9",
                                             "ISO-8859-15",
                                             "CP10000",
                                             "x-MacRoman");
        aliases.forEach((identifier, expected) -> {
            assertEquals(Charset.forName(expected), CharsetTools.charsetDetect(identifier));
            assertEquals(Charset.forName(expected),
                         CharsetTools.charsetDetect(identifier.toLowerCase()));
        });
    }

    @Test
    void codepageOverridesOnlyLegacyIbmpcInEitherControlLineOrder() {
        for (String text : new String[]{"\1CHRS: IBMPC 2\r\1CODEPAGE: 1251\r",
                "\1CODEPAGE: 1251  \r\1CHARSET: IBMPC 2\r"}) {
            assertEquals(Charset.forName("windows-1251"),
                         FtsPackMsg.detectCharset(text.getBytes(StandardCharsets.US_ASCII)));
        }
        assertEquals(StandardCharsets.UTF_8,
                     FtsPackMsg.detectCharset("\1CHRS: UTF-8 4\r\1CODEPAGE: 1251\r".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Charset.forName("CP866"),
                     FtsPackMsg.detectCharset("\1CHRS: IBMPC 2\r\1CODEPAGE: invalid\r".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void ukrainianDosDecodesKnownBytesAndRoundTripsAllBytes() {
        Charset charset = CharsetTools.charsetDetect("CP848");
        byte[] ukrainian = new byte[]{(byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5,
                (byte) 0xf6, (byte) 0xf7, (byte) 0xf8, (byte) 0xf9, (byte) 0xfd};
        assertEquals("ҐґЄєІіЇї€", new String(ukrainian, charset));
        assertArrayEquals(ukrainian, "ҐґЄєІіЇї€".getBytes(charset));
        byte[] all = new byte[256];
        for (int index = 0; index < all.length; index++) {
            all[index] = (byte) index;
        }
        assertArrayEquals(all, new String(all, charset).getBytes(charset));
        assertEquals(charset, CharsetTools.charsetDetect("ibm-848"));
    }

    @Test
    void ukrainianCodecPreservesPositionsAtBufferBoundariesAndReportsInvalidInput() {
        Charset charset = CharsetTools.charsetDetect("CP848");
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{(byte) 0xf2});
        assertEquals(CoderResult.OVERFLOW,
                     charset.newDecoder().decode(bytes, CharBuffer.allocate(0), false));
        assertEquals(0, bytes.position());
        CharBuffer text = CharBuffer.wrap("Ґ");
        assertEquals(CoderResult.OVERFLOW,
                     charset.newEncoder().encode(text, ByteBuffer.allocate(0), false));
        assertEquals(0, text.position());
        assertTrue(charset.newEncoder()
                          .encode(CharBuffer.wrap("😀"), ByteBuffer.allocate(4), true)
                          .isUnmappable());
        assertTrue(charset.newEncoder()
                          .encode(CharBuffer.wrap("\ud800x"), ByteBuffer.allocate(4), true)
                          .isMalformed());
    }
}
