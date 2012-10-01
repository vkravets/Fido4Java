package org.fidonet.jftn.share;

import org.fidonet.events.Event;
import org.fidonet.events.HasEventBus;
import org.fidonet.jftn.engine.script.ScriptEngine;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class HookInterpreter extends HasEventBus {

    public void registerHook(ScriptEngine scriptManager, Class<? extends Event> hookClass, Object hook) throws Exception {
        if ((scriptManager == null)) {
            throw new VerifyError("Parameter jftn is empty");
        }
        if ((hookClass == null)) {
            throw new VerifyError("Parameter name is empty");
        }
        if (hook == null) {
            throw new VerifyError("Parameter command is empty");
        }
        Hook commandObject = scriptManager.getInterface(hook, Hook.class);
        getEventBus().register(hookClass, commandObject);
    }

}
