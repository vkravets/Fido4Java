package org.fidonet.jftn.tosser;

import org.fidonet.jftn.event.Event;
import org.fidonet.types.Message;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 5:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class TossNetmailEvent implements Event {

    private Message message;

    public TossNetmailEvent(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }
}
