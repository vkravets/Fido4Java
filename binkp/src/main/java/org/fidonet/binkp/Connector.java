package org.fidonet.binkp;

import org.fidonet.events.HasEventBus;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/24/12
 * Time: 3:46 PM
 */
public abstract class Connector extends HasEventBus {

    protected final static int BINK_PORT = 24554;

    public abstract void run(SessionContext sessionContext) throws Exception;
    public abstract void stop(SessionContext sessionContext);

}
