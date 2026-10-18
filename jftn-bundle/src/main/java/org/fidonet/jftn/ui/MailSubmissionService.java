package org.fidonet.jftn.ui;

import org.fidonet.jftn.MailRuntime;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;
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
        });
    }
}
