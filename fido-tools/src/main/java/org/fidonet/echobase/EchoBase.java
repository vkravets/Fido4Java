package org.fidonet.echobase;

import org.fidonet.types.Message;

public interface EchoBase {

    public void createArea(String name);

    public void openArea(String name);

    public void closeArea();

    public void addMessage(Message msg);

    public void delMessage(int id);

    public void getMessage(int id);

}
