package org.fidonet.jftn.rpc.auth;

/** An approved posting identity or a request awaiting an administrator's decision. */
public final class AddressClaim {
    public final String address;
    public final String state;

    public AddressClaim(String address, String state) {
        this.address = address;
        this.state = state;
    }
}
