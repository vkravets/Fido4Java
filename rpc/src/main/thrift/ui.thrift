namespace java org.fidonet.jftn.rpc

exception UiError {
    1: required string code,
    2: required string message
}

enum Mailbox { ECHO = 1, INBOX = 2, OUTBOX = 3 }

// Declarative extension forms. No executable plugin code is sent to the browser.
struct ExtensionField {
    1: required string name,
    2: required string label,
    3: required string type,
    4: required list<string> choices
}

struct ExtensionSection {
    1: required string id,
    2: required string title,
    3: required list<ExtensionField> fields,
    4: required list<map<string,string>> rows,
    5: required bool writable
}

struct ExtensionSettings {
    1: required string id,
    2: required string title,
    3: required string source,
    4: required list<ExtensionSection> sections,
    5: required map<string,map<string,string>> translations
}

struct MailItem {
    1: required i64 id,
    2: required string fromName,
    3: required string toName,
    4: required string fromAddress,
    5: required string toAddress,
    6: required string subject,
    7: required string date,
    8: required string messageId,
    9: required bool read,
    10: optional string body,
    11: optional i32 attributes,
    12: optional string replyTo
}

struct MailPage {
    1: required list<MailItem> items,
    2: required i64 nextId,
    3: required bool hasMore
}

struct AreaInfo {
    1: required string name,
    2: required i64 messages,
    3: optional bool readable,
    4: optional list<string> writeAddresses
}

struct EchoContext {
    1: optional MailItem parent,
    2: required MailPage replies,
    3: required bool missingParent
}

struct MailDraft {
    1: required string id,
    2: required Mailbox mailbox,
    3: required string area,
    4: required string fromName,
    5: required string toName,
    6: required string fromAddress,
    7: required string toAddress,
    8: required string subject,
    9: required string body,
    10: optional string replyTo
}

struct LinkInfo {
    1: required string address,
    2: required string host,
    3: required i32 port,
    4: required list<string> localAddresses,
    5: required list<string> subscriptions
}

struct NetmailRoutingPolicy {
    1: required string mode,
    2: required string defaultHop,
    3: required bool allowTransit,
    4: required list<string> transitFrom,
    5: required list<string> transitTo
}

struct NetmailRouteInfo {
    1: required string id,
    2: required i32 priority,
    3: required string pattern,
    4: required string nextHop,
    5: required bool enabled
}

struct NetmailRoutesConfiguration {
    1: required NetmailRoutingPolicy policy,
    2: required list<NetmailRouteInfo> rules,
    3: required list<string> peers
}

struct NetmailRoutePreview {
    1: required string reason,
    2: required string nextHop,
    3: required string ruleId,
    4: required bool permitted
}

struct PollInfo {
    1: required string id,
    2: required string address,
    3: required string state,
    4: required i64 created,
    5: required string detail
    6: optional string source,
    7: optional i64 started,
    8: optional i64 finished
}

struct PollPage {
    1: required list<PollInfo> items,
    2: required string nextCursor,
    3: required bool hasMore
}

struct PollLogPage {
    1: required list<string> lines,
    2: required i64 nextOffset,
    3: required bool hasMore
}

struct QueueItem {
    1: required string peer,
    2: required string name,
    3: required i64 bytes,
    4: required string kind
}

struct NodeStatus {
    1: required string apiVersion,
    2: required i64 uptimeSeconds,
    3: required list<string> localAddresses,
    4: required i64 heapUsed,
    5: required i32 activePolls
}

struct LogPage {
    1: required list<string> lines,
    2: required i64 nextOffset,
    3: required bool rotated
}

struct RobotRuleInfo {
    1: required string service,
    2: required string address,
    3: required string masks,
    4: required string access,
    5: required bool enabled
}

struct RobotRuleChange {
    1: required RobotRuleInfo rule,
    2: optional string password
}

struct RobotAccessConfiguration {
    1: required string source,
    2: required list<RobotRuleInfo> rules,
    3: required list<string> peers
}

struct EchoSubscriptionInfo {
    1: required string peer,
    2: required string area,
    3: required string access
}

struct EchoAccessConfiguration {
    1: required string source,
    2: required list<string> peers,
    3: required list<string> areas,
    4: required list<EchoSubscriptionInfo> subscriptions
}

struct QuarantineItem {
    1: required string id,
    2: required string name,
    3: required i64 bytes,
    4: required i64 modified,
    5: required string checksum,
    6: required string peer,
    7: required string reason,
    8: required i32 attempts,
    9: required i64 lastAttempt
}

