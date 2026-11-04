package org.fidonet;

import java.util.Locale;
import java.util.Set;

/** Display classification only; never validates or rewrites received controls. */
public final class ReaderKludges {
    // GoldED+ FTS/FSC/XXX/RFC tables; see docs/mail-kludges/classification.md.
    private static final Set<String> FTN = Set.of("AREA",
                                                  "INTL",
                                                  "FMPT",
                                                  "TOPT",
                                                  "MSGID",
                                                  "REPLY",
                                                  "SEEN-BY",
                                                  "PATH",
                                                  "CHARSET",
                                                  "CHRC",
                                                  "CHRS",
                                                  "CODEPAGE",
                                                  "DOMAIN",
                                                  "EID",
                                                  "ENC",
                                                  "ENCLFILE",
                                                  "FLAGS",
                                                  "FWDFROM",
                                                  "FWDORIG",
                                                  "FWDTO",
                                                  "FWDDEST",
                                                  "FWDSUBJ",
                                                  "FWDAREA",
                                                  "FWDMSGID",
                                                  "I51",
                                                  "MSGTO",
                                                  "PID",
                                                  "PTH",
                                                  "REPLYADDR",
                                                  "REPLYTO",
                                                  "SPLIT",
                                                  "SPTH",
                                                  "TID",
                                                  "ACUPDATE",
                                                  "DESTADDR",
                                                  "ENCRYPTION",
                                                  "EOT",
                                                  "GATECHK",
                                                  "GID",
                                                  "GIF",
                                                  "GMD",
                                                  "GROUP",
                                                  "MOOD",
                                                  "MSGSEQ",
                                                  "NOTE",
                                                  "ORIGID",
                                                  "ORIGREF",
                                                  "Recd",
                                                  "RFC",
                                                  "RFD",
                                                  "RID",
                                                  "#ROUTE",
                                                  "SN",
                                                  "SOT",
                                                  "TCL1",
                                                  "TCL2",
                                                  "TZUTCINFO",
                                                  "TZUTC",
                                                  "TZ",
                                                  "XID");
    private static final Set<String> INSENSITIVE = Set.of("original", "via");
    private static final Set<String> RFC = Set.of("also-control",
                                                  "apparently-to",
                                                  "approved",
                                                  "article-names",
                                                  "article-updates",
                                                  "bcc",
                                                  "cc",
                                                  "comment",
                                                  "comments",
                                                  "content-description",
                                                  "content-disposition",
                                                  "content-id",
                                                  "content-length",
                                                  "content-transfer-encoding",
                                                  "content-type",
                                                  "control",
                                                  "date",
                                                  "delivered-to",
                                                  "delivery-date",
                                                  "distribution",
                                                  "encrypted",
                                                  "errors-to",
                                                  "expires",
                                                  "followup-to",
                                                  "from",
                                                  "in-reply-to",
                                                  "keywords",
                                                  "lines",
                                                  "message-id",
                                                  "mailing-list",
                                                  "mime-version",
                                                  "newsgroups",
                                                  "news-software",
                                                  "nntp-posting-date",
                                                  "nntp-posting-host",
                                                  "nntp-posting-user",
                                                  "old-date",
                                                  "organization",
                                                  "originator",
                                                  "path",
                                                  "precedence",
                                                  "priority",
                                                  "received",
                                                  "references",
                                                  "reply-to",
                                                  "return-path",
                                                  "return-receipt-to",
                                                  "see-also",
                                                  "sender",
                                                  "status",
                                                  "subject",
                                                  "summary",
                                                  "supersedes",
                                                  "to",
                                                  "version",
                                                  "xref");

    private ReaderKludges() {
    }

    public static boolean isKnown(String line) {
        if (!line.startsWith("\1")) {
            return false;
        }
        String control = line.substring(1);
        int end = 0;
        while (end < control.length() && control.charAt(end) != ' ' && control.charAt(end) != ':') {
            end++;
        }
        if (end == 0) {
            return false;
        }
        String tag = control.substring(0, end);
        String lower = tag.toLowerCase(Locale.ROOT);
        if (FTN.contains(tag) || INSENSITIVE.contains(lower)) {
            return true;
        }
        String header = lower;
        if (header.startsWith("rfc") && header.length() > 3) {
            header = header.substring(3);
            if (!Character.isLetter(header.charAt(0))) {
                header = header.substring(1);
            }
        }
        boolean colon = end < control.length() && control.charAt(end) == ':';
        return header.equals("from") || header.equals("#!") || colon && (RFC.contains(header)
                || header.startsWith("x-") || header.startsWith("resent-"));
    }
}
