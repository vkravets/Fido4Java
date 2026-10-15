namespace java org.fidonet.jftn.rpc

exception UiError {
    1: required string code,
    2: required string message
}

enum Mailbox { ECHO = 1, INBOX = 2, OUTBOX = 3 }

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
    11: optional i32 attributes
}

struct MailPage {
    1: required list<MailItem> items,
    2: required i64 nextId,
    3: required bool hasMore
}

struct AreaInfo {
    1: required string name,
    2: required i64 messages
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

struct PollInfo {
    1: required string id,
    2: required string address,
    3: required string state,
    4: required i64 created,
    5: required string detail
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

service UiService {
    NodeStatus status(1:string session) throws (1:UiError error),
    list<AreaInfo> areas(1:string session) throws (1:UiError error),
    MailPage messages(1:string session, 2:Mailbox mailbox, 3:string area, 4:i64 afterId, 5:i32 limit) throws (1:UiError error),
    MailItem message(1:string session, 2:Mailbox mailbox, 3:string area, 4:i64 id) throws (1:UiError error),
    void markRead(1:string session, 2:Mailbox mailbox, 3:string area, 4:i64 id, 5:bool read) throws (1:UiError error),
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
    void cancelPoll(1:string session, 2:string id) throws (1:UiError error),
    list<QueueItem> outbound(1:string session, 2:string peer, 3:i32 limit) throws (1:UiError error),
    LogPage logs(1:string session, 2:i64 offset, 3:i32 limit) throws (1:UiError error)
}
