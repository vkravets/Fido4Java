package org.fidonet.binkp.common.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ADRCommandTest {
    @Test
    public void serverAdvertisesLocalAddressesBeforePeerIsIdentified() {
        SessionContext context = new SessionContext(new StationConfig("Test",
                                                                      "Sysop",
                                                                      "Test",
                                                                      "BINKP",
                                                                      "1:500/100 1:500/101"),
                                                    new LinksInfo());
        assertEquals("1:500/100.0@fidonet.org 1:500/101.0@fidonet.org",
                     new ADRCommand().getCommandArguments(context));
    }

    @Test
    public void identifiedPeerUsesItsConfiguredLocalAka() {
        Link link = new Link(new FTNAddr("1:500/102"),
                             Collections.singletonList(new FTNAddr("1:500/103")),
                             "secret",
                             "localhost",
                             0);
        LinksInfo links = new LinksInfo(Collections.singletonList(link));
        links.setCurLink(link.getAddr());
        SessionContext context = new SessionContext(new StationConfig("Test",
                                                                      "Sysop",
                                                                      "Test",
                                                                      "BINKP",
                                                                      "1:500/100"),
                                                    links);
        assertEquals("1:500/103.0@fidonet.org", new ADRCommand().getCommandArguments(context));
    }
}
