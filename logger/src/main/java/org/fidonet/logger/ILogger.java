package org.fidonet.logger;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 9/16/11
 * Time: 10:37 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ILogger {

    public void debug(String message);
    public void debug(String message, Throwable e);

    public void error(String message);
    public void error(String message, Throwable e);

    public void warn(String message);
    public void warn(String message, Throwable e);

    public void info(String message);
    public void info(String message, Throwable e);
}
