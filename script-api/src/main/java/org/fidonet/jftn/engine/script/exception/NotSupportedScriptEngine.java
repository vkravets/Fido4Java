package org.fidonet.jftn.engine.script.exception;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/16/14
 * Time: 4:26 PM
 */
public class NotSupportedScriptEngine extends Exception {
    public NotSupportedScriptEngine() {
    }

    public NotSupportedScriptEngine(String message) {
        super(message);
    }

    public NotSupportedScriptEngine(String message, Throwable cause) {
        super(message, cause);
    }

    public NotSupportedScriptEngine(Throwable cause) {
        super(cause);
    }

    public NotSupportedScriptEngine(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
