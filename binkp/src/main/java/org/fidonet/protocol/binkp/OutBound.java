package org.fidonet.protocol.binkp;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

/**
 *
 * @author toch
 */
public class OutBound {
    
    String path;
    
    private class OutbFilter implements FileFilter
    {

        @Override
        public boolean accept(File pathname) {
            if(pathname.isFile() && pathname.getName().endsWith("lo"))
            {
                return true;
            }
            else return false;
        }
        
    }
    
    public OutBound(String p)
    {
        path = p;
    }
    
    public FTNAddr getPoll()
    {
        File outb = new File(path);
        File[] filelist = outb.listFiles(new OutbFilter());
        if(filelist == null) return null;
        if(filelist.length == 0) return null;
        else
        {
            String loshka = filelist[0].getPath();
            String polladdr = loshka.substring(loshka.length() - 8 - 3 - 1,loshka.indexOf("."));
            String net = polladdr.substring(0, 4);
            String node = polladdr.substring(4,8);
            int netnum = Integer.valueOf(net, 16);
            int nodenum = Integer.valueOf(node, 16);
            FTNAddr result = new FTNAddr(2, netnum, nodenum, 0);
            return result;
        }
    }

    public void setBusy(FTNAddr addr)
    {
        String bsyname = addr.toHex();
        File bsy = new File(path+"/"+bsyname+".bsy");
        try {
            bsy.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
