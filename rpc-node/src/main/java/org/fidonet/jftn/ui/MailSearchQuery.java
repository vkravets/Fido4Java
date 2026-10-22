package org.fidonet.jftn.ui;

import org.fidonet.types.Message;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Literal Unicode case-insensitive search over reader-visible fields, excluding kludges. */
final class MailSearchQuery {
    private final Pattern pattern;

    MailSearchQuery(String query) {
        if (query == null || query.strip().length() < 2 || query.length() > 160
                || query.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Search query must contain 2..160 characters without controls");
        }
        pattern = Pattern.compile(Pattern.quote(query.strip()),
                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    boolean matches(Message message) {
        return contains(message.getFrom()) || contains(message.getTo())
                || contains(message.getFAddr() == null ? null : message.getFAddr().toString())
                || contains(message.getTAddr() == null ? null : message.getTAddr().toString())
                || contains(message.getSubject()) || contains(message.getBody().getBody());
    }

    private boolean contains(String value) {
        return value != null && pattern.matcher(value).find();
    }

    String excerpt(Message message) {
        String text = message.getBody().getBody();
        Matcher match = pattern.matcher(text);
        int start = match.find() ? Math.max(0, match.start() - 80) : 0;
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
            start--;
        }
        int end = Math.min(text.length(), start + 240);
        if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return (start > 0 ? "\u2026" : "") + text.substring(start, end).replaceAll("\\s+", " ")
               + (end < text.length() ? "\u2026" : "");
    }
}
