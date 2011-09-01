package org.fidonet.echobase;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

public class EchoMgr {

    private final EchoBase echosbase;
    private EchoList echoList;
    private String areaListFile;
    private String echoPath;

    private boolean valid;

    public EchoMgr(EchoBase base, EchoList echoList, String echoPath) {
        this.echosbase = base;
        this.echoPath = echoPath;
        this.echoList = echoList;
    }

    public boolean isValid() {
        return valid;
    }

    public void addMessage(Message msg, FTNAddr myAddr) {
        if (!echoList.isInList(msg.getArea().toLowerCase())) {
            echoList.addArea(msg.getArea().toLowerCase(), echoPath, msg.getUpLink(), myAddr);
            echosbase.createArea(msg.getArea().toLowerCase());
        }
        echosbase.openArea(msg.getArea().toLowerCase());
        echosbase.addMessage(msg);
        echosbase.closeArea();
    }

    public void getMessage(String area, int id) {
        echosbase.openArea(area);
        echosbase.getMessage(id);
        echosbase.closeArea();

    }

    private void createArea(String name) {
        echosbase.createArea(name);
    }

}
