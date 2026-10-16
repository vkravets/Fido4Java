package org.fidonet.jftn.ui;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.echobase.StoredMail;
import org.fidonet.jftn.MailService;
import org.fidonet.jftn.poll.PollAttempt;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.rpc.AreaInfo;
import org.fidonet.jftn.rpc.LinkInfo;
import org.fidonet.jftn.rpc.LogPage;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.MailItem;
import org.fidonet.jftn.rpc.MailPage;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.NodeStatus;
import org.fidonet.jftn.rpc.PollInfo;
import org.fidonet.jftn.rpc.QueueItem;
import org.fidonet.jftn.rpc.RobotAccessConfiguration;
import org.fidonet.jftn.rpc.RobotRuleChange;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.script.FileDeliveryQueue;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.apache.thrift.TSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Sysop UI adapter. Authentication is supplied by AuthenticatedUi at the RPC boundary. */
public final class NodeUi implements UiService.Iface {
    private static final Logger logger = LoggerFactory.getLogger(NodeUi.class);
    private final JFtnConfig config;
    private final DatabaseManager base;
    private final MailService mail;
    private final UiState state;
    private final PollService polls;
    private final NodeRobotRules robotRules;
    private final long started = System.nanoTime();

    public NodeUi(JFtnConfig config, DatabaseManager base, MailService mail, PollService polls)
            throws Exception {
        this.config = config;
        this.base = base;
        this.mail = mail;
        robotRules = new NodeRobotRules(config, base);
        state = new UiState(Path.of(config.getTmpDir(), "ui-state"));
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
        if (box == null || area == null || area.length() > 255) {
            throw new IllegalArgumentException("Mailbox and area are required");
        }
        if (box == Mailbox.ECHO) {
            return area.trim().toLowerCase(Locale.ROOT);
        }
        if (!area.isEmpty()) {
            throw new IllegalArgumentException("Netmail mailbox uses an empty area");
        }
        return "";
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

    private void id(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("ID must contain 1..80 letters, digits, _ or -");
        }
    }

    private void draftFields(MailDraft draft) {
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
            if (value.length() > 255) {
                throw new IllegalArgumentException("Draft header is too long");
            }
        }
        if (draft.getBody().getBytes(StandardCharsets.UTF_8).length > 60000) {
            throw new IllegalArgumentException("Draft body exceeds 60000 bytes");
        }
    }

    private void draft(MailDraft draft) {
        draftFields(draft);
        header(draft.getFromName(), 35);
        header(draft.getToName(), 35);
        header(draft.getSubject(), 71);
        if (!validAddress(new FTNAddr(draft.getToAddress()))
                || !config.getLocalAddresses().contains(new FTNAddr(draft.getFromAddress()))
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
        if (!address.isValid()) {
            return false;
        }
        for (int value : new int[]{address.getZone(), address.getNet(), address.getNode(),
                address.getPnt()}) {
            if (value < 0 || value > 65535) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<MailDraft> drafts(String owner) throws UiError {
        return call(() -> state.drafts(owner));
    }

    @Override
    public void saveDraft(String owner, MailDraft draft) throws UiError {
        draftFields(draft);
        call(() -> {
            state.save(owner, draft);
            return null;
        });
    }

    @Override
    public void deleteDraft(String owner, String id) throws UiError {
        id(id);
        call(() -> {
            state.delete(owner, id);
            return null;
        });
    }

    @Override
    public String send(String owner, MailDraft draft, String requestId) throws UiError {
        draft(draft);
        id(requestId);
        return call(() -> mail.operate(tosser -> {
            String key = ScriptState.digest(owner + ":" + requestId);
            String msgid = draft.getFromAddress() + " ui-" + key;
            String hash = ScriptState.digest(Base64.getEncoder()
                                                   .encodeToString(new TSerializer().serialize(draft)));
            String text = draft.getBody().replace("\r\n", "\r").replace('\n', '\r');
            Message message = new Message(draft.getFromName(),
                                          draft.getToName(),
                                          new FTNAddr(draft.getFromAddress()),
                                          new FTNAddr(draft.getToAddress()),
                                          draft.getSubject(),
                                          (draft.getMailbox() == Mailbox.ECHO
                                                  ? "AREA:" + draft.getArea() + "\r"
                                                  : "") + "\1CHRS: UTF-8 4\r" + text,
                                          new Date());
            message.setMsgId(msgid);
            if (draft.isSetReplyTo()) {
                message.getBody().getKludges().put("REPLY", draft.getReplyTo());
            }
            tosser.submissionTargets(message);
            state.reserve(key, hash, msgid);
            if (draft.getMailbox() == Mailbox.ECHO) {
                tosser.submitEchomail(message);
            } else {
                message.setAttributes(1);
                tosser.submitNetmail(message, "ui:" + key);
            }
            state.delete(owner, draft.getId());
            return msgid;
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
            tosser.removeEchoSubscription(new FTNAddr(peer), area);
            return null;
        }));
    }

    @Override
    public void rescan(String owner, String peer, String area, int count, String requestId)
            throws UiError {
        id(requestId);
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
                            attempt.getDetail());
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
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Invalid log cursor or page size");
        }
        String filename = config.getValue("RpcLogFile", "");
        if (filename.isEmpty()) {
            throw new UnsupportedOperationException("Configure RpcLogFile to read node logs");
        }
        return call(() -> NodeLogReader.read(Path.of(filename), offset, limit));
    }

}
