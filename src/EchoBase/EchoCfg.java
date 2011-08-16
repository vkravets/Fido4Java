package EchoBase;

import types.FTNAddr;

public class EchoCfg {
    public String Name;
    public String Path;
    public String Type;
    public String Group;
    public FTNAddr AKA;
    public FTNAddr Link;

    public String ListString() {
        String result = "EchoArea " + Name + " " + Path + " -b " + Type + " -a " + AKA.toString() + " ";
        if (Group != null) result += ("-g " + Group);
        result += Link.toString();
        return result;
    }

}
