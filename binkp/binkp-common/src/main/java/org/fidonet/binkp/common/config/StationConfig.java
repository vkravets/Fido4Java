/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.binkp.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;

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
    private boolean nrEnabled = true;
    private boolean binkp11Enabled = true;
    private boolean cryptMode;
    private Path partialDirectory = Paths.get("inbound", ".binkp-partials");

    public Path getPartialDirectory() {
        return partialDirectory;
    }

    public void setPartialDirectory(Path directory) {
        partialDirectory = directory;
    }

    public StationConfig(String name,
                         String sysopName,
                         String location,
                         String NDL,
                         String address) {
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

    /** Requests NR for incoming files by sending OPT NR to the peer. */
    public void setNRMode(boolean state) {
        this.nrMode = state;
    }

    /** Local policy for NR, independent of whether we request it from the peer. */
    public boolean isNREnabled() {
        return nrEnabled;
    }

    /** NR support is mandatory when advertising binkp 1.1. */
    public boolean isBinkp11Enabled() {
        return binkp11Enabled && nrEnabled;
    }

    public void setBinkp11Enabled(boolean enabled) {
        binkp11Enabled = enabled;
    }

    /** Set before opening a session. False also selects binkp 1.0 for this station. */
    public void setNREnabled(boolean enabled) {
        nrEnabled = enabled;
    }

    public boolean isNRRequested() {
        return nrMode;
    }

    public void setNRRequested(boolean requested) {
        nrMode = requested;
    }

    public boolean isCryptMode() {
        return cryptMode;
    }

    public void setCryptMode(boolean cryptMode) {
        this.cryptMode = cryptMode;
    }
}
