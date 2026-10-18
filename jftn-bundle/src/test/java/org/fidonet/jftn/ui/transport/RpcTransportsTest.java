package org.fidonet.jftn.ui.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.DefaultRpcServiceProvider;
import org.fidonet.jftn.rpc.RpcServiceProvider;
import org.fidonet.jftn.rpc.RpcTransport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Timeout(20)
class RpcTransportsTest {
    private final List<String> events = new ArrayList<>();

    @Test
    void supportsAdditionalTransportsAndSkipsDisabledOnes() throws Exception {
        try (RpcTransports transports = open(List.of(factory("tcp", listener("tcp", 1)),
                                                     factory("http", null),
                                                     factory("additional",
                                                             listener("additional", 3))))) {
            assertEquals(1, transports.getPort("tcp"));
            assertEquals(-1, transports.getPort("http"));
            assertEquals(3, transports.getPort("additional"));
            assertEquals(-1, transports.getPort("missing"));
        }
        assertEquals(List.of("start tcp", "start additional", "close additional", "close tcp"),
                     events);
    }

    @Test
    void startFailureClosesTheFailedListenerAndEarlierListeners() {
        RecordingTransport failing = listener("http", 2);
        failing.startFailure = new IllegalStateException("start failed");
        failing.closeFailure = new IllegalStateException("close failed");
        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> open(List.of(factory("tcp",
                                                                           listener("tcp", 1)),
                                                                   factory("http", failing),
                                                                   factory("unused",
                                                                           listener("unused",
                                                                                    3)))));
        assertSame(failing.startFailure, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of("start tcp", "start http", "close http", "close tcp"), events);
    }

    @Test
    void creationFailurePreservesTheOriginalErrorAndClosesEarlierListeners() {
        IOException expected = new IOException("bind failed");
        RpcTransportFactory failing = new RpcTransportFactory() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public RpcTransport create(JFtnConfig config, RpcServiceProvider handlers)
                    throws IOException {
                throw expected;
            }
        };
        assertSame(expected,
                   assertThrows(IOException.class,
                                () -> open(List.of(factory("tcp", listener("tcp", 1)), failing))));
        assertEquals(List.of("start tcp", "close tcp"), events);
    }

    @Test
    void closeContinuesAfterFailuresAndDoesNotCloseListenersTwice() throws Exception {
        RecordingTransport first = listener("tcp", 1);
        RecordingTransport second = listener("http", 2);
        first.closeFailure = new IllegalStateException("tcp close");
        second.closeFailure = new IllegalStateException("http close");
        RpcTransports transports = open(List.of(factory("tcp", first), factory("http", second)));
        RuntimeException failure = assertThrows(RuntimeException.class, transports::close);
        assertSame(second.closeFailure, failure.getCause());
        assertSame(first.closeFailure, failure.getSuppressed()[0]);
        transports.close();
        assertEquals(-1, transports.getPort("tcp"));
        assertEquals(List.of("start tcp", "start http", "close http", "close tcp"), events);
    }

    @Test
    void validatesNamesBeforeCreatingAnyListeners() {
        RpcTransportFactory tcp = factory("tcp", listener("tcp", 1));
        assertThrows(IllegalArgumentException.class, () -> open(List.of(tcp, tcp)));
        assertThrows(IllegalArgumentException.class,
                     () -> open(List.of(factory(" ", listener("bad", 2)))));
        assertEquals(List.of(), events);
    }

    @Test
    void occupiedHttpPortReleasesTheAlreadyStartedTcpListener() throws Exception {
        AtomicInteger tcpPort = new AtomicInteger();
        RpcTransportFactory tcp = new RpcTransportFactory() {
            @Override
            public String name() {
                return "tcp";
            }

            @Override
            public RpcTransport create(JFtnConfig config, RpcServiceProvider handlers) {
                RpcTransport listener = new TcpRpcTransportFactory().create(config, handlers);
                tcpPort.set(listener.getLocalPort());
                return listener;
            }
        };
        try (ServerSocket occupied = new ServerSocket(0)) {
            JFtnConfig config = config("RpcPort=0\nRpcHttpEnabled=true\nRpcHttpPort="
                                       + occupied.getLocalPort());
            assertThrows(IOException.class,
                         () -> RpcTransports.open(config,
                                                  new DefaultRpcServiceProvider(),
                                                  List.of(tcp, new HttpRpcTransportFactory())));
            try (ServerSocket released = new ServerSocket(tcpPort.get())) {
                assertEquals(tcpPort.get(), released.getLocalPort());
            }
        }
    }

    private RpcTransports open(List<RpcTransportFactory> factories) throws Exception {
        return RpcTransports.open(new JFtnConfig(), new DefaultRpcServiceProvider(), factories);
    }

    private static JFtnConfig config(String text) throws Exception {
        JFtnConfig config = new JFtnConfig();
        String complete = "SysOp=Test\nInbound=unused\nOutbound=unused\nTmp=unused\nEchoPath=unused\n"
                + "Link=1:500/101,1:500/100,secret,127.0.0.1\n" + text;
        config.load(new ByteArrayInputStream(complete.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private RecordingTransport listener(String name, int port) {
        return new RecordingTransport(name, port);
    }

    private static RpcTransportFactory factory(String name, RpcTransport listener) {
        return new RpcTransportFactory() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public RpcTransport create(JFtnConfig config, RpcServiceProvider handlers) {
                return listener;
            }
        };
    }

    private final class RecordingTransport implements RpcTransport {
        private final String name;
        private final int port;
        private RuntimeException startFailure;
        private RuntimeException closeFailure;

        private RecordingTransport(String name, int port) {
            this.name = name;
            this.port = port;
        }

        @Override
        public void run() {
            events.add("start " + name);
            if (startFailure != null) {
                throw startFailure;
            }
        }

        @Override
        public int getLocalPort() {
            return port;
        }

        @Override
        public void close() {
            events.add("close " + name);
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
