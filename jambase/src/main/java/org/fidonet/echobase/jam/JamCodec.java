package org.fidonet.echobase.jam;

import org.fidonet.echobase.jam.struct.MessageHeader;
import org.fidonet.echobase.jam.struct.SubField;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.zip.CRC32;

/** Conversion of the wire-independent JAM representation. */
final class JamCodec {
    private static final int[] FTN_BITS = {256, 1, 4, 8, 128, 512, 2, 2048, 16, 32, 4096, 16384};
    private static final int[] JAM_BITS = {1, 4, 8, 16, 32, 128, 256, 4096, 8192, 2, 65536, 131072};

    private JamCodec() {
    }

    static int crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        for (byte value : bytes) {
            int b = value & 255;
            crc.update(b >= 'A' && b <= 'Z' ? b + 32 : b);
        }
        return ~(int) crc.getValue();
    }

    static int timestamp(Date date) {
        return (int) LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
                                  .toEpochSecond(ZoneOffset.UTC);
    }

    static Date date(int timestamp) {
        return Date.from(LocalDateTime.ofEpochSecond(Integer.toUnsignedLong(timestamp),
                                                     0,
                                                     ZoneOffset.UTC)
                                      .atZone(ZoneId.systemDefault())
                                      .toInstant());
    }

    static MessageHeader encode(Message message, int attributes) throws IOException {
        Charset charset = CharsetTools.charsetDetect(message.getMessageCharset());
        MessageHeader header = new MessageHeader();
        header.Attribute = attributes;
        header.MSGIDcrc = -1;
        header.REPLYcrc = -1;
        header.PasswordCRC = -1;
        SimpleDateFormat format = new SimpleDateFormat("dd MMM yy  HH:mm:ss", Locale.US);
        format.setLenient(false);
        try {
            header.DateWritten = timestamp(format.parse(message.getMsgDate()));
        } catch (ParseException e) {
            throw new IOException("Invalid message date", e);
        }
        header.DateProcessed = timestamp(new Date());
        field(header,
              0,
              message.getFAddr() == null ? null : message.getFAddr().toString(),
              charset);
        field(header,
              1,
              message.getTAddr() == null ? null : message.getTAddr().toString(),
              charset);
        field(header, 2, message.getFrom(), charset);
        field(header, 3, message.getTo(), charset);
        field(header, 6, message.getSubject(), charset);
        for (String line : message.getBody().toString().split("\r", -1)) {
            if (line.startsWith("\1")) {
                String control = line.substring(1);
                int end = 0;
                while (end < control.length() && control.charAt(end) != ':'
                        && control.charAt(end) != ' ') {
                    end++;
                }
                String key = control.substring(0, end);
                String value = control.substring(end).replaceFirst("^:?[ ]*", "");
                int id;
                switch (key) {
                    case "INTL" :
                    case "FMPT" :
                    case "TOPT" :
                        continue;
                    case "MSGID" :
                        id = 4;
                        header.MSGIDcrc = crc(value.trim().getBytes(charset));
                        break;
                    case "REPLY" :
                        id = 5;
                        header.REPLYcrc = crc(value.trim().getBytes(charset));
                        break;
                    case "PID" :
                        id = 7;
                        break;
                    case "Via" :
                        id = 8;
                        break;
                    case "PATH" :
                        id = 2002;
                        break;
                    case "FLAGS" :
                        id = 2003;
                        break;
                    case "TZUTC" :
                        id = 2004;
                        break;
                    default :
                        id = 2000;
                        value = control;
                }
                field(header, id, value, charset);
            } else if (line.startsWith("SEEN-BY: ")) {
                field(header, 2001, line.substring(9), charset);
            }
        }
        header.SubfieldLen = header.getSubLen();
        return header;
    }

    static byte[] text(Message message) {
        StringBuilder text = new StringBuilder();
        String[] lines = message.getBody().toString().split("\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == 0 && message.isEchomail() && line.startsWith("AREA:")) {
                continue;
            }
            if (!line.startsWith("\1") && !line.startsWith("SEEN-BY: ") && i < lines.length - 1) {
                text.append(line).append('\r');
            }
        }
        return text.toString().getBytes(CharsetTools.charsetDetect(message.getMessageCharset()));
    }

    static Message decode(MessageHeader header,
                          byte[] text,
                          String area,
                          Charset fallback,
                          FTNAddr aka)
            throws IOException {
        Charset charset = fallback;
        for (SubField field : header.SubFieldList) {
            String value = new String(field.buffer, StandardCharsets.US_ASCII);
            if (field.loID == 2000 && (value.startsWith("CHRS:") || value.startsWith("CHARSET:"))) {
                charset = CharsetTools.charsetDetect(value.substring(value.indexOf(':') + 1)
                                                          .trim()
                                                          .split("\\s+")[0]);
            }
        }
        StringBuilder controls = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        if (area != null) {
            controls.append("AREA:").append(area).append('\r');
        }
        for (SubField field : header.SubFieldList) {
            String value = new String(field.buffer, charset);
            switch (field.loID) {
                case 4 :
                    controls.append("\1MSGID: ").append(value).append('\r');
                    break;
                case 5 :
                    controls.append("\1REPLY: ").append(value).append('\r');
                    break;
                case 7 :
                    controls.append("\1PID: ").append(value).append('\r');
                    break;
                case 8 :
                    tail.append("\1Via ").append(value).append('\r');
                    break;
                case 2000 :
                    controls.append('\1').append(value).append('\r');
                    break;
                case 2001 :
                    tail.append("SEEN-BY: ").append(value).append('\r');
                    break;
                case 2002 :
                    tail.append("\1PATH: ").append(value).append('\r');
                    break;
                case 2003 :
                    controls.append("\1FLAGS ").append(value).append('\r');
                    break;
                case 2004 :
                    controls.append("\1TZUTC: ").append(value).append('\r');
                    break;
                default :
                    break;
            }
        }
        try {
            Message message = new Message(value(header, 2, charset),
                                          value(header, 3, charset),
                                          address(header, 0, charset, aka),
                                          address(header, 1, charset, aka),
                                          value(header, 6, charset),
                                          controls + new String(text, charset) + tail,
                                          date(header.DateWritten));
            message.setAttributes(fromJam(header.Attribute));
            if ((header.Attribute & 0x400) != 0) {
                String flags = message.getBody().getKludges().getOrDefault("FLAGS", "");
                message.getBody().getKludges().put("FLAGS", flags + " DIR");
            }
            return message;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid JAM addressing", e);
        }
    }

    private static FTNAddr address(MessageHeader header,
                                   int id,
                                   Charset charset,
                                   FTNAddr fallback) {
        String value = value(header, id, charset);
        return value.isEmpty() ? fallback : new FTNAddr(value);
    }

    private static String value(MessageHeader header, int id, Charset charset) {
        for (SubField field : header.SubFieldList) {
            if (field.loID == id) {
                return new String(field.buffer, charset);
            }
        }
        return "";
    }

    private static void field(MessageHeader header, int id, String value, Charset charset) {
        if (value != null) {
            SubField field = new SubField();
            field.loID = (short) id;
            field.buffer = value.getBytes(charset);
            field.datalen = field.buffer.length;
            header.SubFieldList.add(field);
        }
    }

    static int toJam(Message message) {
        int result = message.isEchomail() ? JamArea.ECHO : JamArea.NET;
        for (int i = 0; i < FTN_BITS.length; i++) {
            if ((message.getAttributes() & FTN_BITS[i]) != 0) {
                result |= JAM_BITS[i];
            }
        }
        return result;
    }

    private static int fromJam(int attributes) {
        int result = 0;
        for (int i = 0; i < FTN_BITS.length; i++) {
            if ((attributes & JAM_BITS[i]) != 0) {
                result |= FTN_BITS[i];
            }
        }
        return result;
    }
}
