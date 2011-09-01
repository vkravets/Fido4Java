package org.fidonet.jftn.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 4:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class EventBus {

    public HashMap<Class<?>,List<EventHandler>> eventListenerMap;

    public EventBus() {
        eventListenerMap = new HashMap<Class<?>, List<EventHandler>>();
    }

    public void register(Class<? extends Event> event, EventHandler listener) {
        List<EventHandler> listeners = eventListenerMap.get(event);
        if (listeners == null) {
            listeners = new ArrayList<EventHandler>();
            eventListenerMap.put(event, listeners);
        }
        listeners.add(listener);
    }

    public void notify(Event event) {
        List<EventHandler> allListeners = getAllListeners(event.getClass());
        for (EventHandler handler : allListeners) {
            handler.onEventHandle(event);
        }
    }

    public List<EventHandler> getAllListeners(Class<?> event) {
        List<EventHandler> listener = eventListenerMap.get(event);
        if (listener != null) {
            return new ArrayList<EventHandler>(listener);
        } else {
            return new ArrayList<EventHandler>();
        }
    }

    public void clear() {
        eventListenerMap.clear();
    }
}
