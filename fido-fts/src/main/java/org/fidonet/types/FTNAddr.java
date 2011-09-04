package org.fidonet.types;

import java.util.regex.Pattern;

public class FTNAddr {

    private final int zone;
    private final int net;
    private final int node;
    private final int pnt;
    private final boolean valid;

// --Commented out by Inspection START (13.11.10 21:53):
//    public FTNAddr() {
//        zone = 0;
//        net = 0;
//        node = 0;
//        pnt = 0;
//        valid = true;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

    public FTNAddr(int z, int ne, int no, int p) {
        zone = z;
        net = ne;
        node = no;
        pnt = p;
        valid = true;
    }

    public FTNAddr(String _addr) {
        final String addr;
        if (_addr.contains("@")) {
            Pattern p = Pattern.compile("@");
            addr = p.split(_addr)[0];
        } else {
            addr = _addr;
        }
        final int ze = addr.indexOf(':');
        final int nete = addr.indexOf('/');
        int nodee = addr.indexOf('.');
        final int pnte = addr.length();
        if (ze == -1) {
            //System.out.println("Fuck fts!");
            zone = -1;
            net = -1;
            node = -1;
            pnt = -1;
            valid = false;
            return;
        }
        if (nete == -1) {
            zone = -1;
            net = -1;
            node = -1;
            pnt = -1;
            valid = false;
            return;
        }
        if (nodee == -1) {
            nodee = addr.length();
        }
        zone = Integer.valueOf(addr.substring(0, ze));
        net = Integer.valueOf(addr.substring(ze + 1, nete));
        node = Integer.valueOf(addr.substring(nete + 1, nodee));
        if (nodee < pnte) {
            pnt = Integer.valueOf(addr.substring(nodee + 1, pnte));
        } else {
            pnt = 0;
        }
        valid = true;
    }

    public String toString() {
        return zone + ":" + net + '/' + node + '.' + pnt;
    }

    public String toHex()
    {
        String hexnet = String.format("%04x",net);
        String hexnode = String.format("%04x",node);
        return hexnet + hexnode;
    }

// --Commented out by Inspection START (13.11.10 21:53):
//    public int getNet() {
//        return net;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

// --Commented out by Inspection START (13.11.10 21:53):
//    public int getNode() {
//        return node;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

// --Commented out by Inspection START (13.11.10 21:53):
//    public int getPnt() {
//        return pnt;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

// --Commented out by Inspection START (13.11.10 21:53):
//    public int getZone() {
//        return zone;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

    public boolean isValid() {
        return valid;
    }

    public boolean isEquals(FTNAddr raddr) {
        return (raddr.zone == zone) &&
                (raddr.net == net) &&
                (raddr.node == node) &&
                (raddr.pnt == pnt);
    }
}
