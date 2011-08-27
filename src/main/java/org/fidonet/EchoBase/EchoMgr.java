package org.fidonet.EchoBase;

import org.fidonet.EchoBase.JAMEchoBase.JAMEchoBase;
import org.fidonet.types.Message;

public class EchoMgr {

    private final EchoBase echosbase;

    private boolean valid;

    public EchoMgr(String path) {
        valid = false;
        echosbase = new JAMEchoBase(path);
        EchoList.Load(org.fidonet.Config.Config.getArealistfile());
        valid = true;
    }

    public boolean isValid() {
        return valid;
    }

    public void addMessage(Message msg) {
        if (!EchoList.isInList(msg.getArea().toLowerCase())) {
            EchoList.addArea(msg.getArea().toLowerCase(), org.fidonet.Config.Config.getEchopath(), msg.getUpLink());
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
