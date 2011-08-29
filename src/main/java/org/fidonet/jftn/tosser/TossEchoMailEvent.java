package org.fidonet.jftn.tosser;

import org.fidonet.types.Message;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 8/29/11
 * Time: 5:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class TossEchoMailEvent extends TossNetmailEvent{

    public TossEchoMailEvent(Message message) {
        super(message);
    }
}
