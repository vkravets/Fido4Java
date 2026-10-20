package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.tools.CharsetTools;

import java.nio.charset.Charset;
import java.util.List;

/** Explicit outgoing encodings; never silently substitutes a fallback or lost characters. */
final class MailEncoding {
    private MailEncoding() {
    }

    static String name(MailDraft draft) {
        String name = draft.isSetEncoding() ? draft.getEncoding() : "UTF-8";
        if (!List.of("UTF-8", "CP866", "KOI8-R", "CP1251", "CP848").contains(name)) {
            throw new IllegalArgumentException("Unsupported outgoing mail encoding");
        }
        return name;
    }

    static String declaration(MailDraft draft) {
        String name = name(draft);
        return name + (name.equals("UTF-8") ? " 4" : " 2");
    }

    static void validate(MailDraft draft) {
        Charset charset = CharsetTools.charsetDetect(name(draft));
        for (String value : List.of(draft.getFromName(),
                                    draft.getToName(),
                                    draft.getSubject(),
                                    draft.getBody())) {
            if (!charset.newEncoder().canEncode(value)) {
                throw new IllegalArgumentException("Message contains characters not supported by "
                                                   + name(draft));
            }
        }
    }
}
