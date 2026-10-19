package org.fidonet.mail.files;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Lossless UTF-8/Unicode logical message snapshot; no wire normalization is applied to journals. */
final class MailRecord {
    private static final List<String> FIELDS = List.of("attributes",
                                                       "body",
                                                       "date",
                                                       "from",
                                                       "fromAddress",
                                                       "peer",
                                                       "subject",
                                                       "to",
                                                       "toAddress");

    private MailRecord() {
    }

    static void put(Properties target, Message message) {
        target.setProperty("from", message.getFrom());
        target.setProperty("to", message.getTo());
        target.setProperty("fromAddress", message.getFAddr().toString());
        target.setProperty("toAddress", message.getTAddr().toString());
        target.setProperty("subject", message.getSubject());
        target.setProperty("date", message.getMsgDate());
        target.setProperty("body", message.getBody().toString());
        target.setProperty("attributes", Integer.toString(message.getAttributes()));
        target.setProperty("peer",
                           message.getUpLink() == null ? "" : message.getUpLink().toString());
    }

    static Message get(Properties source) {
        String expected = source.getProperty("fingerprint");
        if (expected != null && !expected.equals(fingerprint(source))) {
            throw new IllegalStateException("Persistent mail snapshot failed its integrity check");
        }
        try {
            Date date = new SimpleDateFormat("dd MMM yy  HH:mm:ss",
                                             Locale.US).parse(source.getProperty("date"));
            Message result = new Message(source.getProperty("from"),
                                         source.getProperty("to"),
                                         new FTNAddr(source.getProperty("fromAddress")),
                                         new FTNAddr(source.getProperty("toAddress")),
                                         source.getProperty("subject"),
                                         source.getProperty("body"),
                                         date);
            result.setAttributes(Integer.parseInt(source.getProperty("attributes")));
            String peer = source.getProperty("peer");
            result.setUpLink(peer.isEmpty() ? null : new FTNAddr(peer));
            return result;
        } catch (ParseException | NullPointerException failure) {
            throw new IllegalStateException("Invalid persistent mail record", failure);
        }
    }

    static String fingerprint(Message message) {
        Properties values = new Properties();
        put(values, message);
        return fingerprint(values);
    }

    private static String fingerprint(Properties values) {
        StringBuilder text = new StringBuilder();
        FIELDS.forEach(key -> {
            String value = values.getProperty(key);
            if (value == null) {
                throw new IllegalStateException("Incomplete persistent mail snapshot");
            }
            text.append(key).append(':').append(value.length()).append(':').append(value);
        });
        return FileRecords.key(text.toString());
    }
}
