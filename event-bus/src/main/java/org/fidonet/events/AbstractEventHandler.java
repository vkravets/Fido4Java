package org.fidonet.events;

import net.engio.mbassy.listener.Listener;
import net.engio.mbassy.listener.References;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/17/14
 * Time: 12:02 AM
 */
//@Listener(references = References.Strong)
public abstract class AbstractEventHandler<T extends Event> implements EventHandler<T> {
    public abstract void onEventHandle(T event);
}
