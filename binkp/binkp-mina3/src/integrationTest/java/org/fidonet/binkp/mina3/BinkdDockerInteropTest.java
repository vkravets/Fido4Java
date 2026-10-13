package org.fidonet.binkp.mina3;

import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.interop.AbstractBinkdInteropTest;
import org.fidonet.binkp.interop.ExchangeFiles;
import org.fidonet.binkp.interop.JftnPeer;

public class BinkdDockerInteropTest extends AbstractBinkdInteropTest {
    public BinkdDockerInteropTest(ServerRole role, boolean requestNr) {
        super(role, requestNr);
    }

    @Override protected JftnPeer createPeer(ServerRole role, boolean requestNr, ExchangeFiles files) {
        return new JftnTestPeer(role, requestNr, files);
    }
}