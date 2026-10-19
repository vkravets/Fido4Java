package org.fidonet.jftn.tosser.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.tosser.BsoOutbound;

import java.nio.file.Files;
import java.nio.file.Path;

/** Request phases share the durable BSO fixture but distinguish delivery from a positive answer. */
public final class FrqScenario {
    private FrqScenario() {
    }

    public static void main(String[] args) throws Exception {
        try (BsoScenario node = new BsoScenario(Path.of(args[0]), args[2], args[3])) {
            execute(node, args[1]);
        }
        System.out.println("Verified FRQ " + args[1]);
    }

    private static void execute(BsoScenario node, String command) throws Exception {
        switch (command) {
            case "prepare-request" :
            case "prepare-missing-request" :
                prepare(node, command.equals("prepare-request") ? "ANSWER NOTHERE" : "NOTHERE");
                break;
            case "verify-queued" :
                node.export();
                assertEquals(1, node.base.getQueuedNetmail().size());
                break;
            case "prepare-request-server" :
                Files.createDirectories(node.root.resolve("request-files"));
                Files.writeString(node.root.resolve("request-files/ANSWER.ZIP"),
                                  "Requested payload\r\n");
                break;
            case "verify-response" :
            case "verify-no-response" :
                node.export();
                assertTrue(node.base.getQueuedNetmail().isEmpty());
                assertFalse(Files.exists(new BsoOutbound(node.config).control(node.remote, "req")));
                verifyResponse(node, command.equals("verify-response"));
                break;
            case "verify-request-handled" :
                assertTrue(Files.readString(node.root.resolve("logs/request.srif"))
                                .contains("RemoteStatus PROTECTED"));
                assertTrue(Files.isRegularFile(node.root.resolve("logs/request.txt")));
                assertEquals(1, Files.readAllLines(node.root.resolve("logs/request.calls")).size());
                break;
            default :
                throw new IllegalArgumentException("Unknown FRQ phase " + command);
        }
    }

    private static void prepare(BsoScenario node, String requested) throws Exception {
        node.tosser.submitNetmail(node.message(requested, "File request\r", 0x0801), "request");
        node.export();
        assertEquals(requested.replace(" ", "\r\n") + "\r\n",
                     Files.readString(new BsoOutbound(node.config).control(node.remote, "req")));
    }

    private static void verifyResponse(BsoScenario node, boolean available) throws Exception {
        Path answer = node.root.resolve("inbound/ANSWER.ZIP");
        if (!available) {
            assertFalse(Files.exists(answer));
            return;
        }
        assertEquals("Requested payload\r\n", Files.readString(answer));
    }
}
