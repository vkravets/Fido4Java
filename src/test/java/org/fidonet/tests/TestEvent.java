package org.fidonet.tests;

import org.fidonet.jftn.event.Event;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 4:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestEvent implements Event {
    private String param;

    public TestEvent(String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }
}
