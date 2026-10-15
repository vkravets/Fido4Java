package org.fidonet.nodelist;

import org.fidonet.types.FTNAddr;

import java.util.List;
import java.util.Objects;

/** A directory record, separate from trusted links, passwords and routing permissions. */
public final class DirectoryEntry {
    private final String source;
    private final String address;
    private final String keyword;
    private final String system;
    private final String location;
    private final String sysop;
    private final String phone;
    private final String speed;
    private final Integer region;
    private final Integer hub;
    private final List<String> flags;
    private final long line;

    public DirectoryEntry(String source,
                          FTNAddr address,
                          String keyword,
                          String system,
                          String location,
                          String sysop,
                          String phone,
                          String speed,
                          Integer region,
                          Integer hub,
                          List<String> flags,
                          long line) {
        this.source = Objects.requireNonNull(source);
        this.address = address.toString();
        this.keyword = keyword;
        this.system = system;
        this.location = location;
        this.sysop = sysop;
        this.phone = phone;
        this.speed = speed;
        this.region = region;
        this.hub = hub;
        this.flags = List.copyOf(flags);
        this.line = line;
    }

    public String getSource() {
        return source;
    }

    public FTNAddr getAddress() {
        return new FTNAddr(address);
    }

    String addressKey() {
        return address;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getSystem() {
        return system;
    }

    public String getLocation() {
        return location;
    }

    public String getSysop() {
        return sysop;
    }

    public String getPhone() {
        return phone;
    }

    public String getSpeed() {
        return speed;
    }

    public Integer getRegion() {
        return region;
    }

    public Integer getHub() {
        return hub;
    }

    public List<String> getFlags() {
        return flags;
    }

    public long getLine() {
        return line;
    }

    public List<InternetEndpoint> getBinkpEndpoints() {
        return InternetEndpoint.binkp(this);
    }
}
