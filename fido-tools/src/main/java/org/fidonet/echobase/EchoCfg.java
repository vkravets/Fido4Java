package org.fidonet.echobase;

import org.fidonet.types.FTNAddr;

public class EchoCfg {
    public String Name;
    public String Path;
    public String Type;
    public String Group;
    public FTNAddr AKA;
    public FTNAddr Link;

    public String getEchoString() {
        String result = "EchoArea " + Name + " " + Path + " -b " + Type + " -a " + AKA.toString() + " ";
        if (Group != null) result += ("-g " + Group);
        result += Link.toString();
        return result;
    }

}
