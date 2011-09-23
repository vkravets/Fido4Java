package org.fidonet.misc;

import java.nio.ByteBuffer;

public class PktTemp {
    private String name;
    private ByteBuffer pkt;

    public PktTemp(String name, ByteBuffer pkt) {
        this.name = name;
        this.pkt = pkt;
    }

    public String getName() {
        return name;
    }

    public ByteBuffer getPkt() {
        return pkt;
    }
}
