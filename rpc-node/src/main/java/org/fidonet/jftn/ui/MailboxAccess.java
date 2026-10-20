package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.MailRepository;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.types.FTNAddr;

import java.util.ArrayList;
import java.util.List;

/** Resolves current mail identities on every operation; browser claims are never trusted. */
final class MailboxAccess {
    private final JFtnConfig config;
    private final UiSessions sessions;
    private final MailRepository base;

    MailboxAccess(JFtnConfig config, UiSessions sessions, MailRepository base) {
        this.config = config;
        this.sessions = sessions;
        this.base = base;
    }

    List<FTNAddr> addresses(String owner) throws UiError {
        List<String> station = new ArrayList<>();
        config.getLocalAddresses().forEach(address -> station.add(address.toString()));
        List<FTNAddr> result = new ArrayList<>();
        for (String value : sessions.mailAddresses(owner, station)) {
            result.add(new FTNAddr(value));
        }
        return result;
    }

    boolean administrator(String owner) {
        try {
            sessions.requireAdminUser(owner);
            return true;
        } catch (UiError denied) {
            return false;
        }
    }

    boolean readable(String owner, String area) throws UiError {
        return echoRights(owner).readable(area);
    }

    List<String> writeAddresses(String owner, String area) throws UiError {
        return echoRights(owner).authors(area);
    }

    EchoMailboxRights echoRights(String owner) throws UiError {
        return new EchoMailboxRights(administrator(owner), addresses(owner), config, base);
    }

    void mailbox(String owner, Mailbox box, String area) throws UiError {
        if (box == Mailbox.ECHO && !readable(owner, area)) {
            throw new UiError("FORBIDDEN", "This account cannot read this echo area");
        }
    }

    void draft(String owner, MailDraft draft) throws UiError {
        if (draft.getMailbox() == Mailbox.ECHO) {
            String area = MailDraftValidator.area(Mailbox.ECHO, draft.getArea());
            if (!base.isAreaExists(area)
                    || !writeAddresses(owner,
                                       area).contains(new FTNAddr(draft.getFromAddress()).toString())) {
                throw new UiError("FORBIDDEN", "Sender AKA cannot post to this echo area");
            }
        }
        if (!draft.getFromAddress().isEmpty()
                && !addresses(owner).contains(new FTNAddr(draft.getFromAddress()))) {
            throw new UiError("FORBIDDEN", "Sender AKA is not approved for this account");
        }
    }
}
