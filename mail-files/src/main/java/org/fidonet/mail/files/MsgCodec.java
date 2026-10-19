package org.fidonet.mail.files;

import org.fidonet.fts.FtsPackMsg;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** FTSC stored-message header (190 bytes), followed by NUL-terminated text. */
final class MsgCodec {
    static final int HEADER_SIZE = 190;
    static final int ATTR_OFFSET = 186;
    static final int MAX_SIZE = 64 * 1024 * 1024;
    private static final Pattern ENCODING = Pattern.compile("(?:^|\\r)\\n?\\u0001(?:CHRS|CHARSET|CODEPAGE):\\s*\\S+");

    private MsgCodec() {
    }

    static Message decode(byte[] bytes, Charset fallback, FTNAddr aka) throws IOException {
        if (bytes.length <= HEADER_SIZE || bytes.length > MAX_SIZE
                || bytes[bytes.length - 1] != 0) {
            throw new IOException("Truncated or oversized stored MSG");
        }
        int end = HEADER_SIZE;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        if (end != bytes.length - 1) {
            throw new IOException("Unexpected data after stored MSG terminator");
        }
        byte[] text = Arrays.copyOfRange(bytes, HEADER_SIZE, end);
        String controls = new String(text, StandardCharsets.ISO_8859_1);
        boolean declared = ENCODING.matcher(controls).find();
        Charset charset = declared ? FtsPackMsg.detectCharset(text) : fallback;
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        FTNAddr from = address(header, false, aka);
        FTNAddr to = address(header, true, aka);
        SimpleDateFormat date = new SimpleDateFormat("dd MMM yy  HH:mm:ss", Locale.US);
        date.setLenient(false);
        try {
            Message message = new Message(field(bytes, 0, 36, charset),
                                          field(bytes, 36, 36, charset),
                                          from,
                                          to,
                                          field(bytes, 72, 72, charset),
                                          new String(text, charset),
                                          date.parse(field(bytes,
                                                           144,
                                                           20,
                                                           StandardCharsets.US_ASCII)));
            message.setAttributes(Short.toUnsignedInt(header.getShort(ATTR_OFFSET)));
            if (!declared) {
                message.getBody().getKludges().put("CHRS", charsetControl(fallback));
            }
            if (message.isEchomail() || !message.hasValidAddressing()) {
                throw new IOException("Stored MSG requires valid netmail addressing");
            }
            return message;
        } catch (ParseException | IllegalArgumentException failure) {
            throw new IOException("Invalid stored MSG header", failure);
        }
    }

    static byte[] encode(Message original, Charset fallback, int attributes) throws IOException {
        if (original.isEchomail() || !original.hasValidAddressing()) {
            throw new IOException("Stored MSG requires valid netmail addressing");
        }
        Message message = original.copy();
        FTNAddr from = message.getFAddr();
        FTNAddr to = message.getTAddr();
        message.getBody().getKludges().put("INTL", node(to) + " " + node(from));
        point(message, "FMPT", from.getPnt());
        point(message, "TOPT", to.getPnt());
        boolean declared = message.getBody().getKludges().containsKey("CHRS")
                || message.getBody().getKludges().containsKey("CHARSET")
                || message.getBody().getKludges().containsKey("CODEPAGE");
        Charset charset = declared
                ? CharsetTools.charsetDetect(message.getMessageCharset())
                : fallback;
        // Persist the fallback too, so the packet writer uses the same encoding on scan.
        if (!declared) {
            message.getBody().getKludges().put("CHRS", charsetControl(charset));
        }
        byte[] body = encodeText(message.getBody().toString(), charset);
        if (body.length > MAX_SIZE - HEADER_SIZE - 1) {
            throw new IOException("Stored MSG exceeds size limit");
        }
        ByteBuffer result = ByteBuffer.allocate(HEADER_SIZE + body.length + 1)
                                      .order(ByteOrder.LITTLE_ENDIAN);
        field(result, 0, 36, message.getFrom(), charset);
        field(result, 36, 36, message.getTo(), charset);
        field(result, 72, 72, message.getSubject(), charset);
        field(result, 144, 20, message.getMsgDate(), StandardCharsets.US_ASCII);
        result.putShort(166, word(to.getNode()));
        result.putShort(168, word(from.getNode()));
        result.putShort(172, word(from.getNet()));
        result.putShort(174, word(to.getNet()));
        result.putShort(176, word(to.getZone()));
        result.putShort(178, word(from.getZone()));
        result.putShort(180, word(to.getPnt()));
        result.putShort(182, word(from.getPnt()));
        result.putShort(ATTR_OFFSET, (short) attributes);
        result.position(HEADER_SIZE);
        result.put(body).put((byte) 0);
        return result.array();
    }

    private static FTNAddr address(ByteBuffer header, boolean destination, FTNAddr aka) {
        int zone = Short.toUnsignedInt(header.getShort(destination ? 176 : 178));
        return new FTNAddr(zone == 0 ? aka.getZone() : zone,
                           Short.toUnsignedInt(header.getShort(destination ? 174 : 172)),
                           Short.toUnsignedInt(header.getShort(destination ? 166 : 168)),
                           Short.toUnsignedInt(header.getShort(destination ? 180 : 182)));
    }

    private static String node(FTNAddr address) {
        return address.getZone() + ":" + address.getNet() + "/" + address.getNode();
    }

    private static String charsetControl(Charset charset) {
        return charset.name() + (charset.equals(StandardCharsets.UTF_8) ? " 4" : " 2");
    }

    private static void point(Message message, String key, int value) {
        if (value == 0) {
            message.getBody().getKludges().remove(key);
        } else {
            message.getBody().getKludges().put(key, Integer.toString(value));
        }
    }

    private static short word(int value) throws IOException {
        if (value < 0 || value > 65535) {
            throw new IOException("Stored MSG address exceeds unsigned word");
        }
        return (short) value;
    }

    private static String field(byte[] bytes, int start, int size, Charset charset) {
        int end = start;
        while (end < start + size && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, start, end - start, charset);
    }

    private static void field(ByteBuffer buffer, int offset, int size, String text, Charset charset)
            throws IOException {
        byte[] bytes = encodeText(text, charset);
        if (bytes.length >= size) {
            throw new IOException("Stored MSG field exceeds " + (size - 1) + " bytes");
        }
        buffer.position(offset);
        buffer.put(bytes);
    }

    private static byte[] encodeText(String text, Charset charset) throws IOException {
        if (text == null || text.indexOf('\0') >= 0) {
            throw new IOException("Invalid stored MSG text");
        }
        ByteBuffer encoded = charset.newEncoder().encode(CharBuffer.wrap(text));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        return result;
    }
}
