package org.fidonet.types;

public class Link {
    private FTNAddr addr;
    private FTNAddr myaddr;
    private String pass;
    private String hostAddress;
    private int port;
    private String boxPath;

    public Link(String linkstr) {

        String[] linkToken = linkstr.split(",");
        if (linkToken.length < 2) {
            throw new IllegalArgumentException("Invalid link configuration");
        }
        addr = new FTNAddr(linkToken[0].trim());
        myaddr = new FTNAddr(linkToken[1].trim());
        if (linkToken.length > 3)
            pass = linkToken[2].trim();
        if (linkToken.length >=4) {
            String url = linkToken[3].trim();
            if (url.contains(":")) {
                String[] hostToken = url.split(":");
                this.hostAddress = hostToken[0];
                this.port = Integer.valueOf(hostToken[1].trim());
            } else {
                this.hostAddress = url;
                this.port = 0;
            }
        }
    }

    public FTNAddr getAddr() {
        return addr;
    }

    public String getPass() {
        return (pass != null && pass.equals("-")) ? "":pass;
    }

    public FTNAddr getMyaddr() {
        return myaddr;
    }

    @Override
    public String toString() {
        return myaddr + " -> " + getAddr().toString();
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getPort() {
        return port;
    }

    public String getBoxPath() {
        return boxPath;
    }

    public void setBoxPath(String boxPath) {
        this.boxPath = boxPath;
    }
}
