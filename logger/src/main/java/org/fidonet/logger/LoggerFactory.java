package org.fidonet.logger;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 9/23/11
 * Time: 10:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class LoggerFactory {

    public static boolean useLog4j = true;

    public static ILogger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz.getCanonicalName());
    }

    public static ILogger getLogger(String className) {
        ILogger log = new ConsoleLogger(className);
        if (useLog4j) {
            try {
                ILogger logger = Log4jEngine.getLogger(className);
                if (logger != null)
                    return logger;
            } catch (LogEngineNotFoundException ex) {
                log.error("Logger engine error. Details: " + ex.getMessage());
            }
        }
        return log;
    }

}
