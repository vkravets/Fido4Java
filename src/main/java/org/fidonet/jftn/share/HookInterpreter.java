package org.fidonet.jftn.share;

import org.fidonet.jftn.engine.script.ScriptManager;
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

    public static void registerHook(Class<? extends Event> hookClass, Object hook) throws Exception {
        Hook commandObject = ((Invocable)ScriptManager.getInstance().getJythonScriptEngine()).getInterface(hook, Hook.class);
        getEventBus().register(hookClass, commandObject);
    }

}
