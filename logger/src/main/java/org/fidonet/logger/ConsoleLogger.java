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
 * To change this template use File | Settings | File Templates.
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
