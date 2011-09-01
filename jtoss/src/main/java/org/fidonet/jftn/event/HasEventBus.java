package org.fidonet.jftn.event;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 6:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class HasEventBus {

    private static EventBus eventBusinstance;

    protected EventBus getEventBus() {
        if (eventBusinstance == null) {
            eventBusinstance = new EventBus();
        }
        return eventBusinstance;
    }

}
