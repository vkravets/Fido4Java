package org.fidonet.jftn.share;

import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.event.Event;
import org.fidonet.jftn.event.HasEventBus;

import javax.script.Invocable;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class HookInterpreter extends HasEventBus {

    public static void registerHook(JFtnShare jftn, Class<? extends Event> hookClass, Object hook) throws Exception {
        if ((jftn == null)) {
            throw new VerifyError("Parameter jftn is not correctly specified");
        }
        if ((hookClass == null)) {
            throw new VerifyError("Parameter name is empty");
        }
        if (hook == null) {
            throw new VerifyError("Parameter command is empty");
        }
        Hook commandObject = ((Invocable)jftn.getScriptManager().getJythonScriptEngine()).getInterface(hook, Hook.class);
        getEventBus().register(hookClass, commandObject);
    }

}
