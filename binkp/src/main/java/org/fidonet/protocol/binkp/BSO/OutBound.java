package org.fidonet.protocol.binkp.BSO;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

/**
 *
 * @author toch
 */
public class OutBound {

    String path;
    File descr = null;
    
    private class LoFileFilter implements FileFilter
    {

        @Override
        public boolean accept(File pathname) {
            return pathname.isFile() && pathname.getName().endsWith("dlo");
        }
    }

    private class LinkFilesFilter implements FileFilter
    {

        private FTNAddr link;

        LinkFilesFilter(FTNAddr addr)
        {
            link = addr;
        }

        @Override
        public boolean accept(File pathname) {
            return pathname.isFile() && pathname.getName().contains(link.toHex());
        }
    }

    
    public OutBound(String p)
    {
        path = p;
        descr = new File(path);
    }
    
    public FTNAddr getPoll()
    {
        File[] filelist = descr.listFiles(new LoFileFilter());
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
            return new FTNAddr(2, netnum, nodenum, 0);
        }
    }

    public boolean setBusy(FTNAddr addr)
    {
        String bsyname = addr.toHex();
        File bsy = new File(path+"/"+bsyname+".bsy");
        if(bsy.exists())
        {
            System.out.println("Link is already busy!"+addr.toString());
            return false;
        }
        boolean bsycreated = false;
        try {
            bsycreated = bsy.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bsycreated;
    }

    public boolean setUnBusy(FTNAddr addr)
    {
        String bsyname = addr.toHex();
        File bsy = new File(path+"/"+bsyname+".bsy");
        return bsy.delete();
    }

    public void filesForLink(FTNAddr link)
    {
        File[] filelist = descr.listFiles(new LinkFilesFilter(link));
    }
}
