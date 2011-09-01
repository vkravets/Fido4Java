package org.fidonet.echobase;

import org.fidonet.types.Message;

public interface EchoBase {

    void createArea(String name);

    void openArea(String name);

    void closeArea();

    void addMessage(Message msg);

    void delMessage(int id);

    void getMessage(int id);

}
