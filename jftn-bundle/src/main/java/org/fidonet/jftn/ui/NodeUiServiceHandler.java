package org.fidonet.jftn.ui;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.echobase.StoredMail;
import org.fidonet.jftn.MailRuntime;
import org.fidonet.jftn.poll.PollAttempt;
import org.fidonet.jftn.poll.PollDiagnostics;
import org.fidonet.jftn.poll.PollLogSlice;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.rpc.AreaInfo;
import org.fidonet.jftn.rpc.EchoAccessConfiguration;
import org.fidonet.jftn.rpc.ExtensionSettings;
import org.fidonet.jftn.rpc.LinkInfo;
import org.fidonet.jftn.rpc.LogPage;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.MailItem;
import org.fidonet.jftn.rpc.MailPage;
import org.fidonet.jftn.rpc.Mailbox;
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

/** Sysop UI adapter. Authentication is supplied by AuthenticatedUi at the RPC boundary. */
public final class NodeUiServiceHandler implements UiService.Iface {
    private static final Logger logger = LoggerFactory.getLogger(NodeUiServiceHandler.class);
    private final JFtnConfig config;
    private final MailDatabaseFacade base;
    private final MailRuntime mail;
    private final UserMailStateStore state;
    private final MailDraftValidator draftValidator;
    private final MailSubmissionService submissions;
    private final PollService polls;
    private final NodeRobotRules robotRules;
    private final long started = System.nanoTime();

    public NodeUiServiceHandler(JFtnConfig config,
                                MailDatabaseFacade base,
                                MailRuntime mail,
                                PollService polls)
            throws Exception {
        this.config = config;
        draftValidator = new MailDraftValidator(config);
        this.base = base;
        this.mail = mail;
        robotRules = new NodeRobotRules(config, base);
        state = new UserMailStateStore(Path.of(config.getTmpDir(), "ui-state"));
        submissions = new MailSubmissionService(mail, state);
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
    public List<AreaInfo> areas(String owner) {
        List<AreaInfo> result = new ArrayList<>();
        base.getAreas()
            .stream()
            .sorted()
            .forEach(area -> result.add(new AreaInfo(area, base.getMessageSize(area))));
        return result;
    }

    private String area(Mailbox box, String area) {
        return MailDraftValidator.area(box, area);
    }

    private List<StoredMail> rows(Mailbox box, String area, long after, int limit)
            throws Exception {
        return box == Mailbox.ECHO
                ? base.readEchoPage(area, after, limit)
                : base.readNetmailPage(config.getLocalAddresses(),
                                       box == Mailbox.OUTBOX,
                                       after,
                                       limit);
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
        if (body) {
            result.setBody(message.getBody().toString());
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
            List<StoredMail> rows = rows(box, area, after, limit + 1);
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
            List<StoredMail> rows = rows(box, area, id - 1, 1);
            if (rows.isEmpty() || rows.get(0).getId() != id) {
                throw new UiError("NOT_FOUND", "Message not found in this mailbox");
            }
            return item(rows.get(0), true, state.isRead(owner, box.name(), area, id));
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
    public List<MailDraft> drafts(String owner) throws UiError {
        return call(() -> state.drafts(owner));
    }

    @Override
    public void saveDraft(String owner, MailDraft draft) throws UiError {
        draftValidator.fields(draft);
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
        draftValidator.submission(draft);
        MailDraftValidator.id(requestId);
        return call(() -> submissions.sendValidated(owner, draft, requestId));
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

}
