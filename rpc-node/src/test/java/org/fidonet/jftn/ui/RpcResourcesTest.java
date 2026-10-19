package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class RpcResourcesTest {
    @Test
    void closesAllResourcesInReverseOrderAndIsIdempotent() {
        List<String> closed = new ArrayList<>();
        RpcResources resources = new RpcResources();
        resources.add(() -> closed.add("accounts"));
        resources.add(() -> closed.add("backend"));
        resources.add(() -> closed.add("transports"));
        resources.close();
        resources.close();
        assertEquals(List.of("transports", "backend", "accounts"), closed);
    }

    @Test
    void cleanupFailuresDoNotPreventClosingOtherResources() {
        List<String> closed = new ArrayList<>();
        RpcResources resources = new RpcResources();
        resources.add(() -> closed.add("accounts"));
        IOException expected = new IOException("backend close");
        resources.add(() -> {
            throw expected;
        });
        resources.add(() -> closed.add("transports"));
        RuntimeException failure = assertThrows(RuntimeException.class, resources::close);
        assertSame(expected, failure.getCause());
        assertEquals(List.of("transports", "accounts"), closed);
        resources.close();
    }

    @Test
    void preservesStartupFailureWhenCleanupAlsoFails() {
        RpcResources resources = new RpcResources();
        IOException cleanup = new IOException("close failed");
        resources.add(() -> {
            throw cleanup;
        });
        IOException startup = new IOException("startup failed");
        resources.closeAfterFailure(startup);
        assertEquals(1, startup.getSuppressed().length);
        assertSame(cleanup, startup.getSuppressed()[0].getCause());
    }
}
