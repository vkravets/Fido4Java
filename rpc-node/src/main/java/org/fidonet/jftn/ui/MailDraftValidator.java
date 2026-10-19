package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.types.FTNAddr;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Validation only; does not authorize callers, submit messages or mutate stored drafts. */
final class MailDraftValidator {
    private final JFtnConfig config;

    MailDraftValidator(JFtnConfig config) {
        this.config = config;
    }

    static String area(Mailbox box, String area) {
        if (box == null || area == null || area.length() > 255) {
            throw new IllegalArgumentException("Mailbox and area are required");
        }
        if (box == Mailbox.ECHO)
            return area.trim().toLowerCase(Locale.ROOT);
        if (!area.isEmpty())
            throw new IllegalArgumentException("Netmail mailbox uses an empty area");
        return "";
    }

    static void id(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("ID must contain 1..80 letters, digits, _ or -");
        }
    }

    void fields(MailDraft draft) {
        if (draft == null || draft.getMailbox() == null || draft.getArea() == null
                || draft.getFromAddress() == null || draft.getToAddress() == null
                || draft.getFromName() == null || draft.getToName() == null
                || draft.getSubject() == null || draft.getBody() == null) {
            throw new IllegalArgumentException("Draft fields are required");
        }
        id(draft.getId());
        area(draft.getMailbox(), draft.getArea());
        for (String value : List.of(draft.getFromName(),
                                    draft.getToName(),
                                    draft.getSubject(),
                                    draft.getFromAddress(),
                                    draft.getToAddress())) {
            if (value.length() > 255)
                throw new IllegalArgumentException("Draft header is too long");
        }
        if (draft.getBody().getBytes(StandardCharsets.UTF_8).length > 60000) {
            throw new IllegalArgumentException("Draft body exceeds 60000 bytes");
        }
    }

    void submission(MailDraft draft) {
        submission(draft, config.getLocalAddresses());
    }

    void submission(MailDraft draft, Collection<FTNAddr> authors) {
        fields(draft);
        header(draft.getFromName(), 35);
        header(draft.getToName(), 35);
        header(draft.getSubject(), 71);
        if (!validAddress(new FTNAddr(draft.getToAddress()))
                || !authors.contains(new FTNAddr(draft.getFromAddress()))
                || draft.getBody().getBytes(StandardCharsets.UTF_8).length > 60000
                || draft.getBody().indexOf('\0') >= 0 || draft.getBody().indexOf('\u0001') >= 0
                || draft.getBody().replace('\n', '\r').matches("(?s)(?:.*\\r)?AREA:.*")) {
            throw new IllegalArgumentException("Invalid address, oversized body or embedded routing controls");
        }
        if (draft.isSetReplyTo() && (draft.getReplyTo().length() > 200
                || draft.getReplyTo().matches("(?s).*[\\x00-\\x1f].*"))) {
            throw new IllegalArgumentException("Invalid REPLY identifier");
        }
    }

    private void header(String value, int max) {
        if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > max
                || value.matches("(?s).*[\\x00-\\x1f].*")) {
            throw new IllegalArgumentException("Names must fit 35 bytes, subject 71 bytes, without controls");
        }
    }

    private boolean validAddress(FTNAddr address) {
        if (!address.isValid())
            return false;
        for (int value : new int[]{address.getZone(), address.getNet(), address.getNode(),
                address.getPnt()}) {
            if (value < 0 || value > 65535)
                return false;
        }
        return true;
    }
}
