package org.fidonet.jftn.poll;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Bounded exception diagnostics with configured credentials removed before persistence or display. */
final class PollDiagnostics {
    private final List<String> secrets = new ArrayList<>();

    PollDiagnostics(JFtnConfig config, SessionContext context) {
        config.getConfiguredLinks().forEach(link -> secret(link.getPass()));
        if (context.getLinksInfo() != null) {
            context.getLinksInfo().getLinks().forEach(link -> secret(link.getPass()));
        }
        secret(context.getPasswordKey());
        secrets.sort(Comparator.comparingInt(String::length).reversed());
    }

    private void secret(String value) {
        if (value != null && !value.isEmpty() && !value.equals("-")) {
            secrets.add(value);
        }
    }

    String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        for (String secret : secrets) {
            text = text.replace(secret, "[REDACTED]");
        }
        text = text.replaceAll("(?i)\\bCRAM-[A-Z0-9]+-[0-9a-f]{16,}\\b", "[REDACTED]")
                   .replaceAll("(?i)\\bauthorization\\s*[:=]\\s*(?:(?:Bearer|Basic)\\s+)?\\S+",
                               "authorization=[REDACTED]")
                   .replaceAll("(?i)\\b(password|passwd|pwd|authorization|token)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|\\S+)",
                               "$1=[REDACTED]")
                   .replaceAll("[\\p{Cntrl}]", " ");
        return text.substring(0, Math.min(text.length(), 2048));
    }

    String summary(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.size() < 8 && seen.add(current)) {
            if (text.length() > 0) {
                text.append("; caused by ");
            }
            text.append(current.getClass().getName());
            if (current.getMessage() != null) {
                text.append(": ").append(clean(current.getMessage()));
            }
            current = current.getCause();
        }
        return clean(text.toString());
    }

    List<String> trace(Throwable failure) {
        List<String> lines = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        describe(failure, "", lines, seen, 0);
        return lines;
    }

    private void describe(Throwable failure,
                          String prefix,
                          List<String> lines,
                          Set<Throwable> seen,
                          int depth) {
        if (failure == null || depth >= 8 || lines.size() >= 120 || !seen.add(failure)) {
            return;
        }
        lines.add(clean(prefix
                        + failure.getClass()
                                 .getName()
                        + ": "
                        + (failure.getMessage() == null ? "" : failure.getMessage())));
        StackTraceElement[] frames = failure.getStackTrace();
        for (int index = 0; index < Math.min(24, frames.length) && lines.size() < 120; index++) {
            lines.add(clean("    at " + frames[index]));
        }
        if (frames.length > 24 && lines.size() < 120) {
            lines.add("    [Additional stack frames omitted]");
        }
        Throwable[] suppressed = failure.getSuppressed();
        for (int index = 0; index < Math.min(4, suppressed.length); index++) {
            describe(suppressed[index], "Suppressed: ", lines, seen, depth + 1);
        }
        describe(failure.getCause(), "Caused by: ", lines, seen, depth + 1);
    }
}
