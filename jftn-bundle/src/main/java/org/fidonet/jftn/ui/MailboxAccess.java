package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
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

    MailboxAccess(JFtnConfig config, UiSessions sessions) {
        this.config = config;
        this.sessions = sessions;
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

    void mailbox(String owner, Mailbox box) throws UiError {
        if (box == Mailbox.ECHO) {
            sessions.requireAdminUser(owner);
        }
    }

    void draft(String owner, MailDraft draft) throws UiError {
        mailbox(owner, draft.getMailbox());
        if (!draft.getFromAddress().isEmpty()
                && !addresses(owner).contains(new FTNAddr(draft.getFromAddress()))) {
            throw new UiError("FORBIDDEN", "Sender AKA is not approved for this account");
        }
    }
}
