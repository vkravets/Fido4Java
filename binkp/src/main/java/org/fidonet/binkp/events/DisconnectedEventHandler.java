package org.fidonet.binkp.events;

import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Invoke;
import org.fidonet.events.AbstractEventHandler;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/17/14
 * Time: 12:06 AM
 */
public abstract class DisconnectedEventHandler extends AbstractEventHandler<DisconnectedEvent> {

    @Handler(rejectSubtypes = true, delivery = Invoke.Asynchronously)
    public void handle(DisconnectedEvent event) {
        onEventHandle(event);
    }

}
