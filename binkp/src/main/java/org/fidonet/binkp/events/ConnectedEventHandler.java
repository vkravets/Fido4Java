package org.fidonet.binkp.events;

import net.engio.mbassy.listener.*;
import org.fidonet.events.AbstractEventHandler;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/17/14
 * Time: 12:06 AM
 */

public abstract class ConnectedEventHandler extends AbstractEventHandler<ConnectedEvent> {

    @Handler(rejectSubtypes = true, delivery = Invoke.Synchronously)
    public void handle(ConnectedEvent event) {
        onEventHandle(event);
    }

}
