package org.fidonet.echobase;

import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.types.Message;

public interface EchoBase {

    public void createArea(String name) throws EchoBaseException;

    public void openArea(String name) throws EchoBaseException;

    public void closeArea() throws EchoBaseException;

    public void addMessage(Message msg) throws EchoBaseException;

    public void delMessage(int id) throws EchoBaseException;

    public void getMessage(int id) throws EchoBaseException;

}
