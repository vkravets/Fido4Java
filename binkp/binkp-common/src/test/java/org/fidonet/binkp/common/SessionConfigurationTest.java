package org.fidonet.binkp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class SessionConfigurationTest {
    @Test
    void keptExistingSessionsAndCopiedCoherentIdentityAndLinksDuringReload() throws Exception {
        SessionContext first = context("A");
        SessionContext second = context("B");
        SessionContext existing = new SessionContext(first);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> changing = worker.submit(() -> publish(first, second));
            checkCopies(first);
            changing.get();
            first.reconfigure(second);
            assertEquals("B", new SessionContext(first).getStationConfig().getName());
            assertEquals("A", existing.getStationConfig().getName());
            assertEquals("A", existing.getLinksInfo().getLinks().get(0).getPass());
        } finally {
            worker.shutdownNow();
        }
    }

    private void publish(SessionContext target, SessionContext second) {
        SessionContext initial = new SessionContext(target);
        for (int index = 0; index < 2000; index++)
            target.reconfigure(index % 2 == 0 ? second : initial);
    }

    private void checkCopies(SessionContext template) {
        for (int index = 0; index < 2000; index++) {
            SessionContext session = new SessionContext(template);
            assertEquals(session.getStationConfig().getName(),
                         session.getLinksInfo().getLinks().get(0).getPass());
        }
    }

    private SessionContext context(String name) {
        StationConfig station = new StationConfig(name, "Sysop", "", "BINKP", "1:500/100");
        Link link = new Link(new FTNAddr("1:500/101"),
                             List.of(new FTNAddr("1:500/100")),
                             name,
                             "localhost",
                             24554);
        return new SessionContext(station, new LinksInfo(List.of(link)));
    }
}