struct QuarantinePage {
    1: required list<QuarantineItem> items,
    2: required string nextCursor,
    3: required bool hasMore,
    4: required list<string> peers
}

struct QuarantineRetryResult {
    1: required bool accepted,
    2: required string detail
}

service UiService {
    NetmailRoutesConfiguration netmailRoutes(1:string session) throws (1:UiError error),
    void saveNetmailRoute(1:string session, 2:NetmailRouteInfo rule) throws (1:UiError error),
    void deleteNetmailRoute(1:string session, 2:string id) throws (1:UiError error),
    NetmailRoutePreview previewNetmailRoute(1:string session, 2:string destination, 3:string source, 4:bool direct) throws (1:UiError error),
    list<ExtensionSettings> extensions(1:string session) throws (1:UiError error),
    void saveExtension(1:string session, 2:string id, 3:string section, 4:map<string,string> values) throws (1:UiError error),
    QuarantinePage quarantine(1:string session, 2:string after, 3:i32 limit) throws (1:UiError error),
    QuarantineRetryResult retryQuarantine(1:string session, 2:string id, 3:string checksum, 4:string peer) throws (1:UiError error),
    void archiveQuarantine(1:string session, 2:string id, 3:string checksum) throws (1:UiError error),
    EchoAccessConfiguration echoAccess(1:string session) throws (1:UiError error),
    RobotAccessConfiguration robotAccess(1:string session) throws (1:UiError error),
    void saveRobotRule(1:string session, 2:RobotRuleChange change) throws (1:UiError error),
    void deleteRobotRule(1:string session, 2:string service, 3:string address) throws (1:UiError error),
    NodeStatus status(1:string session) throws (1:UiError error),
    list<AreaInfo> areas(1:string session) throws (1:UiError error),
    MailPage echoMessages(1:string session, 2:string area, 3:i64 beforeId, 4:i32 limit) throws (1:UiError error),
    EchoContext echoContext(1:string session, 2:string area, 3:i64 id, 4:i64 afterId, 5:i32 limit) throws (1:UiError error),
    MailPage messages(1:string session, 2:Mailbox mailbox, 3:string area, 4:i64 afterId, 5:i32 limit) throws (1:UiError error),
    MailItem message(1:string session, 2:Mailbox mailbox, 3:string area, 4:i64 id) throws (1:UiError error),
    void markRead(1:string session, 2:Mailbox mailbox, 3:string area, 4:i64 id, 5:bool read) throws (1:UiError error),
    void deleteMessage(1:string session, 2:Mailbox mailbox, 3:i64 id) throws (1:UiError error),
    i32 pendingNetmail(1:string session) throws (1:UiError error),
    i32 sendPendingNetmail(1:string session) throws (1:UiError error),
    list<MailDraft> drafts(1:string session) throws (1:UiError error),
    void saveDraft(1:string session, 2:MailDraft draft) throws (1:UiError error),
    void deleteDraft(1:string session, 2:string id) throws (1:UiError error),
    string send(1:string session, 2:MailDraft draft, 3:string requestId) throws (1:UiError error),
    list<LinkInfo> links(1:string session) throws (1:UiError error),
    void subscribe(1:string session, 2:string peer, 3:string area, 4:string access) throws (1:UiError error),
    void unsubscribe(1:string session, 2:string peer, 3:string area) throws (1:UiError error),
    void rescan(1:string session, 2:string peer, 3:string area, 4:i32 count, 5:string requestId) throws (1:UiError error),
    void processMail(1:string session) throws (1:UiError error),
    PollInfo poll(1:string session, 2:string peer) throws (1:UiError error),
    list<PollInfo> polls(1:string session) throws (1:UiError error),
    list<PollInfo> pollQueue(1:string session) throws (1:UiError error),
    PollInfo pollDetails(1:string session, 2:string id) throws (1:UiError error),
    PollPage pollHistory(1:string session, 2:string before, 3:i32 limit) throws (1:UiError error),
    PollLogPage pollLog(1:string session, 2:string id, 3:i64 offset, 4:i32 limit) throws (1:UiError error),
    void cancelPoll(1:string session, 2:string id) throws (1:UiError error),
    list<QueueItem> outbound(1:string session, 2:string peer, 3:i32 limit) throws (1:UiError error),
    // offset=-1 requests the latest bounded window; subsequent cursors are UTF-8 byte offsets.
    LogPage logs(1:string session, 2:i64 offset, 3:i32 limit) throws (1:UiError error)
}
