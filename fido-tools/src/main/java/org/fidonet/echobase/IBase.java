package org.fidonet.echobase;

import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:24 AM
 */
public interface IBase {

    public void open();

    public void createArea(String areaName);
    public void createArea(String areaName, String description);

    public void addMessage(Message message, String areaname);


    public Iterator<Message> getMessages(String areaname);
    public Iterator<Message> getMessages(String areaname, int bundleSize);

    public Iterator<Message> getMessages(Link link);
    public Iterator<Message> getMessages(Link link, int bundleSize);
    public Iterator<Message> getMessages(Link link, String areaname, int bundleSize);
    public Iterator<Message> getMessages(Link link, String areaname, long startMessage, int bundleSize);

    public long getMessageSize(String areaname);

    public boolean isDupe(Message message);

    public void close();
}
