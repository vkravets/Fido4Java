package org.fidonet.types;

public class Link {
    private final FTNAddr addr;
    private final FTNAddr myaddr;
    private final String pass;

// --Commented out by Inspection START (13.11.10 21:53):
//    public Link(String _addr, String _myaddr, String _pass) {
//        addr = new FTNAddr(_addr);
//        myaddr = new FTNAddr(_myaddr);
//        pass = _pass;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

    public Link(String linkstr) {
        int adrend = linkstr.indexOf(',');
        int akaend = linkstr.indexOf(',', adrend + 1);
        addr = new FTNAddr(linkstr.substring(0, adrend));
        myaddr = new FTNAddr(linkstr.substring(adrend + 1, akaend));
        pass = linkstr.substring(akaend + 1, linkstr.length());
    }

    public FTNAddr getAddr() {
        return addr;
    }

    public String getPass() {
        return pass;
    }

    public FTNAddr getMyaddr() {
        return myaddr;
    }

    // --Commented out by Inspection START (13.11.10 21:53):
//    public FTNAddr getMyaddr() {
//        return myaddr;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

// --Commented out by Inspection START (13.11.10 21:53):
//    public String getPass() {
//        return pass;
//    }
// --Commented out by Inspection STOP (13.11.10 21:53)

    @Override
    public String toString() {
        return myaddr + " -> " + getAddr().toString();
    }
}
