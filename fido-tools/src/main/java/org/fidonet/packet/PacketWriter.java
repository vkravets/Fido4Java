package org.fidonet.packet;

import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/** Type-2+ packets, with final message addresses independent of the next-hop envelope. */
public final class PacketWriter {
    private PacketWriter() {
    }

    public static byte[] write(FTNAddr origin,
                               FTNAddr destination,
                               String password,
                               List<Message> messages)
            throws IOException {
        validate(origin);
        validate(destination);
        if (password == null || password.length() > 8 || password.indexOf('\0') >= 0
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(password)) {
            throw new IOException("Invalid packet password");
        }
        LocalDateTime now = LocalDateTime.now();
        ByteBuffer header = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) origin.getNode().intValue());
        header.putShort((short) destination.getNode().intValue());
        for (int value : new int[]{now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond(), 0, 2, origin.getNet(),
                destination.getNet()}) {
            header.putShort((short) value);
        }
        header.put((byte) 255).put((byte) 1);
        header.put(password.getBytes(StandardCharsets.US_ASCII));
        header.position(34);
        header.putShort((short) origin.getZone().intValue());
        header.putShort((short) destination.getZone().intValue());
        header.putShort((short) 0).putShort((short) 0x0100);
        header.put((byte) 255).put((byte) 0).putShort((short) 1);
        header.putShort((short) origin.getZone().intValue());
        header.putShort((short) destination.getZone().intValue());
        header.putShort((short) origin.getPnt().intValue());
        header.putShort((short) destination.getPnt().intValue());
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(header.array());
        for (Message message : messages) {
            append(packet, message, destination);
        }
        packet.write(0);
        packet.write(0);
        return packet.toByteArray();
    }

    private static void append(ByteArrayOutputStream packet, Message source, FTNAddr nextHop)
            throws IOException {
        FTNAddr from = source.getFAddr();
        FTNAddr to = source.isEchomail() ? nextHop : source.getTAddr();
        validate(from);
        validate(to);
        Message message = new Message(source.getFrom(),
                                      source.getTo(),
                                      from,
                                      to,
                                      source.getSubject(),
                                      source.getBody().toString(),
                                      new Date());
        if (message.isEchomail()) {
            for (String key : new String[]{"INTL", "FMPT", "TOPT"}) {
                message.getBody().getKludges().remove(key);
            }
        } else {
            message.getBody().getKludges().put("INTL", node(to) + " " + node(from));
            point(message, "FMPT", from.getPnt());
            point(message, "TOPT", to.getPnt());
        }
        ByteBuffer header = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : new int[]{2, from.getNode(), to.getNode(), from.getNet(), to.getNet(),
                source.getAttributes() & 0x7013, 0}) {
            header.putShort((short) value);
        }
        byte[] date = source.getMsgDate().getBytes(StandardCharsets.US_ASCII);
        if (date.length != 19) {
            throw new IOException("Invalid FTN message date");
        }
        header.put(date).put((byte) 0);
        packet.write(header.array());
        Charset charset = CharsetTools.charsetDetect(source.getMessageCharset());
        field(packet, source.getTo(), charset, 35);
        field(packet, source.getFrom(), charset, 35);
        field(packet, source.getSubject(), charset, 71);
        field(packet, message.getBody().toString(), charset, 64 * 1024 * 1024);
    }

    private static String node(FTNAddr address) {
        return address.getZone() + ":" + address.getNet() + "/" + address.getNode();
    }

    private static void point(Message message, String key, int point) {
        if (point == 0) {
            message.getBody().getKludges().remove(key);
        } else {
            message.getBody().getKludges().put(key, Integer.toString(point));
        }
    }

    private static void validate(FTNAddr address) throws IOException {
        if (address == null || !address.isValid()) {
            throw new IOException("Missing FTN address");
        }
        for (int value : new int[]{address.getZone(), address.getNet(), address.getNode(),
                address.getPnt()}) {
            if (value < 0 || value > 65535) {
                throw new IOException("Address component exceeds unsigned word");
            }
        }
    }

    private static void field(ByteArrayOutputStream out, String value, Charset charset, int maximum)
            throws IOException {
        if (value == null || value.indexOf('\0') >= 0) {
            throw new IOException("Invalid null-terminated message field");
        }
        ByteBuffer bytes = charset.newEncoder().encode(CharBuffer.wrap(value));
        if (bytes.remaining() > maximum) {
            throw new IOException("Message field exceeds " + maximum + " bytes");
        }
        byte[] encoded = new byte[bytes.remaining()];
        bytes.get(encoded);
        out.write(encoded);
        out.write(0);
    }
}
