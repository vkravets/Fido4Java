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

import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 9/23/11
 * Time: 10:11 AM
 */
public class ConsoleLogger implements ILogger{

    private static String debugPrefix = "DEBUG";
    private static String warnPrefix = "WARN";
    private static String errorPrefix = "ERROR";
    private static String infoPrefix = "INFO";

    private static Method getStackTraceMethod;
    private static Method getClassNameMethod;
    private static Method getMethodNameMethod;
    private static Method getFileNameMethod;
    private static Method getLineNumberMethod;

    public final static String NA = "?";

    private String fqdmClassName;

    static {
      try {
          Class[] noArgs = null;
          getStackTraceMethod = Throwable.class.getMethod("getStackTrace", noArgs);
          Class stackTraceElementClass = Class.forName("java.lang.StackTraceElement");
          getClassNameMethod = stackTraceElementClass.getMethod("getClassName", noArgs);
          getMethodNameMethod = stackTraceElementClass.getMethod("getMethodName", noArgs);
          getFileNameMethod = stackTraceElementClass.getMethod("getFileName", noArgs);
          getLineNumberMethod = stackTraceElementClass.getMethod("getLineNumber", noArgs);
      } catch(ClassNotFoundException ignored) {
      } catch(NoSuchMethodException ignored) {
      }
    }

    public ConsoleLogger(String className) {
        this.fqdmClassName = className;
    }

    public String getLocationString() {
        if (getLineNumberMethod != null) {
            try {
                Object[] noArgs = null;
                Throwable t = new Throwable();
                Object[] elements =  (Object[]) getStackTraceMethod.invoke(t, noArgs);
                String prevClass = NA;
                for(int i = elements.length - 1; i >= 0; i--) {
                    String thisClass = (String) getClassNameMethod.invoke(elements[i], noArgs);
                    if(ConsoleLogger.class.getName().equals(thisClass)) {
                        int caller = i + 1;
                        if (caller < elements.length) {
                            String className = prevClass;
                            String methodName = (String) getMethodNameMethod.invoke(elements[caller], noArgs);
                            String fileName = (String) getFileNameMethod.invoke(elements[caller], noArgs);
                            if (fileName == null) {
                                fileName = NA;
                            }
                            int line = ((Integer) getLineNumberMethod.invoke(elements[caller], noArgs)).intValue();
                            String lineNumber;
                            if (line < 0) {
                                lineNumber = NA;
                            } else {
                                lineNumber = String.valueOf(line);
                            }
                            StringBuffer buf = new StringBuffer();
//                            buf.append(className);
//                            buf.append(".");
                            buf.append(methodName);
                            buf.append("(");
                            buf.append(fileName);
                            buf.append(":");
                            buf.append(lineNumber);
                            buf.append(")");
                            return buf.toString();
                        }
                    }
                    prevClass = thisClass;
                }
            } catch(IllegalAccessException ignored) {
            } catch(InvocationTargetException ex) {
                if (ex.getTargetException() instanceof InterruptedException
                        || ex.getTargetException() instanceof InterruptedIOException) {
                    Thread.currentThread().interrupt();
                }
            } catch(RuntimeException ignored) {
            }
        }
        return null;
    }

    public void log(String prefix, String message) {
        String time = new SimpleDateFormat().format(new Date());
        String location = getLocationString();
        System.out.println(String.format("%s %s [%s] %s", time, location, prefix, message));
    }

    @Override
    public void debug(String message) {
        log(debugPrefix, message);
    }

    @Override
    public void debug(String message, Throwable e) {
        log(debugPrefix, message);
    }

    @Override
    public void error(String message) {
        log(errorPrefix, message);
    }

    @Override
    public void error(String message, Throwable e) {
        log(errorPrefix, message);
    }

    @Override
    public void warn(String message) {
        log(warnPrefix, message);
    }

    @Override
    public void warn(String message, Throwable e) {
        log(warnPrefix, message);
    }

    @Override
    public void info(String message) {
        log(infoPrefix, message);
    }

    @Override
    public void info(String message, Throwable e) {
        log(infoPrefix, message);
    }

}
