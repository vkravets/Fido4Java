package org.fidonet.binkp.config;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/17/12
 * Time: 11:08 AM
 */
public class StationConfig {
    private String name;
    private String sysopName;
    private String location;
    private String NDL;
    private String address;
    private ServerRole role;
    private boolean nrMode;
    private boolean cryptMode;

    public StationConfig(String name, String sysopName, String location, String NDL, String address) {
        this.name = name;
        this.sysopName = sysopName;
        this.location = location;
        this.NDL = NDL;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getSysopName() {
        return sysopName;
    }

    public String getLocation() {
        return location;
    }

    public String getNDL() {
        return NDL;
    }

    public String getAddress() {
        return address;
    }

    public ServerRole getRole() {
        return role;
    }

    public void setRole(ServerRole role) {
        this.role = role;
    }

    public boolean isNRMode() {
        return nrMode;
    }

    public void setNRMode(boolean state) {
        this.nrMode = state;
    }

    public boolean isCryptMode() {
        return cryptMode;
    }

    public void setCryptMode(boolean cryptMode) {
        this.cryptMode = cryptMode;
    }
}
