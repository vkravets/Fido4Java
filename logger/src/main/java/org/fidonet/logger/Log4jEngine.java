/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 9/23/11
 * Time: 9:22 AM
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

    public static ILogger getLogger(Class<?> clazz)  throws LogEngineNotFoundException {
        return Log4jEngine.getLogger(clazz.getCanonicalName());
    }

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
