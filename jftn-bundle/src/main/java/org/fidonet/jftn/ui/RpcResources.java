package org.fidonet.jftn.ui;

import java.util.ArrayDeque;
import java.util.Deque;

/** Owns resources in acquisition order and preserves failures during cleanup. */
public final class RpcResources implements AutoCloseable {
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();

    public <T extends AutoCloseable> T add(T resource) {
        resources.push(resource);
        return resource;
    }

    public void closeAfterFailure(Exception failure) {
        try {
            close();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    @Override
    public synchronized void close() {
        RuntimeException failure = null;
        while (!resources.isEmpty()) {
            failure = closeResource(resources.pop(), failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException closeResource(AutoCloseable resource,
                                                  RuntimeException failure) {
        try {
            resource.close();
            return failure;
        } catch (Exception cleanup) {
            if (failure == null) {
                return new IllegalStateException("Unable to close RPC resources", cleanup);
            }
            failure.addSuppressed(cleanup);
            return failure;
        }
    }
}
