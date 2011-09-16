package org.fidonet.types;

public class Link {
    private final FTNAddr addr;
    private final FTNAddr myaddr;
    private final String pass;

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

    @Override
    public String toString() {
        return myaddr + " -> " + getAddr().toString();
    }
}
