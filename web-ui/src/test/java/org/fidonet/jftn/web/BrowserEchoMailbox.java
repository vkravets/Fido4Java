package org.fidonet.jftn.web;

import org.fidonet.jftn.rpc.AreaInfo;
import org.fidonet.jftn.rpc.EchoContext;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.MailItem;
import org.fidonet.jftn.rpc.MailPage;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.UiSessions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Browser interaction data only; real SQL rights/thread behavior is covered by EchoReaderRpcTest. */
final class BrowserEchoMailbox {
    private final UiSessions sessions;
    private final List<MailItem> messages = new ArrayList<>();
    private final Map<String, Map<String, MailDraft>> drafts = new HashMap<>();
    private final Map<String, Set<Long>> read = new HashMap<>();
    private final Map<String, String> submitted = new HashMap<>();
    private long next = 9007199254740993L;

    BrowserEchoMailbox(UiSessions sessions) {
        this.sessions = sessions;
        messages.add(item("Alice",
                          "Welcome to general",
                          "1:500/101 original",
                          "Hello everyone!\rThis is literal text: <script>alert('mail')</script>"));
        messages.add(item("Bob",
                          "Re: Welcome to general",
                          "1:500/102 reply",
                          "Welcome Alice!").setReplyTo("1:500/101 original"));
    }

    private MailItem item(String author, String subject, String messageId, String body) {
        return new MailItem(next++,
                            author,
                            "All",
                            "1:500/101.0",
                            "1:500/100.0",
                            subject,
                            "20 Oct 26  10:00:00",
                            messageId,
                            false).setBody(body);
    }

    synchronized Object dispatch(String method, Object[] args) throws Exception {
        String owner = (String) args[0];
        switch (method) {
            case "areas" :
                return List.of(new AreaInfo("general",
                                            messages.size()).setReadable(true)
                                                            .setWriteAddresses(sessions.mailAddresses(owner,
                                                                                                      List.of("1:500/100.0"))));
            case "messages" :
                requireArea((Mailbox) args[1], (String) args[2]);
                return page(owner,
                            messages.stream()
                                    .filter(item -> item.getId() > (Long) args[3])
                                    .collect(Collectors.toList()),
                            (Integer) args[4]);
            case "echoMessages" :
                requireArea(Mailbox.ECHO, (String) args[1]);
                return page(owner,
                            messages.stream()
                                    .filter(item -> (Long) args[2] == 0
                                            || item.getId() < (Long) args[2])
                                    .sorted((first, second) -> Long.compare(second.getId(),
                                                                            first.getId()))
                                    .collect(Collectors.toList()),
                            (Integer) args[3]);
            case "message" :
                requireArea((Mailbox) args[1], (String) args[2]);
                return copy(owner, find((Long) args[3]));
            case "markRead" :
                requireArea((Mailbox) args[1], (String) args[2]);
                Set<Long> marked = read.computeIfAbsent(owner, ignored -> new HashSet<>());
                if ((Boolean) args[4])
                    marked.add((Long) args[3]);
                else
                    marked.remove((Long) args[3]);
                return null;
            case "echoContext" :
                requireArea(Mailbox.ECHO, (String) args[1]);
                MailItem selected = find((Long) args[2]);
                EchoContext context = new EchoContext(page(owner,
                                                           messages.stream()
                                                                   .filter(item -> item.getId() > (Long) args[3]
                                                                           && selected.getMessageId()
                                                                                      .equals(item.getReplyTo()))
                                                                   .collect(Collectors.toList()),
                                                           (Integer) args[4]),
                                                      false);
                messages.stream()
                        .filter(item -> item.getMessageId().equals(selected.getReplyTo()))
                        .findFirst()
                        .ifPresent(parent -> context.setParent(copy(owner, parent)));
                return context;
            case "drafts" :
                return new ArrayList<>(drafts.computeIfAbsent(owner, ignored -> new HashMap<>())
                                             .values());
            case "saveDraft" :
                MailDraft draft = (MailDraft) args[1];
                validate(owner, draft);
                drafts.computeIfAbsent(owner, ignored -> new HashMap<>())
                      .put(draft.getId(), draft.deepCopy());
                return null;
            case "deleteDraft" :
                drafts.computeIfAbsent(owner, ignored -> new HashMap<>()).remove(args[1]);
                return null;
            case "send" :
                return send(owner, (MailDraft) args[1], (String) args[2]);
            default :
                throw new UnsupportedOperationException("Unknown browser mail operation");
        }
    }

    private String send(String owner, MailDraft draft, String request) throws Exception {
        validate(owner, draft);
        String key = owner + ":" + request;
        if (submitted.containsKey(key))
            return submitted.get(key);
        String id = draft.getFromAddress() + " posted-" + next;
        MailItem posted = item(draft.getFromName(),
                               draft.getSubject(),
                               id,
                               draft.getBody()).setFromAddress(draft.getFromAddress())
                                               .setToName(draft.getToName());
        if (draft.isSetReplyTo())
            posted.setReplyTo(draft.getReplyTo());
        messages.add(posted);
        drafts.computeIfAbsent(owner, ignored -> new HashMap<>()).remove(draft.getId());
        submitted.put(key, id);
        return id;
    }

    private void validate(String owner, MailDraft draft) throws Exception {
        requireArea(draft.getMailbox(), draft.getArea());
        if (!sessions.mailAddresses(owner, List.of("1:500/100.0"))
                     .contains(draft.getFromAddress())) {
            throw new IllegalArgumentException("Sender AKA is not approved");
        }
    }

    private void requireArea(Mailbox mailbox, String area) {
        if (mailbox != Mailbox.ECHO || !area.equals("general"))
            throw new IllegalArgumentException("Unknown echo area");
    }

    private MailItem find(long id) {
        return messages.stream().filter(item -> item.getId() == id).findFirst().orElseThrow();
    }

    private MailItem copy(String owner, MailItem item) {
        return item.deepCopy().setRead(read.getOrDefault(owner, Set.of()).contains(item.getId()));
    }

    private MailPage page(String owner, List<MailItem> rows, int limit) {
        List<MailItem> result = rows.stream()
                                    .limit(limit)
                                    .map(item -> copy(owner, item))
                                    .collect(Collectors.toList());
        return new MailPage(result,
                            result.isEmpty() ? 0 : result.get(result.size() - 1).getId(),
                            rows.size() > limit);
    }
}
