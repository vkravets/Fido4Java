package org.fidonet.jftn.ui;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.StorageConfiguration;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.echobase.MailRepository;
import org.fidonet.echobase.StoredMail;
import org.fidonet.jftn.MailRuntime;
import org.fidonet.jftn.poll.PollAttempt;
import org.fidonet.jftn.poll.PollDiagnostics;
import org.fidonet.jftn.poll.PollLogSlice;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.rpc.AreaInfo;
import org.fidonet.jftn.rpc.EchoAccessConfiguration;
import org.fidonet.jftn.rpc.EchoContext;
import org.fidonet.jftn.rpc.ExtensionSettings;
import org.fidonet.jftn.rpc.LinkInfo;
import org.fidonet.jftn.rpc.LogPage;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.MailItem;
import org.fidonet.jftn.rpc.MailPage;
import org.fidonet.jftn.rpc.MailSearchPage;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.NetmailRouteInfo;
import org.fidonet.jftn.rpc.NetmailRoutePreview;
import org.fidonet.jftn.rpc.NetmailRoutesConfiguration;
import org.fidonet.jftn.rpc.NodeStatus;
import org.fidonet.jftn.rpc.PollInfo;
import org.fidonet.jftn.rpc.PollLogPage;
import org.fidonet.jftn.rpc.PollPage;
import org.fidonet.jftn.rpc.QuarantinePage;
import org.fidonet.jftn.rpc.QuarantineRetryResult;
import org.fidonet.jftn.rpc.QueueItem;
import org.fidonet.jftn.rpc.RobotAccessConfiguration;
import org.fidonet.jftn.rpc.RobotRuleChange;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.script.FileDeliveryQueue;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.PacketQuarantine;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Sysop UI adapter. Authentication is supplied by AuthenticatedUi at the RPC boundary. */
public final class NodeUiServiceHandler implements UiService.Iface, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(NodeUiServiceHandler.class);
    private final JFtnConfig config;
    private final MailRepository base;
    private final MailRuntime mail;
    private final UserMailStateStore state;
    private final MailDraftValidator draftValidator;
    private final MailboxAccess access;
    private final MailSubmissionService submissions;
    private final boolean mailSearchEnabled;
    private final NodeMailSearch search;
    private final PollService polls;
    private final NodeRobotRules robotRules;
    private final long started = System.nanoTime();

    public NodeUiServiceHandler(JFtnConfig config,
                                MailRepository base,
                                MailRuntime mail,
                                PollService polls,
                                UiSessions sessions)
            throws Exception {
        this.config = config;
        mailSearchEnabled = RpcConfiguration.enabled(config, "RpcExperimentalMailSearch");
        draftValidator = new MailDraftValidator(config);
        access = new MailboxAccess(config, sessions, base);
        this.base = base;
        this.mail = mail;
        robotRules = new NodeRobotRules(config, base);
        state = new UserMailStateStore(StorageConfiguration.open(config,
                                                                 "reader",
                                                                 Path.of(config.getTmpDir(),
                                                                         "ui-state")));
        submissions = new MailSubmissionService(mail, state);
        search = new NodeMailSearch(base, state, access, (row, read) -> item(row, false, read));
        this.polls = polls;
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run() throws Exception;
    }

    private <T> T call(Operation<T> operation) throws UiError {
        try {
            return operation.run();
        } catch (UiError | IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("UI operation failed", e);
            throw new UiError("OPERATION_FAILED", "Node operation failed; inspect the node log");
        }
    }

    @Override
    public RobotAccessConfiguration robotAccess(String owner) throws UiError {
        return call(robotRules::current);
    }

    @Override
    public NetmailRoutesConfiguration netmailRoutes(String owner) throws UiError {
        return call(() -> mail.operate(tosser -> NodeNetmailRouting.current(config, tosser)));
    }

    @Override
    public void saveNetmailRoute(String owner, NetmailRouteInfo rule) throws UiError {
        call(() -> mail.operate(tosser -> {
            NodeNetmailRouting.save(config, tosser, rule);
            return null;
        }));
    }

    @Override
    public void deleteNetmailRoute(String owner, String id) throws UiError {
        MailDraftValidator.id(id);
        call(() -> mail.operate(tosser -> {
            NodeNetmailRouting.requireManaged(config);
            tosser.getNetmailRouter().removeRule(id);
            return null;
        }));
    }

    @Override
    public NetmailRoutePreview previewNetmailRoute(String owner,
                                                   String destination,
                                                   String source,
                                                   boolean direct)
            throws UiError {
        return call(() -> mail.operate(tosser -> NodeNetmailRouting.preview(config,
                                                                            tosser,
                                                                            destination,
                                                                            source,
                                                                            direct)));
    }

    @Override
    public List<ExtensionSettings> extensions(String owner) throws UiError {
        return call(() -> mail.operate(tosser -> NodeExtensions.describe(mail.getSettingsExtensions())));
    }

    @Override
    public void saveExtension(String owner, String id, String section, Map<String, String> values)
            throws UiError {
        call(() -> mail.operate(tosser -> {
            NodeExtensions.save(mail.getSettingsExtensions(), id, section, values);
            return null;
        }));
    }

    @Override
    public EchoAccessConfiguration echoAccess(String owner) throws UiError {
        return call(() -> mail.operate(tosser -> NodeEchoAccess.current(config, base)));
    }

    @Override
    public QuarantinePage quarantine(String owner, String after, int limit) throws UiError {
        return call(() -> mail.operate(tosser -> NodeQuarantine.current(tosser,
                                                                        config,
                                                                        after,
                                                                        limit)));
    }

    @Override
    public QuarantineRetryResult retryQuarantine(String owner,
                                                 String id,
                                                 String checksum,
                                                 String peer)
            throws UiError {
        return call(() -> mail.operate(tosser -> {
            FTNAddr expected = peer == null || peer.trim().isEmpty()
                    ? null
                    : new FTNAddr(peer.trim());
            PacketQuarantine.Retry result = tosser.retryQuarantine(id, checksum, expected);
            return new QuarantineRetryResult(result.accepted, result.reason);
        }));
    }

    @Override
    public void archiveQuarantine(String owner, String id, String checksum) throws UiError {
        call(() -> mail.operate(tosser -> {
            tosser.archiveQuarantine(id, checksum);
            return null;
        }));
    }

    @Override
    public void saveRobotRule(String owner, RobotRuleChange change) throws UiError {
        call(() -> {
            robotRules.save(change);
            return null;
        });
    }

    @Override
    public void deleteRobotRule(String owner, String service, String address) throws UiError {
        call(() -> {
            robotRules.delete(service, address);
            return null;
        });
    }

    private List<String> localAddresses() {
        List<String> addresses = new ArrayList<>();
        config.getLocalAddresses().forEach(address -> addresses.add(address.toString()));
        return addresses;
    }

    @Override
    public NodeStatus status(String owner) {
        Runtime runtime = Runtime.getRuntime();
        int active = (int) polls.list()
                                .stream()
                                .filter(poll -> poll.isActive()
                                        && !poll.getState().equals("QUEUED"))
                                .count();
        return new NodeStatus("2.0",
                              (System.nanoTime() - started) / 1000000000,
                              localAddresses(),
                              runtime.totalMemory() - runtime.freeMemory(),
                              active);
    }

    @Override
    public List<AreaInfo> areas(String owner) throws UiError {
        return call(() -> {
            List<AreaInfo> result = new ArrayList<>();
            EchoMailboxRights rights = access.echoRights(owner);
            for (String area : base.getAreas().stream().sorted().collect(Collectors.toList())) {
                boolean readable = rights.readable(area);
                List<String> authors = rights.authors(area);
                if (readable || !authors.isEmpty()) {
                    result.add(new AreaInfo(area,
                                            readable ? base.getMessageSize(area) : 0)
                                                                                     .setReadable(readable)
                                                                                     .setWriteAddresses(authors));
                }
            }
            return result;
        });
    }

    private String area(Mailbox box, String area) {
        return MailDraftValidator.area(box, area);
    }

    @Override
    public MailPage echoMessages(String owner, String name, long before, int limit) throws UiError {
        String area = area(Mailbox.ECHO, name);
        if (before < 0 || limit < 1 || limit > 200)
            throw new IllegalArgumentException("Invalid echo page");
        access.mailbox(owner, Mailbox.ECHO, area);
        return call(() -> {
            List<StoredMail> rows = base.readEchoRecent(area, before, limit + 1);
            List<Long> ids = rows.stream()
                                 .limit(limit)
                                 .map(StoredMail::getId)
                                 .collect(Collectors.toList());
            Set<Long> read = state.readIds(owner, "ECHO", area, ids);
            List<MailItem> items = new ArrayList<>();
            for (StoredMail row : rows.subList(0, Math.min(limit, rows.size()))) {
                items.add(item(row, false, read.contains(row.getId())));
            }
            return new MailPage(items,
                                items.isEmpty() ? before : items.get(items.size() - 1).getId(),
                                rows.size() > limit);
        });
    }

    private List<StoredMail> rows(String owner, Mailbox box, String area, long after, int limit)
            throws Exception {
        access.mailbox(owner, box, area);
        if (box == Mailbox.ECHO) {
            return base.readEchoPage(area, after, limit);
        }
        List<FTNAddr> addresses = access.addresses(owner);
        List<StoredMail> result = new ArrayList<>();
        long cursor = after;
        while (result.size() < limit) {
            List<StoredMail> page = base.readNetmailPage(addresses,
                                                         box == Mailbox.OUTBOX,
                                                         cursor,
                                                         201);
            List<Long> ids = new ArrayList<>();
            page.forEach(row -> ids.add(row.getId()));
            Set<Long> deleted = state.deletedIds(owner, box.name(), ids);
            result.addAll(page.stream()
                              .filter(row -> !deleted.contains(row.getId()))
                              .limit(limit - result.size())
                              .collect(Collectors.toList()));
            if (page.size() < 201) {
                break;
            }
            cursor = page.get(page.size() - 1).getId();
        }
        return result;
    }

    private MailItem item(StoredMail stored, boolean body, boolean read) throws Exception {
        Message message = stored.getMessage();
        MailItem result = new MailItem(stored.getId(),
                                       message.getFrom(),
                                       message.getTo(),
                                       message.getFAddr().toString(),
                                       message.getTAddr() == null
                                               ? ""
                                               : message.getTAddr().toString(),
                                       message.getSubject() == null ? "" : message.getSubject(),
                                       message.getMsgDate(),
                                       message.getMsgId() == null ? "" : message.getMsgId(),
                                       read);
        result.setAttributes(message.getAttributes());
        String plain = message.getBody().getBody();
        int end = Math.min(240, plain.length());
        if (end > 0 && Character.isHighSurrogate(plain.charAt(end - 1)))
            end--;
        String excerpt = plain.substring(0, end).replaceAll("\\s+", " ").trim();
        result.setExcerpt(excerpt);
        String reply = message.getBody().getKludges().get("REPLY");
        if (reply != null)
            result.setReplyTo(reply.trim());
        if (body) {
            result.setBody(message.getBody().getBody());
        }
        return result;
    }

    @Override
    public MailPage messages(String owner, Mailbox box, String name, long after, int limit)
            throws UiError {
        String area = area(box, name);
        if (limit < 1 || limit > 200 || after < 0) {
            throw new IllegalArgumentException("Page size must be 1..200 and cursor nonnegative");
        }
        return call(() -> {
            List<StoredMail> rows = rows(owner, box, area, after, limit + 1);
            List<MailItem> items = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, rows.size()); i++) {
                ids.add(rows.get(i).getId());
            }
            Set<Long> read = state.readIds(owner, box.name(), area, ids);
            for (int i = 0; i < Math.min(limit, rows.size()); i++) {
                items.add(item(rows.get(i), false, read.contains(rows.get(i).getId())));
            }
            return new MailPage(items,
                                items.isEmpty() ? after : items.get(items.size() - 1).getId(),
                                rows.size() > limit);
        });
    }

    @Override
    public MailItem message(String owner, Mailbox box, String name, long id) throws UiError {
        String area = area(box, name);
        if (id < 1) {
            throw new IllegalArgumentException("Message ID must be positive");
        }
        return call(() -> {
            List<StoredMail> rows = rows(owner, box, area, id - 1, 1);
            if (rows.isEmpty() || rows.get(0).getId() != id) {
                throw new UiError("NOT_FOUND", "Message not found in this mailbox");
            }
            return item(rows.get(0), true, state.isRead(owner, box.name(), area, id));
        });
    }

    @Override
    public MailSearchPage searchMessages(String owner,
                                         Mailbox box,
                                         String name,
                                         String query,
                                         long after,
                                         int limit)
            throws UiError {
        if (!mailSearchEnabled) {
            throw new UiError("FEATURE_DISABLED", "Experimental mail search is disabled");
        }
        String area = area(box, name);
        return call(() -> search.search(owner, box, area, query, after, limit));
    }

    @Override
    public EchoContext echoContext(String owner, String name, long id, long after, int limit)
            throws UiError {
        String area = area(Mailbox.ECHO, name);
        if (after < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Invalid reply page");
        }
        MailItem selected = message(owner, Mailbox.ECHO, area, id);
        return call(() -> {
            EchoContext context = new EchoContext(new MailPage(List.of(), after, false), false);
            if (selected.isSetReplyTo() && !selected.getReplyTo().isEmpty()) {
                StoredMail parent = base.findEchoMessageId(area, selected.getReplyTo());
                if (parent != null && parent.getId() != id) {
                    context.setParent(item(parent,
                                           false,
                                           state.isRead(owner, "ECHO", area, parent.getId())));
                } else {
                    context.setMissingParent(true);
                }
            }
            List<StoredMail> rows = base.readEchoReplies(area,
                                                         selected.getMessageId(),
                                                         after,
                                                         limit + 1,
                                                         id);
            List<Long> ids = rows.stream()
                                 .limit(limit)
                                 .map(StoredMail::getId)
                                 .collect(Collectors.toList());
            Set<Long> read = state.readIds(owner, "ECHO", area, ids);
            List<MailItem> replies = new ArrayList<>();
            for (StoredMail row : rows.subList(0, Math.min(limit, rows.size()))) {
                replies.add(item(row, false, read.contains(row.getId())));
            }
            context.setReplies(new MailPage(replies,
                                            replies.isEmpty()
                                                    ? after
                                                    : replies.get(replies.size() - 1).getId(),
                                            rows.size() > limit));
            return context;
        });
    }

    @Override
    public void markRead(String owner, Mailbox box, String area, long id, boolean read)
            throws UiError {
        message(owner, box, area, id);
        call(() -> {
            state.mark(owner, box.name(), area(box, area), id, read);
            return null;
        });
    }

    @Override
    public void deleteMessage(String owner, Mailbox box, long id) throws UiError {
        if (box != Mailbox.INBOX && box != Mailbox.OUTBOX || id < 1) {
            throw new IllegalArgumentException("Select a netmail message to delete");
        }
        call(() -> mail.operate(tosser -> {
            List<FTNAddr> addresses = access.addresses(owner);
            List<StoredMail> rows = base.readNetmailPage(addresses,
                                                         box == Mailbox.OUTBOX,
                                                         id - 1,
                                                         1);
            if (rows.isEmpty() || rows.get(0).getId() != id) {
                throw new UiError("NOT_FOUND", "Message not found in this mailbox");
            }
            if (box == Mailbox.OUTBOX) {
                base.cancelWaitingNetmail(id, addresses);
            }
            state.deleteMessage(owner, box.name(), id);
            return null;
        }));
    }

    @Override
    public int pendingNetmail(String owner) throws UiError {
        return call(() -> mail.operate(tosser -> base.waitingNetmail(access.addresses(owner))));
    }

    @Override
    public int sendPendingNetmail(String owner) throws UiError {
        return call(() -> mail.operate(tosser -> {
            int released = base.releaseNetmail(access.addresses(owner));
            tosser.exportPendingNetmail();
            return released;
        }));
    }

    @Override
    public List<MailDraft> drafts(String owner) throws UiError {
        return call(() -> {
            List<MailDraft> result = new ArrayList<>();
            for (MailDraft draft : state.drafts(owner)) {
                if (visibleDraft(owner, draft)) {
                    result.add(draft);
                }
            }
            return result;
        });
    }

    private boolean visibleDraft(String owner, MailDraft draft) throws UiError {
        try {
            access.draft(owner, draft);
            return true;
        } catch (UiError denied) {
            if (!"FORBIDDEN".equals(denied.getCode())) {
                throw denied;
            }
            return false;
        }
    }

    @Override
    public void saveDraft(String owner, MailDraft draft) throws UiError {
        draftValidator.fields(draft);
        access.draft(owner, draft);
        call(() -> {
            state.save(owner, draft);
            return null;
        });
    }

    @Override
    public void deleteDraft(String owner, String id) throws UiError {
        MailDraftValidator.id(id);
        call(() -> {
            state.delete(owner, id);
            return null;
        });
    }

    @Override
    public String send(String owner, MailDraft draft, String requestId) throws UiError {
        draftValidator.fields(draft);
        MailDraftValidator.id(requestId);
        return call(() -> mail.operate(tosser -> {
            access.draft(owner, draft);
            draftValidator.submission(draft, access.addresses(owner));
            return submissions.sendValidated(owner, draft, requestId);
        }));
    }

    @Override
    public List<LinkInfo> links(String owner) {
        List<LinkInfo> result = new ArrayList<>();
        for (Link link : config.getConfiguredLinks()) {
            List<String> local = new ArrayList<>();
            link.getMyaddr().forEach(address -> local.add(address.toString()));
            result.add(new LinkInfo(link.getAddr().toString(),
                                    link.getHostAddress(),
                                    link.getPort(),
                                    local,
                                    base.getSubscriptions(link)));
        }
        return result;
    }

    @Override
    public void subscribe(String owner, String peer, String area, String access) throws UiError {
        call(() -> mail.operate(tosser -> {
            NodeEchoAccess.requireManaged(config);
            tosser.configureEchoSubscription(new FTNAddr(peer),
                                             area,
                                             AccessLevel.valueOf(access.toUpperCase(Locale.ROOT)),
                                             EchoHistory.NEW_ONLY);
            return null;
        }));
    }

    @Override
    public void unsubscribe(String owner, String peer, String area) throws UiError {
        call(() -> mail.operate(tosser -> {
            NodeEchoAccess.requireManaged(config);
            tosser.removeEchoSubscription(new FTNAddr(peer), area);
            return null;
        }));
    }

    @Override
    public void rescan(String owner, String peer, String area, int count, String requestId)
            throws UiError {
        MailDraftValidator.id(requestId);
        call(() -> mail.operate(tosser -> {
            tosser.rescanRecentEcho(area, new FTNAddr(peer), count, requestId);
            return null;
        }));
    }

    @Override
    public void processMail(String owner) throws UiError {
        call(() -> {
            mail.processPending();
            return null;
        });
    }

    @Override
    public PollInfo poll(String owner, String peer) {
        return pollInfo(polls.poll(peer));
    }

    @Override
    public List<PollInfo> polls(String owner) {
        List<PollInfo> result = new ArrayList<>();
        polls.list().forEach(attempt -> result.add(pollInfo(attempt)));
        return result;
    }

    private PollInfo pollInfo(PollAttempt attempt) {
        return new PollInfo(attempt.getId(),
                            attempt.getAddress(),
                            attempt.getState(),
                            attempt.getCreated(),
                            attempt.getDetail()).setSource(attempt.getSource())
                                                .setStarted(attempt.getStarted())
                                                .setFinished(attempt.getFinished());
    }

    @Override
    public PollInfo pollDetails(String owner, String id) throws UiError {
        return call(() -> pollInfo(polls.get(id)));
    }

    @Override
    public List<PollInfo> pollQueue(String owner) {
        List<PollInfo> result = new ArrayList<>();
        polls.list()
             .stream()
             .filter(PollAttempt::isActive)
             .forEach(attempt -> result.add(pollInfo(attempt)));
        return result;
    }

    @Override
    public PollPage pollHistory(String owner, String before, int limit) throws UiError {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Page size must be 1..200");
        }
        return call(() -> {
            List<PollAttempt> rows = polls.page(before, limit + 1);
            List<PollInfo> items = new ArrayList<>();
            rows.subList(0, Math.min(limit, rows.size())).forEach(row -> items.add(pollInfo(row)));
            String next = items.isEmpty() ? before : PollService.cursor(rows.get(items.size() - 1));
            return new PollPage(items, next, rows.size() > limit);
        });
    }

    @Override
    public PollLogPage pollLog(String owner, String id, long offset, int limit) throws UiError {
        return call(() -> {
            PollLogSlice slice = polls.logs(id, offset, limit);
            return new PollLogPage(slice.getLines(), slice.getNextOffset(), slice.isHasMore());
        });
    }

    @Override
    public void cancelPoll(String owner, String id) {
        polls.cancel(id);
    }

    @Override
    public List<QueueItem> outbound(String owner, String peer, int limit) throws UiError {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Queue limit must be 1..200");
        }
        Link link = config.getLink(new FTNAddr(peer));
        if (link == null) {
            throw new IllegalArgumentException("Unknown link");
        }
        return call(() -> {
            List<QueueItem> result = new ArrayList<>();
            List<Path> packets = new OutboundSpool(config).pending(link, true);
            for (Path path : packets.subList(0, Math.min(limit, packets.size()))) {
                result.add(new QueueItem(peer,
                                         path.getFileName().toString(),
                                         Files.size(path),
                                         "MAIL"));
            }
            for (Path path : new FileDeliveryQueue(config).pending(link)) {
                if (result.size() < limit) {
                    result.add(new QueueItem(peer,
                                             path.getFileName().toString(),
                                             Files.size(path),
                                             "FILE"));
                }
            }
            return result;
        });
    }

    @Override
    public LogPage logs(String owner, long offset, int limit) throws UiError {
        if (offset < -1 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Invalid log cursor or page size");
        }
        String filename = config.getValue("RpcLogFile", "logs/binkp.log");
        if (filename.isEmpty()) {
            throw new UnsupportedOperationException("Configure RpcLogFile to read node logs");
        }
        return call(() -> {
            LogPage page = NodeLogReader.read(Path.of(filename), offset, limit);
            PollDiagnostics diagnostics = new PollDiagnostics(config);
            List<String> safe = new ArrayList<>();
            for (String line : page.getLines()) {
                safe.add(diagnostics.clean(line));
            }
            page.setLines(safe);
            return page;
        });
    }

    @Override
    public void close() throws Exception {
        state.close();
    }
}
