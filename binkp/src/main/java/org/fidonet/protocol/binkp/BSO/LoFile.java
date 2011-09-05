package org.fidonet.protocol.binkp.BSO;

import java.io.BufferedReader;
import java.io.File;

public class LoFile {

    private class LoFileLine
    {
        String filename;
        String modif;
    }

    String filename;
    String flavor;
    LoFileLine[] lines;

    public LoFile(String name)
    {
        filename = name;
        flavor = name.substring(name.length() - 4, name.length());
        if(flavor.equals(".dlo")) flavor = "direct";
        if(flavor.equals(".hlo")) flavor = "hold";
        if(flavor.equals(".clo")) flavor = "crash";
        if(flavor.equals(".ilo")) flavor = "immediate";
        if(flavor.equals(".flo")) flavor = "normal";
        /*
         * TODO: Load files here
         *
         */
    }

}
