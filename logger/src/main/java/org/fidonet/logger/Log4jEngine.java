package org.fidonet.logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 9/23/11
 * Time: 9:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class Log4jEngine implements ILogger{

    private static Boolean isLog4jExists = null;
    private static Class log4jClass = null;

    private Object log4j;
    private Method error;
    private Method errorEx;
    private Method warn;
    private Method warnEx;
    private Method debug;
    private Method debugEx;
    private Method info;
    private Method infoEx;


    public static ILogger getLogger(String className) throws LogEngineNotFoundException {
        try {
            if (isLog4jExists == null) {
                checkLog4j();
                return createLogger(className);
            } else if (isLog4jExists) {
                return createLogger(className);
            }
        } catch (LogEngineNotFoundException ex) {
            isLog4jExists = false;
            throw new LogEngineNotFoundException(ex);
        }
        return null;
    }

    private static ILogger createLogger(String className) throws LogEngineNotFoundException {
        return new Log4jEngine(className);
    }

    private static void checkLog4j() throws LogEngineNotFoundException {
        try {
            log4jClass = Class.forName("org.apache.log4j.Logger");
        } catch (ClassNotFoundException ex) {
            throw new LogEngineNotFoundException("Log4j engine is not found. Add log4j.jar to class path.");
        }
    }


    @SuppressWarnings("unchecked")
    public Log4jEngine(String className) throws LogEngineNotFoundException {
        try {
            Method getLog = log4jClass.getMethod("getLogger", String.class);
            log4j = getLog.invoke(null, className);
            debug = log4jClass.getMethod("debug", Object.class);
            debugEx = log4jClass.getMethod("debug", Object.class, Throwable.class);
            warn = log4jClass.getMethod("warn", Object.class);
            warnEx = log4jClass.getMethod("warn", Object.class, Throwable.class);
            error = log4jClass.getMethod("error", Object.class);
            errorEx = log4jClass.getMethod("error", Object.class, Throwable.class);
            info = log4jClass.getMethod("info", Object.class);
            infoEx = log4jClass.getMethod("info", Object.class, Throwable.class);
        } catch (NoSuchMethodException e) {
            throw new LogEngineNotFoundException("Incorrect version of log4j is used.");
        } catch (InvocationTargetException e) {
            throw new LogEngineNotFoundException("Incorrect version of log4j is used.");
        } catch (IllegalAccessException e) {
            throw new LogEngineNotFoundException("Incorrect version of log4j is used.");
        }
    }

    @Override
    public void debug(String message) {
        try {
            debug.invoke(log4j, message);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void debug(String message, Throwable e) {
        try {
            debugEx.invoke(log4j, message, e);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void error(String message) {
        try {
            error.invoke(log4j, message);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void error(String message, Throwable e) {
        try {
            errorEx.invoke(log4j, message, e);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void warn(String message) {
        try {
            warn.invoke(log4j, message);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void warn(String message, Throwable e) {
        try {
            warnEx.invoke(log4j, message, e);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void info(String message) {
        try {
            info.invoke(log4j, message);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    @Override
    public void info(String message, Throwable e) {
        try {
            infoEx.invoke(log4j, message, e);
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

}
