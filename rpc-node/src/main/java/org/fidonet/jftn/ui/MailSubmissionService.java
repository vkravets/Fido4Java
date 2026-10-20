package org.fidonet.jftn.ui;

import org.fidonet.jftn.MailRuntime;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.apache.thrift.TSerializer;

import java.util.Base64;
import java.util.Date;

/** Idempotent draft submission under the mail runtime lock; callers authorize at the RPC boundary. */
final class MailSubmissionService {
    private final MailRuntime mail;
    private final UserMailStateStore state;

    MailSubmissionService(MailRuntime mail, UserMailStateStore state) {
        this.mail = mail;
        this.state = state;
    }

    String sendValidated(String owner, MailDraft draft, String requestId) throws Exception {
        return mail.operate(tosser -> {
            String key = ScriptState.digest(owner + ":" + requestId);
            String hash = ScriptState.digest(Base64.getEncoder()
                                                   .encodeToString(new TSerializer().serialize(draft)));
            Message message = createMessage(draft);
            if (draft.getMailbox() != Mailbox.ECHO
                    && !tosser.previewNetmail(message, null).isPermitted()) {
                throw new UiError("NO_ROUTE",
                                  "No route to recipient; contact the station administrator.");
            }
            tosser.submissionTargets(message);
            String msgid = state.reserve(key, hash, () -> tosser.assignMessageId(message));
            message.setMsgId(msgid);
            if (draft.getMailbox() == Mailbox.ECHO) {
                tosser.submitEchomail(message);
            } else {
                message.setAttributes(1);
                tosser.stageNetmail(message, "ui:" + key);
            }
            state.delete(owner, draft.getId());
            return msgid;
        });
    }

    static Message createMessage(MailDraft draft) {
        MailEncoding.validate(draft);
        String text = draft.getBody().replace("\r\n", "\r").replace('\n', '\r');
        String prefix = draft.getMailbox() == Mailbox.ECHO ? "AREA:" + draft.getArea() + "\r" : "";
        Message message = new Message(draft.getFromName(),
                                      draft.getToName(),
                                      new FTNAddr(draft.getFromAddress()),
                                      new FTNAddr(draft.getToAddress()),
                                      draft.getSubject(),
                                      prefix
                                      + "\1CHRS: "
                                      + MailEncoding.declaration(draft)
                                      + "\r"
                                      + text,
                                      new Date());
        if (draft.isSetReplyTo()) {
            message.getBody().getKludges().put("REPLY", draft.getReplyTo());
        }
        return message;
    }
}
