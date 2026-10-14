package org.fidonet.jftn.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.Collections;

public class RpcIntegrationTest {
    @Test(timeout = 15000)
    public void dispatchesAllServicesOverRealTcp() throws Exception {
        try (Server server = new Server(0, "127.0.0.1", new ServerHandlerTestMock())) {
            server.run();
            try (TFramedTransport connection = connect(server)) {
                assertEquals(Response.OK, new Api.Client(protocol(connection, "api")).ping());
                assertEquals("1.0.1",
                             new Api.Client(protocol(connection, "api")).getVersion().getVersion());
                LoginService.Client login = new LoginService.Client(protocol(connection, "login"));
                LoginResponse response = login.login(new Credential("test", "test"));
                assertEquals(Response.OK, response.getRespose_status());
                assertEquals("session_id_test_1", response.getSession_id());
                login.logout(response.getSession_id());
                assertEquals(Collections.emptyList(),
                             new MessageService.Client(protocol(connection,
                                                                "messages")).getMessages("test_area"));
                StatisticService.Client statistics = new StatisticService.Client(protocol(connection,
                                                                                          "statistics"));
                assertEquals(new JavaStatistics("1.7", 1024L, 512L, (short) 10, (short) 2),
                             statistics.getJavaStatistics());
                assertEquals(new NodeStatistics("1.0", (short) 13, 100, 23098, 101),
                             statistics.getNodeStatistics());
            }
        }
    }

    @Test(timeout = 15000)
    public void defaultHandlersExposeApiAndRejectUnavailableStatistics() throws Exception {
        try (Server server = new Server(0)) {
            server.run();
            try (TFramedTransport connection = connect(server)) {
                Api.Client api = new Api.Client(protocol(connection, "api"));
                assertEquals(Response.OK, api.ping());
                assertEquals(new VersionResponse(Response.OK, "1.0"), api.getVersion());
                // The default login handler is currently a stub; this checks RPC wiring only.
                LoginService.Client login = new LoginService.Client(protocol(connection, "login"));
                LoginResponse session = login.login(new Credential("test", "test"));
                assertEquals("session_id_1", session.getSession_id());
                login.logout(session.getSession_id());
                assertTrue(new MessageService.Client(protocol(connection,
                                                              "messages")).getMessages("empty")
                                                                          .isEmpty());
            }
            // A missing service must produce a protocol error, rather than a null-handler crash.
            try (TFramedTransport connection = connect(server)) {
                try {
                    new StatisticService.Client(protocol(connection,
                                                         "statistics")).getJavaStatistics();
                    fail("Unavailable service must not succeed");
                } catch (TException expected) {
                }
            }
        }
    }

    @Test(timeout = 15000)
    public void closingBeforeStartReleasesPortAndStopIsIdempotent() throws Exception {
        Server server = new Server(0);
        int port = server.getLocalPort();
        server.stop();
        server.stop();
        try (ServerSocket socket = new ServerSocket(port)) {
            assertEquals(port, socket.getLocalPort());
        }
        try {
            server.run();
            fail("Stopped server must not restart");
        } catch (IllegalStateException expected) {
        }
    }

    @Test(timeout = 15000)
    public void reportsOccupiedPortAndRejectsDuplicateStart() throws Exception {
        try (Server server = new Server(0)) {
            try {
                new Server(server.getLocalPort());
                fail("Occupied port must fail immediately");
            } catch (IllegalStateException expected) {
                assertNotNull(expected.getCause());
            }
            server.run();
            try {
                server.run();
                fail("Duplicate start must fail");
            } catch (IllegalStateException expected) {
            }
        }
    }

    private static TFramedTransport connect(Server server) throws Exception {
        TSocket socket = new TSocket("127.0.0.1", server.getLocalPort());
        socket.setTimeout(3000);
        TFramedTransport connection = new TFramedTransport(socket);
        connection.open();
        return connection;
    }

    private static TProtocol protocol(TFramedTransport connection, String service) {
        return new TMultiplexedProtocolFactory(service).getProtocol(connection);
    }
}
