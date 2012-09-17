package org.fidonet.protocol.binkp.BSO;

import org.fidonet.types.FTNAddr;

public class LoFile {

    String filename;
    String flavor;
    FTNAddr linkaddr;

    public LoFile(FTNAddr addr, String fl)
    {
        linkaddr = addr;
        filename = linkaddr.toHex();
        this.flavor = fl;
    }

    public String getFullName()
    {
        return filename + "." + flavor2ext();
    }

    private String flavor2ext()
    {
        if(flavor.equals("direct")) return flavor = "dlo";
        if(flavor.equals("hold")) return flavor = "hlo";
        if(flavor.equals("crash")) return flavor = "clo";
        if(flavor.equals("immediate")) return flavor = "ilo";
        if(flavor.equals("normal")) return flavor = "flol";
        return null;
    }
}
