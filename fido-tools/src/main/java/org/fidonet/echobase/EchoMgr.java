package org.fidonet.echobase;

import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.util.List;

public class EchoMgr {

    private final EchoBase echosbase;
    private EchoList echoList;
    private String echoPath;

    public EchoMgr(EchoBase base, EchoList echoList, String echoPath) {
        this.echosbase = base;
        this.echoPath = echoPath;
        this.echoList = echoList;
    }

    public void addMessage(Message msg, FTNAddr myAddr) throws IOException, EchoBaseException {
        if (!echoList.isInList(msg.getArea().toLowerCase())) {
            echoList.addArea(msg.getArea().toLowerCase(), echoPath, msg.getUpLink(), myAddr);
            echosbase.createArea(msg.getArea().toLowerCase());
        }
        echosbase.openArea(msg.getArea().toLowerCase());
        echosbase.addMessage(msg);
        echosbase.closeArea();
    }

    public void getMessage(String area, int id) throws EchoBaseException {
        echosbase.openArea(area);
        echosbase.getMessage(id);
        echosbase.closeArea();

    }

    private void createArea(String name) throws EchoBaseException {
        echosbase.createArea(name);
    }

    public List<String> getEchos() {
        return echoList.getEchoList();
    }

    public boolean isEchoExists(String name) {
        return echoList.isInList(name);
    }

}
