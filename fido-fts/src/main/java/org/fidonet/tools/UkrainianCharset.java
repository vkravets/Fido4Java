package org.fidonet.tools;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/** CP848: IBM Ukrainian DOS with euro, unavailable in the standard JDK provider. */
final class UkrainianCharset extends Charset {
    private final String characters;

    UkrainianCharset() {
        super("CP848", new String[]{"IBM848", "IBM-848"});
        byte[] bytes = new byte[256];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) index;
        }
        char[] mapping = new String(bytes, Charset.forName("IBM866")).toCharArray();
        // IBM/ICU ibm-848_P100-1999.ucm: Ukrainian letters at F2-F9, euro at FD.
        String ukrainian = "\u0490\u0491\u0404\u0454\u0406\u0456\u0407\u0457";
        for (int index = 0; index < ukrainian.length(); index++) {
            mapping[0xf2 + index] = ukrainian.charAt(index);
        }
        mapping[0xfd] = '\u20ac';
        characters = new String(mapping);
    }

    @Override
    public boolean contains(Charset charset) {
        return charset instanceof UkrainianCharset || charset.name().equals("US-ASCII");
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this, 1, 1) {
            @Override
            protected CoderResult decodeLoop(ByteBuffer input, CharBuffer output) {
                while (input.hasRemaining()) {
                    if (!output.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    output.put(characters.charAt(input.get() & 0xff));
                }
                return CoderResult.UNDERFLOW;
            }
        };
    }

    @Override
    public CharsetEncoder newEncoder() {
        return new CharsetEncoder(this, 1, 1) {
            @Override
            protected CoderResult encodeLoop(CharBuffer input, ByteBuffer output) {
                while (input.hasRemaining()) {
                    char value = input.get(input.position());
                    if (Character.isSurrogate(value)) {
                        return surrogate(input, value);
                    }
                    int index = characters.indexOf(value);
                    if (index < 0) {
                        return CoderResult.unmappableForLength(1);
                    }
                    if (!output.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    output.put((byte) index);
                    input.get();
                }
                return CoderResult.UNDERFLOW;
            }
        };
    }

    private CoderResult surrogate(CharBuffer input, char value) {
        if (!Character.isHighSurrogate(value)) {
            return CoderResult.malformedForLength(1);
        }
        if (input.remaining() == 1) {
            return CoderResult.UNDERFLOW;
        }
        return Character.isLowSurrogate(input.get(input.position() + 1))
                ? CoderResult.unmappableForLength(2)
                : CoderResult.malformedForLength(1);
    }
}
