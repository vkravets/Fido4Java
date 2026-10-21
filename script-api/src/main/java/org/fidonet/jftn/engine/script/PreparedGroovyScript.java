package org.fidonet.jftn.engine.script;

import groovy.lang.MissingMethodException;
import groovy.lang.Script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One isolated script instance, evaluated and registered on the owning runtime thread. */
public final class PreparedGroovyScript {
    private static final Logger log = LoggerFactory.getLogger(PreparedGroovyScript.class);
    private final Class<? extends Script> type;
    private Script script;
    private final boolean cacheHit;

    PreparedGroovyScript(Class<? extends Script> type, boolean cacheHit) {
        this.type = type;
        this.cacheHit = cacheHit;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void evaluate() throws ReflectiveOperationException {
        script = type.getDeclaredConstructor().newInstance();
        script.run();
    }

    public void register(Object api) {
        invoke("register", api);
    }

    public void unload(Object api) {
        invoke("unload", api);
    }

    private void invoke(String method, Object api) {
        try {
            script.invokeMethod(method, new Object[]{api});
        } catch (MissingMethodException failure) {
            if (!failure.getMethod().equals(method) || failure.getType() != script.getClass()) {
                throw failure;
            }
            log.warn("Script doesn't have {} function. Skip", method);
        }
    }
}
