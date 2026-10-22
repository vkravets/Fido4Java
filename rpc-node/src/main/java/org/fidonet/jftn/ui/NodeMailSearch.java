package org.fidonet.jftn.ui;

import org.fidonet.echobase.MailRepository;
import org.fidonet.echobase.StoredMail;
import org.fidonet.jftn.rpc.MailItem;
import org.fidonet.jftn.rpc.MailSearchPage;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.types.FTNAddr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Experimental bounded scan using the same SQL mailbox scopes as normal reader operations. */
final class NodeMailSearch {
    private static final int SCAN_LIMIT = 1000;
    private static final int BATCH_SIZE = 100;
    private final MailRepository base;
    private final UserMailStateStore state;
    private final MailboxAccess access;
    private final ItemMapper mapper;

    @FunctionalInterface
    interface ItemMapper {
        MailItem map(StoredMail row, boolean read) throws Exception;
    }

    NodeMailSearch(MailRepository base,
                   UserMailStateStore state,
                   MailboxAccess access,
                   ItemMapper mapper) {
        this.base = base;
        this.state = state;
        this.access = access;
        this.mapper = mapper;
    }

    MailSearchPage search(String owner,
                          Mailbox box,
                          String area,
                          String text,
                          long after,
                          int limit)
            throws Exception {
        if (after < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Search cursor must be nonnegative and limit 1..100");
        }
        MailSearchQuery query = new MailSearchQuery(text);
        access.mailbox(owner, box, area);
        List<FTNAddr> addresses = access.addresses(owner);
        List<StoredMail> matches = new ArrayList<>();
        long cursor = after;
        int scanned = 0;
        boolean more = true;
        while (matches.size() < limit && scanned < SCAN_LIMIT && more) {
            int count = Math.min(BATCH_SIZE, SCAN_LIMIT - scanned);
            List<StoredMail> rows = box == Mailbox.ECHO
                    ? base.readEchoPage(area, cursor, count + 1)
                    : base.readNetmailPage(addresses, box == Mailbox.OUTBOX, cursor, count + 1);
            Set<Long> deleted = box == Mailbox.ECHO
                    ? Set.of()
                    : state.deletedIds(owner, box.name(), ids(rows));
            int consumed = 0;
            for (StoredMail row : rows) {
                if (consumed == count || matches.size() == limit) {
                    break;
                }
                consumed++;
                scanned++;
                cursor = row.getId();
                if (!deleted.contains(row.getId()) && query.matches(row.getMessage())) {
                    matches.add(row);
                }
            }
            more = consumed < rows.size();
        }
        Set<Long> read = state.readIds(owner, box.name(), area, ids(matches));
        List<MailItem> items = new ArrayList<>();
        for (StoredMail row : matches) {
            items.add(mapper.map(row, read.contains(row.getId()))
                            .setExcerpt(query.excerpt(row.getMessage())));
        }
        return new MailSearchPage(items, cursor, more, scanned);
    }

    private List<Long> ids(List<StoredMail> rows) {
        return rows.stream().map(StoredMail::getId).collect(Collectors.toList());
    }
}
